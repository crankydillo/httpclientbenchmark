package benchmark.apachesync;

import com.ss.benchmark.httpclient.chaos.PersistentConnTimeoutTest;
import com.ss.benchmark.httpclient.common.HttpClientEngine;
import com.ss.benchmark.httpclient.common.Urls;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class HalfClosedTest extends PersistentConnTimeoutTest {

    @Override
    public HttpClientEngine clientWithPoolSize1(String host, int port) {
        String baseUrl = Urls.baseUrl(host, port);

        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
        connMgr.setMaxTotal(1);
        connMgr.setValidateAfterInactivity(60 * 1000);

        CloseableHttpClient apacheClient = HttpClients.custom()
                .setConnectionManager(connMgr)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
                .build();

        Engine engine = new Engine(apacheClient, RequestConfig.DEFAULT, baseUrl);
        return engine;
    }
}
