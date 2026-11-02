package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.service.ProductPhotoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// CAT-12: what is read from a file before decoding it
class ProductPhotoInspectorTest {

    private static final byte[] SCAN = {(byte) 0xFF, (byte) 0xDA, 0, 8, 1, 1, 0, 0, 0x3F, 0};
    private static final byte[] SOI = {(byte) 0xFF, (byte) 0xD8};
    private static final byte[] EOI = {(byte) 0xFF, (byte) 0xD9};

    private static byte[] join(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part);
        }
        return out.toByteArray();
    }

    private static byte[] repeat(byte[] part, int times) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < times; i++) {
            out.write(part);
        }
        return out.toByteArray();
    }

    private static byte[] tiff(ByteOrder order, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(order);
        tiff.put(order == ByteOrder.LITTLE_ENDIAN ? new byte[]{'I', 'I'} : new byte[]{'M', 'M'});
        tiff.putShort((short) 42).putInt(8).putShort((short) 1);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);
        return tiff.array();
    }

    private static byte[] exifSegment(byte[] tiff) throws IOException {
        byte[] body = join("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff);
        return join(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) ((body.length + 2) >> 8), (byte) (body.length + 2)}, body);
    }

    private static byte[] webpChunk(String type, byte[] data) {
        ByteBuffer chunk = ByteBuffer.allocate(8 + data.length + (data.length & 1)).order(ByteOrder.LITTLE_ENDIAN);
        chunk.put(type.getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
        return chunk.array();
    }

    private static byte[] webp(byte[]... chunks) throws IOException {
        byte[] body = join(chunks);
        ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        head.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(body.length + 4).put("WEBP".getBytes(StandardCharsets.US_ASCII));
        return join(head.array(), body);
    }

    private static void assertRejected(byte[] jpeg) {
        assertThatThrownBy(() -> ProductPhotoInspector.checkJpeg(jpeg))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ProductPhotoService.INVALID_FILE);
    }

    @Test
    @DisplayName("Un JPEG admite 100 scans y rechaza el siguiente")
    void jpegScanLimit() throws IOException {
        assertThatCode(() -> ProductPhotoInspector.checkJpeg(join(SOI, repeat(SCAN, 100), EOI))).doesNotThrowAnyException();
        assertRejected(join(SOI, repeat(SCAN, 101), EOI));
    }

    @Test
    @DisplayName("Un marcador sin longitud no esconde los scans que siguen")
    void hiddenScansCount() throws IOException {
        byte[] restartThenScan = join(new byte[]{(byte) 0xFF, (byte) 0xD0, 0, 12}, SCAN);
        assertRejected(join(SOI, repeat(restartThenScan, 101), EOI));
        byte[] temporaryThenScan = join(new byte[]{(byte) 0xFF, 0x01}, SCAN);
        assertRejected(join(SOI, repeat(temporaryThenScan, 101), EOI));
    }

    @Test
    @DisplayName("Rechaza un marcador reservado, que podría esconder scans")
    void reservedMarkersAreRejected() throws IOException {
        assertRejected(join(SOI, new byte[]{(byte) 0xFF, 0x02, 0, 12}, SCAN, EOI));
        assertRejected(join(SOI, SCAN, new byte[]{(byte) 0xFF, (byte) 0xBF, 0, 2}, EOI));
    }

    @Test
    @DisplayName("Lo que viene después de la imagen, como el video de una foto en movimiento, no cuenta")
    void dataAfterTheEndIsIgnored() throws IOException {
        byte[] jpeg = join(SOI, SCAN, EOI, repeat(SCAN, 500));

        assertThatCode(() -> ProductPhotoInspector.checkJpeg(jpeg)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Los marcadores dentro de los metadatos no cuentan como scans")
    void metadataIsSkipped() throws IOException {
        byte[] data = repeat(SCAN, 200);
        byte[] segment = join(new byte[]{(byte) 0xFF, (byte) 0xE2, (byte) ((data.length + 2) >> 8), (byte) (data.length + 2)}, data);

        assertThatCode(() -> ProductPhotoInspector.checkJpeg(join(SOI, segment, SCAN, EOI))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LITTLE_ENDIAN", "BIG_ENDIAN"})
    @DisplayName("Lee la orientación de un JPEG en los dos órdenes de bytes")
    void jpegOrientationInBothOrders(String name) throws IOException {
        ByteOrder order = name.equals("LITTLE_ENDIAN") ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

        assertThat(ProductPhotoInspector.readOrientation(join(SOI, exifSegment(tiff(order, 6)), SCAN, EOI), "jpeg")).isEqualTo(6);
    }

    @Test
    @DisplayName("Lee la orientación de un JPEG con relleno antes del EXIF")
    void jpegOrientationAfterFillBytes() throws IOException {
        byte[] jpeg = join(SOI, new byte[]{(byte) 0xFF, (byte) 0xFF}, exifSegment(tiff(ByteOrder.BIG_ENDIAN, 8)), SCAN, EOI);

        assertThat(ProductPhotoInspector.readOrientation(jpeg, "jpeg")).isEqualTo(8);
    }

    @Test
    @DisplayName("Lee la orientación de un PNG con y sin el prefijo Exif")
    void pngOrientationWithPrefix() throws IOException {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        for (byte[] body : new byte[][]{tiff(ByteOrder.BIG_ENDIAN, 3), join("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff(ByteOrder.BIG_ENDIAN, 3))}) {
            ByteBuffer chunk = ByteBuffer.allocate(8 + body.length + 4);
            chunk.putInt(body.length).put("eXIf".getBytes(StandardCharsets.US_ASCII)).put(body);

            assertThat(ProductPhotoInspector.readOrientation(join(signature, chunk.array()), "png")).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("Lee la orientación de un WebP completo, y acepta su frame con alfa, perfil y XMP")
    void webpWithAllChunks() throws IOException {
        byte[] content = webp(webpChunk("VP8X", new byte[10]), webpChunk("ICCP", new byte[7]), webpChunk("ALPH", new byte[4]),
            webpChunk("VP8 ", new byte[16]), webpChunk("EXIF", tiff(ByteOrder.LITTLE_ENDIAN, 6)), webpChunk("XMP ", new byte[5]));

        assertThat(ProductPhotoInspector.readOrientation(content, "webp")).isEqualTo(6);
        assertThatCode(() -> ProductPhotoInspector.checkWebp(content, 50_000_000L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una orientación fuera de rango o un EXIF cortado no giran la foto")
    void badExifIsIgnored() throws IOException {
        assertThat(ProductPhotoInspector.readOrientation(join(SOI, exifSegment(tiff(ByteOrder.BIG_ENDIAN, 99)), EOI), "jpeg")).isEqualTo(1);
        assertThat(ProductPhotoInspector.readOrientation(join(SOI, new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) 0xFF, (byte) 0xFF, 'E', 'x'}), "jpeg")).isEqualTo(1);
    }
}
