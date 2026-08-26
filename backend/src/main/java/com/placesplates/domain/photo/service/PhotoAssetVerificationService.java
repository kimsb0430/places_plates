package com.placesplates.domain.photo.service;

import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.infra.image.ImageSanitizationException;
import com.placesplates.infra.image.ResponsiveImageGenerator;
import com.placesplates.infra.image.ResponsiveImageVariant;
import com.placesplates.infra.image.SanitizedImage;
import com.placesplates.infra.image.StoredImageVerifier;
import com.placesplates.infra.storage.PrivatePhotoStorage;

@Service
public class PhotoAssetVerificationService {

	private final PhotoAssetRepository photoAssetRepository;
	private final PrivatePhotoStorage photoStorage;
	private final ResponsiveImageGenerator responsiveImageGenerator;
	private final StoredImageVerifier storedImageVerifier;

	public PhotoAssetVerificationService(
		PhotoAssetRepository photoAssetRepository,
		PrivatePhotoStorage photoStorage,
		ResponsiveImageGenerator responsiveImageGenerator,
		StoredImageVerifier storedImageVerifier
	) {
		this.photoAssetRepository = photoAssetRepository;
		this.photoStorage = photoStorage;
		this.responsiveImageGenerator = responsiveImageGenerator;
		this.storedImageVerifier = storedImageVerifier;
	}

	public List<PhotoAsset> verifyAndPublish(UUID photoId) {
		List<PhotoAsset> assets = photoAssetRepository.findAllByPhotoId(photoId);
		Map<PhotoAssetVariantType, PhotoAsset> assetsByType = indexAssets(assets);
		PhotoAsset masterAsset = requireAsset(assetsByType, PhotoAssetVariantType.SANITIZED_MASTER);
		verifyMasterPolicy(masterAsset);

		byte[] masterBytes = photoStorage.downloadSanitizedMaster(masterAsset.getStorageKey());
		storedImageVerifier.verify(
			masterBytes,
			masterAsset.getMimeType(),
			masterAsset.getWidth(),
			masterAsset.getHeight(),
			masterAsset.getByteSize()
		);
		SanitizedImage master = new SanitizedImage(
			masterBytes,
			masterAsset.getMimeType(),
			masterAsset.getWidth(),
			masterAsset.getHeight()
		);
		List<ResponsiveImageVariant> expectedVariants = responsiveImageGenerator.generate(master);
		for (ResponsiveImageVariant expected : expectedVariants) {
			PhotoAsset asset = requireAsset(assetsByType, expected.type());
			byte[] storedBytes = photoStorage.downloadResponsiveVariant(asset.getStorageKey());
			storedImageVerifier.verify(
				storedBytes,
				asset.getMimeType(),
				asset.getWidth(),
				asset.getHeight(),
				asset.getByteSize()
			);
			if (!MessageDigest.isEqual(storedBytes, expected.bytes())) {
				throw failure(
					"PUBLIC_VARIANT_PIXEL_MISMATCH",
					"공개용 사진의 픽셀 또는 워터마크가 검증 결과와 일치하지 않습니다."
				);
			}
		}

		for (ResponsiveImageVariant expected : expectedVariants) {
			PhotoAsset asset = requireAsset(assetsByType, expected.type());
			asset.publishWatermarked(
				asset.getStorageKey(),
				expected.mimeType(),
				expected.width(),
				expected.height(),
				expected.bytes().length,
				expected.watermarkVersion(),
				expected.watermarkPosition()
			);
		}
		return assets;
	}

	private static Map<PhotoAssetVariantType, PhotoAsset> indexAssets(List<PhotoAsset> assets) {
		Map<PhotoAssetVariantType, PhotoAsset> indexed = new EnumMap<>(PhotoAssetVariantType.class);
		for (PhotoAsset asset : assets) {
			if (indexed.put(asset.getVariantType(), asset) != null) {
				throw failure("PHOTO_ASSET_DUPLICATE_VARIANT", "같은 유형의 사진 자산이 중복 저장되어 있습니다.");
			}
		}
		return indexed;
	}

	private static PhotoAsset requireAsset(
		Map<PhotoAssetVariantType, PhotoAsset> assets,
		PhotoAssetVariantType type
	) {
		PhotoAsset asset = assets.get(type);
		if (asset == null) {
			throw failure("PHOTO_ASSET_MISSING", "필수 사진 자산이 누락되었습니다.");
		}
		return asset;
	}

	private static void verifyMasterPolicy(PhotoAsset master) {
		if (!"PRIVATE".equals(master.getAccessLevel())
			|| !master.isMetadataScanPassed()
			|| master.isWatermarkApplied()) {
			throw failure("SANITIZED_MASTER_POLICY_INVALID", "정제 마스터의 보호 정책이 올바르지 않습니다.");
		}
	}

	private static ImageSanitizationException failure(String code, String message) {
		return new ImageSanitizationException(code, message);
	}
}
