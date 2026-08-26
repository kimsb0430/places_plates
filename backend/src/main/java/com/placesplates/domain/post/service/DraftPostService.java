package com.placesplates.domain.post.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.post.dto.DraftPostResponse;
import com.placesplates.domain.post.dto.DraftPostUpdateRequest;
import com.placesplates.domain.post.entity.DraftPost;
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
		return DraftPostResponse.from(findOwnedDraft(ownerUserId, draftId));
	}

	@Transactional
	public DraftPostResponse updateDraft(
		UUID ownerUserId,
		UUID draftId,
		DraftPostUpdateRequest request
	) {
		validateVisitMonthPair(request.publicVisitYear(), request.publicVisitMonth());
		DraftPost draft = findOwnedDraft(ownerUserId, draftId);
		draft.updateEditorFields(
			request.title(),
			request.summary(),
			request.content(),
			request.publicVisitYear(),
			request.publicVisitMonth()
		);
		return DraftPostResponse.from(draft);
	}

	private DraftPost findOwnedDraft(UUID ownerUserId, UUID draftId) {
		return draftPostRepository
			.findByIdAndOwnerUserIdAndStatus(draftId, ownerUserId, PostStatus.DRAFT)
			.orElseThrow(() -> new DraftPostException(
				HttpStatus.NOT_FOUND,
				"DRAFT_POST_NOT_FOUND",
				"작성 중인 초안을 찾을 수 없습니다."
			));
	}

	/**
	 * 公開訪問月は年と月を常に一組として保存する。
	 */
	private void validateVisitMonthPair(Integer publicVisitYear, Integer publicVisitMonth) {
		if ((publicVisitYear == null) != (publicVisitMonth == null)) {
			throw new DraftPostException(
				HttpStatus.BAD_REQUEST,
				"DRAFT_POST_VISIT_MONTH_INVALID",
				"방문 월의 연도와 월을 함께 입력해주세요."
			);
		}
	}
}
