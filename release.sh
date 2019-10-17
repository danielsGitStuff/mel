#!/bin/bash

set -eu -o pipefail

echo -e "running in dir $(pwd)"
VERSION_PREV=$(egrep -o '[0-9]+' <released.version)

VERSION_NEXT=$((VERSION_PREV + 1))
#VERSION_LIVE=$(curl -s https://app.pr0gramm.com/updates/stable/update.json | jq .version)

CREDENTIALS_GITHUB=$(cat miniserver/server/secret/github.token.txt)

## check if we are clear to go
#if [[ -n "$(git status --porcelain)" ]] ; then
#  echo "Please commit all your changes and clean working directory."
#  git status
#  exit 1
#fi

echo "Release steps:"
echo " * Increase version to $VERSION_NEXT"
#echo " * Start release of version $VERSION_NEXT (current beta is $VERSION_LIVE)"
#echo " * Upload apk to the update manager using auth $CREDENTIALS_UPDATE'"
echo " * Create tag for version v$VERSION_NEXT"
echo ""

# user needs to type yes to continue
#read -p 'Is this correct?: ' CONFIRM || exit 1
#[[ "$CONFIRM" == "yes" ]] || exit 1

function format_version() {
  local version=$(date +"%Y-%m-%d-%H-%M-%S")
  echo -n "$version"
#  local VERSION=$1
#  echo -n "0."$((VERSION / 10))'.'$((VERSION % 10))
}

function deployBinaries() {
  echo "uploading!!!!"
  local APK_ALIGNED=app/build/outputs/apk/release/de.mel.mel-release.apk
  local FXBUNDLE=fxbundle/build/libs/fxbundle-fx.jar
  local BLOG=blog/build/libs/blog-standalone.jar
  local TAG=$(format_version ${VERSION_NEXT})
  local owner="danielsGitStuff"
  local repo="mel"

  if test -f "$APK_ALIGNED"; then
    echo "Upload apk file to github"
    ./upload.sh github_api_token="${CREDENTIALS_GITHUB}" \
      owner="$owner" repo="$repo" tag="$TAG" \
      filename="${APK_ALIGNED}"
  fi
  if test -f "$FXBUNDLE"; then
    echo "Upload fx file to github"
    ./upload.sh github_api_token="${CREDENTIALS_GITHUB}" \
      owner="$owner" repo="$repo" tag="$TAG" \
      filename="${FXBUNDLE}"
  fi
  if test -f "$BLOG"; then
    echo "Upload blog file to github"
    ./upload.sh github_api_token="${CREDENTIALS_GITHUB}" \
      owner="$owner" repo="$repo" tag="$TAG" \
      filename="${BLOG}"
  fi

}

# increase app version for further development
echo "ext { appVersion = $VERSION_NEXT }" >released.version

#trap 'git checkout app/version.gradle' ERR

## compile code and create apks
#rm -rf -- model/build/* app/build/*
#./gradlew --console=plain --no-daemon assembleRelease

## verify apk
#if ! unzip -t app/build/outputs/apk/release/app-release.apk | grep publicsuffixes.gz ; then
#    echo "Could not find publicsuffixes.gz in the apk"
#    exit 1
#fi

# verify apk
#if unzip -t app/build/outputs/apk/release/app-release.apk | grep classes2.dex ; then
#    echo "Found classes2.dex in the apk"
#    exit 1
#fi

#git add app/version.gradle
#git commit -m "Released version $VERSION_NEXT"

#trap - ERR

# create tag for this version
git tag -a "$(format_version ${VERSION_NEXT})" \
  -m "Released version $(format_version ${VERSION_NEXT})"

git push
git push --tags

deployBinaries

# generate debug sources in a final step.
#echo "Prepare next dev cycle..."
#./gradlew --console=plain --no-daemon generateDebugSources >/dev/null
