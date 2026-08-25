package com.placesplates.domain.photo.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.photo.entity.PhotoAsset;

public interface PhotoAssetRepository extends JpaRepository<PhotoAsset, UUID> {
}
