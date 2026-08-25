package com.placesplates.infra.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;

class JavaImageSanitizerTests {

	private final JavaImageSanitizer sanitizer = new JavaImageSanitizer(25_000_000, 0.92f);

	@Test
	void appliesExifOrientationAndRemovesSensitiveMetadata() throws Exception {
		byte[] source = appendMetadata(createQuadrantJpeg());
		Metadata sourceMetadata = ImageMetadataReader.readMetadata(
			new ByteArrayInputStream(source),
			source.length
		);
		assertThat(hasSensitiveMetadata(sourceMetadata)).isTrue();

		SanitizedImage result = sanitizer.sanitize(source, "image/jpeg");

		assertThat(result.mimeType()).isEqualTo("image/jpeg");
		assertThat(result.width()).isEqualTo(60);
		assertThat(result.height()).isEqualTo(40);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
		assertColorNear(decoded.getRGB(10, 10), Color.BLUE);
		assertColorNear(decoded.getRGB(50, 10), Color.RED);
		assertThat(hasSensitiveMetadata(ImageMetadataReader.readMetadata(
			new ByteArrayInputStream(result.bytes()),
			result.bytes().length
		))).isFalse();
	}

	@Test
	void rejectsMimeTypeThatDoesNotMatchImageBytes() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);

		assertThatThrownBy(() -> sanitizer.sanitize(output.toByteArray(), "image/jpeg"))
			.isInstanceOf(ImageSanitizationException.class)
			.hasFieldOrPropertyWithValue("failureCode", "IMAGE_CONTENT_TYPE_MISMATCH");
	}

	@Test
	void failsClosedWhenHeicDecoderIsUnavailable() {
		assertThatThrownBy(() -> sanitizer.sanitize(new byte[] {1, 2, 3}, "image/heic"))
			.isInstanceOf(ImageSanitizationException.class)
			.hasFieldOrPropertyWithValue("failureCode", "HEIC_DECODER_UNAVAILABLE")
			.hasMessageContaining("JPEG");
	}

	private static byte[] createQuadrantJpeg() throws Exception {
		BufferedImage image = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(Color.RED);
			graphics.fillRect(0, 0, 20, 30);
			graphics.setColor(Color.GREEN);
			graphics.fillRect(20, 0, 20, 30);
			graphics.setColor(Color.BLUE);
			graphics.fillRect(0, 30, 20, 30);
			graphics.setColor(Color.YELLOW);
			graphics.fillRect(20, 30, 20, 30);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", output);
		return output.toByteArray();
	}

	private static byte[] appendMetadata(byte[] jpeg) {
		byte[] exifPayload = new byte[] {
			'E', 'x', 'i', 'f', 0, 0,
			'I', 'I', 42, 0, 8, 0, 0, 0,
			1, 0,
			18, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0,
			0, 0, 0, 0
		};
		String xmpXml = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF "
			+ "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description "
			+ "xmlns:exif=\"http://ns.adobe.com/exif/1.0/\" exif:GPSLatitude=\"35,0N\"/>"
			+ "</rdf:RDF></x:xmpmeta>";
		byte[] xmpPrefix = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);
		byte[] xmpXmlBytes = xmpXml.getBytes(StandardCharsets.UTF_8);
		byte[] xmpPayload = ByteBuffer.allocate(xmpPrefix.length + xmpXmlBytes.length)
			.put(xmpPrefix)
			.put(xmpXmlBytes)
			.array();

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.writeBytes(new byte[] {(byte) 0xff, (byte) 0xd8});
		output.writeBytes(app1(exifPayload));
		output.writeBytes(app1(xmpPayload));
		output.write(jpeg, 2, jpeg.length - 2);
		return output.toByteArray();
	}

	private static byte[] app1(byte[] payload) {
		return ByteBuffer.allocate(payload.length + 4)
			.put((byte) 0xff)
			.put((byte) 0xe1)
			.putShort((short) (payload.length + 2))
			.put(payload)
			.array();
	}

	private static boolean hasSensitiveMetadata(Metadata metadata) {
		for (Directory directory : metadata.getDirectories()) {
			if (directory instanceof ExifDirectoryBase
				|| directory instanceof XmpDirectory
				|| directory instanceof IptcDirectory) {
				return true;
			}
		}
		return false;
	}

	private static void assertColorNear(int actualRgb, Color expected) {
		Color actual = new Color(actualRgb);
		assertThat(actual.getRed()).isCloseTo(expected.getRed(), within(30));
		assertThat(actual.getGreen()).isCloseTo(expected.getGreen(), within(30));
		assertThat(actual.getBlue()).isCloseTo(expected.getBlue(), within(30));
	}

	private static org.assertj.core.data.Offset<Integer> within(int value) {
		return org.assertj.core.data.Offset.offset(value);
	}
}
