package com.placesplates.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "places-plates.deployment.commit-sha=0123456789abcdef0123456789abcdef01234567")
@AutoConfigureMockMvc
class HealthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthIsPubliclyAvailable() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(header().string(
				"X-Places-Plates-Commit",
				"0123456789abcdef0123456789abcdef01234567"
			))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(header().string(
				"Content-Security-Policy",
				"default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
			))
			.andExpect(header().string(
				"Permissions-Policy",
				"camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()"
			))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("X-Permitted-Cross-Domain-Policies", "none"))
			.andExpect(header().string("X-XSS-Protection", "0"));
	}

	@Test
	void securityHeadersAreAlsoAppliedToAuthenticationFailures() throws Exception {
		mockMvc.perform(get("/api/v1/manage/missing"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(
				"Content-Security-Policy",
				"default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
			))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"));
	}
}
