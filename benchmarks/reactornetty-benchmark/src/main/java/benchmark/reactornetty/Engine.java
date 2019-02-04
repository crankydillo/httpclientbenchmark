package benchmark.reactornetty;

import com.ss.benchmark.httpclient.common.HttpClientEngine;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Engine implements HttpClientEngine {
    HttpClient client;

    @Override
    public void createClient(String host, int port) {
        String baseURL = url(host, port);
        client = reactor.netty.http.client.HttpClient
                .create(ConnectionProvider.fixed("benchmark", MAX_CONNECTION_POOL_SIZE))
                .baseUrl(baseURL)
                .tcpConfiguration(tcpClient ->
                        tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT)
                                .doOnConnected(con -> con.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT, TimeUnit.MILLISECONDS))));
    }

    @Override
    public CompletableFuture<HttpClientEngine.Resp> get(String path) {
        return toResp(
                client.request(HttpMethod.GET).uri(path)
        ).toFuture();
    }

    @Override
    public CompletableFuture<HttpClientEngine.Resp> post(String path, String payload) {
         return toResp(
                 client.headers(entries ->
                         entries.add("Content-Type", "application/json" )
                                 .add("Content-Length", payload.length())
                         ).post()
                         .uri(path)
                         .send(ByteBufFlux.fromString(Flux.just(payload)))
         ).toFuture();
    }

    @Override
    public CompletableFuture<HttpClientEngine.Resp> delete(String path) {
        // TODO When I used `toResp` I was getting nulls when blocking on the CompletableFuture.  What's up
        // with that?
        return client.delete().uri(path).response().map(r -> new Resp(r.status().code(), null)).toFuture();
    }

    private static Mono<HttpClientEngine.Resp> toResp(HttpClient.ResponseReceiver<?> receiver) {
        return receiver
                .responseSingle((res, body) -> Mono.just(res.status().code()).zipWith(body.asString(StandardCharsets.UTF_8)))
                .map(t -> {
                    return new Resp(t.getT1(), t.getT2());
                });
    }

    // I'm letting the default methods handle all of the others.  This _could_ be a performance issue
    // as we _may_ get better performance by doing things here (e.g. blocking against a Mono as opposed
    // to what the default does (Mono -> CompletionStage -> block).
}
