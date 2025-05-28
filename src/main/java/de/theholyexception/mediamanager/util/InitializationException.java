package de.theholyexception.mediamanager.util;

import lombok.Getter;

public class InitializationException extends RuntimeException {

	@Getter
	private final String name;
	private final String message;

	public InitializationException(String name, String message) {
		this.name = name;
		this.message = message;
	}

	@Override
	public String getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return "InitializationException{" +
				"name='" + name + '\'' +
				", message='" + message + '\'' +
				'}';
	}
}
