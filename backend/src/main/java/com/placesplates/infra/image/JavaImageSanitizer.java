package com.placesplates.infra.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;

@Component
public class JavaImageSanitizer implements ImageSanitizer {

	private static final Set<String> HEIF_MIME_TYPES = Set.of("image/heic", "image/heif");
	private static final String OUTPUT_MIME_TYPE = "image/jpeg";

	private final long maxPixels;
	private final float jpegQuality;

	public JavaImageSanitizer(
		@Value("${places-plates.image.max-pixels:25000000}") long maxPixels,
		@Value("${places-plates.image.jpeg-quality:0.92}") float jpegQuality
	) {
		this.maxPixels = maxPixels;
		this.jpegQuality = jpegQuality;
	}

	@Override
	public SanitizedImage sanitize(byte[] source, String declaredMimeType) {
		if (HEIF_MIME_TYPES.contains(declaredMimeType)) {
			throw new ImageSanitizationException(
				"HEIC_DECODER_UNAVAILABLE",
				"현재 서버에서는 HEIC 사진을 변환할 수 없습니다. JPEG로 변환한 뒤 다시 업로드해주세요."
			);
		}

		int orientation = readOrientation(source);
		BufferedImage decoded = decode(source, declaredMimeType);
		BufferedImage oriented = applyOrientation(decoded, orientation);
		byte[] encoded = encodeJpeg(oriented);
		assertSensitiveMetadataRemoved(encoded);
		return new SanitizedImage(encoded, OUTPUT_MIME_TYPE, oriented.getWidth(), oriented.getHeight());
	}

	private BufferedImage decode(byte[] source, String declaredMimeType) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new ImageSanitizationException("IMAGE_DECODER_UNAVAILABLE", "사진 형식을 해석할 수 없습니다.");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
				assertMatchingFormat(declaredMimeType, formatName);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				long pixelCount = Math.multiplyExact((long) width, height);
				if (pixelCount > maxPixels) {
					throw new ImageSanitizationException(
						"IMAGE_PIXEL_LIMIT_EXCEEDED",
						"사진 해상도가 처리 한도를 초과합니다. 크기를 줄인 뒤 다시 업로드해주세요."
					);
				}
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw new ImageSanitizationException("IMAGE_DECODE_FAILED", "사진 픽셀을 읽지 못했습니다.");
				}
				return image;
			} finally {
				reader.dispose();
			}
		} catch (ImageSanitizationException exception) {
			throw exception;
		} catch (IOException | ArithmeticException exception) {
			throw new ImageSanitizationException("IMAGE_DECODE_FAILED", "사진을 안전하게 변환하지 못했습니다.", exception);
		}
	}

	private byte[] encodeJpeg(BufferedImage source) {
		BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = rgb.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
			graphics.drawImage(source, 0, 0, null);
		} finally {
			graphics.dispose();
		}

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
			writer.write(null, new IIOImage(rgb, null, null), writeParam);
			imageOutput.flush();
			return output.toByteArray();
		} catch (IOException exception) {
			throw new ImageSanitizationException("IMAGE_ENCODE_FAILED", "정제 사진을 생성하지 못했습니다.", exception);
		} finally {
			writer.dispose();
		}
	}

	private static BufferedImage applyOrientation(BufferedImage source, int orientation) {
		if (orientation <= 1 || orientation > 8) {
			return source;
		}
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();
		boolean swapsAxes = orientation >= 5;
		BufferedImage target = new BufferedImage(
			swapsAxes ? sourceHeight : sourceWidth,
			swapsAxes ? sourceWidth : sourceHeight,
			BufferedImage.TYPE_INT_ARGB
		);
		for (int y = 0; y < sourceHeight; y++) {
			for (int x = 0; x < sourceWidth; x++) {
				int[] point = orientedPoint(orientation, x, y, sourceWidth, sourceHeight);
				target.setRGB(point[0], point[1], source.getRGB(x, y));
			}
		}
		return target;
	}

	private static int[] orientedPoint(int orientation, int x, int y, int width, int height) {
		return switch (orientation) {
			case 2 -> new int[] {width - 1 - x, y};
			case 3 -> new int[] {width - 1 - x, height - 1 - y};
			case 4 -> new int[] {x, height - 1 - y};
			case 5 -> new int[] {y, x};
			case 6 -> new int[] {height - 1 - y, x};
			case 7 -> new int[] {height - 1 - y, width - 1 - x};
			case 8 -> new int[] {y, width - 1 - x};
			default -> new int[] {x, y};
		};
	}

	private static int readOrientation(byte[] source) {
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(source), source.length);
			ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
			return directory == null ? 1 : directory.getInt(ExifDirectoryBase.TAG_ORIENTATION);
		} catch (Exception exception) {
			return 1;
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
						"정제 결과에서 촬영 메타데이터가 발견되었습니다."
					);
				}
			}
		} catch (ImageSanitizationException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ImageSanitizationException(
				"METADATA_SCAN_FAILED",
				"정제 결과의 메타데이터를 검사하지 못했습니다.",
				exception
			);
		}
	}

	private static void assertMatchingFormat(String declaredMimeType, String formatName) {
		boolean matches = (declaredMimeType.equals("image/jpeg") && formatName.contains("jpeg"))
			|| (declaredMimeType.equals("image/png") && formatName.contains("png"));
		if (!matches) {
			throw new ImageSanitizationException(
				"IMAGE_CONTENT_TYPE_MISMATCH",
				"파일 내용과 사진 형식이 일치하지 않습니다."
			);
		}
	}
}
