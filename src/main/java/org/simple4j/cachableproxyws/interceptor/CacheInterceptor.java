package org.simple4j.cachableproxyws.interceptor;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.beanutils.PropertyUtils;
import org.simple4j.cachableproxyws.Main;
import org.simple4j.cachableproxyws.utils.JSONUtil;
import org.simple4j.wsclient.caller.CallerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class CacheInterceptor implements MethodInterceptor
{

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .serializationInclusion(Include.NON_NULL)
            .build();

	private CallerFactory cacheGetCallerFactory;

	private CallerFactory cacheSetCallerFactory;

	private List<String> ignoteParameters = new ArrayList<String>();

    private String apiIdQueryParamName = "API";

	private long retreivalTimeoutMilliSec = 75;

	private int cacheTimeToLiveMilliSec = 0;
	
	private String cacheServiceKeyParameterName = "key";
	
	private String cacheServiceValueParameterName = "value";
	
	private String cacheServiceTimeToLiveParameterName = "timeToLiveMillisec";
	
	private String cacheServiceResponsePathForCacheValue = null;
	
	private String targetServiceStatusPath = null;
	
	private String targetServiceSuccessStatusRegex = null;
	

	public CallerFactory getCacheGetCallerFactory()
	{
		return cacheGetCallerFactory;
	}

	public void setCacheGetCallerFactory(CallerFactory cacheGetCallerFactory)
	{
		this.cacheGetCallerFactory = cacheGetCallerFactory;
	}

	public CallerFactory getCacheSetCallerFactory()
	{
		return cacheSetCallerFactory;
	}

	public void setCacheSetCallerFactory(CallerFactory cacheSetCallerFactory)
	{
		this.cacheSetCallerFactory = cacheSetCallerFactory;
	}

	public List<String> getIgnoteParameters()
	{
		return ignoteParameters;
	}

	public void setIgnoteParameters(List<String> ignoteParameters)
	{
		this.ignoteParameters = ignoteParameters;
	}

	public long getRetreivalTimeoutMilliSec()
	{
		return retreivalTimeoutMilliSec;
	}

	public void setRetreivalTimeoutMilliSec(long retreivalTimeoutMilliSec)
	{
		this.retreivalTimeoutMilliSec = retreivalTimeoutMilliSec;
	}

	public int getCacheTimeToLiveMilliSec()
	{
		return cacheTimeToLiveMilliSec;
	}

	public void setCacheTimeToLiveMilliSec(int cacheTimeToLiveMilliSec)
	{
		this.cacheTimeToLiveMilliSec = cacheTimeToLiveMilliSec;
	}

    public String getApiIdQueryParamName()
	{
		return apiIdQueryParamName;
	}

	public void setApiIdQueryParamName(String apiIdQueryParamName)
	{
		this.apiIdQueryParamName = apiIdQueryParamName;
	}

	public String getCacheServiceKeyParameterName()
	{
		return cacheServiceKeyParameterName;
	}

	public void setCacheServiceKeyParameterName(String cacheServiceKeyParameterName)
	{
		this.cacheServiceKeyParameterName = cacheServiceKeyParameterName;
	}

	public String getCacheServiceValueParameterName()
	{
		return cacheServiceValueParameterName;
	}

	public void setCacheServiceValueParameterName(String cacheServiceValueParameterName)
	{
		this.cacheServiceValueParameterName = cacheServiceValueParameterName;
	}

	public String getCacheServiceTimeToLiveParameterName()
	{
		return cacheServiceTimeToLiveParameterName;
	}

	public void setCacheServiceTimeToLiveParameterName(String cacheServiceTimeToLiveParameterName)
	{
		this.cacheServiceTimeToLiveParameterName = cacheServiceTimeToLiveParameterName;
	}

	public String getCacheServiceResponsePathForCacheValue()
	{
		return cacheServiceResponsePathForCacheValue;
	}

	public void setCacheServiceResponsePathForCacheValue(String cacheServiceResponsePathForCacheValue)
	{
		this.cacheServiceResponsePathForCacheValue = cacheServiceResponsePathForCacheValue;
	}

	public String getTargetServiceStatusPath()
	{
		return targetServiceStatusPath;
	}

	public void setTargetServiceStatusPath(String targetServiceStatusPath)
	{
		this.targetServiceStatusPath = targetServiceStatusPath;
	}

	public String getTargetServiceSuccessStatusRegex()
	{
		return targetServiceSuccessStatusRegex;
	}

	public void setTargetServiceSuccessStatusRegex(String targetServiceSuccessStatusRegex)
	{
		this.targetServiceSuccessStatusRegex = targetServiceSuccessStatusRegex;
	}

	public Object invoke(MethodInvocation methodInvocation) throws Throwable
	{
		logger.info("Entering CacheInterceptor.invoke");
		logger.debug("parameter methodInvocation={}", methodInvocation);

		Object methodReturn = null;
		final String targetMethodName = methodInvocation.getMethod().getName();

		logger.trace("Target Method Name in invoke: {}", targetMethodName);

		Object[] arguments = methodInvocation.getArguments();
		
		Map<String, String> parameters = (Map<String, String>) arguments[0];
		String api = parameters.get(this.getApiIdQueryParamName());
		TreeMap<String, String> sortedParameters = new TreeMap<String, String> ();
		sortedParameters.putAll(parameters);
		
		StringBuilder cacheKeyParametersSB = new StringBuilder();
		cacheKeyParametersSB.append(api).append('_');
		
		for (Iterator<String> iterator = sortedParameters.keySet().iterator(); iterator.hasNext();)
		{
			String key = (String) iterator.next();
			if(key.equals(this.getApiIdQueryParamName()) || this.getIgnoteParameters().contains(key))
			{
				logger.debug("skipping key:{}", key);
			}
			else
			{
				this.appendToCacheKey(cacheKeyParametersSB, key, parameters.get(key));
			}
		}

		String cacheKey = cacheKeyParametersSB.toString();
		
		Map<String, String> callerParam = new HashMap<String, String>();
		callerParam.put(this.getCacheServiceKeyParameterName(), cacheKey);
		
		String cacheValue = null;
		if(!Main.getRefreshFlag())
		{
			try
			{
				Map<String, Object> getCacheCallResponse = this.getCacheGetCallerFactory().getCaller().call(callerParam);
				logger.debug("Response from cache {}", getCacheCallResponse);
				logger.debug("CacheServiceResponsePathForCacheValue {}", this.getCacheServiceResponsePathForCacheValue());
				cacheValue = (String) PropertyUtils.getNestedProperty(getCacheCallResponse, this.getCacheServiceResponsePathForCacheValue());
			}
			catch(Throwable t)
			{
				logger.warn("Error while fetching value from cache service", t);
			}
		}
		if(cacheValue == null)
		{
			methodReturn = methodInvocation.proceed();
			Map<String, Object> targetServiceCallResponse = (Map<String, Object>) methodReturn;
			
			String targetServiceStatus = (String) PropertyUtils.getNestedProperty(targetServiceCallResponse, this.getTargetServiceStatusPath());
			if(targetServiceStatus.matches(this.getTargetServiceSuccessStatusRegex()))
			{
//				cacheValue = (String) PropertyUtils.getNestedProperty(targetServiceCallResponse, this.getTargetServiceResponsePathForCacheValue());
				cacheValue = OBJECT_MAPPER.writeValueAsString(targetServiceCallResponse);
				cacheValue = JSONUtil.escape(cacheValue);
				Map<String,String> setCacheParam = new HashMap<String, String>();
				setCacheParam.put(this.getCacheServiceKeyParameterName(), cacheKey);
				setCacheParam.put(this.getCacheServiceValueParameterName(), cacheValue);
				setCacheParam.put(this.getCacheServiceTimeToLiveParameterName(), ""+this.getCacheTimeToLiveMilliSec());
				try
				{
					this.getCacheSetCallerFactory().getCaller().call(setCacheParam);
				}
				catch(Throwable t)
				{
					logger.warn("Error while setting value to cache service", t);
				}
			}
		}
		else
		{
			logger.debug("cacheValue {}", cacheValue);
			cacheValue = JSONUtil.unescape(cacheValue);
			methodReturn = OBJECT_MAPPER.readValue(cacheValue, Map.class);
		}
		
		return methodReturn;
	}

	private void appendToCacheKey(StringBuilder sb, String keyName, String keyValue)
	{
		String finalKeyName = getURLSafeString(keyName);
		String finalKeyValue = getURLSafeString(keyValue);
		sb.append('$').append(finalKeyName).append('-').append(finalKeyValue);
	}

	private String getURLSafeString(String inString)
	{
		String ret = inString;
		try
		{
			String urlEncodedKeyName = URLEncoder.encode(inString, StandardCharsets.UTF_8.toString());
			if(!inString.equals(urlEncodedKeyName))
			{
				//keyName is not URL safe. Will do Base64URL encoding
				// Not using URL encoding as the web container may automatically decode when the string is part of the query param.
				// But the same value will not be transformed by the web container when sent in the JSON body of POST call.
				logger.info("{} is not URL safe. Base64URL encoding.", inString);
				ret = Base64.getUrlEncoder().withoutPadding().encodeToString(inString.getBytes(StandardCharsets.UTF_8));			
				logger.info("Base64URL encoded value: {}.", ret);
			}
		}
		catch(UnsupportedEncodingException e)
		{
			logger.warn("Encoding failed. May cause issue when sending value {} over htt.", inString, e);
		}
		return ret;
	}
	

}
