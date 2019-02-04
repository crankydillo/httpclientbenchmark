package com.ss.benchmark.httpclient.chaos;

import com.codahale.metrics.*;
import com.ss.benchmark.httpclient.common.Config;
import com.ss.benchmark.httpclient.common.Exceptions;
import com.ss.benchmark.httpclient.common.HttpClientEngine;
import com.ss.benchmark.httpclient.common.MetricsGatheringTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;
import payloads.Payloads;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Put a client through a series of exercises.  Exercises
 * typically revolve around injecting some type of 'chaos'
 * (e.g. network failure).
 */
@Test(groups = "chaos")
public abstract class BaseChaosTest extends MetricsGatheringTest {

    private static Logger LOG = LoggerFactory.getLogger(BaseChaosTest.class);

    /**
     * HTTP client under test.
     */
    private HttpClientEngine client;

    /**
     * Duration of each exercise.
     */
    private Duration exerciseDuration = Duration.ofSeconds(60);

    /**
     * Client HTTP call.  Each exercise repeatedly calls this in a rate-limited loop.
     */
    private Supplier<CompletionStage<HttpClientEngine.Resp>> clientCall =
            () -> client.get(Config.TargetServer.Urls.SHORT);

    /**
     * This Phaser is used to make sure all the client requests complete before
     * moving to the next test.  Some clients seem to get overloaded and can't
     * proceed.  May need to dig on this a bit..
     */
    // https://stackoverflow.com/a/1637030
    Phaser taskCounter = new Phaser();

    /**
     * HTTP client libraries implement this to be tested.
     */
    protected abstract HttpClientEngine getClient();

    @BeforeTest
    public void beforeTest() throws InterruptedException {
        client = getClient();
        client.createClient(Config.TargetServer.HOST, Config.TargetServer.PORT);
        clientWarmUp();
    }

    @AfterTest
    public void afterTest() throws IOException {
        client.close();
    }

    @BeforeMethod
    public void beforeMethod() {
        taskCounter = new Phaser();
    }

    @Test
    public void normal(Method m) throws Exception {
        exercise(m);
    }

    @Test//(DataProvider = )
    public void withNetworkFailures(Method m) throws InterruptedException {
        injectScheduledChaos(ChaosType.NETWORK_FAILURE, m);
    }

    @Test
    public void withServiceFailure(Method m) throws InterruptedException {
        injectScheduledChaos(ChaosType.SERVICE_FAILURE, m);
    }

    private void injectScheduledChaos(ChaosType chaosType, Method method) throws InterruptedException {
        // TODO this sucks:(
        Chaos chaos = new Chaos();
        Runnable chaosTask = () -> new Chaos().inject(chaosType, Duration.ofSeconds(5));
        FixedRateRunner chaosInjector = FixedRateRunner.create(1, 1.0 / 10.0, chaosTask);
        LOG.info("Starting chaos");
        chaosInjector.start();
        exercise(method);
        LOG.info("Stopping chaos");
        chaosInjector.stop();
        LOG.info("Resetting server");
        chaos.reset();
    }

    enum ChaosType {
        NETWORK_FAILURE,
        SERVICE_FAILURE
    }

    class Chaos {

        HttpClientEngine chaosClient = getClient();
        {
            int saboteurPort = 6660;
            chaosClient.createClient(Config.TargetServer.HOST, saboteurPort);
        }

        Map<ChaosType, Supplier<HttpClientEngine.Resp>> chaosRequests = new HashMap<>();
        {
            chaosRequests.put(ChaosType.NETWORK_FAILURE, () -> {
                // TODO use some kind of java.util.Map marshalling..
                String payload = "{ \"name\": \"net-fail\", \"type\": \"NETWORK_FAILURE\", \"direction\": \"IN\", \"to_port\": " + Config.TargetServer.PORT + "  }";
                return Exceptions.rethrowChecked(() -> chaosClient.post("/", payload).get());
            });

            chaosRequests.put(ChaosType.SERVICE_FAILURE, () -> {
                String payload = "{ \"name\": \"service-fail\", \"type\": \"SERVICE_FAILURE\", \"direction\": \"IN\", \"to_port\": " + Config.TargetServer.PORT + "  }";
                return Exceptions.rethrowChecked(() -> chaosClient.post("/", payload).get());
            });
        }

        HttpClientEngine.Resp reset() {
            return Exceptions.rethrowChecked(() -> chaosClient.delete("/").get());
        }

        void inject(ChaosType type, Duration duration) {
            execute(chaosRequests.get(type));
            Exceptions.rethrowChecked(() -> {
                Thread.sleep(duration.toMillis());
                return null;
            });
            reset();
        }

        private void execute(Supplier<HttpClientEngine.Resp> call) {
            int status = call.get().status;
            if (status != 200) {
                throw new RuntimeException("Failed to inject chaos.  Expected 200, got " + status + ".");
            }
        }
    }

    private void clientWarmUp() throws InterruptedException {
        LOG.info("Warming up the client");
        exercise(Duration.ofSeconds(5), () -> phaserArrive(clientCall.get()));
        LOG.info("Done with warm up!");
    }

    private <T> CompletionStage<T> phaserArrive(CompletionStage<T> cs) {
        return cs.whenComplete((r, t) -> taskCounter.arriveAndDeregister());
    }

    private void exercise(Method method) throws InterruptedException {
        Metrics metrics = new Metrics(this.getClass(), method);
        exercise(exerciseDuration, () -> doGet(metrics));
    }

    private void exercise(Duration duration, Runnable task) throws InterruptedException {
        taskCounter.register();
        Runnable taskThatNeedsToBeCompleted = () -> {
            task.run();
            taskCounter.register();
        };

        LOG.info("Starting exercise");
        FixedRateRunner runner = FixedRateRunner.create(10, 50, taskThatNeedsToBeCompleted);
        runner.start();
        CountDownLatch latch = new CountDownLatch(1);
        new java.util.Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        runner.stop();
                        latch.countDown();
                    }
                }, duration.toMillis());
        latch.await();
        LOG.info("Exercise time up.");
        taskCounter.arriveAndAwaitAdvance();
        LOG.info("Tasks done");
    }

    // If we do more than GET /short, break out the monitoring part.
    private CompletionStage<HttpClientEngine.Resp> doGet(Metrics metrics) {
        final Timer.Context timeCtx = metrics.timer.time();
        try {
            return phaserArrive(clientCall.get())
                    .whenComplete((resp, throwable) -> {
                        timeCtx.stop();
                        if (throwable != null) {
                            metrics.globalErrorRate.mark();
                        } else {
                            Counter responseCounter = metricRegistry.counter(
                                    MetricRegistry.name(metrics.className, metrics.methodName, String.valueOf(resp.status)));
                            responseCounter.inc();
                            if (resp.status != 200 || !Payloads.SHORT.equals(resp.body)) {
                                metrics.appErrorRate.mark();
                                metrics.globalErrorRate.mark();
                            }
                        }
                    });
        } catch (Exception e) {
            taskCounter.arriveAndDeregister();
            metrics.globalErrorRate.mark();
            throw e;
        }
    }

    class Metrics {
        final String className;
        final String methodName;
        final Timer timer;
        final Meter globalErrorRate;
        final Meter appErrorRate;

        public Metrics(Class clazz, Method method) {

            this.className  = clazz.getName();
            this.methodName = method.getName();

            Function<String, String> mkName = type -> MetricRegistry.name(className, methodName, type);
            this.timer           = metricRegistry.timer(mkName.apply("timing"));
            this.globalErrorRate = metricRegistry.meter(mkName.apply("error-rate"));
            this.appErrorRate    = metricRegistry.meter(mkName.apply("app-error-rate"));
        }
    }
}
