package javax.microedition.lcdui;

import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;

public final class Font {
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    private static Font defaultFont;

    private final java.awt.Font awtFont;
    private final int face;
    private final int style;
    private final int size;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;

        String family;
        if ((face & FACE_MONOSPACE) != 0) {
            family = java.awt.Font.MONOSPACED;
        } else if ((face & FACE_PROPORTIONAL) != 0) {
            family = java.awt.Font.SANS_SERIF;
        } else {
            family = java.awt.Font.DIALOG;
        }

        int awtStyle = java.awt.Font.PLAIN;
        if ((style & STYLE_BOLD) != 0) {
            awtStyle |= java.awt.Font.BOLD;
        }
        if ((style & STYLE_ITALIC) != 0) {
            awtStyle |= java.awt.Font.ITALIC;
        }

        int pointSize;
        if (size == SIZE_SMALL) {
            pointSize = 10;
        } else if (size == SIZE_LARGE) {
            pointSize = 18;
        } else {
            pointSize = 13;
        }
        awtFont = new java.awt.Font(family, awtStyle, pointSize);
    }

    public static Font getFont(int face, int style, int size) {
        return new Font(face, style, size);
    }

    public static Font getDefaultFont() {
        if (defaultFont == null) {
            defaultFont = new Font(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
        }
        return defaultFont;
    }

    public int getFace() {
        return face;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public boolean isPlain() {
        return style == STYLE_PLAIN;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public int getHeight() {
        return Math.round(metrics().getHeight());
    }

    public int getBaselinePosition() {
        return Math.round(metrics().getAscent());
    }

    public int charWidth(char ch) {
        return (int) Math.round(width(String.valueOf(ch)));
    }

    public int charsWidth(char[] ch, int offset, int length) {
        return (int) Math.round(width(new String(ch, offset, length)));
    }

    public int stringWidth(String str) {
        return (int) Math.round(width(str));
    }

    public int substringWidth(String str, int offset, int len) {
        return stringWidth(str.substring(offset, offset + len));
    }

    private double width(String str) {
        if (str == null || str.length() == 0) {
            return 0;
        }
        FontRenderContext frc = new FontRenderContext(null, false, true);
        return awtFont.getStringBounds(str, frc).getWidth();
    }

    private LineMetrics metrics() {
        FontRenderContext frc = new FontRenderContext(null, false, true);
        return awtFont.getLineMetrics("Ag", frc);
    }

    public java.awt.Font getAwtFont() {
        return awtFont;
    }
}
