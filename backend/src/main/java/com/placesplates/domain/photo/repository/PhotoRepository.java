package com.placesplates.domain.photo.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.photo.entity.Photo;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
}
