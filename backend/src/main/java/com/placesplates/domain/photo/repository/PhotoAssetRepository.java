package com.placesplates.domain.photo.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;

public interface PhotoAssetRepository extends JpaRepository<PhotoAsset, UUID> {

	List<PhotoAsset> findAllByPhotoId(UUID photoId);

	Optional<PhotoAsset> findByPhotoIdAndVariantType(UUID photoId, PhotoAssetVariantType variantType);
}
