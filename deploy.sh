#!/bin/bash
serverDir="deploy/server/"
filesDir="${serverDir}files"
stopFile="${serverDir}stop.input"
rm -rf "${serverDir}"
mkdir "${serverDir}"
mkdir ${filesDir}
if [[ "$1" == "clean" ]]; then
    echo "cleaning"
    ./gradlew clean
fi
if [[ -f ${stopFile} ]]; then
    echo "stopping running server -> $(realpath ${filesDir})"
    echo "2">${stopFile} &
fi
echo "build server"
./gradlew :miniserver:buildServerJar
#echo "build fx"
#./gradlew :fxbundle:buildFxJar
#echo "build android"
#./gradlew :app:assembleDebug

cp miniserver/build/libs/* "${filesDir}"
cp fxbundle/build/libs/* "${filesDir}"
cp app/build/outputs/apk/debug/* "${filesDir}"
rm "$filesDir/output.json"

jars=ls ${serverDir}
echo ${jars}

echo "done! binaris copied to $filesDir"
