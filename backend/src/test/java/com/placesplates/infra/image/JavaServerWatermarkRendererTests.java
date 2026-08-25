package com.placesplates.infra.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class JavaServerWatermarkRendererTests {

	private static final String VERSION = "places-plates-corner-v1";

	@Test
	void burnsLightTextIntoDarkBottomRightPixels() {
		BufferedImage source = solidImage(1000, 600, new Color(20, 30, 40));
		JavaServerWatermarkRenderer renderer = renderer();

		WatermarkedImage result = renderer.apply(source);

		assertThat(result.version()).isEqualTo(VERSION);
		assertThat(result.position()).isEqualTo("BOTTOM_RIGHT");
		assertThat(changedPixelCount(source, result.image())).isGreaterThan(100);
		assertThat(changedPixelsAreInBottomRight(source, result.image())).isTrue();
		assertThat(averageChangedLuminance(source, result.image())).isGreaterThan(30.0);
	}

	@Test
	void burnsDarkTextIntoLightBottomRightPixels() {
		BufferedImage source = solidImage(1000, 600, new Color(240, 240, 240));

		WatermarkedImage result = renderer().apply(source);

		assertThat(changedPixelCount(source, result.image())).isGreaterThan(100);
		assertThat(averageChangedLuminance(source, result.image())).isLessThan(230.0);
	}

	@Test
	void rejectsInvalidPolicyValues() {
		assertThatThrownBy(() -> new JavaServerWatermarkRenderer("", 0.28f, 0.16, 0.03))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JavaServerWatermarkRenderer(VERSION, 0.0f, 0.16, 0.03))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JavaServerWatermarkRenderer(VERSION, 0.28f, 0.20, 0.03))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static JavaServerWatermarkRenderer renderer() {
		return new JavaServerWatermarkRenderer(VERSION, 0.28f, 0.16, 0.03);
	}

	private static BufferedImage solidImage(int width, int height, Color color) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(color);
			graphics.fillRect(0, 0, width, height);
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static int changedPixelCount(BufferedImage before, BufferedImage after) {
		int changed = 0;
		for (int y = 0; y < before.getHeight(); y++) {
			for (int x = 0; x < before.getWidth(); x++) {
				if (before.getRGB(x, y) != after.getRGB(x, y)) {
					changed++;
				}
			}
		}
		return changed;
	}

	private static boolean changedPixelsAreInBottomRight(BufferedImage before, BufferedImage after) {
		int minimumX = (int) Math.floor(before.getWidth() * 0.75);
		int minimumY = (int) Math.floor(before.getHeight() * 0.75);
		for (int y = 0; y < before.getHeight(); y++) {
			for (int x = 0; x < before.getWidth(); x++) {
				if (before.getRGB(x, y) != after.getRGB(x, y) && (x < minimumX || y < minimumY)) {
					return false;
				}
			}
		}
		return true;
	}

	private static double averageChangedLuminance(BufferedImage before, BufferedImage after) {
		long total = 0;
		long changed = 0;
		for (int y = 0; y < before.getHeight(); y++) {
			for (int x = 0; x < before.getWidth(); x++) {
				if (before.getRGB(x, y) != after.getRGB(x, y)) {
					Color color = new Color(after.getRGB(x, y));
					total += Math.round(
						0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()
					);
					changed++;
				}
			}
		}
		return changed == 0 ? 0 : (double) total / changed;
	}
}
