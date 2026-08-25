package com.placesplates.infra.image;

import java.awt.image.BufferedImage;

public interface ServerWatermarkRenderer {

	WatermarkedImage apply(BufferedImage source);

	String version();

	String position();
}
