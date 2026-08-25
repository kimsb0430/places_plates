package com.placesplates.infra.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;

@Component
public class JavaResponsiveImageGenerator implements ResponsiveImageGenerator {

	private static final String OUTPUT_MIME_TYPE = "image/jpeg";
	private static final List<VariantSpec> VARIANT_SPECS = List.of(
		new VariantSpec(PhotoAssetVariantType.THUMBNAIL, 320),
		new VariantSpec(PhotoAssetVariantType.MAP_CARD, 960),
		new VariantSpec(PhotoAssetVariantType.PUBLIC_DETAIL, 2000)
	);

	private final float jpegQuality;
	private final ServerWatermarkRenderer watermarkRenderer;

	public JavaResponsiveImageGenerator(
		@Value("${places-plates.image.variant-jpeg-quality:0.88}") float jpegQuality,
		ServerWatermarkRenderer watermarkRenderer
	) {
		this.jpegQuality = jpegQuality;
		this.watermarkRenderer = watermarkRenderer;
	}

	@Override
	public List<ResponsiveImageVariant> generate(SanitizedImage sanitizedMaster) {
		BufferedImage decoded = decode(sanitizedMaster.bytes());
		return VARIANT_SPECS.stream()
			.map(spec -> generateVariant(decoded, spec))
			.toList();
	}

	private ResponsiveImageVariant generateVariant(BufferedImage source, VariantSpec spec) {
		BufferedImage resized = resizeToFit(source, spec.maxLongEdge());
		WatermarkedImage watermarked = watermarkRenderer.apply(resized);
		byte[] encoded = encodeJpeg(watermarked.image());
		assertSensitiveMetadataRemoved(encoded);
		return new ResponsiveImageVariant(
			spec.type(),
			encoded,
			OUTPUT_MIME_TYPE,
			watermarked.image().getWidth(),
			watermarked.image().getHeight(),
			watermarked.version(),
			watermarked.position()
		);
	}

	@Override
	public String watermarkVersion() {
		return watermarkRenderer.version();
	}

	@Override
	public String watermarkPosition() {
		return watermarkRenderer.position();
	}

	private static BufferedImage decode(byte[] source) {
		try {
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(source));
			if (decoded == null) {
				throw new ImageSanitizationException(
					"SANITIZED_MASTER_DECODE_FAILED",
					"정제 마스터에서 반응형 이미지를 생성할 수 없습니다."
				);
			}
			return decoded;
		} catch (IOException exception) {
			throw new ImageSanitizationException(
				"SANITIZED_MASTER_DECODE_FAILED",
				"정제 마스터에서 반응형 이미지를 생성할 수 없습니다.",
				exception
			);
		}
	}

	private static BufferedImage resizeToFit(BufferedImage source, int maxLongEdge) {
		int sourceLongEdge = Math.max(source.getWidth(), source.getHeight());
		double scale = Math.min(1.0, (double) maxLongEdge / sourceLongEdge);
		int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, targetWidth, targetHeight);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private byte[] encodeJpeg(BufferedImage source) {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			throw new ImageSanitizationException("IMAGE_ENCODER_UNAVAILABLE", "JPEG 인코더를 찾을 수 없습니다.");
		}
		ImageWriter writer = writers.next();
		try (ByteArrayOutputStream output = new ByteArrayOutputStream();
			 ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
			ImageWriteParam writeParam = writer.getDefaultWriteParam();
			writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			writeParam.setCompressionQuality(jpegQuality);
			writer.setOutput(imageOutput);
			writer.write(null, new IIOImage(source, null, null), writeParam);
			imageOutput.flush();
			return output.toByteArray();
		} catch (IOException exception) {
			throw new ImageSanitizationException(
				"RESPONSIVE_IMAGE_ENCODE_FAILED",
				"화면별 반응형 이미지를 생성하지 못했습니다.",
				exception
			);
		} finally {
			writer.dispose();
		}
	}

	private static void assertSensitiveMetadataRemoved(byte[] encoded) {
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(encoded), encoded.length);
			for (Directory directory : metadata.getDirectories()) {
				if (directory instanceof ExifDirectoryBase
					|| directory instanceof XmpDirectory
					|| directory instanceof IptcDirectory) {
					throw new ImageSanitizationException(
						"SENSITIVE_METADATA_REMAINS",
						"반응형 이미지에서 촬영 메타데이터가 발견되었습니다."
					);
				}
			}
		} catch (ImageSanitizationException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ImageSanitizationException(
				"METADATA_SCAN_FAILED",
				"반응형 이미지의 메타데이터를 검사하지 못했습니다.",
				exception
			);
		}
	}

	private record VariantSpec(PhotoAssetVariantType type, int maxLongEdge) {
	}
}
