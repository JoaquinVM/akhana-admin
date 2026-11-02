package com.akhana.akhana_admin.service.impl;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

// Images built in the test, so the repository holds no real location (CAT-12)
final class ProductPhotoFixtures {

    static final Color RED = new Color(220, 20, 20);
    static final Color GREEN = new Color(20, 200, 20);
    static final Color BLUE = new Color(20, 20, 220);
    static final Color YELLOW = new Color(230, 230, 20);

    private ProductPhotoFixtures() {
    }

    // Red and green on top, blue and yellow below
    static BufferedImage quadrants(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(RED);
        graphics.fillRect(0, 0, width / 2, height / 2);
        graphics.setColor(GREEN);
        graphics.fillRect(width / 2, 0, width - width / 2, height / 2);
        graphics.setColor(BLUE);
        graphics.fillRect(0, height / 2, width / 2, height - height / 2);
        graphics.setColor(YELLOW);
        graphics.fillRect(width / 2, height / 2, width - width / 2, height - height / 2);
        graphics.dispose();
        return image;
    }

    static BufferedImage noise(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(23);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        return image;
    }

    static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", bytes);
        return bytes.toByteArray();
    }

    static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    // Four bands with no color conversion: what the JDK reader cannot open
    static byte[] cmykJpeg(int width, int height) throws IOException {
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 4, null);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.setPixel(x, y, new int[]{0, 200, 200, 30});
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(raster, null, null), null);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    // Adds an EXIF block right after the JPEG start marker, with its orientation and a GPS latitude
    static byte[] withExif(byte[] jpeg, int orientation) throws IOException {
        byte[] tiff = tiffWithOrientationAndGps(orientation);
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(header);
        out.write(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] tiffWithOrientationAndGps(int orientation) {
        int gpsOffset = 8 + 2 + 2 * 12 + 4;
        int latitudeOffset = gpsOffset + 2 + 2 * 12 + 4;
        ByteBuffer tiff = ByteBuffer.allocate(latitudeOffset + 24);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        // IFD0: orientation and pointer to the GPS block
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gpsOffset);
        tiff.putInt(0);
        // GPS: latitude reference and latitude
        tiff.putShort((short) 2);
        tiff.putShort((short) 1).putShort((short) 2).putInt(2).put((byte) 'S').put((byte) 0).putShort((short) 0);
        tiff.putShort((short) 2).putShort((short) 5).putInt(3).putInt(latitudeOffset);
        tiff.putInt(0);
        tiff.putInt(16).putInt(1).putInt(30).putInt(1).putInt(0).putInt(1);
        return tiff.array();
    }

    // A real PNG header claiming the given size, with no pixels: decoding it could only fail
    static byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer chunk = ByteBuffer.allocate(17);
        chunk.put("IHDR".getBytes(StandardCharsets.US_ASCII));
        chunk.putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(chunk.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
        png.put(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        png.putInt(13).put(chunk.array()).putInt((int) crc.getValue());
        return png.array();
    }

    // A valid small canvas in the file header, with a frame inside that declares another size
    static byte[] webpWithLargeFrame(String frameType, int width, int height) {
        ByteBuffer frame = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        if (frameType.equals("VP8 ")) {
            frame.put(new byte[]{0x10, 0, 0, (byte) 0x9D, 0x01, 0x2A});
            frame.putShort((short) width).putShort((short) height);
        } else {
            frame.put((byte) 0x2F);
            frame.putInt((width - 1) | ((height - 1) << 14));
        }
        ByteBuffer file = ByteBuffer.allocate(12 + 18 + 8 + 16).order(ByteOrder.LITTLE_ENDIAN);
        file.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(file.capacity() - 8).put("WEBP".getBytes(StandardCharsets.US_ASCII));
        file.put("VP8X".getBytes(StandardCharsets.US_ASCII)).putInt(10);
        file.put(new byte[]{0, 0, 0, 0, 99, 0, 0, 99, 0, 0});
        file.put(frameType.getBytes(StandardCharsets.US_ASCII)).putInt(16).put(frame.array());
        return file.array();
    }

    // A real small PNG with a compressed text chunk that inflates to the given size
    static byte[] pngWithTextBomb(long inflatedBytes) throws IOException {
        byte[] png = png(quadrants(8, 8));
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        compressed.write("Comment\0\0".getBytes(StandardCharsets.ISO_8859_1));
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed, new Deflater(Deflater.BEST_COMPRESSION))) {
            byte[] zeros = new byte[1024 * 1024];
            for (long left = inflatedBytes; left > 0; left -= zeros.length) {
                deflater.write(zeros);
            }
        }
        byte[] data = compressed.toByteArray();
        ByteBuffer chunk = ByteBuffer.allocate(4 + data.length);
        chunk.put("zTXt".getBytes(StandardCharsets.US_ASCII)).put(data);
        CRC32 crc = new CRC32();
        crc.update(chunk.array());
        int afterHeader = 8 + 25;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, afterHeader);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(chunk.array());
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        out.write(png, afterHeader, png.length - afterHeader);
        return out.toByteArray();
    }

    // A real PNG with its EXIF in an eXIf chunk
    static byte[] pngWithOrientation(BufferedImage image, int orientation) throws IOException {
        byte[] png = png(image);
        byte[] tiff = tiffWithOrientationAndGps(orientation);
        ByteBuffer chunk = ByteBuffer.allocate(4 + tiff.length);
        chunk.put("eXIf".getBytes(StandardCharsets.US_ASCII)).put(tiff);
        CRC32 crc = new CRC32();
        crc.update(chunk.array());
        int afterHeader = 8 + 25;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, afterHeader);
        out.write(ByteBuffer.allocate(4).putInt(tiff.length).array());
        out.write(chunk.array());
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        out.write(png, afterHeader, png.length - afterHeader);
        return out.toByteArray();
    }
}
