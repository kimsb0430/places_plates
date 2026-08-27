package com.placesplates.domain.post.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.post.entity.DestinationDetail;

public interface DestinationDetailRepository extends JpaRepository<DestinationDetail, UUID> {
}
