package com.ss.benchmark.httpclient.common;

public class Config {

    private Config() {}

    public static class TargetServer {
        private TargetServer() {}

        public static final String HOST =
                "http-benchmark-client";
                //SysProps.str("host", "localhost");

        public static final int PORT = SysProps.integer("bm.port", 8080);

        public static class Urls {
            private Urls() {}

            public static final String HELLO = "/hello";
            public static final String SHORT = "/short";
            public static final String LONG  = "/long";
        }
    }

    public static class Metrics {
        private Metrics() {}

        public static final int DROPWIZARD_REPORTER_SECONDS =
                SysProps.integer("dropwizard.seconds", 30);

    }

    static class SysProps {
        static String str(String relName, String defalt) {
            return System.getProperty("bm." + relName, defalt);
        }

        static int integer(String relName, int defalt) {
            return Integer.parseInt(str(relName, String.valueOf(defalt)));
        }
    }
}
