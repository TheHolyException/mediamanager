package de.theholyexception.mediamanager;

import lombok.Getter;
import org.jsoup.nodes.Element;

import java.util.List;

public enum AniworldProvider {
	VOE("VOE", "Hoster VOE", "voe.sx"),
	DOODSTREAM("Doodstream", "Hoster Doodstream", "doodstream.com", "dood.li"),
	VIDOZA("Vidoza", "Hoster Vidoza", "videzz.net", "vidoza.net"),
	STREAMTAPE("Streamtape", "Hoster Streamtape", "streamtape.com", "watchadsontape.com");

	@Getter
	private String displayName;
	@Getter
	private String hosterIdentifier;
	@Getter
	private List<String> url;

	AniworldProvider(String displayName, String hosterIdentifier, String... url) {
		this.displayName = displayName;
		this.hosterIdentifier = hosterIdentifier;
		this.url = List.of(url);
	}

	public static AniworldProvider getProvider(Element streamProviderListeItem) {
		Element element = streamProviderListeItem.selectFirst(".watchEpisode > i");
		String title = element.attr("title");
		for (AniworldProvider value : values()) {
			if (title.equalsIgnoreCase(value.hosterIdentifier))
				return value;
		}
		return null;
	}

	public static AniworldProvider getProvider(String url) {
		for (AniworldProvider value : values()) {
			if (value.getUrl().contains(url))
				return value;
		}
		return null;
	}

	public static AniworldProvider getProviderByName(String name) {
		for (AniworldProvider value : values()) {
			if (value.getDisplayName().equalsIgnoreCase(name))
				return value;
		}
		return null;
	}
}
