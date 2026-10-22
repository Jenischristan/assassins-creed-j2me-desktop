package javax.microedition.lcdui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

public final class Image {
    private final BufferedImage image;
    private final boolean mutable;

    private Image(BufferedImage image, boolean mutable) {
        this.image = image;
        this.mutable = mutable;
    }

    public static Image createImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        return new Image(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), true);
    }

    public static Image createImage(String name) throws IOException {
        InputStream in = Image.class.getResourceAsStream(name);
        if (in == null) {
            throw new IOException("resource not found: " + name);
        }
        try {
            return decode(readFully(in));
        } finally {
            in.close();
        }
    }

    public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
        if (imageData == null) {
            throw new NullPointerException();
        }
        if (imageOffset < 0 || imageLength < 0
                || imageOffset + imageLength > imageData.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        try {
            return decode(java.util.Arrays.copyOfRange(
                    imageData, imageOffset, imageOffset + imageLength));
        } catch (IOException e) {
            throw new IllegalArgumentException("could not decode image: " + e);
        }
    }

    public static Image createImage(InputStream stream) throws IOException {
        return decode(readFully(stream));
    }

    public static Image createRGBImage(int[] rgb, int width, int height,
            boolean processAlpha) {
        if (rgb == null) {
            throw new NullPointerException();
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        if (rgb.length < width * height) {
            throw new ArrayIndexOutOfBoundsException();
        }
        BufferedImage img = new BufferedImage(width, height,
                processAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, width, height, rgb, 0, width);
        return new Image(img, false);
    }

    private static Image decode(byte[] data) throws IOException {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));
        if (decoded == null) {
            throw new IOException("unsupported image format");
        }
        if (decoded.getType() != BufferedImage.TYPE_INT_ARGB
                && decoded.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage converted = new BufferedImage(
                    decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = converted.createGraphics();
            g.drawImage(decoded, 0, 0, null);
            g.dispose();
            decoded = converted;
        }
        return new Image(decoded, false);
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public boolean isMutable() {
        return mutable;
    }

    public Graphics getGraphics() {
        if (!mutable) {
            throw new IllegalStateException("image is immutable");
        }
        return new Graphics(image);
    }

    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y,
            int width, int height) {
        if (rgbData == null) {
            throw new NullPointerException();
        }
        image.getRGB(x, y, width, height, rgbData, offset, scanlength);
    }

    public BufferedImage getAWTImage() {
        return image;
    }
}
