package com.placesplates.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class SupabasePrivatePhotoStorageTests {

	private static final String SERVICE_ROLE_KEY = "test-service-role-key";

	private HttpServer server;
	private String storageApiUrl;
	private final AtomicReference<String> uploadedPath = new AtomicReference<>();
	private final AtomicReference<String> uploadedContentType = new AtomicReference<>();
	private final AtomicReference<String> upsertHeader = new AtomicReference<>();
	private final AtomicReference<byte[]> uploadedBody = new AtomicReference<>();

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/storage/v1/object/temporary-uploads/temporary/owner/photo.jpg", exchange -> {
			byte[] body = "temporary-photo".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.createContext("/storage/v1/object/private-assets/sanitized/", exchange -> {
			uploadedPath.set(exchange.getRequestURI().getRawPath());
			uploadedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			upsertHeader.set(exchange.getRequestHeaders().getFirst("x-upsert"));
			uploadedBody.set(exchange.getRequestBody().readAllBytes());
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.createContext("/storage/v1/object/private-assets/variants/", exchange -> {
			uploadedPath.set(exchange.getRequestURI().getRawPath());
			uploadedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			upsertHeader.set(exchange.getRequestHeaders().getFirst("x-upsert"));
			uploadedBody.set(exchange.getRequestBody().readAllBytes());
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		storageApiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/storage/v1";
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void downloadsTemporaryObjectAndStoresSanitizedMasterPrivately() {
		SupabasePrivatePhotoStorage storage = new SupabasePrivatePhotoStorage(
			storageApiUrl,
			SERVICE_ROLE_KEY,
			"temporary-uploads",
			"private-assets"
		);

		byte[] downloaded = storage.downloadTemporary("temporary/owner/photo.jpg");
		byte[] sanitized = "sanitized-photo".getBytes(StandardCharsets.UTF_8);
		storage.storeSanitizedMaster("sanitized/owner/job.jpg", sanitized, "image/jpeg");

		assertThat(downloaded).isEqualTo("temporary-photo".getBytes(StandardCharsets.UTF_8));
		assertThat(uploadedPath.get()).isEqualTo(
			"/storage/v1/object/private-assets/sanitized/owner/job.jpg"
		);
		assertThat(uploadedContentType.get()).isEqualTo("image/jpeg");
		assertThat(upsertHeader.get()).isEqualTo("true");
		assertThat(uploadedBody.get()).isEqualTo(sanitized);
	}

	@Test
	void storesResponsiveVariantInPrivateAssetBucket() {
		SupabasePrivatePhotoStorage storage = new SupabasePrivatePhotoStorage(
			storageApiUrl,
			SERVICE_ROLE_KEY,
			"temporary-uploads",
			"private-assets"
		);

		byte[] variant = "responsive-photo".getBytes(StandardCharsets.UTF_8);
		storage.storeResponsiveVariant("variants/owner/job/thumbnail.jpg", variant, "image/jpeg");

		assertThat(uploadedPath.get()).isEqualTo(
			"/storage/v1/object/private-assets/variants/owner/job/thumbnail.jpg"
		);
		assertThat(uploadedContentType.get()).isEqualTo("image/jpeg");
		assertThat(upsertHeader.get()).isEqualTo("true");
		assertThat(uploadedBody.get()).isEqualTo(variant);
	}
}
