package org.simple4j.cachableproxyws.test;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.simple4j.cachableproxyws.Main;
import org.simple4j.wsfeeler.model.TestCase;
import org.simple4j.wsfeeler.model.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainTest
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	static String targetServiceWMPort="2001";
	static String targetServiceWMRootDir = "/targetServiceWM";
	static String cacheServiceWMPort="2002";
	static String cacheServiceWMRootDir = "/cacheServiceWM";
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		
		startWireMockService(targetServiceWMPort, targetServiceWMRootDir);

		startWireMockService(cacheServiceWMPort, cacheServiceWMRootDir);

		Main.main(null);
	}

	private static void startWireMockService(String port, String rootDir)
			throws IOException
	{
		String buildDir = System.getProperty("buildDir");
		String wiremockjar = System.getProperty("wiremockjar");
		String buildTestOutputDirectory = System.getProperty("buildTestOutputDirectory");
		
		ProcessBuilder pb = new ProcessBuilder("java", "-jar", buildDir+"/"+wiremockjar,
				"--disable-gzip", "true", "--bind-address", "localhost", "--port", port,
				"--root-dir", buildTestOutputDirectory+rootDir, "--verbose");
		
		// Merge stderr into stdout, then inherit
		pb.redirectErrorStream(true);   // merges stderr into stdout
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		Process process = pb.start();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		shutdownWiremock(targetServiceWMPort);
		shutdownWiremock(cacheServiceWMPort);
	}

	private static void shutdownWiremock(String port)
	{

        HttpClient client = HttpClient.newHttpClient();

        String json = "";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+port+"/__admin/shutdown"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
		try
		{
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
			LOGGER.info("Status : " + response.statusCode());
			LOGGER.info("Body   : " + response.body());		
		} catch (IOException e)
		{
			LOGGER.warn("",e);
		} catch (InterruptedException e)
		{
			LOGGER.warn("",e);
		}

	}

	@Before
	public void setUp() throws Exception
	{
	}

	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void test()
	{
		TestSuite ts = new TestSuite();
		ts.setTestApplicationContext(Main.getContext());
		boolean success = ts.execute();
		List<String> tcPaths = new ArrayList<String>();
		if(ts.getFailedTestCases() != null)
		{
			for (Iterator<TestCase> iterator = ts.getFailedTestCases().iterator(); iterator.hasNext();)
			{
				TestCase tc = (TestCase) iterator.next();
				tcPaths.add(tc.getName());
			}
		}
		Assert.assertTrue("Failed testcases are :" + tcPaths, success);
	}
}
