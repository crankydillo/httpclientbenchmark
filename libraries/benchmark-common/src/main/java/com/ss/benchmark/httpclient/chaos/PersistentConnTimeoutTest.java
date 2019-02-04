package com.ss.benchmark.httpclient.chaos;

import com.ss.benchmark.httpclient.common.BasePerformanceTest;
import com.ss.benchmark.httpclient.common.HttpClientEngine;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;

/**
 * We've been calling this the 'half-closed test'; however,
 * it is only one of several half-closed scenarios.  In this
 * one, the HTTP/1.1 persistent connection established by
 * the client has timed out on the server.  The server sends
 * a FIN-ACK outside the context of some HTTP request call
 * and the expectation is that the client somehow evicts that
 * connection from its pool.
 */
public abstract class PersistentConnTimeoutTest {

    public static final int SERVER_PERSISTENT_CONN_TIMEOUT_IN_SECS = 1;

    /**
     * Create a client that uses a connection pool.  The pool's
     * maximum size should be 1.  The client <b>should not</b>
     * timeout its connection before {SERVER_PERSISTENT_CONN_TIMEOUT_IN_SECS}.
     * <p>
     * This contracts differs a bit from {{@link BasePerformanceTest#getClient()}};
     * however, I think that one should be considered for refactoring:)
     */
    protected abstract HttpClientEngine clientWithPoolSize1(String host, int port);

    @Test
    void halfClosed() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.getConnector().setPort(0); // let tomcat choose a random port
        tomcat.getConnector().setAttribute("keepAliveTimeout", SERVER_PERSISTENT_CONN_TIMEOUT_IN_SECS);
        tomcat.getConnector().setAttribute("maxConnections", 1); // do we really need this?

        Context ctx = tomcat.addContext("", new File(".").getAbsolutePath());

        Tomcat.addServlet(ctx, "Embedded", new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException {

                try (Writer w = resp.getWriter()) {
                    w.write("Hi from Tomcat!\n");
                    w.flush();
                }
            }
        });

        ctx.addServletMapping("/*", "Embedded");
        tomcat.start();

        HttpClientEngine client = clientWithPoolSize1("localhost",tomcat.getConnector().getLocalPort());
        Supplier<String> doGet = () -> client.blockingGET("/anypath");
        String expected = doGet.get();
        Thread.sleep(SERVER_PERSISTENT_CONN_TIMEOUT_IN_SECS * 1_000 + 500);
        // By this time, the server should have closed the connection; however
        // the client should gracefully handle this.
        assertEquals(doGet.get(), expected);
    }
}
