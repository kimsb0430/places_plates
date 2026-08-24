package com.placesplates.infra.storage;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SupabaseTemporaryUploadSigner implements TemporaryUploadSigner {

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String storageApiUrl;
	private final String serviceRoleKey;
	private final String bucket;

	public SupabaseTemporaryUploadSigner(
		ObjectMapper objectMapper,
		@Value("${places-plates.storage.api-url:}") String storageApiUrl,
		@Value("${places-plates.storage.service-role-key:}") String serviceRoleKey,
		@Value("${places-plates.storage.temporary-bucket:temporary-uploads}") String bucket
	) {
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		this.objectMapper = objectMapper;
		this.storageApiUrl = stripTrailingSlash(storageApiUrl);
		this.serviceRoleKey = serviceRoleKey;
		this.bucket = bucket;
	}

	@Override
	public SignedUploadTicket issue(String objectKey) {
		assertConfigured();
		URI uri = URI.create(storageApiUrl + "/object/upload/sign/" + encodePath(bucket + "/" + objectKey));
		HttpRequest request = authorizedRequest(uri)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.build();
		JsonNode response = sendJson(request, 200);
		String signedPath = response.path("url").asText();
		String token = response.path("token").asText();
		if (!StringUtils.hasText(token)) {
			token = queryParameter(signedPath, "token");
		}
		if (!StringUtils.hasText(token)) {
			throw new StorageAccessException("Storage did not return an upload token");
		}
		return new SignedUploadTicket(storageApiUrl + "/upload/resumable", token, bucket, objectKey);
	}

	@Override
	public boolean objectMatches(String objectKey, long expectedByteSize) {
		assertConfigured();
		URI uri = URI.create(storageApiUrl + "/object/info/" + encodePath(bucket + "/" + objectKey));
		HttpRequest request = authorizedRequest(uri).GET().build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 404) {
				return false;
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new StorageAccessException("Storage object verification failed");
			}
			JsonNode body = objectMapper.readTree(response.body());
			long actualByteSize = body.path("metadata").path("size").asLong(body.path("size").asLong(-1));
			return actualByteSize == expectedByteSize;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new StorageAccessException("Storage object verification was interrupted", exception);
		} catch (IOException exception) {
			throw new StorageAccessException("Storage object verification failed", exception);
		}
	}

	private HttpRequest.Builder authorizedRequest(URI uri) {
		return HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(20))
			.header("Authorization", "Bearer " + serviceRoleKey)
			.header("apikey", serviceRoleKey);
	}

	private JsonNode sendJson(HttpRequest request, int expectedStatus) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != expectedStatus) {
				throw new StorageAccessException("Storage upload authorization failed");
			}
			return objectMapper.readTree(response.body());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new StorageAccessException("Storage upload authorization was interrupted", exception);
		} catch (IOException exception) {
			throw new StorageAccessException("Storage upload authorization failed", exception);
		}
	}

	private void assertConfigured() {
		if (!StringUtils.hasText(storageApiUrl) || !StringUtils.hasText(serviceRoleKey)) {
			throw new StorageAccessException("Temporary photo storage is not configured");
		}
	}

	private static String encodePath(String value) {
		return Arrays.stream(value.split("/"))
			.map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
			.reduce((left, right) -> left + "/" + right)
			.orElse("");
	}

	private static String queryParameter(String value, String name) {
		int queryStart = value.indexOf('?');
		if (queryStart < 0) {
			return "";
		}
		return Arrays.stream(value.substring(queryStart + 1).split("&"))
			.map(parameter -> parameter.split("=", 2))
			.filter(parts -> parts.length == 2 && parts[0].equals(name))
			.map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
			.findFirst()
			.orElse("");
	}

	private static String stripTrailingSlash(String value) {
		return value == null ? "" : value.replaceAll("/+$", "");
	}
}
