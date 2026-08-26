package com.placesplates.infra.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;

@Component
public class JavaStoredImageVerifier implements StoredImageVerifier {

	@Override
	public void verify(
		byte[] bytes,
		String mimeType,
		int expectedWidth,
		int expectedHeight,
		long expectedByteSize
	) {
		if (!"image/jpeg".equals(mimeType)) {
			throw failure("STORED_IMAGE_MIME_TYPE_INVALID", "저장된 사진 형식이 JPEG가 아닙니다.");
		}
		if (bytes.length != expectedByteSize) {
			throw failure("STORED_IMAGE_SIZE_MISMATCH", "저장된 사진의 크기가 기록과 일치하지 않습니다.");
		}
		try {
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
			if (decoded == null
				|| decoded.getWidth() != expectedWidth
				|| decoded.getHeight() != expectedHeight) {
				throw failure("STORED_IMAGE_DIMENSION_MISMATCH", "저장된 사진의 해상도를 확인하지 못했습니다.");
			}
			Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes), bytes.length);
			for (Directory directory : metadata.getDirectories()) {
				if (directory instanceof ExifDirectoryBase
					|| directory instanceof XmpDirectory
					|| directory instanceof IptcDirectory) {
					throw failure("SENSITIVE_METADATA_REMAINS", "저장된 사진에서 촬영 메타데이터가 발견되었습니다.");
				}
			}
		} catch (ImageSanitizationException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ImageSanitizationException(
				"STORED_IMAGE_VERIFICATION_FAILED",
				"저장된 사진을 검사하지 못했습니다.",
				exception
			);
		}
	}

	private static ImageSanitizationException failure(String code, String message) {
		return new ImageSanitizationException(code, message);
	}
}
