package com.placesplates.domain.post.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.placesplates.domain.post.entity.DraftPost;

public record PostPublicationResponse(
	UUID id,
	String visibility,
	String status,
	OffsetDateTime publishedAt
) {

	public static PostPublicationResponse from(DraftPost post) {
		return new PostPublicationResponse(
			post.getId(),
			post.getVisibility().name(),
			post.getStatus().name(),
			post.getPublishedAt()
		);
	}
}
