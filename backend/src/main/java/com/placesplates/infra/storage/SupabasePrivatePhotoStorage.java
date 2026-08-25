package com.placesplates.infra.storage;

import java.io.IOException;
import java.net.URI;
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

@Component
public class SupabasePrivatePhotoStorage implements PrivatePhotoStorage {

	private final HttpClient httpClient;
	private final String storageApiUrl;
	private final String serviceRoleKey;
	private final String temporaryBucket;
	private final String sanitizedBucket;

	public SupabasePrivatePhotoStorage(
		@Value("${places-plates.storage.api-url:}") String storageApiUrl,
		@Value("${places-plates.storage.service-role-key:}") String serviceRoleKey,
		@Value("${places-plates.storage.temporary-bucket:temporary-uploads}") String temporaryBucket,
		@Value("${places-plates.storage.sanitized-bucket:temporary-uploads}") String sanitizedBucket
	) {
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		this.storageApiUrl = stripTrailingSlash(storageApiUrl);
		this.serviceRoleKey = serviceRoleKey;
		this.temporaryBucket = temporaryBucket;
		this.sanitizedBucket = sanitizedBucket;
	}

	@Override
	public byte[] downloadTemporary(String objectKey) {
		assertConfigured();
		HttpRequest request = authorizedRequest(objectUri(temporaryBucket, objectKey)).GET().build();
		try {
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new StorageAccessException("Temporary storage object download failed");
			}
			return response.body();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new StorageAccessException("Temporary storage object download was interrupted", exception);
		} catch (IOException exception) {
			throw new StorageAccessException("Temporary storage object download failed", exception);
		}
	}

	@Override
	public void storeSanitizedMaster(String objectKey, byte[] bytes, String mimeType) {
		assertConfigured();
		HttpRequest request = authorizedRequest(objectUri(sanitizedBucket, objectKey))
			.header("Content-Type", mimeType)
			.header("x-upsert", "true")
			.POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
			.build();
		try {
			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new StorageAccessException("Sanitized master upload failed");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new StorageAccessException("Sanitized master upload was interrupted", exception);
		} catch (IOException exception) {
			throw new StorageAccessException("Sanitized master upload failed", exception);
		}
	}

	private URI objectUri(String bucket, String objectKey) {
		return URI.create(storageApiUrl + "/object/" + encodePath(bucket + "/" + objectKey));
	}

	private HttpRequest.Builder authorizedRequest(URI uri) {
		return HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(30))
			.header("Authorization", "Bearer " + serviceRoleKey)
			.header("apikey", serviceRoleKey);
	}

	private void assertConfigured() {
		if (!StringUtils.hasText(storageApiUrl) || !StringUtils.hasText(serviceRoleKey)) {
			throw new StorageAccessException("Private photo storage is not configured");
		}
	}

	private static String encodePath(String value) {
		return Arrays.stream(value.split("/"))
			.map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
			.reduce((left, right) -> left + "/" + right)
			.orElse("");
	}

	private static String stripTrailingSlash(String value) {
		return value == null ? "" : value.replaceAll("/+$", "");
	}
}
