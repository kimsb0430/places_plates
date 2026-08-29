package com.placesplates.domain.photo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.placesplates.infra.persistence.TemporaryOriginalCleanupOwnerRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class TemporaryOriginalCleanupWorkerTests {

	@Test
	void doesNotWriteStorageKeysOrExceptionDetailsToFailureLogs() {
		TemporaryOriginalCleanupOwnerRepository ownerRepository =
			mock(TemporaryOriginalCleanupOwnerRepository.class);
		TemporaryOriginalCleanupService cleanupService = mock(TemporaryOriginalCleanupService.class);
		when(ownerRepository.findCandidateOwnerIds(anyInt()))
			.thenThrow(new IllegalStateException("temporary/private/sensitive-original.jpg"));

		Logger logger = (Logger) LoggerFactory.getLogger(TemporaryOriginalCleanupWorker.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			new TemporaryOriginalCleanupWorker(ownerRepository, cleanupService, 25)
				.purgeTemporaryOriginals();
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}

		List<ILoggingEvent> events = appender.list;
		assertEquals(1, events.size());
		ILoggingEvent event = events.getFirst();
		assertTrue(event.getFormattedMessage().contains("failureType=IllegalStateException"));
		assertFalse(event.getFormattedMessage().contains("sensitive-original.jpg"));
		assertNull(event.getThrowableProxy());
	}
}
