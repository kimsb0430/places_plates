package com.placesplates.domain.post.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.placesplates.domain.post.entity.DraftPost;

public record DraftPostResponse(
	UUID id,
	String category,
	String title,
	String summary,
	String content,
	Integer publicVisitYear,
	Integer publicVisitMonth,
	String visibility,
	String status,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt
) {
	public static DraftPostResponse from(DraftPost draft) {
		return new DraftPostResponse(
			draft.getId(),
			draft.getCategory().name(),
			draft.getTitle(),
			draft.getSummary(),
			draft.getContent(),
			draft.getPublicVisitYear() == null ? null : draft.getPublicVisitYear().intValue(),
			draft.getPublicVisitMonth() == null ? null : draft.getPublicVisitMonth().intValue(),
			draft.getVisibility().name(),
			draft.getStatus().name(),
			draft.getCreatedAt(),
			draft.getUpdatedAt()
		);
	}
}
