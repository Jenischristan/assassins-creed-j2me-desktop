import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class Launcher implements Display.Painter {
    private static final int CANVAS_WIDTH = 480;
    private static final int CANVAS_HEIGHT = 640;

    // Directional key codes (MIDlet numpad keys) and their bits in the
    // game's held-key bitmask, used to detect and repair input wipes.
    private static final int[] DIRECTION_KEYS = {50, 52, 54, 56};
    private static final int GAMEPLAY_STATE = 8;
    // Taps in the lower part of the canvas hit the game's own on-screen
    // buttons and must stay pointer events even while a direction key is held.
    private static final int TOUCH_BUTTON_ZONE_Y = 480;

    private final Image canvasImage;
    private final boolean headless;
    private boolean debugStats;
    private int frameCount;
    private final String screenshotPath;
    private final long screenshotIntervalMs;
    private final String scaleMode;
    private final Set<Integer> heldKeys = new HashSet<Integer>();
    private final Timer timer = new Timer("launcher-timer", true);

    private volatile Canvas canvas;
    private JFrame frame;
    private GamePanel panel;
    private GraphicsDevice fullScreenDevice;
    private boolean fullScreen;
    private boolean startMaximized;
    private boolean paintPending;
    private long lastScreenshot;
    private int screenshotIndex;
    private boolean destroyed;

    private Launcher(boolean headless, String screenshotPath, String scaleMode,
            long screenshotIntervalMs) {
        this.headless = headless;
        this.screenshotPath = screenshotPath;
        this.scaleMode = scaleMode;
        this.screenshotIntervalMs = screenshotIntervalMs;
        this.canvasImage = Image.createImage(CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    public static void main(String[] args) throws Exception {
        boolean headless = false;
        boolean debugStats = false;
        String screenshot = null;
        String configFile = "config.properties";
        String keys = null;
        long exitAfterMs = -1;
        String midletClass = "GloftASCR";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--headless".equals(arg)) {
                headless = true;
            } else if ("--stats".equals(arg)) {
                debugStats = true;
            } else if ("--screenshot".equals(arg)) {
                screenshot = requireValue(args, ++i, "--screenshot");
            } else if ("--config".equals(arg)) {
                configFile = requireValue(args, ++i, "--config");
            } else if ("--keys".equals(arg)) {
                keys = requireValue(args, ++i, "--keys");
            } else if ("--exit-after".equals(arg)) {
                exitAfterMs = (long) (Double.parseDouble(
                        requireValue(args, ++i, "--exit-after")) * 1000);
            } else if (arg.startsWith("--")) {
                System.err.println("Unknown option: " + arg);
                printUsage();
                System.exit(2);
            } else {
                midletClass = arg;
            }
        }

        Properties config = loadConfig(configFile);
        String scaleMode = config.getProperty("scale", "nearest").trim().toLowerCase();
        if (!"nearest".equals(scaleMode) && !"integer".equals(scaleMode)
                && !"bilinear".equals(scaleMode)) {
            System.err.println("Unknown scale mode '" + scaleMode
                    + "', using nearest");
            scaleMode = "nearest";
        }
        long screenshotInterval = Long.parseLong(
                config.getProperty("screenshot.interval.ms", "300").trim());
        int defaultWidth = Integer.parseInt(
                config.getProperty("window.width", "1280").trim());
        int defaultHeight = Integer.parseInt(
                config.getProperty("window.height", "800").trim());
        boolean startMaximized = Boolean.parseBoolean(
                config.getProperty("window.maximized", "true").trim());

        if (headless) {
            System.setProperty("java.awt.headless", "true");
        }

        Launcher launcher = new Launcher(headless, screenshot, scaleMode,
                screenshotInterval);
        launcher.debugStats = debugStats;
        launcher.startMaximized = startMaximized;
        Display.setScreenSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        Display.setPainter(launcher);

        if (!headless) {
            launcher.createWindow(defaultWidth, defaultHeight);
        }

        if (keys != null) {
            launcher.scheduleKeys(keys);
        }
        if (exitAfterMs > 0) {
            launcher.timer.schedule(new TimerTask() {
                public void run() {
                    System.exit(0);
                }
            }, exitAfterMs);
        }

        MIDlet midlet = (MIDlet) Class.forName(midletClass)
                .getDeclaredConstructor().newInstance();
        midlet.startAppForLauncher();
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            System.err.println(option + " requires a value");
            System.exit(2);
        }
        return args[index];
    }

    private static Properties loadConfig(String path) {
        Properties properties = new Properties();
        File file = new File(path);
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (Exception e) {
                System.err.println("Could not read " + path + ": " + e);
            }
        }
        return properties;
    }

    private static void printUsage() {
        System.err.println("Usage: java -cp build:assassins_creed_260615.jar Launcher"
                + " [MIDletClass] [options]");
        System.err.println("  --headless             render without opening a window");
        System.err.println("  --screenshot <path>    save frames (file.png or directory)");
        System.err.println("  --config <file>        properties file (default config.properties)");
        System.err.println("  --keys <t:key,...>     inject keys at times, e.g. 1500:enter");
        System.err.println("  --exit-after <secs>    quit after the given time");
    }

    private void createWindow(int width, int height) {
        frame = new JFrame("Assassin's Creed");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBackground(Color.BLACK);
        panel = new GamePanel();
        frame.setContentPane(panel);
        frame.setPreferredSize(new Dimension(width, height));
        frame.pack();
        frame.setMinimumSize(new Dimension(240, 400));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        if (startMaximized) {
            frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                releaseHeldKeys();
                if (panel != null) {
                    panel.cancelPointer();
                }
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                panel.requestFocusInWindow();
            }
        });
        startInputResyncTimer();
    }

    private void pressKey(int code) {
        if (!heldKeys.add(code)) {
            return;
        }
        Canvas current = canvas;
        if (current != null) {
            current.dispatchKeyPressed(code);
        }
    }

    private void releaseKey(int code) {
        if (!heldKeys.remove(code)) {
            return;
        }
        Canvas current = canvas;
        if (current != null) {
            current.dispatchKeyReleased(code);
        }
    }

    private void releaseHeldKeys() {
        Canvas current = canvas;
        if (current != null) {
            for (int code : new ArrayList<Integer>(heldKeys)) {
                current.dispatchKeyReleased(code);
            }
        }
        heldKeys.clear();
    }

    // The MIDlet's touch handlers clear its own held-key state on every
    // pointer release and while a touch action animates, and they swallow
    // (and clear!) key events during that window. Because the operating
    // system will not resend a keyPressed for a key the user is still
    // holding, any such wipe used to kill movement until the key was
    // released and pressed again. resyncHeldKeys re-asserts only direction
    // keys that are still physically held but missing from the game's
    // bitmask, which restores movement without creating false key edges for
    // fire/soft keys.
    private static int directionKeyBit(int code) {
        switch (code) {
            case 50:
                return 1 << 2;
            case 52:
                return 1 << 4;
            case 54:
                return 1 << 6;
            case 56:
                return 1 << 8;
            default:
                return 0;
        }
    }

    private boolean isDirectionKeyHeld(int code) {
        return heldKeys.contains(code);
    }

    private void resyncHeldKeys(Canvas current) {
        if (current == null) {
            return;
        }
        int mask = current.getGameHeldKeyMask();
        if (mask < 0) {
            return; // game state unavailable; do not guess
        }
        for (int code : DIRECTION_KEYS) {
            if (!isDirectionKeyHeld(code)) {
                continue;
            }
            int bit = directionKeyBit(code);
            if (bit != 0 && (mask & bit) == 0) {
                current.dispatchKeyPressed(code);
            }
        }
    }

    private void startInputResyncTimer() {
        if (headless) {
            return;
        }
        // Periodic safety net: repairs movement if the game wiped held keys
        // at any point (touch actions, screen transitions, focus changes).
        new javax.swing.Timer(250, new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                Canvas current = canvas;
                if (current != null && !heldKeys.isEmpty()) {
                    resyncHeldKeys(current);
                }
            }
        }).start();
    }

    @Override
    public void requestPaint(Canvas requestedCanvas) {
        canvas = requestedCanvas;
        if (headless) {
            renderFrame(requestedCanvas);
            return;
        }
        synchronized (this) {
            if (paintPending) {
                return;
            }
            paintPending = true;
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                synchronized (Launcher.this) {
                    paintPending = false;
                }
                Canvas current = canvas;
                if (current != null) {
                    renderFrame(current);
                }
                if (panel != null) {
                    panel.repaint();
                }
            }
        });
    }

    @Override
    public void serviceRepaints(Canvas requestedCanvas) {
        if (SwingUtilities.isEventDispatchThread()) {
            renderFrame(requestedCanvas);
        }
    }

    @Override
    public void notifyDestroyed() {
        synchronized (this) {
            if (destroyed) {
                return;
            }
            destroyed = true;
        }
        timer.cancel();
        if (headless) {
            System.exit(0);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (frame != null) {
                    frame.dispose();
                }
                System.exit(0);
            }
        });
    }

    private void renderFrame(Canvas requestedCanvas) {
        try {
            Graphics graphics = canvasImage.getGraphics();
            requestedCanvas.render(graphics);
        } catch (Throwable t) {
            System.err.println("Game paint failed:");
            t.printStackTrace();
        }
        if (debugStats) {
            printStats();
        }
        maybeScreenshot();
    }

    private void printStats() {
        if (++frameCount % 30 != 0) {
            return;
        }
        BufferedImage img = canvasImage.getAWTImage();
        long r = 0, g = 0, b = 0;
        int nonBlack = 0;
        int samples = 0;
        for (int y = 0; y < CANVAS_HEIGHT; y += 8) {
            for (int x = 0; x < CANVAS_WIDTH; x += 8) {
                int rgb = img.getRGB(x, y);
                int rr = (rgb >> 16) & 0xFF;
                int gg = (rgb >> 8) & 0xFF;
                int bb = rgb & 0xFF;
                if (rr + gg + bb > 15) {
                    nonBlack++;
                }
                r += rr; g += gg; b += bb;
                samples++;
            }
        }
        System.out.printf("frame %d: nonBlack=%.1f%% avg=#%02x%02x%02x%n",
                frameCount, 100.0 * nonBlack / samples,
                (int) (r / samples), (int) (g / samples), (int) (b / samples));
    }

    private synchronized void maybeScreenshot() {
        if (screenshotPath == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastScreenshot != 0 && now - lastScreenshot < screenshotIntervalMs) {
            return;
        }
        lastScreenshot = now;
        try {
            File file;
            if (screenshotPath.toLowerCase().endsWith(".png")) {
                file = new File(screenshotPath);
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
            } else {
                File dir = new File(screenshotPath);
                dir.mkdirs();
                file = new File(dir, String.format("shot-%04d.png", ++screenshotIndex));
            }
            ImageIO.write(canvasImage.getAWTImage(), "png", file);
        } catch (Exception e) {
            System.err.println("Screenshot failed: " + e);
        }
    }

    private void scheduleKeys(String spec) {
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.length() == 0) {
                continue;
            }
            int colon = part.indexOf(':');
            if (colon < 0) {
                System.err.println("Bad key spec: " + part);
                continue;
            }
            long delay = Long.parseLong(part.substring(0, colon).trim());
            final int keyCode = keyCodeForName(part.substring(colon + 1).trim());
            if (keyCode == Integer.MIN_VALUE) {
                System.err.println("Unknown key: " + part);
                continue;
            }
            timer.schedule(new TimerTask() {
                public void run() {
                    injectKey(keyCode, true);
                }
            }, delay);
            timer.schedule(new TimerTask() {
                public void run() {
                    injectKey(keyCode, false);
                }
            }, delay + 120);
        }
    }

    private void injectKey(final int keyCode, final boolean pressed) {
        Canvas current = canvas;
        if (current == null) {
            return;
        }
        if (headless) {
            if (pressed) {
                current.dispatchKeyPressed(keyCode);
            } else {
                current.dispatchKeyReleased(keyCode);
            }
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    Canvas c = canvas;
                    if (c != null) {
                        if (pressed) {
                            c.dispatchKeyPressed(keyCode);
                        } else {
                            c.dispatchKeyReleased(keyCode);
                        }
                    }
                }
            });
        }
    }

    private static int keyCodeForName(String name) {
        String lower = name.toLowerCase();
        if ("up".equals(lower) || "w".equals(lower)) {
            return 50;
        }
        if ("down".equals(lower) || "s".equals(lower)) {
            return 56;
        }
        if ("left".equals(lower) || "a".equals(lower)) {
            return 52;
        }
        if ("right".equals(lower) || "d".equals(lower)) {
            return 54;
        }
        if ("enter".equals(lower) || "ok".equals(lower) || "fire".equals(lower)
                || "space".equals(lower)) {
            return 53;
        }
        if ("q".equals(lower) || "soft1".equals(lower)) {
            return -6;
        }
        if ("e".equals(lower) || "soft2".equals(lower)) {
            return -7;
        }
        if (lower.length() == 1 && lower.charAt(0) >= '0' && lower.charAt(0) <= '9') {
            return lower.charAt(0);
        }
        return Integer.MIN_VALUE;
    }

    private static int mapAwtKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                return 50;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                return 56;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                return 52;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                return 54;
            case KeyEvent.VK_Q:
                return -6;
            case KeyEvent.VK_E:
                return -7;
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SPACE:
                return 53;
            case KeyEvent.VK_NUMPAD0:
            case KeyEvent.VK_0:
                return 48;
            case KeyEvent.VK_NUMPAD1:
            case KeyEvent.VK_1:
                return 49;
            case KeyEvent.VK_NUMPAD2:
            case KeyEvent.VK_2:
                return 50;
            case KeyEvent.VK_NUMPAD3:
            case KeyEvent.VK_3:
                return 51;
            case KeyEvent.VK_NUMPAD4:
            case KeyEvent.VK_4:
                return 52;
            case KeyEvent.VK_NUMPAD5:
            case KeyEvent.VK_5:
                return 53;
            case KeyEvent.VK_NUMPAD6:
            case KeyEvent.VK_6:
                return 54;
            case KeyEvent.VK_NUMPAD7:
            case KeyEvent.VK_7:
                return 55;
            case KeyEvent.VK_NUMPAD8:
            case KeyEvent.VK_8:
                return 56;
            case KeyEvent.VK_NUMPAD9:
            case KeyEvent.VK_9:
                return 57;
            default:
                return Integer.MIN_VALUE;
        }
    }

    private void toggleFullScreen() {
        if (frame == null) {
            return;
        }
        releaseHeldKeys();
        if (panel != null) {
            panel.cancelPointer();
        }
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (!fullScreen) {
            fullScreenDevice = env.getDefaultScreenDevice();
            if (fullScreenDevice.isFullScreenSupported()) {
                frame.dispose();
                frame.setUndecorated(true);
                fullScreenDevice.setFullScreenWindow(frame);
                frame.setVisible(true);
                fullScreen = true;
                panel.requestFocusInWindow();
            }
        } else {
            fullScreenDevice.setFullScreenWindow(null);
            frame.dispose();
            frame.setUndecorated(false);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            fullScreen = false;
            panel.requestFocusInWindow();
        }
    }

    private final class GamePanel extends JPanel {
        private boolean pointerDown;
        private boolean translatedClick;
        private int pointerX = -1;
        private int pointerY = -1;
        private int mouseX = -1;
        private int mouseY = -1;
        private long lastDragRepress;

        GamePanel() {
            setBackground(Color.BLACK);
            setFocusable(true);
            setDoubleBuffered(true);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_F11) {
                        toggleFullScreen();
                        return;
                    }
                    int code = mapAwtKey(e.getKeyCode());
                    if (code != Integer.MIN_VALUE) {
                        pressKey(code);
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    int code = mapAwtKey(e.getKeyCode());
                    if (code != Integer.MIN_VALUE) {
                        releaseKey(code);
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (SwingUtilities.isRightMouseButton(e)) {
                        pressKey(-7);
                        return;
                    }
                    if (SwingUtilities.isMiddleMouseButton(e)) {
                        pressKey(-6);
                        return;
                    }
                    if (!SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    int[] p = toCanvas(e.getX(), e.getY());
                    if (p == null) {
                        return;
                    }
                    Canvas current = canvas;
                    pointerX = p[0];
                    pointerY = p[1];
                    mouseX = p[0];
                    mouseY = p[1];
                    lastDragRepress = System.currentTimeMillis();
                    // While moving with the keyboard, a tap on open gameplay
                    // area is sent as the 5 (attack) key instead: the game's
                    // touch handler would otherwise wipe the held input and
                    // any touch action would cancel the movement. Taps on the
                    // on-screen touch buttons and in menus stay pointer
                    // events.
                    if (shouldTranslateTapToFire()) {
                        translatedClick = true;
                        pointerDown = false;
                        if (current != null) {
                            current.dispatchKeyPressed(53);
                        }
                        return;
                    }
                    translatedClick = false;
                    pointerDown = true;
                    if (current != null) {
                        current.dispatchPointerPressed(p[0], p[1]);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        releaseKey(-7);
                        return;
                    }
                    if (SwingUtilities.isMiddleMouseButton(e)) {
                        releaseKey(-6);
                        return;
                    }
                    if (!SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    Canvas current = canvas;
                    if (translatedClick) {
                        translatedClick = false;
                        if (current != null) {
                            current.dispatchKeyReleased(53);
                            resyncHeldKeys(current);
                        }
                        pointerX = -1;
                        pointerY = -1;
                        mouseX = -1;
                        mouseY = -1;
                        return;
                    }
                    if (!pointerDown) {
                        return;
                    }
                    int x = mouseX >= 0 ? mouseX : pointerX;
                    int y = mouseY >= 0 ? mouseY : pointerY;
                    pointerDown = false;
                    if (current != null) {
                        current.dispatchPointerReleased(x, y);
                        resyncHeldKeys(current);
                    }
                    pointerX = -1;
                    pointerY = -1;
                    mouseX = -1;
                    mouseY = -1;
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!pointerDown || translatedClick) {
                        return;
                    }
                    int[] p = toCanvas(e.getX(), e.getY());
                    Canvas current = canvas;
                    if (current == null || p == null) {
                        return;
                    }
                    mouseX = p[0];
                    mouseY = p[1];
                    current.dispatchPointerDragged(p[0], p[1]);
                    long now = System.currentTimeMillis();
                    if (now - lastDragRepress < 70) {
                        return;
                    }
                    if (Math.abs(p[0] - pointerX) < 6 && Math.abs(p[1] - pointerY) < 6) {
                        return;
                    }
                    current.dispatchPointerReleased(pointerX, pointerY);
                    current.dispatchPointerPressed(p[0], p[1]);
                    resyncHeldKeys(current);
                    pointerX = p[0];
                    pointerY = p[1];
                    lastDragRepress = now;
                }
            });
        }

        // Translates a left click into the 5 action key while a movement key
        // is physically held and the MIDlet is in gameplay (state 8), except
        // over the on-screen touch button strip at the bottom of the canvas.
        private boolean shouldTranslateTapToFire() {
            Canvas current = canvas;
            if (current == null) {
                return false;
            }
            if (pointerY >= TOUCH_BUTTON_ZONE_Y) {
                return false;
            }
            boolean moving = false;
            for (int code : DIRECTION_KEYS) {
                if (isDirectionKeyHeld(code)) {
                    moving = true;
                    break;
                }
            }
            if (!moving) {
                return false;
            }
            return current.getGameScreenState() == GAMEPLAY_STATE;
        }

        private void cancelPointer() {
            Canvas current = canvas;
            if (!pointerDown && !translatedClick) {
                return;
            }
            boolean wasTranslated = translatedClick;
            pointerDown = false;
            translatedClick = false;
            if (current != null) {
                if (wasTranslated) {
                    current.dispatchKeyReleased(53);
                } else {
                    current.dispatchPointerReleased(mouseX >= 0 ? mouseX : pointerX,
                            mouseY >= 0 ? mouseY : pointerY);
                }
                resyncHeldKeys(current);
            }
            pointerX = -1;
            pointerY = -1;
            mouseX = -1;
            mouseY = -1;
        }

        private int[] toCanvas(int x, int y) {
            int[] rect = destinationRect(getWidth(), getHeight());
            int dx = rect[0];
            int dy = rect[1];
            int dw = rect[2];
            int dh = rect[3];
            if (dw <= 0 || dh <= 0) {
                return null;
            }
            if (x < dx || x >= dx + dw || y < dy || y >= dy + dh) {
                return null;
            }
            int cx = (x - dx) * CANVAS_WIDTH / dw;
            int cy = (y - dy) * CANVAS_HEIGHT / dh;
            cx = Math.max(0, Math.min(CANVAS_WIDTH - 1, cx));
            cy = Math.max(0, Math.min(CANVAS_HEIGHT - 1, cy));
            return new int[]{cx, cy};
        }

        private int[] destinationRect(int panelWidth, int panelHeight) {
            if ("integer".equals(scaleMode)) {
                int factor = Math.min(panelWidth / CANVAS_WIDTH, panelHeight / CANVAS_HEIGHT);
                if (factor >= 1) {
                    int dw = CANVAS_WIDTH * factor;
                    int dh = CANVAS_HEIGHT * factor;
                    return new int[]{(panelWidth - dw) / 2, (panelHeight - dh) / 2, dw, dh};
                }
            }
            double scale = Math.min(panelWidth / (double) CANVAS_WIDTH,
                    panelHeight / (double) CANVAS_HEIGHT);
            int dw = Math.max(1, (int) Math.round(CANVAS_WIDTH * scale));
            int dh = Math.max(1, (int) Math.round(CANVAS_HEIGHT * scale));
            return new int[]{(panelWidth - dw) / 2, (panelHeight - dh) / 2, dw, dh};
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics;
            int[] rect = destinationRect(getWidth(), getHeight());
            if ("bilinear".equals(scaleMode)) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            } else {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            }
            g.drawImage(canvasImage.getAWTImage(), rect[0], rect[1], rect[2], rect[3], null);
        }
    }
}
