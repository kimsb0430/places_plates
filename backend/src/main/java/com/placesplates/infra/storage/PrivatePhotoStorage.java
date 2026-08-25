package com.placesplates.infra.storage;

public interface PrivatePhotoStorage {

	byte[] downloadTemporary(String objectKey);

	void storeSanitizedMaster(String objectKey, byte[] bytes, String mimeType);
}
