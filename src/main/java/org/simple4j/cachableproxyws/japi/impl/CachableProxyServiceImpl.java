package org.simple4j.cachableproxyws.japi.impl;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.simple4j.apiaopvalidator.beans.AppResponse;
import org.simple4j.cachableproxyws.japi.CachableProxyService;
import org.simple4j.cachableproxyws.japi.CallerWrapper;
import org.simple4j.wsclient.caller.ICaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CachableProxyServiceImpl implements CachableProxyService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Map<String, ApplicationContext> api2AC = new ConcurrentHashMap<String, ApplicationContext>();

    private String apiIdQueryParamName = "API";

    public String getApiIdQueryParamName()
	{
		return apiIdQueryParamName;
	}

	public void setApiIdQueryParamName(String apiIdQueryParamName)
	{
		this.apiIdQueryParamName = apiIdQueryParamName;
	}

	@Override
	public AppResponse<Map<String, Object>> callService(String callerId, String userId, Map<String, String> queryParamMap)
	{
		String apiId = queryParamMap.get(this.getApiIdQueryParamName());
		CallerWrapper callerWrapper = this.getCallerWrapper(apiId);
		AppResponse<Map<String, Object>> appRes = callerWrapper.call(queryParamMap);
		AppResponse<Map<String, Object>> ret = new AppResponse<Map<String, Object>>();
		if(appRes.errorDetails != null)
		{
			ret.errorDetails = appRes.errorDetails;
			return ret;
		}

		ret.responseObject = appRes.responseObject;
		return ret ;
	}

	private CallerWrapper getCallerWrapper(String apiId)
	{
		ApplicationContext ac = null;
		if(!this.api2AC.containsKey(apiId))
		{
			ac = new ClassPathXmlApplicationContext("/apiconfigs/" + apiId+".xml");
			this.api2AC.put(apiId, ac);
		}
		else
		{
			ac = this.api2AC.get(apiId);
		}
		
		return ac.getBean(apiId, CallerWrapper.class);
	}

}
