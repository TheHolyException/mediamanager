package de.theholyexception.mediamanager.util;

import lombok.Getter;

public class WebResponseException extends RuntimeException {

	@Getter
	private WebSocketResponse response;

	public WebResponseException(WebSocketResponse response) {
		this.response = response;
	}

	@Override
	public String toString() {
		return response.getResponse().toString();
	}
}
