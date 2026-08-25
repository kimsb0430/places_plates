package com.placesplates.infra.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;

class JavaResponsiveImageGeneratorTests {

	private final JavaResponsiveImageGenerator generator = new JavaResponsiveImageGenerator(
		0.88f,
		new JavaServerWatermarkRenderer("places-plates-corner-v1", 0.28f, 0.16, 0.03)
	);

	@Test
	void createsThreeMetadataFreeVariantsWithoutChangingAspectRatio() throws Exception {
		SanitizedImage master = sanitizedImage(2400, 1200);

		List<ResponsiveImageVariant> variants = generator.generate(master);

		assertThat(variants).extracting(ResponsiveImageVariant::type).containsExactly(
			PhotoAssetVariantType.THUMBNAIL,
			PhotoAssetVariantType.MAP_CARD,
			PhotoAssetVariantType.PUBLIC_DETAIL
		);
		assertThat(variants).extracting(ResponsiveImageVariant::width).containsExactly(320, 960, 2000);
		assertThat(variants).extracting(ResponsiveImageVariant::height).containsExactly(160, 480, 1000);
		for (ResponsiveImageVariant variant : variants) {
			assertThat(variant.mimeType()).isEqualTo("image/jpeg");
			assertThat(variant.watermarkVersion()).isEqualTo("places-plates-corner-v1");
			assertThat(variant.watermarkPosition()).isEqualTo("BOTTOM_RIGHT");
			assertThat(hasSensitiveMetadata(variant.bytes())).isFalse();
			assertThat(hasVisibleBottomRightVariation(variant)).isTrue();
		}
	}

	@Test
	void neverUpscalesSmallSanitizedMaster() throws Exception {
		SanitizedImage master = sanitizedImage(120, 80);

		List<ResponsiveImageVariant> variants = generator.generate(master);

		assertThat(variants).allSatisfy(variant -> {
			assertThat(variant.width()).isEqualTo(120);
			assertThat(variant.height()).isEqualTo(80);
		});
	}

	private static SanitizedImage sanitizedImage(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(42, 91, 75));
			graphics.fillRect(0, 0, width, height);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", output);
		return new SanitizedImage(output.toByteArray(), "image/jpeg", width, height);
	}

	private static boolean hasSensitiveMetadata(byte[] image) throws Exception {
		for (Directory directory : ImageMetadataReader.readMetadata(
			new ByteArrayInputStream(image), image.length
		).getDirectories()) {
			if (directory instanceof ExifDirectoryBase
				|| directory instanceof XmpDirectory
				|| directory instanceof IptcDirectory) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasVisibleBottomRightVariation(ResponsiveImageVariant variant) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(variant.bytes()));
		int startX = (int) Math.floor(image.getWidth() * 0.75);
		int startY = (int) Math.floor(image.getHeight() * 0.75);
		int reference = image.getRGB(startX, startY);
		for (int y = startY; y < image.getHeight(); y++) {
			for (int x = startX; x < image.getWidth(); x++) {
				if (image.getRGB(x, y) != reference) {
					return true;
				}
			}
		}
		return false;
	}
}
