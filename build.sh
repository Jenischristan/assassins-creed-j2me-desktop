#!/bin/sh
set -e
cd "$(dirname "$0")"
JAR=assassins_creed_260615.jar
rm -rf build
mkdir -p build
find src -name '*.java' > .sources.txt
javac -encoding UTF-8 -d build @.sources.txt
rm -f .sources.txt

# Resource-only copy of the game data for --patch-module java.base.
# Needed because the MIDlet reads data via String.class.getResourceAsStream().
BOOTRES=build/bootres
mkdir -p "$BOOTRES"
unzip -o -q "$JAR" -x '*.class' 'META-INF/*' -d "$BOOTRES"
touch "$BOOTRES/.stamp"
