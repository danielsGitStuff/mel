#!/bin/bash
targetDir="collection"
rm -rf "$targetDir"
mkdir "$targetDir"
if [ "$1" == "clean" ]; then
    echo "cleaning"
    ./gradlew clean
fi
echo "build server"
./gradlew :miniserver:buildServerJar
echo "build fx"
./gradlew :fxbundle:buildFxJar
echo "build android"
./gradlew :app:assembleDebug

cp miniserver/build/libs/* "$targetDir"
cp fxbundle/build/libs/* "$targetDir"
cp app/build/outputs/apk/debug/* "$targetDir"
rm "$targetDir/output.json"

echo "done! binaris copied to $targetDir"
