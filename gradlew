#!/bin/sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="8.10.2"
DIST_NAME="gradle-${GRADLE_VERSION}-bin"
DIST_ZIP="${APP_HOME}/.gradle/${DIST_NAME}.zip"
DIST_DIR="${APP_HOME}/.gradle/${DIST_NAME}"
GRADLE_BIN="${DIST_DIR}/gradle-${GRADLE_VERSION}/bin/gradle"

if [ -x "${GRADLE_BIN}" ]; then
  exec "${GRADLE_BIN}" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

mkdir -p "${APP_HOME}/.gradle"
if [ ! -f "${DIST_ZIP}" ]; then
  URL="https://services.gradle.org/distributions/${DIST_NAME}.zip"
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "${DIST_ZIP}" "${URL}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${DIST_ZIP}" "${URL}"
  else
    echo "ERROR: curl or wget is required to download Gradle ${GRADLE_VERSION}." >&2
    exit 1
  fi
fi

rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}"
if command -v unzip >/dev/null 2>&1; then
  unzip -q "${DIST_ZIP}" -d "${DIST_DIR}"
else
  echo "ERROR: unzip is required to install Gradle ${GRADLE_VERSION}." >&2
  exit 1
fi

exec "${GRADLE_BIN}" "$@"
