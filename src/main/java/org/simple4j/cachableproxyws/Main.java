package org.simple4j.cachableproxyws;

import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.simple4j.apiaopvalidator.beans.AppResponse;
import org.simple4j.apiaopvalidator.beans.ErrorDetails;
import org.simple4j.cachableproxyws.beans.ErrorType;
import org.simple4j.cachableproxyws.beans.HealthCheck;
import org.simple4j.cachableproxyws.japi.CachableProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.Javalin;
import io.javalin.http.Context;


/**
 * This class holds the main method and the entry point for startup of the application.
 */
public class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static ApplicationContext context;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .serializationInclusion(Include.NON_NULL)
            .build();
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String JAPI_RETURN_OBJECT = "returnObject";
    private static final String START_TIME_MILLISEC = "startTimeMillisec";

    private static Main main = null;
    
    private static ThreadLocal<Boolean> refreshFlag = new ThreadLocal<Boolean>(); 
    
    private int listenerPortNumber = 2410;
    private int listenerIdleTimeoutMillis = 600000;
    private String urlBase = "/cachableproxyws/V1";

    private String userIdHeader = "userId";
    private String hostName = null;
    private HealthCheck.Status healthCheckStatus = HealthCheck.Status.HEALTHY; 
    
    private Map<String, Map<String, Integer>> errorType2HTTPStatusMapping = null;
    
	private String groupId = null;
	private String artifactId = null;
	private String version = null;
	
	private CachableProxyService cachableProxyService = null; 

	/**
     * This method being the entry point, it initializes all the beans with dependency injection,
     *  initializes web service routes
     * @param args
     */
    public static void main(String[] args)
    {
        LOGGER.info("CachableProxyWS is starting, please wait...");

        context = new ClassPathXmlApplicationContext("appContext.xml");

        main = context.getBean("main", Main.class);
        main.init();

		Javalin javalin = Javalin.create();

        javalin.before(ctx ->
        {
            long startTimeMillisec = System.currentTimeMillis();
			String requestId = startTimeMillisec + "@" + main.hostName;
            ctx.attribute(START_TIME_MILLISEC, startTimeMillisec);

            String headerRequestId = ctx.header(REQUEST_ID_KEY);
            if (StringUtils.isNotBlank(headerRequestId))
            {
                requestId = headerRequestId;
            }

            MDC.put(REQUEST_ID_KEY, requestId);
            String body = "";
            String query = ctx.queryString();
            ctx.header("content-type", "application/json");
            LOGGER.info("Start request url is {}, method is {}, {}{}", ctx.url(), ctx.method(),
                    StringUtils.isNotBlank(body) ? ("body is " + body + ", ") : "",
                    StringUtils.isNotBlank(query) ? ("query string is " + query) : "");
        });

        javalin.after(ctx ->
        {
            
            Object returnObject = ctx.attribute(JAPI_RETURN_OBJECT);
            if(returnObject instanceof AppResponse)
            {
                AppResponse appResponse = ctx.attribute(JAPI_RETURN_OBJECT);
                ctx.status(main.getStatusCode(appResponse));
            }
            else
            {
//            	LOGGER.info("Setting HTTP code 200");
//                ctx.status(200);
            }
            
            setHeader(ctx);
            finishCall(ctx);
        });

        javalin.exception(Exception.class, (e, ctx) ->
        {
            LOGGER.error("Status {}", ctx.statusCode());
            LOGGER.error("Unhandled exception", e);
            setHeader(ctx);
            ctx.status(500);
            ErrorDetails errorDetails = new ErrorDetails();
            errorDetails.errorId = MDC.get(REQUEST_ID_KEY);
            errorDetails.errorType = ErrorType.RUNTIME_ERROR.toString();
            errorDetails.errorDescription = e.toString();

            finishCall(ctx);

            try
            {
                ctx.result(OBJECT_MAPPER.writeValueAsString(errorDetails));
            }
            catch (JsonProcessingException e1)
            {
                LOGGER.error("Marshaling error:", e1);
                throw new RuntimeException("Marshaling error:", e1);
            }
        });

        javalin.post(main.getUrlBase()+"/serverhealth.json", ctx -> 
        {
            String bodyJson = ctx.body();
            HealthCheck healthCheckReq = null;
            try
            {
                healthCheckReq = OBJECT_MAPPER.readValue(bodyJson, HealthCheck.class);
            }
            catch(Exception e)
            {
                throw new RuntimeException("Error parsing request body <" + bodyJson +">",  e);
            }
            main.setHealthCheckStatus(healthCheckReq.status);
            setHeader(ctx);
            ctx.result("{}");
        });

        javalin.get(main.getUrlBase()+"/serverhealth.json", ctx -> 
        {
            HealthCheck healthCheckRes = new HealthCheck();
            healthCheckRes.status = main.getHealthCheckStatus();
            healthCheckRes.groupId = main.getGroupId();
            healthCheckRes.artifactId = main.getArtifactId();
            healthCheckRes.version = main.getVersion();
            setHeader(ctx);
            ctx.result(OBJECT_MAPPER.writeValueAsString(healthCheckRes));
        });

        javalin.get(main.getUrlBase()+"/proxy.json", ctx -> 
        {
        	Main.refreshFlag.set(false);
            callService(ctx);
        });

        javalin.get(main.getUrlBase()+"/refresh/proxy.json", ctx -> 
        {
        	Main.refreshFlag.set(true);
            callService(ctx);
        });

        javalin.start(main.getListenerPortNumber());
        
    	LOGGER.info("Start up completed");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        	LOGGER.info("Shutdown hook called");
        	javalin.stop();
        }));

        javalin.events(event -> {
            event.serverStopping(() -> {
            	LOGGER.info("Stopping event");
            });
            event.serverStopped(() -> {
            	LOGGER.info("Stopped event");
            });
        });
    	LOGGER.info("End of main method");
    }

	private static void callService(@NotNull Context ctx) throws JsonProcessingException
	{
		AppResponse<Map<String,Object>> ret = null;
		String callerId = getStringWithEmptyCheck(ctx.header("callerId"), null);
		String userId = getStringWithEmptyCheck(ctx.header(main.getUserIdHeader()), null);
		Map<String, List<String>> queryParamMapList = ctx.queryParamMap();
		Map<String, String> queryParamMap = new HashMap<String, String>();
		for (Iterator<String> iterator = queryParamMapList.keySet().iterator(); iterator.hasNext();)
		{
			String key = iterator.next();
			if(queryParamMapList.get(key) == null || queryParamMapList.get(key).size() == 0)
			{
		        queryParamMap.put(key, "");
			}
			else
			{
		        queryParamMap.put(key, queryParamMapList.get(key).get(0));
			}
			
		}
		
		//TODO: need to change the variable names to paramMap and included configured set of headers in it
		ret = main.getCachableProxyService().callService(callerId, userId, queryParamMap);
		
		LOGGER.info("ret:{}", ret);

		//setting AppResponse object for usage in javalin.after handler
		ctx.attribute(JAPI_RETURN_OBJECT, ret);
		if(ret.errorDetails != null)
		{
			ctx.result(OBJECT_MAPPER.writeValueAsString(ret.errorDetails));
		    return;
		}
		ctx.status(Integer.parseInt((String) ret.responseObject.get("HTTP_STATUS_CODE")));
		ctx.result(OBJECT_MAPPER.writeValueAsString(ret.responseObject.get("HTTP_RESPONSE_OBJECT")));
	}

	private static String getStringWithEmptyCheck(String in, String defaultValue)
	{
		String ret = defaultValue;
		if(in != null && in.trim().length() > 0)
		{
			ret = in;
		}
		return ret;
	}

    private void init()
    {
    	OBJECT_MAPPER.registerModule(new JavaTimeModule());
    	try
		{
			this.hostName = InetAddress.getLocalHost().getHostName();
		}
    	catch (UnknownHostException e)
		{
			throw new RuntimeException(e);
		}
    }

    private int getStatusCode(AppResponse appResponse)
    {
        if(appResponse.errorDetails == null)
        {
        	if(appResponse.responseObject instanceof Map)
        	{
        		Map responseObject = (Map) appResponse.responseObject;
        		if(responseObject.containsKey("HTTP_STATUS_CODE"))
        		{
        			try
        			{
	        			return Integer.parseInt(""+responseObject.get("HTTP_STATUS_CODE"));
        			}
        			catch(Throwable t)
        			{
        				LOGGER.warn("Error while parsing http code", t);
        			}
        		}
        	}
            return 200;
        }
        LOGGER.info("Error id is {}", appResponse.errorDetails.errorId);
        Map<String, Integer> apiCallName2HTTPStatusMapping = this.getErrorType2HTTPStatusMapping().get(appResponse.errorDetails.errorType.toString());
        if(apiCallName2HTTPStatusMapping != null)
        {
            if(apiCallName2HTTPStatusMapping.get(appResponse.apiCallName) != null)
            {
               return apiCallName2HTTPStatusMapping.get(appResponse.apiCallName);
            }
            else
                if(apiCallName2HTTPStatusMapping.get("") != null)
                {
                    return apiCallName2HTTPStatusMapping.get("");
                }
                else
                {
                    LOGGER.error("Missing mapping for apiCallName {} or for fallback", appResponse.apiCallName);
                }
        }
        else
        {
            LOGGER.error("Missing mapping for error type {}", appResponse.errorDetails.errorType);
        }
        throw new RuntimeException("main.getErrorType2HTTPStatusMapping() not configured for :"+appResponse);
    }

    private static void setHeader(Context ctx)
    {
        ctx.contentType("application/json;charset=utf-8");
        ctx.header("Content-Language", "en");
        String requestId = MDC.get(REQUEST_ID_KEY);
        if(requestId != null)
			ctx.header("requestid", requestId);
    }

    private static void finishCall(Context ctx)
    {
        long startTimeMillisec = ctx.attribute(START_TIME_MILLISEC);
        LOGGER.info("End request url is {}, method is {}, time {}s, query string is {}, response code is {}", ctx.url(),
        		ctx.method(), (System.currentTimeMillis() - startTimeMillisec) / 1000.0,
        		ctx.queryString(), ctx.statusCode());
        MDC.clear();
    }
    
    public static ApplicationContext getContext()
	{
		return context;
	}
    
    public static boolean getRefreshFlag()
    {
    	return Main.refreshFlag.get();
    }

	public int getListenerPortNumber()
    {
        return listenerPortNumber;
    }

    public void setListenerPortNumber(int listenerPortNumber)
    {
        this.listenerPortNumber = listenerPortNumber;
    }

    public int getListenerIdleTimeoutMillis()
    {
        return listenerIdleTimeoutMillis;
    }

    public void setListenerIdleTimeoutMillis(int listenerIdleTimeoutMillis)
    {
        this.listenerIdleTimeoutMillis = listenerIdleTimeoutMillis;
    }

    public String getUrlBase()
    {
        return urlBase;
    }

    public void setUrlBase(String urlBase)
    {
        this.urlBase = urlBase;
    }

    public Map<String, Map<String, Integer>> getErrorType2HTTPStatusMapping()
    {
        return errorType2HTTPStatusMapping;
    }

    /**
     * This setter will set the mapping for AppResponse to HTTP codes.
     * The mapping is set via dependency injection
     * {
     *      "SUCCESS" : 
     *      {
     *          "" : "200",
     *          "&lt;API name&gt;" : "201"
     *      },
     *      "PARAMETER_ERROR" : 
     *      {
     *          "" : "412",
     *      }
     * }
     */
    public void setErrorType2HTTPStatusMapping(
            Map<String, Map<String, Integer>> errorType2HTTPStatusMapping)
    {
        this.errorType2HTTPStatusMapping = errorType2HTTPStatusMapping;
    }

	public CachableProxyService getCachableProxyService()
	{
		if(this.cachableProxyService == null)
			throw new RuntimeException("cachableproxyService not configured");
		return cachableProxyService;
	}

	public void setCachableProxyService(CachableProxyService cachableProxyService)
	{
		this.cachableProxyService = cachableProxyService;
	}

	public String getUserIdHeader()
	{
		return userIdHeader;
	}

	/**
	 * This method can be used to set the header name for sending userid by the authentication systems.
	 * 
	 * @param userIdHeader
	 */
	public void setUserIdHeader(String userIdHeader)
	{
		this.userIdHeader = userIdHeader;
	}

	public HealthCheck.Status getHealthCheckStatus()
	{
		return healthCheckStatus;
	}

	public void setHealthCheckStatus(HealthCheck.Status healthCheckStatus)
	{
		this.healthCheckStatus = healthCheckStatus;
	}

	public String getGroupId()
	{
		return groupId;
	}

	public void setGroupId(String groupId)
	{
		this.groupId = groupId;
	}

	public String getArtifactId()
	{
		return artifactId;
	}

	public void setArtifactId(String artifactId)
	{
		this.artifactId = artifactId;
	}

	public String getVersion()
	{
		return version;
	}

	public void setVersion(String version)
	{
		this.version = version;
	}

}
