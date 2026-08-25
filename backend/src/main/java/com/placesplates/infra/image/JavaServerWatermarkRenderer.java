package com.placesplates.infra.image;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JavaServerWatermarkRenderer implements ServerWatermarkRenderer {

	static final String TEXT = "Places & Plates";
	static final String POSITION = "BOTTOM_RIGHT";
	private static final double DARK_BACKGROUND_THRESHOLD = 145.0;

	private final String version;
	private final float opacity;
	private final double targetWidthRatio;
	private final double marginRatio;

	public JavaServerWatermarkRenderer(
		@Value("${places-plates.image.watermark.version:places-plates-corner-v1}") String version,
		@Value("${places-plates.image.watermark.opacity:0.28}") float opacity,
		@Value("${places-plates.image.watermark.target-width-ratio:0.16}") double targetWidthRatio,
		@Value("${places-plates.image.watermark.margin-ratio:0.03}") double marginRatio
	) {
		if (version == null || version.isBlank() || version.length() > 40) {
			throw new IllegalArgumentException("Watermark version must contain 1 to 40 characters");
		}
		if (opacity <= 0 || opacity > 1) {
			throw new IllegalArgumentException("Watermark opacity must be greater than 0 and at most 1");
		}
		if (targetWidthRatio < 0.12 || targetWidthRatio > 0.18) {
			throw new IllegalArgumentException("Watermark width ratio must be between 0.12 and 0.18");
		}
		if (marginRatio <= 0 || marginRatio > 0.1) {
			throw new IllegalArgumentException("Watermark margin ratio must be greater than 0 and at most 0.1");
		}
		this.version = version;
		this.opacity = opacity;
		this.targetWidthRatio = targetWidthRatio;
		this.marginRatio = marginRatio;
	}

	@Override
	public WatermarkedImage apply(BufferedImage source) {
		BufferedImage target = copyRgb(source);
		Graphics2D graphics = target.createGraphics();
		try {
			applyRenderingHints(graphics);
			Font font = fitFont(graphics, target.getWidth());
			graphics.setFont(font);
			FontMetrics metrics = graphics.getFontMetrics();
			int textWidth = metrics.stringWidth(TEXT);
			int marginX = Math.max(1, (int) Math.round(target.getWidth() * marginRatio));
			int marginY = Math.max(1, (int) Math.round(target.getHeight() * marginRatio));
			int x = target.getWidth() - marginX - textWidth;
			int baseline = target.getHeight() - marginY - metrics.getDescent();
			int top = baseline - metrics.getAscent();
			if (x < 0 || top < 0 || baseline >= target.getHeight()) {
				throw new ImageSanitizationException(
					"WATERMARK_IMAGE_TOO_SMALL",
					"사진이 너무 작아 서버 워터마크를 적용할 수 없습니다."
				);
			}

			Color watermarkColor = averageLuminance(target, x, top, textWidth, metrics.getHeight())
				< DARK_BACKGROUND_THRESHOLD ? Color.WHITE : Color.BLACK;
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			graphics.setColor(watermarkColor);
			graphics.drawString(TEXT, x, baseline);
			if (!pixelsChanged(source, target, x, top, textWidth, metrics.getHeight())) {
				throw new ImageSanitizationException(
					"WATERMARK_RENDER_FAILED",
					"서버 워터마크가 사진 픽셀에 적용되지 않았습니다."
				);
			}
		} finally {
			graphics.dispose();
		}
		return new WatermarkedImage(target, version, POSITION);
	}

	@Override
	public String version() {
		return version;
	}

	@Override
	public String position() {
		return POSITION;
	}

	private Font fitFont(Graphics2D graphics, int imageWidth) {
		Font probe = new Font(Font.SANS_SERIF, Font.BOLD, 100);
		graphics.setFont(probe);
		int probeWidth = Math.max(1, graphics.getFontMetrics().stringWidth(TEXT));
		float targetWidth = (float) (imageWidth * targetWidthRatio);
		float fontSize = Math.max(1.0f, targetWidth * probe.getSize2D() / probeWidth);
		return probe.deriveFont(fontSize);
	}

	private static BufferedImage copyRgb(BufferedImage source) {
		BufferedImage target = new BufferedImage(
			source.getWidth(),
			source.getHeight(),
			BufferedImage.TYPE_INT_RGB
		);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.drawImage(source, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private static void applyRenderingHints(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	}

	private static double averageLuminance(BufferedImage image, int x, int y, int width, int height) {
		long luminanceTotal = 0;
		long samples = 0;
		int maxX = Math.min(image.getWidth(), x + width);
		int maxY = Math.min(image.getHeight(), y + height);
		for (int sampleY = Math.max(0, y); sampleY < maxY; sampleY++) {
			for (int sampleX = Math.max(0, x); sampleX < maxX; sampleX++) {
				Color color = new Color(image.getRGB(sampleX, sampleY));
				luminanceTotal += Math.round(
					0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()
				);
				samples++;
			}
		}
		return samples == 0 ? 255.0 : (double) luminanceTotal / samples;
	}

	private static boolean pixelsChanged(
		BufferedImage source,
		BufferedImage target,
		int x,
		int y,
		int width,
		int height
	) {
		int maxX = Math.min(target.getWidth(), x + width);
		int maxY = Math.min(target.getHeight(), y + height);
		for (int sampleY = Math.max(0, y); sampleY < maxY; sampleY++) {
			for (int sampleX = Math.max(0, x); sampleX < maxX; sampleX++) {
				if (source.getRGB(sampleX, sampleY) != target.getRGB(sampleX, sampleY)) {
					return true;
				}
			}
		}
		return false;
	}
}
