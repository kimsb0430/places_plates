package com.placesplates.domain.post.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.post.dto.DraftPostResponse;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.exception.DraftPostException;
import com.placesplates.domain.post.repository.DraftPostRepository;

@Service
@Transactional(readOnly = true)
public class DraftPostService {

	private final DraftPostRepository draftPostRepository;

	public DraftPostService(DraftPostRepository draftPostRepository) {
		this.draftPostRepository = draftPostRepository;
	}

	public List<DraftPostResponse> findDrafts(UUID ownerUserId) {
		return draftPostRepository
			.findAllByOwnerUserIdAndStatusOrderByUpdatedAtDesc(ownerUserId, PostStatus.DRAFT)
			.stream()
			.map(DraftPostResponse::from)
			.toList();
	}

	public DraftPostResponse findDraft(UUID ownerUserId, UUID draftId) {
		return draftPostRepository
			.findByIdAndOwnerUserIdAndStatus(draftId, ownerUserId, PostStatus.DRAFT)
			.map(DraftPostResponse::from)
			.orElseThrow(() -> new DraftPostException(
				HttpStatus.NOT_FOUND,
				"DRAFT_POST_NOT_FOUND",
				"작성 중인 초안을 찾을 수 없습니다."
			));
	}
}
