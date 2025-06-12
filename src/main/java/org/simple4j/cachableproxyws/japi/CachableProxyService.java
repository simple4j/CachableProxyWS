package org.simple4j.cachableproxyws.japi;

import java.util.Map;

import org.simple4j.apiaopvalidator.beans.AppResponse;

public interface CachableProxyService
{

	AppResponse<Map<String, Object>> callService(String callerId, String userId,  Map<String, String> queryParamMap);
}
