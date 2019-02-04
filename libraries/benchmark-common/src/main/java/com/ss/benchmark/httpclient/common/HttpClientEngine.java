package com.ss.benchmark.httpclient.common;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static com.ss.benchmark.httpclient.common.Exceptions.rethrowChecked;

public interface HttpClientEngine extends Closeable {

    //All times are milliseconds unless otherwise noted
    int MAX_CONNECTION_POOL_SIZE = 200;
    int CONNECT_TIMEOUT = 5_000;
    int READ_TIMEOUT = 50_000;

    /**
     * HTTP protocol is assumed.
     */
    void createClient(String host, int port);

    CompletableFuture<Resp> get(String path);
    CompletableFuture<Resp> post(String path, String body);
    CompletableFuture<Resp> delete(String path);

    default CompletableFuture<String> nonblockingGET(String path) {
        return extractBody(get(path));
    }

    default CompletableFuture<String> nonblockingPOST(String path, String body) {
        return extractBody(post(path, body));
    }

    private CompletableFuture<String> extractBody(CompletableFuture<Resp> resp) {
        return resp.thenApply(r -> {
            if (r.status != 200) {
                throw new RuntimeException("Expected a 200, got a " + r.status);
            }
            return r.body;
        });
    }

    default String blockingGET(String path) {
        return rethrowChecked(() -> nonblockingGET(path).get());
    }

    default String blockingPOST(String path, String body) {
        return rethrowChecked(() -> nonblockingPOST(path, body).get());
    }

    default String url(String host, int port) {
        return Urls.baseUrl(host, port);
    }

    @Override
    default void close() throws IOException {}

    class Resp {
        public final int status;
        public final String body;

        public Resp(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public String toString() {
            return status + " - " + body;
        }
    }
}
