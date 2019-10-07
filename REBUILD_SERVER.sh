git pull
./gradlew clean
./gradlew :miniserver:bootJar
cp miniserver/build/libs/miniserver-0.jar miniserver/server/miniserver.jar

