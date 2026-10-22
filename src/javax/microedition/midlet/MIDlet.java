package javax.microedition.midlet;

import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.microedition.lcdui.Display;

public abstract class MIDlet {
    private static Attributes manifestAttributes;
    private static boolean manifestLoaded;
    private boolean destroyed;

    protected MIDlet() {
    }

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional)
            throws MIDletStateChangeException;

    public final void startAppForLauncher() throws MIDletStateChangeException {
        startApp();
    }

    public final String getAppProperty(String key) {
        loadManifest();
        if (manifestAttributes == null) {
            return null;
        }
        return manifestAttributes.getValue(key);
    }

    private static synchronized void loadManifest() {
        if (manifestLoaded) {
            return;
        }
        manifestLoaded = true;
        try (InputStream in = MIDlet.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (in != null) {
                Manifest manifest = new Manifest(in);
                manifestAttributes = manifest.getMainAttributes();
            }
        } catch (Exception ignored) {
        }
    }

    public final void notifyDestroyed() {
        destroyed = true;
        Display.notifyDestroyed();
    }

    public final void notifyPaused() {
    }

    public final void resumeRequest() {
    }

    public final boolean platformRequest(String url) {
        return false;
    }

    public final void notifyDestroyedQuietly() {
        destroyed = true;
    }

    public final boolean isDestroyed() {
        return destroyed;
    }
}
