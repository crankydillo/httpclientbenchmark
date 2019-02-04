package com.ss.benchmark.httpclient.common;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

import java.io.File;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * A base class for TestNG tests that are going to capture
 * dropwizard metrics.
 */
public abstract class MetricsGatheringTest {
    protected final MetricRegistry metricRegistry = new MetricRegistry();
    private final ScheduledReporter reporter = ConsoleReporter.forRegistry(metricRegistry).convertDurationsTo(TimeUnit.MILLISECONDS).build();
    private ScheduledReporter csvReporter;

    @BeforeTest
    public void startReporters() {
        // output metrics on a schedule
        if (Config.Metrics.DROPWIZARD_REPORTER_SECONDS > 0) {
            reporter.start(Config.Metrics.DROPWIZARD_REPORTER_SECONDS, TimeUnit.SECONDS);
        }

        File csvParentDir = new File(
                java.util.Optional.ofNullable(System.getenv("BM.METRICS.DIR"))
                        .orElse("metrics-csv")
        );
        if (csvParentDir.isFile()) {
            throw new RuntimeException("Expected " + csvParentDir.getAbsolutePath() + " to be a directory.");
        }
        File csvDir = new File(csvParentDir, Instant.now().toString());
        if (!csvDir.mkdirs()) {
            throw new RuntimeException("Could not create the directory:  " + csvDir.getAbsolutePath());
        }
        csvReporter = CsvReporter.forRegistry(metricRegistry).convertDurationsTo(TimeUnit.MILLISECONDS).build(csvDir);
        csvReporter.start(365, TimeUnit.DAYS);  // the goal is to just get the end numbers.
    }

    @AfterTest
    public void stopReporters() {
        reporter.report();
        reporter.stop();
        reporter.close();
        csvReporter.report();
        csvReporter.stop();
        csvReporter.close();
    }
}
