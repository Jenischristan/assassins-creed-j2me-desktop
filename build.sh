#!/bin/sh
set -e
cd "$(dirname "$0")"
JAR=assassins_creed_260615.jar
rm -rf build
mkdir -p build
find src -name '*.java' > .sources.txt
javac -encoding UTF-8 -d build @.sources.txt
rm -f .sources.txt

# --- Game bytecode patch: null-guard the e.e[13].s touch-feedback writes ---
# The game dereferences touch sprite e.e[13] in e.z() (x3) and
# e.pointerReleased() (x1) without a null check, but that sprite is only
# loaded when the level pack declares touch data (dF[13] == 1). On this
# build's data it stays null, so every mouse click threw a
# NullPointerException at the end of pointerReleased(). The patched class
# is placed into build/ so it shadows the one inside the game jar on the
# runtime classpath (build comes first). The jar itself is never modified.
ASMJAR=tools/asm.jar
if [ ! -f "$ASMJAR" ]; then
    echo "missing $ASMJAR (see tools/PatchE.java header)" >&2
    exit 1
fi
PATCHCP=build/_patchcp
mkdir -p "$PATCHCP"
javac -encoding UTF-8 -cp "$ASMJAR" -d "$PATCHCP" tools/PatchE.java
unzip -o -q "$JAR" e.class -d build
java -cp "$PATCHCP:$ASMJAR" PatchE build/e.class build/e.class
rm -rf "$PATCHCP"

# Resource-only copy of the game data for --patch-module java.base.
# Needed because the MIDlet reads data via String.class.getResourceAsStream().
BOOTRES=build/bootres
mkdir -p "$BOOTRES"
unzip -o -q "$JAR" -x '*.class' 'META-INF/*' -d "$BOOTRES"
touch "$BOOTRES/.stamp"
