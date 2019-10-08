git pull
./gradlew clean
./gradlew :miniserver:bootJar
cp miniserver/build/libs/miniserver-standalone.jar miniserver/server/miniserver.jar

