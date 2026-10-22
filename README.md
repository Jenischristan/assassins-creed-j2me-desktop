# Assassin's Creed - desktop J2ME launcher

Run the original Gameloft Assassin's Creed J2ME jar on desktop Java using a
small, self-contained implementation of the MIDP APIs the game uses. The game
jar, its classes, and its data files are left untouched.

## Requirements

- Java 25 (`java`, `javac`) - already installed.
- POSIX shell plus `unzip` for the build/launch scripts.
- No external libraries, no downloads, no `sudo`.

## Run

```sh
./run.sh
```

The game opens in a landscape desktop window (maximized by default) with the
portrait game centered at the largest size that fits. There is no on-screen
keypad; use the keyboard. `F11` toggles fullscreen.

If sources changed, `run.sh` rebuilds automatically. To force a rebuild:

```sh
./build.sh
```

## Controls

| Input | Action |
|-------|--------|
| Arrows or WASD | Move / menu navigation (numpad 2/4/8/6) |
| Number row or numpad | Digits 0-9 (5 = OK / attack) |
| Enter or Space | 5 / OK / attack |
| Q | Left soft key (yes/back) |
| E | Right soft key (no/menu) |
| F11 | Toggle fullscreen |
| Left mouse click | Touch in the visible game area, including on-screen buttons |
| Right / middle mouse | Right / left soft key |

The game's own startup flow: after the logo and loading screens, press `5` (or
Enter) on the warning, then `Q` for sound on or `E` for sound off. The main menu
then accepts `5`/Enter and the direction keys.

In gameplay, a left click without a movement key held still behaves as a touch
event. While WASD/arrows are held, it is translated to the `5` action key so
that attacking or jumping does not cancel movement; the game's touch handler
otherwise re-evaluates the moving player box and clears held input.

## Configuration

`config.properties`:

```properties
# nearest = sharp, arbitrary size (default)
# integer = sharp, whole multipliers only
# bilinear = smooth
scale=nearest

# Landscape desktop window; portrait game is centered and scaled to height.
window.width=1280
window.height=800
window.maximized=true

# Minimum delay between --screenshot captures.
screenshot.interval.ms=300
```

Scaling is nearest-neighbour by default, so pixels stay crisp. `integer`
avoids uneven pixel sizes; `bilinear` smooths the image if preferred.

## Saves

`RecordStore` data is stored in:

```
~/.local/share/assassins-creed/records/
```

Saves persist between runs. Store names are sanitized for the filesystem; for
example the game's `/assassin.sav` becomes `_assassin.sav.rms`.

## Audio

MIDI/WAV playback uses `javax.sound.midi` and `javax.sound.sampled`. If no
synthesizer or audio device is available, the game runs silently instead of
failing.

## How it works

- `src/Launcher.java` owns the Swing window, scaling, input mapping, and the
  offscreen 480x640 frame.
- `src/javax/microedition/lcdui/` implements `Display`, `Canvas`, `Graphics`,
  `Image`, `Font`, and commands on top of Java2D.
- `src/javax/microedition/midlet/` implements the MIDlet lifecycle and reads
  manifest properties from the game jar.
- `src/javax/microedition/rms/` implements a file-backed `RecordStore`.
- `src/javax/microedition/media/` implements `Manager` and `Player`.

The launch command is:

```sh
java --patch-module java.base=build/bootres \
     -cp build:assassins_creed_260615.jar \
     Launcher GloftASCR
```

`build/bootres` is a resource-only copy of the game data. The original code
loads data through `String.class.getResourceAsStream(...)`; on desktop Java
`java.lang.String` is in the `java.base` module, which would otherwise not see
the application class path. Patching `java.base` makes those lookups work
without modifying the game.

## Debug and test options

```sh
./run.sh --headless --stats --exit-after 20
./run.sh --headless --stats --keys "20000:5,22000:e,25000:5" --exit-after 30
```

- `--headless` - no window, useful for automated checks.
- `--stats` - print sampled non-black percentage and average color.
- `--keys "ms:key,..."` - inject keys at the given times (`up`, `down`,
  `left`, `right`, `enter`, `q`, `e`, or a digit).
- `--exit-after <seconds>` - quit automatically.
- `--screenshot <file.png|dir>` - save PNG frames (use only when visual output
  is wanted).
- `--config <file>` - alternate properties file.

## Known issues

- The game is portrait-only; it explicitly warns about true landscape mode.
  The window is landscape, but the game content remains portrait and is
  letterboxed on the sides.
- The source image is 240x320 upscaled by the game itself to 480x640. Larger
  windows scale that image but cannot add detail.
- Gameplay and save/load have not yet been verified end-to-end in a visual
  session; see `PROGRESS.md`.
- In gameplay, a left click while holding a movement key always sends the `5`
  action key; coordinate-based touch movement is used only when no movement key
  is held.
- The removed virtual keypad is not clickable. Use the keyboard soft keys.
- The game's IGP/Gameloft Live middleware initializes from `dataIGP`; its
  online portal links are not used and `platformRequest` is a no-op.
