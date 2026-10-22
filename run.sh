#!/bin/sh
set -e
cd "$(dirname "$0")"
JAR=assassins_creed_260615.jar
if [ ! -d build ] || find src -newer build -print -quit | grep -q .; then
    ./build.sh
fi
# The original MIDlet code loads data files through String.class.getResourceAsStream().
# On a desktop JVM java.lang.String belongs to the java.base module, which does not see
# the application class path. Patching java.base with a resource-only directory makes
# those lookups work without touching the game jar.
BOOTRES=build/bootres
if [ ! -f "$BOOTRES/.stamp" ] || [ "$JAR" -nt "$BOOTRES/.stamp" ]; then
    rm -rf "$BOOTRES"
    mkdir -p "$BOOTRES"
    unzip -o -q "$JAR" -x '*.class' 'META-INF/*' -d "$BOOTRES"
    touch "$BOOTRES/.stamp"
fi
exec java --patch-module java.base="$BOOTRES" -cp build:"$JAR" Launcher GloftASCR "$@"
