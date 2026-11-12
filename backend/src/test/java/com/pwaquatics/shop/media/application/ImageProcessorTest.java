package com.pwaquatics.shop.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void createsWebPRenditionsUpToThePhotosWidth() throws IOException {
        ImageProcessor.Result result = processor.process(jpeg(1300, 900, null, null));

        assertThat(result.width()).isEqualTo(1300);
        assertThat(result.height()).isEqualTo(900);
        assertThat(result.renditions()).containsOnlyKeys("400.webp", "800.webp", "1200.webp");
        BufferedImage small = ImageIO.read(new ByteArrayInputStream(result.renditions().get("400.webp")));
        assertThat(small.getWidth()).isEqualTo(400);
        assertThat(small.getHeight()).isEqualTo(277); // 900 × 400 / 1300, rounded
        assertThat(new String(result.renditions().get("400.webp"), 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
    }

    @Test
    void turnsCameraPhotosUprightAndDropsEverythingButThePixels() throws IOException {
        // A landscape JPEG whose EXIF says "rotate 90° clockwise", plus a comment with private data.
        byte[] photo = jpeg(800, 600, (short) 6, "GPS 52.52N 13.40E – SECRET-PAYLOAD");
        assertThat(new String(photo, StandardCharsets.ISO_8859_1)).contains("SECRET-PAYLOAD", "Exif");

        ImageProcessor.Result result = processor.process(photo);

        assertThat(result.width()).isEqualTo(600);
        assertThat(result.height()).isEqualTo(800);
        for (byte[] rendition : result.renditions().values()) {
            String text = new String(rendition, StandardCharsets.ISO_8859_1);
            assertThat(text).doesNotContain("SECRET-PAYLOAD").doesNotContain("Exif");
        }
    }

    @Test
    void acceptsPngWithTransparency() throws IOException {
        BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        assertThat(processor.process(out.toByteArray()).renditions()).containsOnlyKeys("400.webp");
    }

    @Test
    void refusesFilesThatAreNotUsablePhotos() throws IOException {
        assertCode("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8), "IMAGE_TYPE_UNSUPPORTED");
        assertCode("GIF89a".getBytes(StandardCharsets.US_ASCII), "IMAGE_TYPE_UNSUPPORTED");
        // Looks like a JPEG by its first bytes, but is not one.
        assertCode(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5}, "IMAGE_INVALID");
        assertCode(jpeg(300, 300, null, null), "IMAGE_TOO_SMALL");
        // A tiny PNG header that claims 20 000 × 20 000 pixels is refused before decoding.
        assertCode(pngHeader(20_000, 20_000), "IMAGE_TOO_LARGE");
    }

    private void assertCode(byte[] file, String code) {
        assertThatThrownBy(() -> processor.process(file)).extracting("code").isEqualTo(code);
    }

    /** A JPEG with an optional EXIF orientation and an optional comment segment. */
    private static byte[] jpeg(int width, int height, Short orientation, String comment) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x88C0DB));
        graphics.fillRect(0, 0, width / 2, height);
        graphics.dispose();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", encoded);
        byte[] plain = encoded.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2); // SOI
        if (orientation != null) {
            out.write(exifSegment(orientation));
        }
        if (comment != null) {
            byte[] text = comment.getBytes(StandardCharsets.ISO_8859_1);
            out.write(new byte[] {(byte) 0xFF, (byte) 0xFE});
            out.write(ByteBuffer.allocate(2).putShort((short) (text.length + 2)).array());
            out.write(text);
        }
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    /** APP1 segment with a big-endian TIFF header and one IFD entry: Orientation. */
    private static byte[] exifSegment(short orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4);
        tiff.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort(orientation).putShort((short) 0);
        tiff.putInt(0);
        byte[] payload = tiff.array();
        ByteBuffer segment = ByteBuffer.allocate(4 + 6 + payload.length);
        segment.put((byte) 0xFF).put((byte) 0xE1).putShort((short) (2 + 6 + payload.length));
        segment.put("Exif".getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) 0);
        segment.put(payload);
        return segment.array();
    }

    /** A PNG signature and IHDR chunk only. */
    private static byte[] pngHeader(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        return png.array();
    }
}
