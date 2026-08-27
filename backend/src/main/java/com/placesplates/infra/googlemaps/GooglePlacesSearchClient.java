package com.placesplates.infra.googlemaps;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.placesplates.domain.place.dto.PlaceSearchResult;
import com.placesplates.domain.place.exception.PlaceException;
import com.placesplates.domain.place.service.PlaceSearchGateway;

@Component
public class GooglePlacesSearchClient implements PlaceSearchGateway {

	private static final String FIELD_MASK = String.join(",",
		"places.id", "places.displayName", "places.formattedAddress", "places.primaryType",
		"places.location", "places.googleMapsUri"
	);

	private final RestClient restClient;
	private final String apiKey;

	public GooglePlacesSearchClient(
		@Value("${places-plates.google-maps.places-api-url:https://places.googleapis.com/v1/places:searchText}")
		String apiUrl,
		@Value("${places-plates.google-maps.places-api-key:}") String apiKey
	) {
		this.restClient = RestClient.builder().baseUrl(apiUrl).build();
		this.apiKey = apiKey;
	}

	@Override
	public List<PlaceSearchResult> search(String query) {
		if (apiKey.isBlank()) {
			throw new PlaceException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"PLACE_SEARCH_NOT_CONFIGURED",
				"Google 장소 검색이 아직 설정되지 않았습니다. 직접 장소를 입력해주세요."
			);
		}
		try {
			SearchResponse response = restClient.post()
				.uri("")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Goog-Api-Key", apiKey)
				.header("X-Goog-FieldMask", FIELD_MASK)
				.body(new SearchRequest(query, "ko", 5))
				.retrieve()
				.body(SearchResponse.class);
			return response == null || response.places() == null
				? List.of()
				: response.places().stream().map(GooglePlacesSearchClient::toResult).toList();
		} catch (RestClientException exception) {
			throw new PlaceException(
				HttpStatus.BAD_GATEWAY,
				"PLACE_SEARCH_FAILED",
				"Google 장소 검색을 완료하지 못했습니다. 직접 입력하거나 잠시 후 다시 시도해주세요."
			);
		}
	}

	private static PlaceSearchResult toResult(GooglePlace place) {
		return new PlaceSearchResult(
			place.id(),
			place.displayName() == null ? "이름 없는 장소" : place.displayName().text(),
			place.primaryType(),
			place.formattedAddress(),
			place.location() == null ? null : place.location().latitude(),
			place.location() == null ? null : place.location().longitude(),
			place.googleMapsUri()
		);
	}

	private record SearchRequest(String textQuery, String languageCode, int pageSize) { }
	private record SearchResponse(List<GooglePlace> places) { }
	private record GooglePlace(
		String id,
		LocalizedText displayName,
		String formattedAddress,
		String primaryType,
		Location location,
		String googleMapsUri
	) { }
	private record LocalizedText(String text) { }
	private record Location(BigDecimal latitude, BigDecimal longitude) { }
}
