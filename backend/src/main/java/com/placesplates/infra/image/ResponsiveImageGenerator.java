package com.placesplates.infra.image;

import java.util.List;

public interface ResponsiveImageGenerator {

	List<ResponsiveImageVariant> generate(SanitizedImage sanitizedMaster);

	String watermarkVersion();

	String watermarkPosition();
}
