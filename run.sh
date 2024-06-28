#!/bin/sh

mvn clean install -DskipTests
java -jar application/target/rad-source-application-0.0.5.jar
