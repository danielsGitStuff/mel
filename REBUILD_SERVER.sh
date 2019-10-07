git pull
./gradlew clean
./gradlew :miniserver:buildServerJar
cp miniserver/build/libs/miniserver-0.jar miniserver/server/miniserver.jar

