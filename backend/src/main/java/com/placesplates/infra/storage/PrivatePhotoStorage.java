package com.placesplates.infra.storage;

public interface PrivatePhotoStorage {

	byte[] downloadTemporary(String objectKey);

	byte[] downloadSanitizedMaster(String objectKey);

	void storeSanitizedMaster(String objectKey, byte[] bytes, String mimeType);

	void storeResponsiveVariant(String objectKey, byte[] bytes, String mimeType);
}
