package com.ss.test.httpclient.reactornetty;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.testng.Assert.assertEquals;

/**
 * @author sharath.srinivasa
 */
@Test
public class ReactorNettyHCPerformanceTests {

    private static final String echoEndpointResponse = " {\n" +
            "        \"path\": \"chivas\",\n" +
            "                \"planned-delay\": 464,\n" +
            "                \"real-delay\": 458\n" +
            "        }";

    private static final Logger logger = LoggerFactory.getLogger(ReactorNettyHCPerformanceTests.class);

    private final String ECHO_DELAY_BASE_URL = "/echodelayserv/echo/";

    private static final int EXECUTIONS = 100000;

    HttpClient reactorNettyClient;

    AtomicInteger count = new AtomicInteger(0);
    private Set<CountDownLatch> latches = new HashSet<>();
    private Lock lock = new ReentrantLock();

    @BeforeTest
    public void initializeTest() {

        // build client instance
        reactorNettyClient = HttpClient
                .create()
                .baseUrl(this.getBaseUrl());
    }

    private String getBaseUrl() {
        return "http://localhost:8080";
    }

    @AfterTest
    public void finalizeTest(){
        for (CountDownLatch latcher : latches) {
            try {
                latcher.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test(invocationCount = 200, threadPoolSize = 100, priority = 0)
    public void warmupBlocking() {
        //using blocking requests here
        String uuid = UUID.randomUUID().toString();

        HttpClient.RequestSender requestSender = reactorNettyClient
                .request(HttpMethod.GET)
                .uri(echoURL(uuid));

        try {
            String result = executeSync(requestSender);
            assertEquals(result, echoEndpointResponse);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test(priority = 2)
    public void testBlockingGET() {
        for (int i = 0; i < EXECUTIONS; i++){
            String uuid = UUID.randomUUID().toString();

            HttpClient.RequestSender requestSender = reactorNettyClient
                    .request(HttpMethod.GET)
                    .uri(echoURL(uuid));

            try {
                String result = executeSync(requestSender);
                assertEquals(result, echoEndpointResponse);
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test(priority = 3)
    public void testNonBlockingGET() {
        logger.info("testNonBlockingGET start");
        CountDownLatch latcher = new CountDownLatch(EXECUTIONS);
        for (int i = 0; i < EXECUTIONS; i++) {
            String uuid = UUID.randomUUID().toString();

            HttpClient.RequestSender requestSender = reactorNettyClient
                    .request(HttpMethod.GET)
                    .uri(echoURL(uuid));

            executeAsync(requestSender, latcher, i)
            .subscribe(s -> {});
        }
        try {
            latcher.await();
        } catch (InterruptedException e) {
            logger.error("hey, don't interrupt me!");
            e.printStackTrace();
        }
        logger.info("testNonBlockingGET end");
    }

    public String executeSync(HttpClient.ResponseReceiver<?> request) {
        return request
                .responseSingle((res, body) -> {
                    if (res.status().code() != 200) {
                       Mono.error(new IllegalStateException("Unexpected response code : " + res.status().code()));
                    }
                    return body;
                })
                .map(byteBuf -> byteBuf.toString(StandardCharsets.UTF_8))
                .block();
    }

    public Mono<String> executeAsync(
            final HttpClient.ResponseReceiver<?> request,
            final CountDownLatch latch,
            final int counter
    ) {
        return request
                .responseSingle((res, body) -> {
                    if (res.status().code() != 200) {
                        Mono.error(new IllegalStateException("Unexpected response code : " + res.status().code()));
                    }
                    return body;
                })
                .map(byteBuf -> byteBuf.toString(StandardCharsets.UTF_8))
                .log()
                .doOnTerminate(() -> {
                    if (latch != null) latch.countDown();
                })
                .doOnError(throwable -> {
                    logger.error("Failed : {}", counter);
                    throwable.printStackTrace();
                });
    }

    protected String echoURL(String echophrase){return ECHO_DELAY_BASE_URL + "/" + echophrase;}
}
