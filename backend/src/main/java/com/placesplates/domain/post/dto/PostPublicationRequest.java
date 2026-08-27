package com.placesplates.domain.post.dto;

import com.placesplates.domain.post.entity.PostVisibility;

import jakarta.validation.constraints.NotNull;

public record PostPublicationRequest(
	@NotNull PostVisibility visibility
) {
}
