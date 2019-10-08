#!/bin/bash
echo 'this script builds the server and deploys the jar to miniserver/server/miniserver.jar'
./gradlew clean
./gradlew buildServerJar
cp miniserver/build/libs/miniserver-standalone.jar miniserver/server/miniserver.jar