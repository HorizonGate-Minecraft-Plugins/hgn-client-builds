package online.horizongate.hgn.ui;

import net.minecraft.client.gui.Font;

/** Fits a native world's save-folder ID and date into the HGN row without changing its identity. */
public final class HgnWorldRowMetadata {
	private static final String ELLIPSIS = "...";

	private HgnWorldRowMetadata() {
	}

	public static String fit(Font font, String metadata, int availableWidth) {
		if (availableWidth <= 0) return "";
		if (font.width(metadata) <= availableWidth) return metadata;
		int dateStart = metadata.lastIndexOf(" (");
		if (dateStart > 0) {
			String date = metadata.substring(dateStart);
			int idWidth = availableWidth - font.width(date);
			if (idWidth >= font.width(ELLIPSIS)) {
				return prefix(font, metadata.substring(0, dateStart), idWidth) + date;
			}
		}
		return prefix(font, metadata, availableWidth);
	}

	private static String prefix(Font font, String text, int availableWidth) {
		if (font.width(text) <= availableWidth) return text;
		if (font.width(ELLIPSIS) > availableWidth) return "";
		int low = 0;
		int high = text.codePointCount(0, text.length());
		while (low < high) {
			int middle = (low + high + 1) >>> 1;
			String candidate = text.substring(0, text.offsetByCodePoints(0, middle)) + ELLIPSIS;
			if (font.width(candidate) <= availableWidth) low = middle;
			else high = middle - 1;
		}
		return text.substring(0, text.offsetByCodePoints(0, low)) + ELLIPSIS;
	}
}
