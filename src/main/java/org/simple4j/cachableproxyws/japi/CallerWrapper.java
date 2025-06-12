package org.simple4j.cachableproxyws.japi;

import java.util.Map;

import org.simple4j.apiaopvalidator.beans.AppResponse;

public interface CallerWrapper
{
	public AppResponse<Map<String, Object>>  call(java.lang.Object param);
}
