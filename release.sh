#!/bin/bash
# usage: script.sh VERSION PATH_TO_BINARY
set -eu -o pipefail

echo -e "running in dir $(pwd)"

VERSION=$1
BINARY=$2

CREDENTIALS_GITHUB=$(cat miniserver/server/secret/github.token.txt)

function format_version() {
  local version=$(date +"%Y-%m-%d-%H-%M-%S")
  echo -n "$version"
}

function deployBinary() {
  echo "uploading!!!!"
  local owner="danielsGitStuff"
  local repo="mel"

  if test -f "$BINARY"; then
    echo "Upload $BINARY file to github"
    ./upload.sh github_api_token="${CREDENTIALS_GITHUB}" \
      owner="$owner" repo="$repo" tag="$VERSION" \
      filename="${BINARY}"
  fi
}

deployBinary