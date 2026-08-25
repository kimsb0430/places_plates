package com.placesplates.infra.image;

import java.awt.image.BufferedImage;

public record WatermarkedImage(
	BufferedImage image,
	String version,
	String position
) {
}
