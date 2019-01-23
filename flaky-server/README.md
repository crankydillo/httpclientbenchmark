# Introduction

Used for HTTP client resiliency tests.

# Idea

## Start a 'flaky' server

The server is started with 2 web applications.  One supports 'normal' client
requests (i.e. `GET /page`).  Both applications accept client requests that modify
the behavior of the server (inject delays, packet loss, etc.).

The 'normal' application is built from the `mock-application` module.  The
other application is [saboteur](https://github.com/tomakehurst/saboteur).

I have been trying to build this server as a docker application; however, that
may not be feasible.  I don't know if it's docker and/or saboteur, but I
haven't been able to get it fully working.  Even so, I'd still spend a bit more
time using the docker route.  I'd also search for some newer alternatives to
`saboteur`, which is both old and doesn't appear to be hugely popular (based on
github stars).

## Run resiliency test

With the 'flaky' server running, the main steps in the current (internal)
strategy are:

1. Establish instrumentation (e.g. dropwizard metrics)
1. Start a long-running stream of requests
1. Periodically submit server-modifying behavior requests (e.g. add a delay)

## Changes to current (internal) strategy

I haven't identified a need for TestNG.  I'm still considering if it's best to
have a long running stream of requests, or if we should have a
(modify-server-behavior -> submit-requests) loop.  Regardless, I would start
with the existing code without TestNG (unless a reason for that is found).

# Usage

On mac (i.e. where you can't do `--net=host`):

```sh
docker run --name flaky-server --cap-add=NET_ADMIN --rm -ti -p 8080:8080 -p 6660:6660 crankydillo/flaky-server
```

# Building

Assuming you have built at the root, which builds the required `mock-application`.

```sh
mvn package docker:build
```
