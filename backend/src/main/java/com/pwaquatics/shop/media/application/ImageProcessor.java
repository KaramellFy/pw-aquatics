package com.pwaquatics.shop.media.application;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded photo into WebP renditions.
 *
 * <ul>
 *   <li>The type is detected from the file's first bytes, never from its name or the
 *       browser's content type. JPEG, PNG and WebP are accepted.
 *   <li>The size is read from the header before decoding, so a tiny file that would expand
 *       into gigabytes of pixels (a "decompression bomb") is refused.
 *   <li>The photo is turned upright according to its EXIF orientation, then re-encoded from
 *       pixels. Nothing of the original file survives: no EXIF data (camera, GPS position),
 *       no comments, no hidden payloads.
 * </ul>
 */
@Component
class ImageProcessor {

    /** Narrowest accepted photo; also the smallest rendition. */
    static final int MIN_WIDTH = MediaUrls.WIDTHS.getFirst();
    private static final int MAX_SIDE = 12_000;
    private static final long MAX_PIXELS = 40_000_000;
    private static final float QUALITY = 0.82f;
    /** Decoding a large photo takes a lot of memory; only two are processed at a time. */
    private static final Semaphore CONCURRENT = new Semaphore(2);

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** A processed photo: its upright size and the encoded renditions by file name. */
    record Result(int width, int height, Map<String, byte[]> renditions) {}

    /**
     * @throws BusinessRuleException {@code IMAGE_TYPE_UNSUPPORTED}, {@code IMAGE_INVALID},
     *     {@code IMAGE_TOO_LARGE} or {@code IMAGE_TOO_SMALL}
     */
    Result process(byte[] bytes) {
        if (!isSupportedType(bytes)) {
            throw new BusinessRuleException("IMAGE_TYPE_UNSUPPORTED", "Please upload a JPEG, PNG or WebP photo.");
        }
        checkDimensions(bytes);
        CONCURRENT.acquireUninterruptibly();
        try {
            BufferedImage upright = decodeUpright(bytes);
            if (upright.getWidth() < MIN_WIDTH) {
                throw new BusinessRuleException(
                        "IMAGE_TOO_SMALL", "The photo must be at least " + MIN_WIDTH + " pixels wide.");
            }
            Map<String, byte[]> renditions = new LinkedHashMap<>();
            for (int width : MediaUrls.WIDTHS) {
                if (width <= upright.getWidth()) {
                    renditions.put(width + ".webp", encodeWebP(resize(upright, width)));
                }
            }
            return new Result(upright.getWidth(), upright.getHeight(), renditions);
        } finally {
            CONCURRENT.release();
        }
    }

    private static boolean isSupportedType(byte[] bytes) {
        return startsWith(bytes, JPEG)
                || startsWith(bytes, PNG)
                || (bytes.length > 12
                        && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP"));
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** Reads width and height from the header only. */
    private static void checkDimensions(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width > MAX_SIDE || height > MAX_SIDE || width * height > MAX_PIXELS) {
                    throw new BusinessRuleException(
                            "IMAGE_TOO_LARGE", "The photo has too many pixels (at most 40 megapixels).");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid();
        }
    }

    /** Decodes the photo and turns it upright (EXIF orientation of camera JPEGs). */
    private static BufferedImage decodeUpright(byte[] bytes) {
        try {
            BufferedImage decoded = Thumbnails.of(new ByteArrayInputStream(bytes))
                    .scale(1.0)
                    .useExifOrientation(true)
                    .asBufferedImage();
            return normalised(decoded);
        } catch (IOException | RuntimeException e) {
            throw invalid();
        }
    }

    /** Plain RGB, or ARGB when the photo has transparency (what the WebP encoder expects). */
    private static BufferedImage normalised(BufferedImage image) {
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (image.getType() == type) {
            return image;
        }
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), type);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static BufferedImage resize(BufferedImage image, int width) {
        if (image.getWidth() == width) {
            return image;
        }
        try {
            return normalised(Thumbnails.of(image).width(width).asBufferedImage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] encodeWebP(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No WebP encoder available");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType(Arrays.stream(param.getCompressionTypes())
                .filter(type -> type.equalsIgnoreCase("lossy"))
                .findFirst()
                .orElse(param.getCompressionTypes()[0]));
        param.setCompressionQuality(QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException("WebP encoding failed", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("IMAGE_INVALID", "This file could not be read as a photo.");
    }
}
