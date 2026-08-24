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

import tools.jackson.databind.ObjectMapper;

class SupabaseTemporaryUploadSignerTests {

	private static final String SERVICE_ROLE_KEY = "test-service-role-key";
	private static final String SIGNED_TOKEN = "header.payload.signature";

	private HttpServer server;
	private String storageApiUrl;
	private final AtomicReference<String> requestedPath = new AtomicReference<>();
	private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/storage/v1/object/upload/sign/", exchange -> {
			requestedPath.set(exchange.getRequestURI().getRawPath());
			authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] body = ("{\"url\":\"/object/upload/sign/temporary-uploads/temporary/owner/photo.jpg"
				+ "?token=" + SIGNED_TOKEN + "\"}").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
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
	void issuesTicketForSignedResumableEndpoint() {
		SupabaseTemporaryUploadSigner signer = new SupabaseTemporaryUploadSigner(
			new ObjectMapper(),
			storageApiUrl,
			SERVICE_ROLE_KEY,
			"temporary-uploads"
		);

		SignedUploadTicket ticket = signer.issue("temporary/owner/photo.jpg");

		assertThat(ticket.endpoint()).isEqualTo(storageApiUrl + "/upload/resumable/sign");
		assertThat(ticket.token()).isEqualTo(SIGNED_TOKEN);
		assertThat(requestedPath.get()).isEqualTo(
			"/storage/v1/object/upload/sign/temporary-uploads/temporary/owner/photo.jpg"
		);
		assertThat(authorizationHeader.get()).isEqualTo("Bearer " + SERVICE_ROLE_KEY);
	}
}
