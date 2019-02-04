package benchmark.apachesync;

import com.ss.benchmark.httpclient.common.Exceptions;
import com.ss.benchmark.httpclient.common.HttpClientEngine;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class Engine implements HttpClientEngine {

    private CloseableHttpClient client;
    private RequestConfig requestConfig;
    private String baseUrl;

    private ExecutorService executorService = Executors.newFixedThreadPool(10);

    Engine() {
    }

    Engine(CloseableHttpClient client, RequestConfig requestConfig, String baseUrl) {
        this.client = client;
        this.requestConfig = requestConfig;
        this.baseUrl = baseUrl;
    }

    @Override
    public void createClient(String host, int port) {

        this.baseUrl = url(host, port);

        requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(READ_TIMEOUT)
                .setSocketTimeout(READ_TIMEOUT)
                .build();

        PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
        poolingHttpClientConnectionManager.setMaxTotal(MAX_CONNECTION_POOL_SIZE);

        client = HttpClients.custom()
                .setConnectionManager(poolingHttpClientConnectionManager)
                .build();
    }

    @Override
    public CompletableFuture<Resp> get(String path) {
        return async(() -> privBlockingGet(path));
    }

    @Override
    public CompletableFuture<Resp> post(String path, String body) {
        return async(() -> privBlockingPost(path, body));
    }

    @Override
    public CompletableFuture<Resp> delete(String path) {
        return async(() -> {
            HttpDelete delReq = new HttpDelete(baseUrl + path);
            delReq.setConfig(requestConfig);
            return execute(delReq);
        });
    }

    @Override
    public String blockingGET(String path) {
        return privBlockingGet(path).body;
    }

    @Override
    public String blockingPOST(String path, String body) {
        return privBlockingPost(path, body).body;
    }

    @Override
    public CompletableFuture<String> nonblockingGET(String path) {
        return async(() -> privBlockingGet(path)).thenApply(r -> r.body);
    }

    @Override
    public CompletableFuture<String> nonblockingPOST(String path, String body) {
        return async(() -> privBlockingPost(path, body)).thenApply(r -> r.body);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private Resp execute(HttpUriRequest req) {
        return Exceptions.rethrowChecked(() -> {
            HttpResponse response = client.execute(req);
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            return new Resp(status, body);
        });
    }

    private CompletableFuture<Resp> async(Supplier<Resp> supplier) {
        return CompletableFuture.supplyAsync(supplier, executorService);
    }

    private Resp privBlockingGet(String path) {
        final HttpGet request = new HttpGet(baseUrl + path);
        request.setConfig(requestConfig);
        return execute(request);
    }

    private Resp privBlockingPost(String path, String payload) {
        final HttpPost request = new HttpPost(baseUrl + path);
        request.setConfig(requestConfig);
        StringEntity stringEntity = Exceptions.rethrowChecked(() -> new StringEntity(payload));
        request.setEntity(stringEntity);
        return execute(request);
    }
}
