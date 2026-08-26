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
		return download(temporaryBucket, objectKey, "Temporary storage object");
	}

	@Override
	public byte[] downloadSanitizedMaster(String objectKey) {
		return download(sanitizedBucket, objectKey, "Sanitized master");
	}

	@Override
	public byte[] downloadResponsiveVariant(String objectKey) {
		return download(sanitizedBucket, objectKey, "Responsive image variant");
	}

	@Override
	public void storeSanitizedMaster(String objectKey, byte[] bytes, String mimeType) {
		upload(objectKey, bytes, mimeType, "Sanitized master");
	}

	@Override
	public void storeResponsiveVariant(String objectKey, byte[] bytes, String mimeType) {
		upload(objectKey, bytes, mimeType, "Responsive image variant");
	}

	@Override
	public void deleteTemporary(String objectKey) {
		if (objectKey == null || !objectKey.startsWith("temporary/") || objectKey.contains("..")) {
			throw new StorageAccessException("Temporary storage key is invalid");
		}
		assertConfigured();
		HttpRequest request = authorizedRequest(objectUri(temporaryBucket, objectKey)).DELETE().build();
		try {
			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			if ((response.statusCode() < 200 || response.statusCode() >= 300) && response.statusCode() != 404) {
				throw new StorageAccessException("Temporary storage object deletion failed");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new StorageAccessException("Temporary storage object deletion was interrupted", exception);
		} catch (IOException exception) {
			throw new StorageAccessException("Temporary storage object deletion failed", exception);
		}
	}

	private byte[] download(String bucket, String objectKey, String assetName) {
		assertConfigured();
		HttpRequest request = authorizedRequest(objectUri(bucket, objectKey)).GET().build();
		try {
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new StorageAccessException(assetName + " download failed");
			}
			return response.body();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new StorageAccessException(assetName + " download was interrupted", exception);
		} catch (IOException exception) {
			throw new StorageAccessException(assetName + " download failed", exception);
		}
	}

	private void upload(String objectKey, byte[] bytes, String mimeType, String assetName) {
		assertConfigured();
		HttpRequest request = authorizedRequest(objectUri(sanitizedBucket, objectKey))
			.header("Content-Type", mimeType)
			.header("x-upsert", "true")
			.POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
			.build();
		try {
			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new StorageAccessException(assetName + " upload failed");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new StorageAccessException(assetName + " upload was interrupted", exception);
		} catch (IOException exception) {
			throw new StorageAccessException(assetName + " upload failed", exception);
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
