package benchmark.reactornetty;

import com.ss.benchmark.httpclient.chaos.BaseChaosTest;
import com.ss.benchmark.httpclient.common.HttpClientEngine;

public class ChaosTests extends BaseChaosTest {

    @Override
    protected HttpClientEngine getClient() {
        return new benchmark.reactornetty.Engine();
    }
}
