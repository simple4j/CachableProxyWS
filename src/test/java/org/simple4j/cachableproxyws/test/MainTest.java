package org.simple4j.cachableproxyws.test;

import java.lang.invoke.MethodHandles;
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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public class MainTest
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	static WireMockServer targetServiceWM = null;
	static WireMockServer cacheServiceWM1 = null;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		targetServiceWM = new WireMockServer(WireMockConfiguration.options().bindAddress("localhost").port(2001).withRootDirectory(MainTest.class.getResource("/targetServiceWM").getPath()).notifier(new Slf4jNotifier(true)));
		targetServiceWM.start();
		String path = MainTest.class.getResource("/cacheServiceWM").getPath();
		LOGGER.info("path for cache WM {}", path);
		cacheServiceWM1 = new WireMockServer(WireMockConfiguration.options().bindAddress("localhost").port(2002).withRootDirectory(path).notifier(new Slf4jNotifier(true)));
		cacheServiceWM1.start();
		
		Main.main(null);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		if(targetServiceWM != null)
			targetServiceWM.shutdownServer();
		if(cacheServiceWM1 != null)
			cacheServiceWM1.shutdownServer();
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
