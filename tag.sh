#!/bin/bash
# creates a given tag on github
VERSION=$1

git tag -f -a "$VERSION"  -m "Released version $VERSION"

git push
git push --tags