package com.ss.benchmark.httpclient.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

import com.codahale.metrics.*;
import payloads.Payloads;

/**
 * Base class for perform some rudimentary performance tests of
 * HTTP client libraries.  Orchestrated by TestNG.
 * <p></p>
 * Each client library needs to implement {@link HttpClientEngine}, which
 * exposes both synchronous and asynchronous modes.
 * </p>
 * The test names have the following convention:
 * <dl>
 * <dt>testBlockingSyncXyx</dt>
 * <dd>Test the client's synchronous mode in blocking scenarios</dd>
 * <dt>testBlockingAsyncXyx</dt>
 * <dd>Test the client's asynchronous mode in blocking scenarios</dd>
 * <dt>testNonBlockingAsyncXyx</dt>
 * <dd>Test the client's asynchronous mode in non-blocking scenarios</dd>
 * </dl>
 * </dl>
 * @author sharath.srinivasa
 */
@Test(groups = "performance")
public abstract class BasePerformanceTest extends MetricsGatheringTest {

    public static class BlockingVars {
        protected static final int EXECUTIONS = 10_000;
        protected static final int WORKERS = 40;
    }

    public static class NonBlockingVars {
        static final int EXECUTIONS = 1_000;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BasePerformanceTest.class);

    // These blockingLatches are for the blocking cases.
    private ConcurrentHashMap<String, CountDownLatch> blockingLatches = new ConcurrentHashMap<>();

    private Set<CountDownLatch> nonBlockingLatches = new HashSet<>();

    /**
     * HTTP client under test.
     */
    private HttpClientEngine client;

    /**
     * HTTP client libraries implement this to be tested.
     */
    protected abstract HttpClientEngine getClient();

    @BeforeTest
    public void beforeTest() {
        client = getClient();
        client.createClient(Config.TargetServer.HOST, Config.TargetServer.PORT);
    }

    @AfterTest
    public void afterTest() throws IOException {
        client.close();
    }

    @BeforeMethod
    public void beforeMethod() {
    }

    @AfterMethod
    public void afterMethod() {
        // Yes, this sucks, but I haven't thought of a low-cost refactor.
        Exceptions.rethrowChecked(() -> {
            CountDownLatch blockingLatch = blockingLatches.remove(Thread.currentThread().getName());
            if (blockingLatch != null)
                blockingLatch.await();
            for (CountDownLatch latch : nonBlockingLatches) {
                latch.await();
            }
            return null;
        });
        LOGGER.debug("Completed");
    }

    @Test(priority = 0)
    public void testWarmupCache(Method m) {
        String method = m.getName();

        LOGGER.debug("Start " + method);

        for (int i = 0; i < HttpClientEngine.MAX_CONNECTION_POOL_SIZE; i++) {
            syncGET(
                    Config.TargetServer.Urls.SHORT,
                    Payloads.SHORT,
                    metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                    metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
        }
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"blocking"})
    public void testBlockingSyncShortGET(Method m) {
        String method = m.getName();

        LOGGER.debug("Start " + method);

        syncGET(
                Config.TargetServer.Urls.SHORT,
                Payloads.SHORT,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"blocking"})
    public void testBlockingSyncShortShortPOST(Method m) {
        String method = m.getName();
        LOGGER.debug("Start " + method);

        syncPOST(
                Config.TargetServer.Urls.SHORT,
                Payloads.SHORT,
                Payloads.SHORT,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"blocking", "sync"})
    public void testBlockingSyncShortLongPOST(Method m) {
        String method = m.getName();
        LOGGER.debug("Start " + method);

        syncPOST(
                Config.TargetServer.Urls.LONG,
                Payloads.SHORT,
                Payloads.LONG,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"blocking", "sync"})
    public void testBlockingSyncLongLongPOST(Method m) {
        String method = m.getName();
        LOGGER.debug("Start " + method);

        syncPOST(
                Config.TargetServer.Urls.LONG,
                Payloads.LONG,
                Payloads.LONG,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"locking", "async"})
    public void testBlockingAsyncShortGET(Method m) {
        String method = m.getName();
        LOGGER.debug("Start " + method);

        blockingAsyncGET(
                Config.TargetServer.Urls.SHORT,
                Payloads.SHORT,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"blocking", "async"})
    public void testBlockingAsyncShortShortPOST(Method m) {
        String method = m.getName();
        LOGGER.debug("Start " + method);

        blockingAsyncPOST(
                Config.TargetServer.Urls.SHORT,
                Payloads.SHORT,
                Payloads.SHORT,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"blocking", "async"})
    public void testBlockingAsyncShortLongPOST(Method m) {
        String method = m.getName();
        LOGGER.debug("Start " + method);

        blockingAsyncPOST(
                Config.TargetServer.Urls.LONG,
                Payloads.SHORT,
                Payloads.LONG,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 1, invocationCount = BlockingVars.EXECUTIONS, threadPoolSize = BlockingVars.WORKERS, groups = {"blocking", "async"})
    public void testBlockingAsyncLongLongPOST(Method m) {
        String method = m.getName();
        LOGGER.debug("Start " + method);

        blockingAsyncPOST(
                Config.TargetServer.Urls.LONG,
                Payloads.LONG,
                Payloads.LONG,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 2, dataProvider = "nonblocking-executions", groups = {"nonblocking", "async"})
    public void testNonBlockingAsyncShortGET(Method m, String executionSizeName, Integer executions) {
        String method = parameterizedName(m, executionSizeName);
        LOGGER.debug("Start " + method);

        nonBlockingAsyncGET(
                executions,
                Config.TargetServer.Urls.SHORT,
                Payloads.SHORT,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    @Test(priority = 2, dataProvider = "nonblocking-executions", groups = {"nonblocking", "async"})
    public void testNonBlockingAsyncShortShortPOST(Method m, String executionSizeName, Integer executions) {
        String method = parameterizedName(m, executionSizeName);
        LOGGER.debug("Start " + method);

        nonBlockingAsyncPOST(
                executions,
                Config.TargetServer.Urls.SHORT,
                Payloads.SHORT,
                Payloads.SHORT,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));

    }

    @Test(priority = 2, dataProvider = "nonblocking-executions", groups = {"nonblocking", "async"})
    public void testNonBlockingAsyncLongLongPOST(Method m, String executionSizeName, Integer executions) {
        String method = parameterizedName(m, executionSizeName);
        LOGGER.debug("Start " + method);

        nonBlockingAsyncPOST(
                executions,
                Config.TargetServer.Urls.LONG,
                Payloads.LONG,
                Payloads.LONG,
                metricRegistry.timer(MetricRegistry.name(this.getClass(), method, "timing")),
                metricRegistry.counter(MetricRegistry.name(this.getClass(), method, "errorRate")));
    }

    private void nonBlockingAsyncGET(
            int executions,
            String url,
            String expectedResponsePayload,
            Timer timer,
            Counter errors
    ) {
        CountDownLatch latch = new CountDownLatch(executions);
        nonBlockingLatches.add(latch);
        for (int i = 0; i < executions; i++) {
            asyncGET(url, expectedResponsePayload, latch, timer, errors);
        }
    }

    private void nonBlockingAsyncPOST(
            int executions,
            String url,
            String payload,
            String expectedResponsePayload,
            Timer timer,
            Counter errors
    ) {
        CountDownLatch latch = new CountDownLatch(executions);
        nonBlockingLatches.add(latch);
        for (int i = 0; i < executions; i++) {
            asyncPOST(url, payload, expectedResponsePayload, latch, timer, errors);
        }
    }

    private void blockingAsyncGET(
            String url,
            String expectedResponsePayload,
            Timer timer,
            Counter errors
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        blockingLatches.putIfAbsent(Thread.currentThread().getName(), latch);
        asyncGET(url, expectedResponsePayload, latch, timer, errors);
    }

    private void blockingAsyncPOST(
            String url,
            String payload,
            String expectedResponsePayload,
            Timer timer,
            Counter errors
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        blockingLatches.putIfAbsent(Thread.currentThread().getName(), latch);
        asyncPOST(url, payload, expectedResponsePayload, latch, timer, errors);
    }

    private void asyncGET(String url, String expectedResponsePayload, CountDownLatch latch, Timer timer, Counter errors) {
        doAsync(
                () -> client.nonblockingGET(url),
                expectedResponsePayload,
                latch,
                timer,
                errors
        );
    }

    private void asyncPOST(String url, String payload, String expect, CountDownLatch latch, Timer timer, Counter errors) {
        doAsync(
                () -> client.nonblockingPOST(url, payload),
                expect,
                latch,
                timer,
                errors
        );
    }

    private void syncGET(String url, String expectedResponsePayload, Timer timer, Counter errors) {
        doSync(
                () -> client.blockingGET(url),
                expectedResponsePayload,
                timer,
                errors
        );
    }

    private void syncPOST(String url, String payload, String expectedResponsePayload, Timer timer, Counter errors) {
        doSync(
                () -> client.blockingPOST(url, payload),
                expectedResponsePayload,
                timer,
                errors
        );
    }

    // I felt like the code below was tricky enough to not duplicate it between the (a)syncXYZ cases; however,
    // if you feel this is adversely affecting performance, we can go back to duplicating it..
    private void doAsync(
            Supplier<CompletableFuture<String>> op,
            String expectedResponsePayload,
            CountDownLatch latch,
            Timer timer,
            Counter errors
    ) {
        Timer.Context ctx = timer.time();
        try {
            CompletableFuture<String> cf = op.get();
            cf.handle((result, ex) -> {
                if (ex != null || !expectedResponsePayload.equals(result)) {
                    errors.inc();
                } else {
                    ctx.stop(); // the goal is to not count error cases in the timing metrics
                }
                latch.countDown();
                return result;
            });
        } catch (Exception e) {
            errors.inc();
            latch.countDown();  // not sure on this..
        }
    }

    private void doSync(Supplier<String> op, String expectedResponsePayload, Timer timer, Counter errors) {
        Timer.Context ctx = timer.time();
        String response = null;
        try {
            response = op.get();
            ctx.stop();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        } finally {
            // I guess if an exception is thrown, this will be true..
            if (!expectedResponsePayload.equals(response)) {
                errors.inc();
            }
        }
    }

    @DataProvider(name = "nonblocking-executions")
    public static Object[][] dataProviderMethod() {
        return new Object[][] {
                { "Parameterized", NonBlockingVars.EXECUTIONS },
                { "Pool_Size"    , HttpClientEngine.MAX_CONNECTION_POOL_SIZE }
        };
    }

    private String parameterizedName(Method m, String executionSizeName) {
        return m.getName() + "-" + executionSizeName;
    }

}
