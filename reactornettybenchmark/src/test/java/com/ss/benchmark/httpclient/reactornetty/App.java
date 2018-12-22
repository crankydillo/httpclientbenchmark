package com.ss.benchmark.httpclient.reactornetty;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class App {
    public static void main(String[] args) throws Exception {
        int executions = 1_000;
        // If we use a ConnectionProvider with 200, the app 'hangs' my Macbook.  If I wait long enough,
        // think_ the application will finish.
        int poolSize = 50;
        int connReqTimeout = 10_000;
        int connTimeout = 2_000;
        int readTimeout = 2_000;
        HttpClient client =
                HttpClient
                        .create(
                                ConnectionProvider.fixed("benchmark", poolSize, connReqTimeout)
                        )
                        .baseUrl("http://localhost:8080/echodelayserv/echo")
                        .tcpConfiguration(tcpClient ->
                                tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connTimeout)
                                        .doOnConnected( con ->
                                                con.addHandlerLast(new ReadTimeoutHandler(readTimeout,
                                                        TimeUnit.MILLISECONDS)))
                        );

        CountDownLatch latch = new CountDownLatch(executions);
        for (int i = 0; i < executions; i++) {
            final int ctr = i;
            client.get()
                    .uri("/long")
                    .responseSingle((res, body) -> {
                        if (res.status().code() != 200) {
                            Mono.error(new IllegalStateException("Unexpected response code : " + res.status().code()));
                        }
                        return body;
                    })
                    .map(byteBuf -> byteBuf.toString(StandardCharsets.UTF_8))
                    .doFinally(Void -> latch.countDown())
                    .subscribe(respBody -> System.out.println((ctr + 1) + ". " + respBody.length()));
        }
        latch.await();
    }














}
