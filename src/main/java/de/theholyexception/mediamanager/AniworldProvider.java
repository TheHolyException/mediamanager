package de.theholyexception.mediamanager;

import lombok.Getter;
import me.kaigermany.downloaders.DownloaderSelector;
import org.jsoup.nodes.Element;

public enum AniworldProvider {
	VOE("VOE", "Hoster VOE"),
	STREAMTAPE("StreamTape", "Hoster Streamtape"),
	VIDOZA("Vidoza", "Hoster Vidoza"),
	LULUVDO("Luluvdo", "Hoster Luluvdo"),
	VIDMOLY("Vidmoly", "Hoster Vidmoly"),
	DOODSTREAM("Doodstream", "Hoster Doodstream"),
	;


	@Getter
	private String displayName;
	@Getter
	private String hosterIdentifier;

	AniworldProvider(String displayName, String hosterIdentifier) {
		this.displayName = displayName;
		this.hosterIdentifier = hosterIdentifier;
	}

	public static AniworldProvider getProvider(Element streamProviderListeItem) {
		Element element = streamProviderListeItem.selectFirst(".watchEpisode > i");
		String title = element.attr("title");
		for (AniworldProvider value : values()) {
			if (title.equalsIgnoreCase(value.hosterIdentifier)) {
				return value;
			}
		}
		return null;
	}

	public static AniworldProvider getProvider(String url) {
		String downloadKey = DownloaderSelector.detectDownloaderByDomain(url);
		for (AniworldProvider value : values()) {
			if (value.getDisplayName().equals(downloadKey))
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

	@Override
	public String toString() {
		return "AniworldProvider{" +
				"displayName='" + displayName + '\'' +
				", hosterIdentifier='" + hosterIdentifier + '\'' +
				'}';
	}
}
