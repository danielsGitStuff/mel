git pull
./gradlew clean
./gradlew :miniserver:buildServerJar
cp miniserver/build/libs/miniserver-1.88.jar miniserver/server/miniserver.jar

