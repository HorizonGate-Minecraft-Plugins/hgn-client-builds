package online.horizongate.hgn.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Keeps the selected cosmetic bound to the current filtered catalogue page. */
public final class HgnCosmeticsSelection {
	private HgnCosmeticsSelection() {
	}

	public static <T> T visibleOrFirst(List<T> visible, T current, Function<T, String> id) {
		T matching = matchingOrNone(visible, current, id);
		return matching != null ? matching : visible.isEmpty() ? null : visible.get(0);
	}

	public static <T> T matchingOrNone(List<T> visible, T current, Function<T, String> id) {
		if (current != null) {
			String currentId = id.apply(current);
			for (T item : visible) {
				if (Objects.equals(currentId, id.apply(item))) return item;
			}
		}
		return null;
	}
}
