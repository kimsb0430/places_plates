package com.placesplates.domain.post.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.placesplates.domain.post.entity.DraftPost;

public record PublicPostSummaryResponse(
	UUID id,
	String category,
	String title,
	String summary,
	Integer publicVisitYear,
	Integer publicVisitMonth,
	OffsetDateTime publishedAt
) {

	public static PublicPostSummaryResponse from(DraftPost post) {
		return new PublicPostSummaryResponse(
			post.getId(),
			post.getCategory().name(),
			post.getTitle(),
			post.getSummary(),
			post.getPublicVisitYear() == null ? null : post.getPublicVisitYear().intValue(),
			post.getPublicVisitMonth() == null ? null : post.getPublicVisitMonth().intValue(),
			post.getPublishedAt()
		);
	}
}
