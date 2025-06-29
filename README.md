# CachableProxyWS
As the name suggests, this is a proxy service with caching woring for other web/micro services (WS/MS).
This will be mainly used in cases where the downstream WS/MS are not able to give higher performance because of some complexities. The complexity can be business logic, legacy technology stack etc.

CachableProxyWS (CPWS) uses Spring framework for dependency injection, APIAOPValidator for validation, WSClient for calling downstream target WS/MS and cache service.

The beans are managed with one main application context and a separate application context for each of the downstream APIs it proxies. This is done to prevent interference of validation and caching wiring between APIs as Spring AOP weaves the interceptors at interface or type level weaving rather than bean id based weaving.

There are 2 levels of validations. One at the entry point to the CPWS to validate common fields before passing on the data to the specific API Caller. Second level is at each of the API level at that API application context.

Please refer to the architecture.pdf

Sample requests:
Get call - /cachableproxyws/V1/proxy.json?API=${apiId}&PARAM1=${param1}&PARAM2=${param2}
Refresh call - /cachableproxyws/V1/refresh/proxy.json?API=${apiId}&PARAM1=${param1}&PARAM2=${param2}
Where  PARAM1 and PARAM2 are the parameters for the downstream WS/MS
apiId value will be used to look up the application context xml for the target API as /main/resources/common/apiconfigs/<apiId>.xml

/main/test has various test cases and samples using WSFeeler as testing framework and WireMock to mock the target WS/MS and also  cache WS/MS
