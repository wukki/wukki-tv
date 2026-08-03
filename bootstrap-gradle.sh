#!/bin/sh
set -eu

BOOTSTRAP_DIR="${TMPDIR:-/tmp}/wukki-gradle-bootstrap"
GRADLE_VERSION="9.1.0"
ARCHIVE="$BOOTSTRAP_DIR/gradle-$GRADLE_VERSION-bin.zip"

mkdir -p "$BOOTSTRAP_DIR"
if [ ! -f "$ARCHIVE" ]; then
  curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ARCHIVE"
fi
unzip -q -o "$ARCHIVE" -d "$BOOTSTRAP_DIR"
"$BOOTSTRAP_DIR/gradle-$GRADLE_VERSION/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION"
