#!/bin/sh
python /usr/lib/saboteur/agent.py &
java -jar flaky-server/lib/mock-application-1.0.0-SNAPSHOT-jar-with-dependencies.jar
