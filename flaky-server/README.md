# Introduction

Used for HTTP client resiliency tests.

## WARNING

I had some issues with the saboteur commands when the Docker container is
running on my Mac.  I tried many of Ubuntu image bases (12 -> 18) to no avail.
While I haven't tried every command, I haven't experienced similar issues when
running the container on a [GCP](https://cloud.google.com/compute/)-powered
Ubuntu instance.

# Idea

## Start a 'flaky' server

The server is started with 2 web applications.  One supports 'normal' client
requests (i.e. `GET /page`).  Both applications accept client requests that modify
the behavior of the server (inject delays, packet loss, etc.).

The 'normal' application is built from the `mock-application` module.  The
other application is [saboteur](https://github.com/tomakehurst/saboteur).

## Run resiliency test

With the 'flaky' server running, the main steps in the current (internal)
strategy are:

1. Establish instrumentation (e.g. dropwizard metrics)
1. Start a long-running stream of requests
1. Periodically submit server-modifying behavior requests (e.g. add a delay)

#### Possible changes to current (internal) strategy

I haven't identified a need for TestNG.  I'm still considering if it's best to
have a long running stream of requests, or if we should have a
(modify-server-behavior -> submit-requests) loop.

Regardless, I would start with the existing code without TestNG (unless a
reason for that is found).

##### A case for TestNG

_If_ we can find a way to house the server changes into testXyz methods (e.g.
`testWhenPacketLoss50%`), I think you could make a case for TestNG.  We did
something similar in the performance tests using `@DataProvider`.

#### Half-closed connections

We are especially interested in correct pool management when the server severs
the connection without following HTTP's negotiation rules (See the `8.1.2.1
Negotiation` of [HTTP 1.1
protocol](https://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html).  I believe
this can be mimicked with saboteur's NETWORK_FAILURE command and setting client
connection pool size to 1.  I don't know if the current tests target this
scenario.  This _may_ be the first case where it you could justify using
TestNG.

# Usage

On mac (i.e. where you can't do `--net=host`):

```sh
docker run --name flaky-server --cap-add=NET_ADMIN --rm -ti -p 8080:8080 -p 6660:6660 crankydillo/flaky-server
```

## Example of sabotaging the server

```sh
curl $HOST:8080/short
curl -X POST -d '{ "name": "crash", "type": "NETWORK_FAILURE", "direction": "IN", "to_port": 8080 }' $HOST:6660
curl $HOST:8080/short
curl -X DELETE $HOST:6660
curl $HOST:8080/short
```

What should happen:
1. You should see a bunch of 'a' characters.
1. You should see '{}' and the server's behavior is modified.
1. You should get a timeout complaining about connection failure.
1. See nothing (probably a 200 status), but server's behavior is modified.
1. Same as step 1.

# Building

Assuming you have built at the root, which builds the required `mock-application`.

```sh
mvn package docker:build
```