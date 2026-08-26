package com.placesplates.infra.image;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class JavaStoredImageVerifierTests {

	private final JavaStoredImageVerifier verifier = new JavaStoredImageVerifier();

	@Test
	void acceptsDecodableMetadataFreeJpegWithRecordedDimensions() throws Exception {
		byte[] bytes = createJpeg();

		verifier.verify(bytes, "image/jpeg", 24, 16, bytes.length);
	}

	@Test
	void rejectsStoredJpegContainingExif() throws Exception {
		byte[] bytes = appendExif(createJpeg());

		assertThatThrownBy(() -> verifier.verify(bytes, "image/jpeg", 24, 16, bytes.length))
			.isInstanceOf(ImageSanitizationException.class)
			.hasFieldOrPropertyWithValue("failureCode", "SENSITIVE_METADATA_REMAINS");
	}

	@Test
	void rejectsStoredBytesThatDoNotMatchRecordedSize() throws Exception {
		byte[] bytes = createJpeg();

		assertThatThrownBy(() -> verifier.verify(bytes, "image/jpeg", 24, 16, bytes.length + 1))
			.isInstanceOf(ImageSanitizationException.class)
			.hasFieldOrPropertyWithValue("failureCode", "STORED_IMAGE_SIZE_MISMATCH");
	}

	private static byte[] createJpeg() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB), "jpeg", output);
		return output.toByteArray();
	}

	private static byte[] appendExif(byte[] jpeg) {
		byte[] payload = new byte[] {
			'E', 'x', 'i', 'f', 0, 0,
			'I', 'I', 42, 0, 8, 0, 0, 0,
			0, 0, 0, 0
		};
		byte[] marker = ByteBuffer.allocate(payload.length + 4)
			.put((byte) 0xff)
			.put((byte) 0xe1)
			.putShort((short) (payload.length + 2))
			.put(payload)
			.array();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.writeBytes(new byte[] {(byte) 0xff, (byte) 0xd8});
		output.writeBytes(marker);
		output.write(jpeg, 2, jpeg.length - 2);
		return output.toByteArray();
	}
}
