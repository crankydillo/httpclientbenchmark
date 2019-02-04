package com.ss.benchmark.httpclient.common;

public class Urls {

    private Urls() {}

    public static String baseUrl(String host, int port) {
        return "http://" + host + ":" + port;
    }
}
