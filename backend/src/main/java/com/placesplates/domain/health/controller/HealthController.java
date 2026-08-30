package com.placesplates.domain.health.controller;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	private static final Pattern COMMIT_SHA_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");
	private static final String DEPLOYMENT_COMMIT_HEADER = "X-Places-Plates-Commit";

	private final String commitSha;

	public HealthController(@Value("${places-plates.deployment.commit-sha:local}") String commitSha) {
		String normalizedCommitSha = commitSha == null ? "" : commitSha.trim();
		this.commitSha = COMMIT_SHA_PATTERN.matcher(normalizedCommitSha).matches() ? normalizedCommitSha : "local";
	}

	@GetMapping
	public ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header(DEPLOYMENT_COMMIT_HEADER, commitSha)
			.body(new HealthResponse("UP"));
	}
}
