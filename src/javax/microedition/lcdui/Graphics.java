package javax.microedition.lcdui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public final class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;

    public static final int TRANS_NONE = 0;
    public static final int TRANS_MIRROR_ROT180 = 1;
    public static final int TRANS_MIRROR = 2;
    public static final int TRANS_ROT180 = 3;
    public static final int TRANS_MIRROR_ROT270 = 4;
    public static final int TRANS_ROT90 = 5;
    public static final int TRANS_ROT270 = 6;
    public static final int TRANS_MIRROR_ROT90 = 7;

    private final BufferedImage target;
    private final Graphics2D g;
    private int color;
    private Font font = Font.getDefaultFont();
    private int clipX;
    private int clipY;
    private int clipWidth;
    private int clipHeight;

    Graphics(BufferedImage target) {
        this.target = target;
        this.g = target.createGraphics();
        this.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        this.g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        this.g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_SPEED);
        this.g.setStroke(new BasicStroke(1.0f));
        setColor(0);
        setClip(0, 0, target.getWidth(), target.getHeight());
    }

    public void setColor(int rgb) {
        color = rgb & 0xFFFFFF;
        g.setColor(new Color(color));
    }

    public void setColor(int red, int green, int blue) {
        setColor(((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF));
    }

    public int getColor() {
        return color;
    }

    public void setFont(Font font) {
        if (font != null) {
            this.font = font;
            g.setFont(font.getAwtFont());
        }
    }

    public Font getFont() {
        return font;
    }

    public void setClip(int x, int y, int width, int height) {
        if (width < 0) {
            x += width;
            width = -width;
        }
        if (height < 0) {
            y += height;
            height = -height;
        }
        int x2 = x + width;
        int y2 = y + height;
        int nx = Math.max(x, 0);
        int ny = Math.max(y, 0);
        int nx2 = Math.min(x2, target.getWidth());
        int ny2 = Math.min(y2, target.getHeight());
        clipX = nx;
        clipY = ny;
        clipWidth = Math.max(0, nx2 - nx);
        clipHeight = Math.max(0, ny2 - ny);
        g.setClip(clipX, clipY, clipWidth, clipHeight);
    }

    public int getClipX() {
        return clipX;
    }

    public int getClipY() {
        return clipY;
    }

    public int getClipWidth() {
        return clipWidth;
    }

    public int getClipHeight() {
        return clipHeight;
    }

    public void clipRect(int x, int y, int width, int height) {
        int x2 = Math.min(clipX + clipWidth, x + width);
        int y2 = Math.min(clipY + clipHeight, y + height);
        int nx = Math.max(clipX, x);
        int ny = Math.max(clipY, y);
        setClip(nx, ny, Math.max(0, x2 - nx), Math.max(0, y2 - ny));
    }

    public void fillRect(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.fillRect(x, y, width, height);
    }

    public void drawRect(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.drawRect(x, y, width - 1, height - 1);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        g.drawLine(x1, y1, x2, y2);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        Path2D path = new Path2D.Double();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.closePath();
        g.fill(path);
    }

    public void fillRoundRect(int x, int y, int width, int height,
            int arcWidth, int arcHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.fill(new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight));
    }

    public void drawRoundRect(int x, int y, int width, int height,
            int arcWidth, int arcHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.draw(new RoundRectangle2D.Double(x, y, width - 1, height - 1,
                arcWidth, arcHeight));
    }

    public void drawArc(int x, int y, int width, int height,
            int startAngle, int arcAngle) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.draw(new Arc2D.Double(x, y, width, height, startAngle, arcAngle, Arc2D.OPEN));
    }

    public void fillArc(int x, int y, int width, int height,
            int startAngle, int arcAngle) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.fill(new Arc2D.Double(x, y, width, height, startAngle, arcAngle, Arc2D.PIE));
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) {
            return;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int dx = x - horizontalAnchor(anchor, w);
        int dy = y - verticalAnchor(anchor, h);
        g.drawImage(img.getAWTImage(), dx, dy, null);
    }

    public void drawRegion(Image src, int xSrc, int ySrc, int width, int height,
            int transform, int xDest, int yDest, int anchor) {
        if (src == null || width <= 0 || height <= 0) {
            return;
        }
        BufferedImage bi = src.getAWTImage();
        int x0 = xSrc;
        int y0 = ySrc;
        int x1 = xSrc + width;
        int y1 = ySrc + height;

        int cx0 = Math.max(x0, 0);
        int cy0 = Math.max(y0, 0);
        int cx1 = Math.min(x1, bi.getWidth());
        int cy1 = Math.min(y1, bi.getHeight());
        if (cx0 >= cx1 || cy0 >= cy1) {
            return;
        }

        int xoff = cx0 - x0;
        int yoff = cy0 - y0;
        BufferedImage sub = bi.getSubimage(cx0, cy0, cx1 - cx0, cy1 - cy0);

        boolean swap = transform == TRANS_ROT90 || transform == TRANS_ROT270
                || transform == TRANS_MIRROR_ROT90 || transform == TRANS_MIRROR_ROT270;
        int drawW = swap ? height : width;
        int drawH = swap ? width : height;

        int dx = xDest - horizontalAnchor(anchor, drawW);
        int dy = yDest - verticalAnchor(anchor, drawH);

        if (transform == TRANS_NONE) {
            g.drawImage(sub, dx + xoff, dy + yoff, null);
            return;
        }

        AffineTransform at = regionTransform(transform, width, height,
                dx + xoff, dy + yoff);
        g.drawImage(sub, at, null);
    }

    private static AffineTransform regionTransform(int transform,
            int width, int height, double dx, double dy) {
        switch (transform) {
            case TRANS_MIRROR:
                return new AffineTransform(-1, 0, 0, 1, dx + width, dy);
            case TRANS_ROT180:
                return new AffineTransform(-1, 0, 0, -1, dx + width, dy + height);
            case TRANS_ROT90:
                return new AffineTransform(0, 1, -1, 0, dx + height, dy);
            case TRANS_ROT270:
                return new AffineTransform(0, -1, 1, 0, dx, dy + width);
            case TRANS_MIRROR_ROT90:
                return new AffineTransform(0, -1, -1, 0, dx + height, dy + width);
            case TRANS_MIRROR_ROT180:
                return new AffineTransform(1, 0, 0, -1, dx, dy + height);
            case TRANS_MIRROR_ROT270:
                return new AffineTransform(0, 1, 1, 0, dx, dy);
            case TRANS_NONE:
            default:
                return new AffineTransform(1, 0, 0, 1, dx, dy);
        }
    }

    public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y,
            int width, int height, boolean processAlpha) {
        if (rgbData == null) {
            throw new NullPointerException();
        }
        if (width <= 0 || height <= 0) {
            return;
        }
        int x1 = Math.max(x, clipX);
        int y1 = Math.max(y, clipY);
        int x2 = Math.min(x + width, clipX + clipWidth);
        int y2 = Math.min(y + height, clipY + clipHeight);
        if (x1 >= x2 || y1 >= y2) {
            return;
        }
        int w = x2 - x1;
        int h = y2 - y1;
        int off = offset + (y1 - y) * scanlength + (x1 - x);
        if (processAlpha) {
            BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            tmp.setRGB(0, 0, w, h, rgbData, off, scanlength);
            g.drawImage(tmp, x1, y1, null);
        } else if (target.getType() == BufferedImage.TYPE_INT_RGB) {
            target.setRGB(x1, y1, w, h, rgbData, off, scanlength);
        } else {
            int[] row = new int[w];
            int[] rows = new int[w * h];
            int o = off;
            int p = 0;
            for (int j = 0; j < h; j++) {
                System.arraycopy(rgbData, o, row, 0, w);
                for (int i = 0; i < w; i++) {
                    rows[p++] = row[i] | 0xFF000000;
                }
                o += scanlength;
            }
            target.setRGB(x1, y1, w, h, rows, 0, w);
        }
    }

    public void drawString(String str, int x, int y, int anchor) {
        if (str == null) {
            str = "";
        }
        int w = font.stringWidth(str);
        int h = font.getHeight();
        int ascent = font.getBaselinePosition();
        int dx = x - horizontalAnchor(anchor, w);
        int baseline;
        if ((anchor & BASELINE) != 0) {
            baseline = y;
        } else {
            int top = y - verticalAnchor(anchor, h);
            baseline = top + ascent;
        }
        g.setFont(font.getAwtFont());
        g.drawString(str, dx, baseline);
        if (font.isUnderlined()) {
            g.drawLine(dx, baseline + 1, dx + w, baseline + 1);
        }
    }

    public void drawChar(char character, int x, int y, int anchor) {
        drawString(String.valueOf(character), x, y, anchor);
    }

    public void drawChars(char[] data, int offset, int length, int x, int y,
            int anchor) {
        drawString(new String(data, offset, length), x, y, anchor);
    }

    public void drawSubstring(String str, int offset, int len, int x, int y,
            int anchor) {
        drawString(str.substring(offset, offset + len), x, y, anchor);
    }

    private static int horizontalAnchor(int anchor, int width) {
        if ((anchor & HCENTER) != 0) {
            return width / 2;
        }
        if ((anchor & RIGHT) != 0) {
            return width;
        }
        return 0;
    }

    private static int verticalAnchor(int anchor, int height) {
        if ((anchor & VCENTER) != 0) {
            return height / 2;
        }
        if ((anchor & BOTTOM) != 0 || (anchor & BASELINE) != 0) {
            return height;
        }
        return 0;
    }
}
