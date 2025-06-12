package org.simple4j.cachableproxyws.japi.impl;

import java.util.Map;

import org.simple4j.apiaopvalidator.beans.AppResponse;
import org.simple4j.cachableproxyws.japi.CallerWrapper;
import org.simple4j.wsclient.caller.ICaller;

public class CallerWrapperImpl implements CallerWrapper
{

	private ICaller caller = null;
	
	public ICaller getCaller()
	{
		return caller;
	}

	public void setCaller(ICaller caller)
	{
		this.caller = caller;
	}

	@Override
	public AppResponse<Map<String, Object>> call(Object param)
	{
		Map<String, Object> resp = this.getCaller().call(param);
		AppResponse<Map<String, Object>> ret = new AppResponse<Map<String,Object>>();
		ret.responseObject = resp;
		return ret ;
	}

}
