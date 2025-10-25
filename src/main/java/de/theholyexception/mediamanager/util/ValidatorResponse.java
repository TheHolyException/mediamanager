package de.theholyexception.mediamanager.util;

import lombok.Getter;

public enum ValidatorResponse {
	VALID("Valid"),
	VIDEO_LENGTH("Video length does not match the average from other videos in the same folder!"),
	FILE_SIZE("File size does not match the average in the same folder!");

	@Getter
	String description;
	ValidatorResponse(String description) {
		this.description = description;
	}
}