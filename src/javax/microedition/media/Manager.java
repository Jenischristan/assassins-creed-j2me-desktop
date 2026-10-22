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
        releaseIdlePlayers();
        AudioPlayer player = new AudioPlayer(out.toByteArray(), type);
        synchronized (created) {
            created.add(player);
        }
        return player;
    }

    private static void releaseIdlePlayers() {
        List<AudioPlayer> snapshot;
        synchronized (created) {
            snapshot = new ArrayList<AudioPlayer>(created);
        }
        for (AudioPlayer p : snapshot) {
            int s = p.getState();
            if (s == Player.STARTED) {
                continue;
            }
            if (s != Player.CLOSED) {
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
