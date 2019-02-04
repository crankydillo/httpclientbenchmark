package benchmark.reactornetty;

import com.ss.benchmark.httpclient.chaos.PersistentConnTimeoutTest;
import com.ss.benchmark.httpclient.common.HttpClientEngine;
import com.ss.benchmark.httpclient.common.Urls;
import reactor.netty.resources.ConnectionProvider;

public class HalfClosedTest extends PersistentConnTimeoutTest {

    @Override
    public HttpClientEngine clientWithPoolSize1(String host, int port) {
        String baseURL = Urls.baseUrl(host, port);
        Engine engine = new Engine();
        // Am I cheating???  I don't want to refactor so much right now:(
        engine.client = reactor.netty.http.client.HttpClient
                .create(ConnectionProvider.fixed("half-closed-test", 1))
                .baseUrl(baseURL);
        return engine;
    }
}
