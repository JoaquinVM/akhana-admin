package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.service.ProductPhotoService;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

import static com.akhana.akhana_admin.service.impl.ProductPhotoFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// CAT-12: what is accepted and what is saved
class ProductPhotoProcessorTest {

    private record Named(char name, Color color) {}

    private final ProductPhotoProcessor processor = new ProductPhotoProcessor();

    private static BufferedImage read(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = ProductPhotoProcessorTest.class.getResourceAsStream("/product-photos/" + name)) {
            return input.readAllBytes();
        }
    }

    private static char colorAt(BufferedImage image, double x, double y) {
        Color pixel = new Color(image.getRGB((int) (image.getWidth() * x), (int) (image.getHeight() * y)));
        return List.of(new Named('R', RED), new Named('G', GREEN), new Named('B', BLUE), new Named('Y', YELLOW)).stream()
            .min(Comparator.comparingInt(named -> distance(pixel, named.color())))
            .orElseThrow().name();
    }

    private static int distance(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue());
    }

    private void assertInvalid(byte[] content) {
        assertThatThrownBy(() -> processor.process(content))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ProductPhotoService.INVALID_FILE);
    }

    @Test
    @DisplayName("Reduce el lado mayor a 1600 px y la miniatura a 300 px, sin deformar")
    void reducesImageAndThumbnail() throws IOException {
        var result = processor.process(jpeg(quadrants(4000, 3000)));

        BufferedImage image = read(result.image());
        BufferedImage thumbnail = read(result.thumbnail());
        assertThat(image.getWidth()).isEqualTo(1600);
        assertThat(image.getHeight()).isEqualTo(1200);
        assertThat(thumbnail.getWidth()).isEqualTo(300);
        assertThat(thumbnail.getHeight()).isEqualTo(225);
    }

    @Test
    @DisplayName("No agranda una foto chica")
    void doesNotEnlarge() throws IOException {
        var result = processor.process(jpeg(quadrants(200, 100)));

        assertThat(read(result.image()).getWidth()).isEqualTo(200);
        assertThat(read(result.thumbnail()).getWidth()).isEqualTo(200);
    }

    @Test
    @DisplayName("Una foto de 12 megapíxeles, aun la más difícil de comprimir, pesa 500 KB o menos")
    void twelveMegapixelsWithinLimit() throws IOException {
        var result = processor.process(jpeg(noise(4000, 3000)));

        assertThat(result.image().length).isLessThanOrEqualTo(ProductPhotoProcessor.MAX_IMAGE_BYTES);
        BufferedImage image = read(result.image());
        assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(1600);
    }

    @ParameterizedTest(name = "orientación {0}: {1}{2}/{3}{4}")
    @CsvSource({
        "1,R,G,B,Y",
        "2,G,R,Y,B",
        "3,Y,B,G,R",
        "4,B,Y,R,G",
        "5,R,B,G,Y",
        "6,B,R,Y,G",
        "7,Y,G,B,R",
        "8,G,Y,R,B"
    })
    @DisplayName("Aplica la orientación del celular antes de quitar los metadatos")
    void appliesOrientation(int orientation, char topLeft, char topRight, char bottomLeft, char bottomRight) throws IOException {
        byte[] content = withExif(jpeg(quadrants(400, 200)), orientation);

        BufferedImage image = read(processor.process(content).image());

        assertThat(image.getWidth()).isEqualTo(orientation >= 5 ? 200 : 400);
        assertThat(image.getHeight()).isEqualTo(orientation >= 5 ? 400 : 200);
        assertThat(colorAt(image, 0.25, 0.25)).isEqualTo(topLeft);
        assertThat(colorAt(image, 0.75, 0.25)).isEqualTo(topRight);
        assertThat(colorAt(image, 0.25, 0.75)).isEqualTo(bottomLeft);
        assertThat(colorAt(image, 0.75, 0.75)).isEqualTo(bottomRight);
    }

    @Test
    @DisplayName("Ni la foto ni la miniatura conservan GPS ni otros metadatos")
    void removesMetadata() throws Exception {
        byte[] content = withExif(jpeg(quadrants(400, 200)), 6);
        Metadata before = ImageMetadataReader.readMetadata(new ByteArrayInputStream(content));
        assertThat(before.getFirstDirectoryOfType(GpsDirectory.class)).as("la foto de prueba trae GPS").isNotNull();
        assertThat(before.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNotNull();

        var result = processor.process(content);

        for (byte[] saved : List.of(result.image(), result.thumbnail())) {
            Metadata after = ImageMetadataReader.readMetadata(new ByteArrayInputStream(saved));
            assertThat(after.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
            assertThat(after.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
            assertThat(StreamSupport.stream(after.getDirectories().spliterator(), false).map(Directory::getName))
                .noneMatch(name -> name.contains("Exif") || name.contains("GPS") || name.contains("XMP"));
            assertThat(new String(saved, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        }
    }

    @Test
    @DisplayName("Lo transparente de un PNG queda blanco")
    void transparentPngBecomesWhite() throws IOException {
        BufferedImage source = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(RED);
        graphics.fillRect(0, 0, 50, 100);
        graphics.dispose();

        BufferedImage image = read(processor.process(png(source)).image());

        assertThat(new Color(image.getRGB(75, 50)).getRed()).isGreaterThan(245);
        assertThat(new Color(image.getRGB(75, 50)).getGreen()).isGreaterThan(245);
        assertThat(colorAt(image, 0.25, 0.5)).isEqualTo('R');
    }

    @ParameterizedTest
    @ValueSource(strings = {"half-transparent-lossy.webp", "half-transparent-lossless.webp"})
    @DisplayName("Lee WebP y deja blanco lo transparente")
    void readsWebp(String file) throws IOException {
        BufferedImage image = read(processor.process(resource(file)).image());

        assertThat(image.getWidth()).isEqualTo(64);
        assertThat(colorAt(image, 0.25, 0.5)).isEqualTo('R');
        assertThat(new Color(image.getRGB(48, 24)).getGreen()).isGreaterThan(245);
    }

    @Test
    @DisplayName("Lee un JPEG en CMYK, que el lector del JDK no abre")
    void readsCmykJpeg() throws IOException {
        BufferedImage image = read(processor.process(cmykJpeg(64, 64)).image());

        assertThat(image.getWidth()).isEqualTo(64);
    }

    @Test
    @DisplayName("Rechaza por el contenido real, aunque parezca una foto")
    void rejectsFakeImages() {
        assertInvalid("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8));
        assertInvalid("GIF89a nada mas".getBytes(StandardCharsets.UTF_8));
        assertInvalid(new byte[0]);
    }

    @Test
    @DisplayName("Rechaza un archivo dañado o cortado")
    void rejectsDamagedFiles() throws IOException {
        byte[] jpeg = jpeg(noise(300, 300));
        byte[] png = png(noise(300, 300));

        assertInvalid(Arrays.copyOf(jpeg, jpeg.length / 2));
        assertInvalid(Arrays.copyOf(png, png.length / 2));
        assertInvalid(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3});
        assertInvalid("RIFFxxxxWEBPbasura".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("Rechaza más de 10 MB")
    void rejectsLargeFiles() {
        byte[] content = new byte[(int) ProductPhotoProcessor.MAX_FILE_BYTES + 1];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;

        assertInvalid(content);
    }

    @Test
    @DisplayName("Rechaza más de 50 megapíxeles leyendo solo el encabezado")
    void rejectsTooManyPixelsWithoutDecoding() {
        assertThatThrownBy(() -> processor.process(pngHeaderOnly(8000, 8000)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ProductPhotoService.TOO_MANY_PIXELS);
    }
}
