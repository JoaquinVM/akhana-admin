package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.exception.ProductPhotoStorageException;
import com.akhana.akhana_admin.service.ProductPhotoService;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOReadWarningListener;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// CAT-12: turns an uploaded file into a clean JPEG and its thumbnail. Drawing onto a new RGB image and writing it
// with no metadata is what removes the location and every other EXIF field.
@Component
public class ProductPhotoProcessor {

    public record ProcessedPhoto(byte[] image, byte[] thumbnail) {}

    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final long MAX_PIXELS = 50_000_000L;
    static final int MAX_IMAGE_SIDE = 1600;
    static final int MAX_THUMBNAIL_SIDE = 300;
    static final int MAX_IMAGE_BYTES = 500 * 1024;

    // Decoding stops around twice the final size, so a large photo uses far less memory
    private static final int DECODE_SIDE = MAX_IMAGE_SIDE * 2;
    private static final float[] QUALITIES = {0.82f, 0.72f, 0.6f};
    private static final float THUMBNAIL_QUALITY = 0.8f;
    private static final double SHRINK_STEP = 0.85;
    private static final int PERMIT_WAIT_SECONDS = 30;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_MAGIC = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MAGIC = "WEBP".getBytes(StandardCharsets.US_ASCII);

    // One image at a time: a 50 megapixel WebP is decoded whole, about 400 MB of heap with its copy
    private final Semaphore permit = new Semaphore(1);

    public ProductPhotoProcessor() {
        // Inside the Spring Boot jar the plugins may not register by themselves
        ImageIO.scanForPlugins();
    }

    public ProcessedPhoto process(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(ProductPhotoService.INVALID_FILE);
        }
        String format = detectFormat(content)
            .orElseThrow(() -> new IllegalArgumentException(ProductPhotoService.INVALID_FILE));
        acquirePermit();
        try {
            BufferedImage decoded = decode(content, format);
            BufferedImage oriented = orientOnWhite(decoded, ProductPhotoInspector.readOrientation(content, format));
            BufferedImage image = fit(oriented, MAX_IMAGE_SIDE);
            return new ProcessedPhoto(encodeWithinLimit(image),
                encode(fit(image, MAX_THUMBNAIL_SIDE), THUMBNAIL_QUALITY));
        } catch (IOException | OutOfMemoryError ex) {
            throw new ProductPhotoStorageException(ProductPhotoService.SAVE_FAILED, ex);
        } finally {
            permit.release();
        }
    }

    private void acquirePermit() {
        try {
            if (!permit.tryAcquire(PERMIT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new ProductPhotoStorageException(ProductPhotoService.SAVE_FAILED, null);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProductPhotoStorageException(ProductPhotoService.SAVE_FAILED, ex);
        }
    }

    // By the real content of the file, whatever its name or declared type says
    private static Optional<String> detectFormat(byte[] content) {
        if (startsWith(content, 0, JPEG_MAGIC)) {
            return Optional.of("jpeg");
        }
        if (startsWith(content, 0, PNG_MAGIC)) {
            return Optional.of("png");
        }
        if (startsWith(content, 0, RIFF_MAGIC) && startsWith(content, 8, WEBP_MAGIC)) {
            return Optional.of("webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
            && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static BufferedImage decode(byte[] content, String format) {
        if ("webp".equals(format)) {
            ProductPhotoInspector.checkWebp(content, MAX_PIXELS);
        } else if ("jpeg".equals(format)) {
            ProductPhotoInspector.checkJpeg(content);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            throw new ProductPhotoStorageException(ProductPhotoService.SAVE_FAILED, null);
        }
        ImageReader reader = readers.next();
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            long width = reader.getWidth(0);
            long height = reader.getHeight(0);
            if (width * height > MAX_PIXELS) {
                throw new IllegalArgumentException(ProductPhotoService.TOO_MANY_PIXELS);
            }
            TruncationListener truncation = new TruncationListener();
            reader.addIIOReadWarningListener(truncation);

            ImageReadParam param = reader.getDefaultReadParam();
            int subsampling = (int) Math.max(1, Math.max(width, height) / DECODE_SIDE);
            param.setSourceSubsampling(subsampling, subsampling, 0, 0);
            BufferedImage image = reader.read(0, param);
            if (image == null || truncation.truncated) {
                throw new IllegalArgumentException(ProductPhotoService.INVALID_FILE);
            }
            return image;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException illegal && isOwnMessage(illegal)) {
                throw illegal;
            }
            // Whatever the decoder rejects is a damaged file
            throw new IllegalArgumentException(ProductPhotoService.INVALID_FILE, ex);
        } finally {
            reader.dispose();
        }
    }

    private static boolean isOwnMessage(IllegalArgumentException ex) {
        return ProductPhotoService.TOO_MANY_PIXELS.equals(ex.getMessage())
            || ProductPhotoService.INVALID_FILE.equals(ex.getMessage());
    }

    // White shows through transparency (PNG, WebP) and every color space ends up in RGB
    private static BufferedImage orientOnWhite(BufferedImage source, int orientation) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swapped = orientation >= 5 && orientation <= 8;

        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> { transform.scale(-1, 1); transform.translate(-width, 0); }
            case 3 -> { transform.translate(width, height); transform.rotate(Math.PI); }
            case 4 -> { transform.scale(1, -1); transform.translate(0, -height); }
            case 5 -> { transform.rotate(-Math.PI / 2); transform.scale(-1, 1); }
            case 6 -> { transform.translate(height, 0); transform.rotate(Math.PI / 2); }
            case 7 -> { transform.scale(-1, 1); transform.translate(-height, 0); transform.translate(0, width); transform.rotate(-Math.PI / 2); }
            case 8 -> { transform.translate(0, width); transform.rotate(-Math.PI / 2); }
            default -> { }
        }

        BufferedImage result = new BufferedImage(swapped ? height : width, swapped ? width : height,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    // Never enlarges; halving first keeps the reduction sharp
    private static BufferedImage fit(BufferedImage image, int maxSide) {
        BufferedImage current = image;
        int side = Math.max(current.getWidth(), current.getHeight());
        while (side > maxSide) {
            double ratio = Math.max(0.5, (double) maxSide / side);
            current = scale(current, ratio);
            side = Math.max(current.getWidth(), current.getHeight());
        }
        return current;
    }

    private static BufferedImage scale(BufferedImage image, double ratio) {
        int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static byte[] encodeWithinLimit(BufferedImage image) throws IOException {
        BufferedImage current = image;
        while (true) {
            for (float quality : QUALITIES) {
                byte[] encoded = encode(current, quality);
                if (encoded.length <= MAX_IMAGE_BYTES) {
                    return encoded;
                }
            }
            current = scale(current, SHRINK_STEP);
        }
    }

    private static byte[] encode(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    // The JDK returns a cut JPEG without an exception, only with this warning
    private static final class TruncationListener implements IIOReadWarningListener {
        private boolean truncated;

        @Override
        public void warningOccurred(ImageReader source, String warning) {
            String text = warning == null ? "" : warning.toLowerCase(Locale.ROOT);
            if (text.contains("premature") || text.contains("truncated")) {
                truncated = true;
            }
        }
    }
}
