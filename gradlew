#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-8.10.2-bin/1a1k4b3n6b8x2p5a8g4f3c2d1/gradle-8.10.2/bin/gradle" "$@"
