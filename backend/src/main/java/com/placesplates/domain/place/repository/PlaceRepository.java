package com.placesplates.domain.place.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.place.entity.Place;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

	Optional<Place> findByGooglePlaceId(String googlePlaceId);
}
