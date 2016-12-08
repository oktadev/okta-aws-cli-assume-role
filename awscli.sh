#!/bin/bash

JAR=$(find target/ -type f -name "okta-aws-cli-*.jar")

java -jar $JAR
