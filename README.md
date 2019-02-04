# Description

This is an attempt to compare performance and resiliency characteristics of
different http clients.

# Results

We are maintaining benchmark results in this repository's Issues section.  This
allows us to have discussions about them as well as easily add supporting
files.  When new results are added, the previous ones should be closed (there
should only be one set of results in Open state).  The issue should have some
pointer (i.e. SHA) to the code that was used to produce them.

# Building

Build everything other than the docker application.

```sh
mvn clean install
```

Build the docker application, which contains all the utilities you need (e.g.
client tests, server, report utilities) to gather benchmarks.

```sh
mvn -pl servers/docker-app package docker:build
```

# Running the performance benchmarks

## Running with docker

See the [docker-app](docker-app) module for instructions.

## Running outside of docker

For development, you will likely be doing what follows; however, be warned that
we routinely 'hung' our laptops by running in this mode.  Our current
recommendation is to set the number of executions to a small number for test
development and use the docker application for doing the 'real' benchmarking.

### Running a server the HTTP clients will hit

The [mock-application](mock-application) module is a wiremock based application
for stubbing http client benchmark use cases.  The HTTP clients under test will
submit requests to this.

To start the server:

```sh
mvn -pl servers/mock-application compile exec:java
```

### Running the client performance tests

Each client goes into an xyz-benchmark module.  To run them, you will be
leverating a Maven profile and Maven's `verify` phase.  For example, to get
reactor-netty's benchmarks, you would do:

```sh
mvn -Pperformance -pl benchmarks/reactornetty-benchmark verify
```

## Configuration

### Number of test runs

By default, it is configured to 10,000.

# Running the resilience benchmarks

## Running with docker

TBD

## Running outside of docker

### Running a server the HTTP clients will hit

For most of the tests to work, the host (for now) needs to be Linux-based.  We
introduce 'chaos' using [this tool](https://github.com/tomakehurst/saboteur),
and not all the commands seem to work with non-Linux hosts.

```sh
mvn -pl servers/flaky-server package docker:build
docker run --name flaky-server --cap-add NET_ADMIN --rm -ti -p 8080:8080 -p 6660:6660 crankydillo/flaky-server
```

### Running the client resiliency tests

Same the performance test; however, use the `chaos` profile.

```sh
```sh
mvn -Pchaos -pl benchmarks/reactornetty-benchmark verify
```
