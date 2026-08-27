package com.placesplates.domain.post.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.post.dto.DraftPostResponse;
import com.placesplates.domain.post.dto.DraftPostUpdateRequest;
import com.placesplates.domain.post.dto.DestinationDetailResponse;
import com.placesplates.domain.post.dto.DestinationDetailUpdateRequest;
import com.placesplates.domain.post.dto.RestaurantDetailResponse;
import com.placesplates.domain.post.dto.RestaurantDetailUpdateRequest;
import com.placesplates.domain.post.entity.DestinationDetail;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.entity.RestaurantDetail;
import com.placesplates.domain.post.exception.DraftPostException;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.post.repository.DestinationDetailRepository;
import com.placesplates.domain.post.repository.RestaurantDetailRepository;

@Service
@Transactional(readOnly = true)
public class DraftPostService {

	private final DraftPostRepository draftPostRepository;
	private final RestaurantDetailRepository restaurantDetailRepository;
	private final DestinationDetailRepository destinationDetailRepository;

	public DraftPostService(
		DraftPostRepository draftPostRepository,
		RestaurantDetailRepository restaurantDetailRepository,
		DestinationDetailRepository destinationDetailRepository
	) {
		this.draftPostRepository = draftPostRepository;
		this.restaurantDetailRepository = restaurantDetailRepository;
		this.destinationDetailRepository = destinationDetailRepository;
	}

	public List<DraftPostResponse> findDrafts(UUID ownerUserId) {
		List<DraftPost> drafts = draftPostRepository
			.findAllByOwnerUserIdAndStatusOrderByUpdatedAtDesc(ownerUserId, PostStatus.DRAFT);
		Map<UUID, RestaurantDetail> restaurantDetails = restaurantDetailRepository.findAllById(
			drafts.stream()
				.filter(draft -> draft.getCategory() == PostCategory.RESTAURANT)
				.map(DraftPost::getId)
				.toList()
		).stream().collect(Collectors.toMap(RestaurantDetail::getPostId, Function.identity()));
		Map<UUID, DestinationDetail> destinationDetails = destinationDetailRepository.findAllById(
			drafts.stream()
				.filter(draft -> draft.getCategory() == PostCategory.DESTINATION)
				.map(DraftPost::getId)
				.toList()
		).stream().collect(Collectors.toMap(DestinationDetail::getPostId, Function.identity()));
		return drafts
			.stream()
			.map(draft -> toResponse(
				draft,
				restaurantDetails.get(draft.getId()),
				destinationDetails.get(draft.getId())
			))
			.toList();
	}

	public DraftPostResponse findDraft(UUID ownerUserId, UUID draftId) {
		DraftPost draft = findOwnedDraft(ownerUserId, draftId);
		return toResponse(draft, findRestaurantDetail(draft), findDestinationDetail(draft));
	}

	@Transactional
	public DraftPostResponse updateDraft(
		UUID ownerUserId,
		UUID draftId,
		DraftPostUpdateRequest request
	) {
		validateVisitMonthPair(request.publicVisitYear(), request.publicVisitMonth());
		DraftPost draft = findOwnedDraft(ownerUserId, draftId);
		updateRestaurantDetail(draft, request.restaurantDetails());
		updateDestinationDetail(draft, request.destinationDetails());
		draft.updateEditorFields(
			request.title(),
			request.summary(),
			request.content(),
			request.publicVisitYear(),
			request.publicVisitMonth()
		);
		return toResponse(draft, findRestaurantDetail(draft), findDestinationDetail(draft));
	}

	private RestaurantDetail findRestaurantDetail(DraftPost draft) {
		if (draft.getCategory() != PostCategory.RESTAURANT) {
			return null;
		}
		return restaurantDetailRepository.findById(draft.getId()).orElse(null);
	}

	private DestinationDetail findDestinationDetail(DraftPost draft) {
		if (draft.getCategory() != PostCategory.DESTINATION) {
			return null;
		}
		return destinationDetailRepository.findById(draft.getId()).orElse(null);
	}

	/**
	 * レストラン下書きに限って固有項目を更新し、全項目が空なら詳細行を削除する。
	 */
	private void updateRestaurantDetail(
		DraftPost draft,
		RestaurantDetailUpdateRequest request
	) {
		if (request == null) {
			return;
		}
		if (draft.getCategory() != PostCategory.RESTAURANT) {
			throw new DraftPostException(
				HttpStatus.BAD_REQUEST,
				"DRAFT_POST_RESTAURANT_FIELDS_INVALID",
				"맛집 전용 항목은 맛집 기록에만 입력할 수 있습니다."
			);
		}
		if (request.isEmpty()) {
			restaurantDetailRepository.findById(draft.getId())
				.ifPresent(restaurantDetailRepository::delete);
			return;
		}
		RestaurantDetail detail = restaurantDetailRepository.findById(draft.getId())
			.orElseGet(() -> RestaurantDetail.create(draft.getId()));
		detail.update(
			request.rating(),
			request.recommendedMenu(),
			request.priceRange(),
			request.waitingMinutes(),
			request.revisitIntention()
		);
		restaurantDetailRepository.save(detail);
	}

	/**
	 * 旅行先の下書きに限って固有項目を更新し、全項目が空なら詳細行を削除する。
	 */
	private void updateDestinationDetail(
		DraftPost draft,
		DestinationDetailUpdateRequest request
	) {
		if (request == null) {
			return;
		}
		if (draft.getCategory() != PostCategory.DESTINATION) {
			throw new DraftPostException(
				HttpStatus.BAD_REQUEST,
				"DRAFT_POST_DESTINATION_FIELDS_INVALID",
				"여행지 전용 항목은 여행지 기록에만 입력할 수 있습니다."
			);
		}
		if (request.isEmpty()) {
			destinationDetailRepository.findById(draft.getId())
				.ifPresent(destinationDetailRepository::delete);
			return;
		}
		DestinationDetail detail = destinationDetailRepository.findById(draft.getId())
			.orElseGet(() -> DestinationDetail.create(draft.getId()));
		detail.update(
			request.recommendedTime(),
			request.durationMinutes(),
			request.highlights(),
			request.travelTips()
		);
		destinationDetailRepository.save(detail);
	}

	private DraftPostResponse toResponse(
		DraftPost draft,
		RestaurantDetail restaurantDetail,
		DestinationDetail destinationDetail
	) {
		return DraftPostResponse.from(
			draft,
			restaurantDetail == null ? null : RestaurantDetailResponse.from(restaurantDetail),
			destinationDetail == null ? null : DestinationDetailResponse.from(destinationDetail)
		);
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
