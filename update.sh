source ~/.keychain/`uname -n`-sh
keychain --agents ssh ~/.ssh/id_github

git pull
./gradlew clean
./gradlew :miniserver:buildServerJar
#./gradlew :fxbundle:buildFxJar
#./gradlew :app:assembleRelease
cp miniserver/build/libs/miniserver-1.88.jar miniserver/server/miniserver.jar
systemctl --user restart miniserver.service
