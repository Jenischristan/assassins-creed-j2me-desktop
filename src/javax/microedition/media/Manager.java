package javax.microedition.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class Manager {
    // The game creates a fresh Player for every sound and only closes the one
    // it is currently holding, so finished players would otherwise keep their
    // Clip / Sequencer / Synthesizer (and the audio line behind them) open
    // forever until the mixer runs out of lines and every new sound fails.
    private static final List<AudioPlayer> created = new ArrayList<AudioPlayer>();

    // Remembers the data hash of a looping player that was recently closed
    // (see AudioPlayer.close / resumeLoopHint).
    private static final long RESUME_HINT_WINDOW_MS = 5000;
    private static int recentLoopedHash;
    private static long recentLoopedAtMs;

    private Manager() {
    }

    public static Player createPlayer(InputStream stream, String type)
            throws IOException, MediaException {
        if (stream == null) {
            throw new IllegalArgumentException("stream is null");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = stream.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        if (javax.microedition.AcDebug.SOUND) {
            int started = 0;
            int total;
            synchronized (created) {
                total = created.size();
                for (AudioPlayer p : created) {
                    if (p.getState() == Player.STARTED) {
                        started++;
                    }
                }
            }
            javax.microedition.AcDebug.log("sound", "createPlayer type=" + type
                    + " bytes=" + out.size() + " tracked=" + total
                    + " startedNow=" + started);
        }
        if (javax.microedition.AcDebug.SOUND_DUMP) {
            try {
                java.io.File dir = new java.io.File("build/sound-dump");
                dir.mkdirs();
                java.io.File file = new java.io.File(dir, "snd-" + dumpIndex
                        + "-" + (type == null ? "null" : type.replace('/', '_'))
                        + ".bin");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                fos.write(out.toByteArray());
                fos.close();
                dumpIndex++;
            } catch (Exception ignored) {
            }
        }
        releaseIdlePlayers();
        AudioPlayer player = new AudioPlayer(out.toByteArray(), type);
        int hash = player.contentHash();
        synchronized (Manager.class) {
            if (hash == recentLoopedHash
                    && System.currentTimeMillis() - recentLoopedAtMs <= RESUME_HINT_WINDOW_MS) {
                if (javax.microedition.AcDebug.SOUND) {
                    javax.microedition.AcDebug.log("sound",
                            "P" + player.idForDebug() + " matches recently looped"
                                    + " sound; arming resume hint");
                }
                player.markResumeLoopHint();
            }
        }
        synchronized (created) {
            created.add(player);
        }
        return player;
    }

    static void noteLoopedClose(int hash) {
        synchronized (Manager.class) {
            recentLoopedHash = hash;
            recentLoopedAtMs = System.currentTimeMillis();
        }
    }

    private static int dumpIndex;

    private static void releaseIdlePlayers() {
        List<AudioPlayer> snapshot;
        synchronized (created) {
            snapshot = new ArrayList<AudioPlayer>(created);
        }
        for (AudioPlayer p : snapshot) {
            int s = p.getState();
            if (s == Player.STARTED) {
                if (javax.microedition.AcDebug.SOUND) {
                    javax.microedition.AcDebug.log("sound",
                            "releaseIdle: keep STARTED " + p);
                }
                continue;
            }
            if (s != Player.CLOSED) {
                if (javax.microedition.AcDebug.SOUND) {
                    javax.microedition.AcDebug.log("sound",
                            "releaseIdle: closing state=" + s + " " + p);
                }
                p.close();
            }
            synchronized (created) {
                created.remove(p);
            }
        }
    }

    public static Player createPlayer(String locator)
            throws IOException, MediaException {
        throw new MediaException("locator players are not supported: " + locator);
    }

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[]{"audio/midi", "audio/x-wav", "audio/wav", "audio/mpeg"};
    }

    public static String[] getSupportedProtocols(String contentType) {
        return new String[]{"file", "http"};
    }
}
