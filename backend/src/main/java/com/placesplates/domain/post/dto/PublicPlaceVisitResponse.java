package com.placesplates.domain.post.dto;

import java.util.UUID;

import com.placesplates.domain.post.entity.DraftPost;

public record PublicPlaceVisitResponse(
	UUID id,
	String category,
	String title,
	String summary,
	Integer publicVisitYear,
	Integer publicVisitMonth,
	PublicPostCoverResponse cover
) {

	public static PublicPlaceVisitResponse from(DraftPost post, PublicPostCoverResponse cover) {
		return new PublicPlaceVisitResponse(
			post.getId(),
			post.getCategory().name(),
			post.getTitle(),
			post.getSummary(),
			post.getPublicVisitYear() == null ? null : post.getPublicVisitYear().intValue(),
			post.getPublicVisitMonth() == null ? null : post.getPublicVisitMonth().intValue(),
			cover
		);
	}
}
