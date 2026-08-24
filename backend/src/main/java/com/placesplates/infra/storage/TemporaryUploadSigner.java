package com.placesplates.infra.storage;

public interface TemporaryUploadSigner {

	SignedUploadTicket issue(String objectKey);

	boolean objectMatches(String objectKey, long expectedByteSize);
}
