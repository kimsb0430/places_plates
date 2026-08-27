package com.placesplates.infra.googlemaps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.placesplates.domain.place.exception.PlaceException;
import com.sun.net.httpserver.HttpServer;

class GooglePlacesSearchClientTests {

	@Test
	void sendsRestrictedRequestAndMapsGoogleResponse() throws IOException {
		AtomicReference<String> apiKey = new AtomicReference<>();
		AtomicReference<String> fieldMask = new AtomicReference<>();
		AtomicReference<String> requestBody = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/places:searchText", exchange -> {
			apiKey.set(exchange.getRequestHeaders().getFirst("X-Goog-Api-Key"));
			fieldMask.set(exchange.getRequestHeaders().getFirst("X-Goog-FieldMask"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] response = """
				{
				  "places": [{
				    "id": "ChIJ-test-place",
				    "displayName": {"text": "니시키 시장"},
				    "formattedAddress": "일본 교토부 교토시",
				    "primaryType": "market",
				    "location": {"latitude": 35.005, "longitude": 135.764},
				    "googleMapsUri": "https://maps.google.com/example"
				  }]
				}
				""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		try {
			GooglePlacesSearchClient client = new GooglePlacesSearchClient(
				"http://127.0.0.1:" + server.getAddress().getPort() + "/v1/places:searchText",
				"test-places-key"
			);

			var results = client.search("니시키 시장");

			assertThat(results).hasSize(1);
			assertThat(results.getFirst().googlePlaceId()).isEqualTo("ChIJ-test-place");
			assertThat(results.getFirst().name()).isEqualTo("니시키 시장");
			assertThat(apiKey.get()).isEqualTo("test-places-key");
			assertThat(fieldMask.get()).contains("places.id", "places.location");
			assertThat(requestBody.get()).contains("\"pageSize\":5", "\"languageCode\":\"ko\"");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void explainsManualFallbackWhenApiKeyIsMissing() {
		GooglePlacesSearchClient client = new GooglePlacesSearchClient(
			"https://places.googleapis.com/v1/places:searchText",
			""
		);

		assertThatThrownBy(() -> client.search("니시키 시장"))
			.isInstanceOf(PlaceException.class)
			.extracting(exception -> ((PlaceException) exception).getCode())
			.isEqualTo("PLACE_SEARCH_NOT_CONFIGURED");
	}
}
