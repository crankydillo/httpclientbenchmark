package com.ss.benchmark.httpclient.common;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

/**
 * Created by ssrinivasa on 12/13/18.
 */
public class BenchmarkCommon extends MetricsHelper {

    public static final String ECHO_DELAY_BASE_URL = "/echodelayserv/echo";
    public static final String ECHO_DELAY_POST_SHORT_URL = "/echodelayserv/echo/short";
    public static final String ECHO_DELAY_POST_LONG_URL = "/echodelayserv/echo/long";
    public static final String TEST_ENDPOINT = "/echodelayserv/echo/testmonkey";
    public static final String ECHO_DELAY_SETUP_FULL_URL = "/echodelayserv/delay/uniform?min=1ms&max=2ms";

    //All times are milliseconds unless otherwise noted
    public static final int MAX_CONNECTION_POOL_SIZE = 200;
    public static final int CONNECTION_TTL = 60_000;
    public static final int CONNECT_TIMEOUT = 500;
    public static final int CONNECTION_REQUEST_TIMEOUT = 2_000;
    public static final int READ_TIMEOUT = 2_000;

    protected static final int EXECUTIONS = 10_000;

    @BeforeTest
    public void initializeTest() {
        initializeMetrics();
    }

    // TODO : use atomic interger rather than int in async execute
    @AfterTest
    public void finalizeTest(){
        dumpMetrics();
        closeMetrics();
    }

    protected String echoURL(String echophrase) {
        return BenchmarkCommon.ECHO_DELAY_BASE_URL + "/" + echophrase;
    }

    protected String getBaseUrl() {
        return "http://localhost:8080";
    }

}
