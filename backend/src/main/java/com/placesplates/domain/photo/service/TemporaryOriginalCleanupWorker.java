package com.placesplates.domain.photo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.placesplates.infra.persistence.TemporaryOriginalCleanupOwnerRepository;

@Component
@ConditionalOnProperty(
	prefix = "places-plates.cleanup",
	name = "enabled",
	havingValue = "true",
	matchIfMissing = true
)
public class TemporaryOriginalCleanupWorker {

	private static final Logger log = LoggerFactory.getLogger(TemporaryOriginalCleanupWorker.class);

	private final TemporaryOriginalCleanupOwnerRepository ownerRepository;
	private final TemporaryOriginalCleanupService cleanupService;
	private final int batchSize;

	public TemporaryOriginalCleanupWorker(
		TemporaryOriginalCleanupOwnerRepository ownerRepository,
		TemporaryOriginalCleanupService cleanupService,
		@Value("${places-plates.cleanup.batch-size:25}") int batchSize
	) {
		this.ownerRepository = ownerRepository;
		this.cleanupService = cleanupService;
		this.batchSize = Math.max(1, Math.min(batchSize, 100));
	}

	@Scheduled(
		initialDelayString = "${places-plates.cleanup.initial-delay:PT30S}",
		fixedDelayString = "${places-plates.cleanup.interval:PT15M}"
	)
	public void purgeTemporaryOriginals() {
		try {
			ownerRepository.findCandidateOwnerIds(batchSize)
				.forEach(ownerUserId -> cleanupService.purgeOwner(ownerUserId, batchSize));
		} catch (RuntimeException exception) {
			log.warn("Temporary original cleanup run failed; the next scheduled run will retry", exception);
		}
	}
}
