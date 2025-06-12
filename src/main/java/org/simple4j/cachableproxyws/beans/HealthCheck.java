package org.simple4j.cachableproxyws.beans;

public class HealthCheck
{
    public String groupId = null;
    public String artifactId = null;
    public String version = null;
    public Status status = null;

    public enum Status
    {
        HEALTHY, UNHEALTHY
    }
}
