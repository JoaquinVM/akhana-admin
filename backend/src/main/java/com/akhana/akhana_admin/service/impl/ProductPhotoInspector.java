package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.service.ProductPhotoService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// CAT-12: looks at an uploaded file without decoding it. A file of 10 MB can ask for gigabytes or hours, so what is
// read here is walked by hand, in bounded steps, instead of through a general reader of images or metadata.
final class ProductPhotoInspector {

    // A photo uses about ten scans; thousands of them take hours to decode
    static final int MAX_JPEG_SCANS = 100;

    private static final int RIFF_HEADER_BYTES = 12;
    private static final int CHUNK_HEADER_BYTES = 8;
    private static final int PNG_SIGNATURE_BYTES = 8;
    private static final int PNG_CRC_BYTES = 4;
    private static final int APP1 = 0xE1;
    private static final int START_OF_SCAN = 0xDA;
    private static final int START_OF_IMAGE = 0xD8;
    private static final int TEMPORARY_MARKER = 0x01;
    private static final int FIRST_RESERVED_MARKER = 0x02;
    private static final int LAST_RESERVED_MARKER = 0xBF;
    private static final int END_OF_IMAGE = 0xD9;
    private static final int ORIENTATION_TAG = 0x0112;
    private static final int SHORT_TYPE = 3;
    private static final int IFD_ENTRY_BYTES = 12;
    private static final int DEFAULT_ORIENTATION = 1;
    private static final byte[] EXIF_PREFIX = {'E', 'x', 'i', 'f', 0, 0};

    private ProductPhotoInspector() {
    }

    // Exactly one image, because the decoder goes through every frame it finds and reserves memory for what each one
    // declares, not for the size in the file header. An animation is not a photo.
    static void checkWebp(byte[] content, long maxPixels) {
        int frames = 0;
        int alphas = 0;
        ByteBuffer file = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        int offset = RIFF_HEADER_BYTES;
        while (offset + CHUNK_HEADER_BYTES <= content.length) {
            String type = chunkType(content, offset);
            long size = Integer.toUnsignedLong(file.getInt(offset + 4));
            int payload = offset + CHUNK_HEADER_BYTES;
            if (type.equals("ANMF")) {
                throw invalid();
            }
            if (type.equals("ALPH")) {
                alphas++;
            }
            if (type.equals("VP8 ") || type.equals("VP8L")) {
                frames++;
                if (webpFramePixels(file, type, payload) > maxPixels) {
                    throw new IllegalArgumentException(ProductPhotoService.TOO_MANY_PIXELS);
                }
            }
            long next = payload + size + (size & 1);
            if (next > content.length) {
                break;
            }
            offset = (int) next;
        }
        if (frames != 1 || alphas > 1) {
            throw invalid();
        }
    }

    private static long webpFramePixels(ByteBuffer file, String type, int payload) {
        if (type.equals("VP8 ")) {
            // Frame tag (3 bytes), start code (3), then width and height in 14 bits each
            if (payload + 10 > file.capacity()) {
                throw invalid();
            }
            long width = file.getShort(payload + 6) & 0x3FFF;
            long height = file.getShort(payload + 8) & 0x3FFF;
            return width * height;
        }
        // Signature (1 byte), then width - 1 and height - 1 in 14 bits each
        if (payload + 5 > file.capacity()) {
            throw invalid();
        }
        int bits = file.getInt(payload + 1);
        long width = (bits & 0x3FFF) + 1L;
        long height = ((bits >>> 14) & 0x3FFF) + 1L;
        return width * height;
    }

    // Walked as the decoder does: scans that follow a marker with no length count too, and what comes after the end of
    // the image (the video of a motion photo, other images) is not part of it
    static void checkJpeg(byte[] content) {
        int scans = 0;
        int offset = nextJpegMarker(content, 2);
        while (offset >= 0) {
            int marker = content[offset + 1] & 0xFF;
            if (marker == END_OF_IMAGE) {
                return;
            }
            // Reserved markers: the walk would skip them by their length, but the decoder can discard them and find a
            // scan hidden inside
            if (marker >= FIRST_RESERVED_MARKER && marker <= LAST_RESERVED_MARKER) {
                throw invalid();
            }
            if (marker == START_OF_SCAN && ++scans > MAX_JPEG_SCANS) {
                throw invalid();
            }
            offset = nextJpegMarker(content, afterJpegMarker(content, offset, marker));
        }
    }

    // The next real marker: fill bytes, stuffed zeros, restart markers and data in between are skipped
    private static int nextJpegMarker(byte[] content, int from) {
        for (int i = from; i + 1 < content.length; i++) {
            if ((content[i] & 0xFF) == 0xFF) {
                int next = content[i + 1] & 0xFF;
                if (next != 0xFF && next != 0 && !isRestartMarker(next)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int afterJpegMarker(byte[] content, int offset, int marker) {
        if (marker == TEMPORARY_MARKER || marker == START_OF_IMAGE || offset + 4 > content.length) {
            return offset + 2;
        }
        return offset + 2 + Math.max(2, unsignedShort(content, offset + 2, false));
    }

    private static boolean isRestartMarker(int marker) {
        return marker >= 0xD0 && marker <= 0xD7;
    }

    // Only tag 0x0112 of the first EXIF block: a general reader builds an array for every tag, and thousands of tags can
    // point to the same bytes
    static int readOrientation(byte[] content, String format) {
        return switch (format) {
            case "jpeg" -> jpegOrientation(content);
            case "png" -> pngOrientation(content);
            case "webp" -> webpOrientation(content);
            default -> DEFAULT_ORIENTATION;
        };
    }

    private static int jpegOrientation(byte[] content) {
        int offset = nextJpegMarker(content, 2);
        while (offset >= 0) {
            int marker = content[offset + 1] & 0xFF;
            if (marker == START_OF_SCAN || marker == END_OF_IMAGE) {
                break;
            }
            int next = afterJpegMarker(content, offset, marker);
            if (marker == APP1 && hasExifPrefix(content, offset + 4, Math.min(next, content.length))) {
                return exifOrientation(content, offset + 4, Math.min(next, content.length));
            }
            offset = nextJpegMarker(content, next);
        }
        return DEFAULT_ORIENTATION;
    }

    private static int pngOrientation(byte[] content) {
        ByteBuffer file = ByteBuffer.wrap(content);
        int offset = PNG_SIGNATURE_BYTES;
        while (offset + CHUNK_HEADER_BYTES <= content.length) {
            long size = Integer.toUnsignedLong(file.getInt(offset));
            int payload = offset + CHUNK_HEADER_BYTES;
            if (payload + size > content.length) {
                break;
            }
            if (chunkType(content, offset + 4).equals("eXIf")) {
                return exifOrientation(content, payload, payload + (int) size);
            }
            offset = payload + (int) size + PNG_CRC_BYTES;
        }
        return DEFAULT_ORIENTATION;
    }

    private static int webpOrientation(byte[] content) {
        ByteBuffer file = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        int offset = RIFF_HEADER_BYTES;
        while (offset + CHUNK_HEADER_BYTES <= content.length) {
            long size = Integer.toUnsignedLong(file.getInt(offset + 4));
            int payload = offset + CHUNK_HEADER_BYTES;
            if (payload + size > content.length) {
                break;
            }
            if (chunkType(content, offset).equals("EXIF")) {
                return exifOrientation(content, payload, payload + (int) size);
            }
            offset = payload + (int) (size + (size & 1));
        }
        return DEFAULT_ORIENTATION;
    }

    private static int exifOrientation(byte[] content, int start, int end) {
        return tiffOrientation(content, hasExifPrefix(content, start, end) ? start + EXIF_PREFIX.length : start, end);
    }

    private static int tiffOrientation(byte[] content, int start, int end) {
        if (start + 8 > end) {
            return DEFAULT_ORIENTATION;
        }
        boolean littleEndian;
        if (content[start] == 'I' && content[start + 1] == 'I') {
            littleEndian = true;
        } else if (content[start] == 'M' && content[start + 1] == 'M') {
            littleEndian = false;
        } else {
            return DEFAULT_ORIENTATION;
        }
        long ifd = start + unsignedInt(content, start + 4, littleEndian);
        if (ifd + 2 > end) {
            return DEFAULT_ORIENTATION;
        }
        int entries = unsignedShort(content, (int) ifd, littleEndian);
        for (int i = 0; i < entries; i++) {
            long entry = ifd + 2 + (long) i * IFD_ENTRY_BYTES;
            if (entry + IFD_ENTRY_BYTES > end) {
                break;
            }
            int position = (int) entry;
            if (unsignedShort(content, position, littleEndian) == ORIENTATION_TAG) {
                int value = unsignedShort(content, position + 8, littleEndian);
                boolean valid = unsignedShort(content, position + 2, littleEndian) == SHORT_TYPE && value >= 1 && value <= 8;
                return valid ? value : DEFAULT_ORIENTATION;
            }
        }
        return DEFAULT_ORIENTATION;
    }

    private static boolean hasExifPrefix(byte[] content, int start, int end) {
        if (start + EXIF_PREFIX.length > end) {
            return false;
        }
        for (int i = 0; i < EXIF_PREFIX.length; i++) {
            if (content[start + i] != EXIF_PREFIX[i]) {
                return false;
            }
        }
        return true;
    }

    private static String chunkType(byte[] content, int offset) {
        return new String(content, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int unsignedShort(byte[] content, int offset, boolean littleEndian) {
        int first = content[offset] & 0xFF;
        int second = content[offset + 1] & 0xFF;
        return littleEndian ? first | second << 8 : first << 8 | second;
    }

    private static long unsignedInt(byte[] content, int offset, boolean littleEndian) {
        long first = unsignedShort(content, offset, littleEndian);
        long second = unsignedShort(content, offset + 2, littleEndian);
        return littleEndian ? first | second << 16 : first << 16 | second;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(ProductPhotoService.INVALID_FILE);
    }
}
