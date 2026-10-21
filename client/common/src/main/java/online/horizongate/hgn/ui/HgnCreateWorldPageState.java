package online.horizongate.hgn.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Identity-based page plan; never changes values, native state, listeners, or registration itself. */
public final class HgnCreateWorldPageState<T> {
	public enum RowKind { SINGLE, PAIR, LABELED }

	public record Row<T>(RowKind kind, List<T> widgets) {
		public Row {
			widgets = List.copyOf(widgets);
			if (widgets.size() != (kind == RowKind.SINGLE ? 1 : 2)) throw new IllegalArgumentException("Invalid native row");
		}

		public List<HgnRect> bounds(HgnRect rect) {
			if (kind == RowKind.SINGLE) return List.of(rect);
			if (kind == RowKind.LABELED) return List.of(
				new HgnRect(rect.x(), rect.y() + (rect.height() - 9) / 2, rect.width() - 80, 9),
				new HgnRect(rect.right() - 72, rect.y(), 72, rect.height()));
			int width = (rect.width() - 6) / 2;
			return List.of(new HgnRect(rect.x(), rect.y(), width, rect.height()),
				new HgnRect(rect.right() - width, rect.y(), width, rect.height()));
		}
	}

	private final HgnCreateWorldLayout.Kind kind;
	private final List<T> original;
	private final List<T> pinned;
	private final List<Row<T>> rows;
	private final List<T> actions;
	private HgnCreateWorldLayout layout;
	private List<T> visible = List.of();
	private boolean disposed;

	/** Native switch rows have separate labels; ambiguous overlap retains vanilla arrangement. */
	public static int pairedSwitchIndex(HgnRect label, List<HgnRect> candidates) {
		int match = -1;
		for (int index = 0; index < candidates.size(); index++) {
			var candidate = candidates.get(index);
			if (candidate.y() < label.bottom() && candidate.bottom() > label.y()) {
				if (match >= 0) return -1;
				match = index;
			}
		}
		return match;
	}

	public HgnCreateWorldPageState(List<T> original, T nameLabel, T name) {
		this(HgnCreateWorldLayout.Kind.GAME, original, List.of(nameLabel, name), original.stream()
			.filter(widget -> widget != nameLabel && widget != name)
			.map(widget -> new Row<>(RowKind.SINGLE, List.of(widget))).toList());
	}

	public HgnCreateWorldPageState(HgnCreateWorldLayout.Kind kind, List<T> original,
		List<T> pinned, List<Row<T>> rows) {
		this.kind = kind;
		this.original = List.copyOf(original);
		this.pinned = List.copyOf(pinned);
		this.rows = List.copyOf(rows);
		actions = rows.stream().flatMap(row -> row.widgets().stream()).toList();
		var captured = new IdentityHashMap<T, Boolean>();
		for (T widget : pinned) if (captured.put(widget, true) != null) throw new IllegalArgumentException("Repeated pinned widget");
		for (T widget : actions) if (captured.put(widget, true) != null) throw new IllegalArgumentException("Repeated native widget");
		if (captured.size() != original.size() || original.stream().anyMatch(widget -> !captured.containsKey(widget)))
			throw new IllegalArgumentException("Incomplete native roster");
	}

	public void arrange(int width, int height, int tabBottom, int footerHeight,
		int page, boolean keepFocusedPage, T focused) {
		if (disposed) return;
		layout = HgnCreateWorldLayout.resolve(kind, width, height, tabBottom, footerHeight, rows.size(), page);
		int focusedIndex = -1;
		if (focused != null) for (int index = 0; index < rows.size(); index++) {
			if (rows.get(index).widgets().stream().anyMatch(widget -> widget == focused)) focusedIndex = index;
		}
		if (keepFocusedPage && focusedIndex >= 0) layout = HgnCreateWorldLayout.resolve(kind, width, height,
			tabBottom, footerHeight, rows.size(), focusedIndex / layout.capacity());
		List<T> shown = new ArrayList<>(pinned);
		for (var slot : layout.actions()) shown.addAll(rows.get(slot.index()).widgets());
		visible = List.copyOf(shown);
	}

	public HgnCreateWorldLayout.Kind kind() { return kind; }
	public List<T> original() { return original; }
	public List<Row<T>> rows() { return rows; }
	public List<T> pinned() { return pinned; }
	public List<T> actions() { return actions; }
	public List<T> visible() { return visible; }
	public HgnCreateWorldLayout layout() { return layout; }
	public boolean disposed() { return disposed; }
	public void dispose() { disposed = true; }
}
