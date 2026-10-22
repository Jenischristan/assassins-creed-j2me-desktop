package javax.microedition.media;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

final class AudioPlayer implements Player {
    private final byte[] data;
    private final String contentType;
    private final List<PlayerListener> listeners = new ArrayList<PlayerListener>();

    private int state = UNREALIZED;
    private int loopCount;
    private boolean loopCountSet;
    private boolean midiAttempted;
    private boolean wavAttempted;
    private Sequence sequence;
    private Sequencer sequencer;
    private Synthesizer synthesizer;
    private Transmitter transmitter;
    private Clip clip;
    private boolean failed;

    // Debug-only fake playback: with no audio device, clips/sequencers cannot
    // run, so getState() would demote immediately and the game's own
    // sound-serialization logic can never be observed. AC_FAKE_AUDIO=<ms per KB>
    // makes started players report running for a duration proportional to
    // their data size, which emulates real hardware timing closely enough to
    // trace the game's audio decisions.
    private static final int FAKE_MS_PER_KB = fakeMsPerKb();
    private final int id = ++idCounter;
    private long fakeStartMs;
    private long fakeDurationMs;
    // Set by Manager when this player was created shortly after a looping
    // player with identical data was closed. The game's own "resume after
    // stall" logic does exactly that (its paint watchdog stops the current
    // sound via stop()+close() and then replays it with setLoopCount(1)),
    // which turned every looping ambient/music track into a one-shot after
    // the first loading screen. With the hint active, a following
    // setLoopCount(1) is treated as "keep looping" once.
    private boolean resumeLoopHint;
    private boolean resumeLoopHintUsed;

    final int contentHash() {
        return 31 * java.util.Arrays.hashCode(data) + data.length;
    }

    final int idForDebug() {
        return id;
    }

    final void markResumeLoopHint() {
        resumeLoopHint = true;
    }

    private static int idCounter;

    private static int fakeMsPerKb() {
        String spec = System.getProperty("ac.fake.audio", System.getenv("AC_FAKE_AUDIO"));
        if (spec == null || spec.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(spec.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void debug(String what) {
        if (javax.microedition.AcDebug.SOUND) {
            javax.microedition.AcDebug.log("sound", "P" + id + " " + what
                    + " type=" + contentType + " state=" + state + " loop="
                    + loopCount);
        }
    }

    AudioPlayer(byte[] data, String contentType) {
        this.data = data;
        this.contentType = contentType == null ? "audio/x-wav" : contentType;
    }

    private boolean isMidiType() {
        String t = contentType.toLowerCase();
        return t.indexOf("midi") >= 0 || t.indexOf("mid") >= 0;
    }

    private synchronized void realizeInternal() {
        if (state == CLOSED || state >= REALIZED) {
            return;
        }
        Exception midiError = null;
        Exception wavError = null;
        if (!midiAttempted && isMidiType()) {
            midiAttempted = true;
            try {
                sequence = MidiSystem.getSequence(new ByteArrayInputStream(data));
            } catch (Exception e) {
                midiError = e;
                sequence = null;
            }
        }
        if (sequence == null && !wavAttempted) {
            wavAttempted = true;
            try {
                AudioInputStream in = AudioSystem.getAudioInputStream(
                        new ByteArrayInputStream(data));
                if (in != null) {
                    in = toPcm16(in);
                    clip = AudioSystem.getClip();
                    clip.open(in);
                }
            } catch (Exception e) {
                wavError = e;
                if (clip != null) {
                    clip.close();
                    clip = null;
                }
            }
        }
        if (sequence == null && clip == null && FAKE_MS_PER_KB <= 0) {
            failed = true;
            System.err.println("[AudioPlayer] cannot play " + contentType + " ("
                    + data.length + " bytes). midi=" + midiError + " wav=" + wavError);
        }
        state = REALIZED;
    }

    // Clip.open() only accepts formats the mixer supports natively. Convert
    // 8-bit / u-law / a-law / big-endian sources to 16-bit signed PCM so that
    // unusual sample formats are not silently dropped.
    private static AudioInputStream toPcm16(AudioInputStream in) {
        AudioFormat src = in.getFormat();
        boolean ok = AudioFormat.Encoding.PCM_SIGNED.equals(src.getEncoding())
                && src.getSampleSizeInBits() == 16 && !src.isBigEndian();
        if (ok) {
            return in;
        }
        try {
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(), 16, src.getChannels(),
                    src.getChannels() * 2, src.getSampleRate(), false);
            if (AudioSystem.isConversionSupported(pcm, src)) {
                return AudioSystem.getAudioInputStream(pcm, in);
            }
        } catch (Exception ignored) {
        }
        return in;
    }

    private synchronized void ensureSequencer() {
        if (sequencer != null || sequence == null) {
            return;
        }
        if (FAKE_MS_PER_KB > 0) {
            return; // debug fake playback: never touch real MIDI devices
        }
        try {
            sequencer = openSequencerWithSynthesizer();
            sequencer.setSequence(sequence);
        } catch (Exception first) {
            closeMidi();
            try {
                sequencer = MidiSystem.getSequencer();
                sequencer.open();
                sequencer.setSequence(sequence);
            } catch (Exception second) {
                closeMidi();
                if (clip == null) {
                    failed = true;
                    // Without this line a missing/broken MIDI backend (no
                    // audio device, headless JRE, no Gervill soundbank) looks
                    // exactly like "the game has no ambient sounds": the game
                    // swallows the start failure and never retries.
                    System.err.println("[AudioPlayer] MIDI playback unavailable"
                            + " (" + first + " / " + second + "); music and"
                            + " ambient tracks will stay silent");
                }
            }
        }
    }

    private Sequencer openSequencerWithSynthesizer() throws Exception {
        Sequencer seq = MidiSystem.getSequencer(false);
        Synthesizer synth = null;
        try {
            seq.open();
            synth = MidiSystem.getSynthesizer();
            synth.open();
            Transmitter tx = seq.getTransmitter();
            tx.setReceiver(synth.getReceiver());
            synthesizer = synth;
            transmitter = tx;
            return seq;
        } catch (Exception e) {
            if (synth != null) {
                try {
                    synth.close();
                } catch (Exception ignored) {
                }
            }
            try {
                seq.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private void closeMidi() {
        if (transmitter != null) {
            try {
                transmitter.close();
            } catch (Exception ignored) {
            }
            transmitter = null;
        }
        if (sequencer != null) {
            try {
                sequencer.close();
            } catch (Exception ignored) {
            }
            sequencer = null;
        }
        if (synthesizer != null) {
            try {
                synthesizer.close();
            } catch (Exception ignored) {
            }
            synthesizer = null;
        }
    }

    public synchronized void realize() throws MediaException {
        if (state == CLOSED) {
            throw new MediaException("player is closed");
        }
        realizeInternal();
    }

    public synchronized void prefetch() throws MediaException {
        if (state == CLOSED) {
            throw new MediaException("player is closed");
        }
        realizeInternal();
        if (!failed) {
            ensureSequencer();
        }
        if (state < PREFETCHED) {
            state = PREFETCHED;
        }
    }

    public synchronized void start() throws MediaException {
        if (state == CLOSED) {
            throw new MediaException("player is closed");
        }
        prefetch();
        if (failed) {
            state = STARTED;
            return;
        }
        try {
            if (sequencer != null) {
                sequencer.setLoopCount(effectiveLoopCount());
                if (sequencer.isRunning()) {
                    sequencer.stop();
                }
                sequencer.setTickPosition(0);
                sequencer.start();
            } else if (clip != null) {
                clip.stop();
                clip.setFramePosition(0);
                int loops = effectiveLoopCount();
                if (loops < 0) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                } else if (loops == 0) {
                    clip.start();
                } else {
                    clip.loop(loops);
                }
            } else if (FAKE_MS_PER_KB > 0) {
                fakeStartMs = System.currentTimeMillis();
                fakeDurationMs = data.length / 1024L * FAKE_MS_PER_KB;
                if (loopCountSet && loopCount < 0) {
                    fakeDurationMs = Long.MAX_VALUE; // loops forever
                }
                debug("fake start, ms=" + fakeDurationMs);
            }
        } catch (Exception e) {
            failed = true;
        }
        state = STARTED;
        debug("start");
    }

    public synchronized void stop() throws MediaException {
        if (state == CLOSED) {
            throw new MediaException("player is closed");
        }
        try {
            if (sequencer != null && sequencer.isRunning()) {
                sequencer.stop();
            }
            if (clip != null && clip.isRunning()) {
                clip.stop();
            }
        } catch (Exception ignored) {
        }
        if (state == STARTED) {
            state = PREFETCHED;
        }
        debug("stop");
    }

    public synchronized void deallocate() {
        if (state == CLOSED) {
            return;
        }
        stopInternal();
        if (state > REALIZED) {
            state = REALIZED;
        }
    }

    private void stopInternal() {
        try {
            if (sequencer != null && sequencer.isRunning()) {
                sequencer.stop();
            }
            if (clip != null && clip.isRunning()) {
                clip.stop();
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void close() {
        if (state == CLOSED) {
            return;
        }
        stopInternal();
        closeMidi();
        if (clip != null) {
            clip.close();
            clip = null;
        }
        state = CLOSED;
        debug("close");
        if (loopCountSet && loopCount < 0) {
            Manager.noteLoopedClose(contentHash());
        }
        for (PlayerListener listener : new ArrayList<PlayerListener>(listeners)) {
            listener.playerUpdate(this, PlayerListener.CLOSED, null);
        }
    }

    public synchronized void setLoopCount(int count) {
        if (state == STARTED) {
            throw new IllegalStateException("cannot set loop count while started");
        }
        if (count < -1) {
            throw new IllegalArgumentException("invalid loop count");
        }
        if (resumeLoopHint && !resumeLoopHintUsed) {
            resumeLoopHintUsed = true;
            if (count == 1) {
                debug("resume hint: restoring continuous loop");
                count = -1;
            }
        }
        loopCount = count;
        loopCountSet = true;
    }

    // MIDP: count N means play N times (-1 = forever). Java Sound's loop count is
    // the number of EXTRA repeats, so N must become N - 1 (the old code made
    // every one-shot sound play twice, keeping the player "started" twice as
    // long, which makes the game drop any sound requested in the meantime).
    private int effectiveLoopCount() {
        if (!loopCountSet) {
            return 0;
        }
        if (loopCount < 0) {
            return -1;
        }
        return Math.max(0, loopCount - 1);
    }

    public long setMediaTime(long now) throws MediaException {
        if (state == CLOSED) {
            throw new MediaException("player is closed");
        }
        try {
            if (sequencer != null) {
                sequencer.setMicrosecondPosition(now);
            } else if (clip != null) {
                clip.setMicrosecondPosition(now);
            }
        } catch (Exception ignored) {
        }
        return now;
    }

    public synchronized long getMediaTime() {
        try {
            if (sequencer != null) {
                return sequencer.getMicrosecondPosition();
            }
            if (clip != null) {
                return clip.getMicrosecondPosition();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public long getDuration() {
        if (sequence != null) {
            return sequence.getMicrosecondLength();
        }
        if (clip != null) {
            return clip.getMicrosecondLength();
        }
        return TIME_UNKNOWN;
    }

    public static final long TIME_UNKNOWN = -1;

    public synchronized int getState() {
        if (state == STARTED && state != CLOSED) {
            if (sequencer != null && !sequencer.isRunning()) {
                state = PREFETCHED;
                debug("demote: sequencer stopped");
            } else if (clip != null && !clip.isRunning() && !failed) {
                state = PREFETCHED;
                debug("demote: clip stopped");
            } else if (failed) {
                state = PREFETCHED;
                debug("demote: failed");
            } else if (FAKE_MS_PER_KB > 0 && clip == null && sequencer == null) {
                if (System.currentTimeMillis() - fakeStartMs >= fakeDurationMs) {
                    state = PREFETCHED;
                    debug("demote: fake playback finished");
                }
            }
        }
        return state;
    }

    public String getContentType() {
        return contentType;
    }

    public synchronized void addPlayerListener(PlayerListener playerListener) {
        if (playerListener != null && !listeners.contains(playerListener)) {
            listeners.add(playerListener);
        }
    }

    public synchronized void removePlayerListener(PlayerListener playerListener) {
        listeners.remove(playerListener);
    }
}
