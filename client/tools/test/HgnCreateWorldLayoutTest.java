import java.util.ArrayList;
import java.util.List;
import online.horizongate.hgn.ui.HgnCreateWorldLayout;
import online.horizongate.hgn.ui.HgnCreateWorldPageState;
import online.horizongate.hgn.ui.HgnCreateWorldLayout.Kind;
import online.horizongate.hgn.ui.HgnCreateWorldPageState.Row;
import online.horizongate.hgn.ui.HgnCreateWorldPageState.RowKind;
import online.horizongate.hgn.ui.HgnRect;

/** Isolated geometry/identity model checks, not native creation-context or input acceptance. */
public final class HgnCreateWorldLayoutTest {
	private static final class Widget {
		final String translatedLabel;
		final Object callback = new Object();
		String value = "unchanged native draft";
		boolean active = true;
		Widget(String label) { translatedLabel = label; }
	}

	public static void main(String[] args) {
		int cases = 0;
		for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 240}, {320, 299}, {320, 319},
			{320, 320}, {759, 359}, {760, 359}, {759, 360}, {760, 360}, {836, 471}, {1280, 720}, {1920, 1080}}) {
			for (int count : new int[] {0, 3, 4, 5, 18, 40}) {
				for (int[] nativeBounds : new int[][] {{24, 32}, {28, 40}}) {
					var initial = HgnCreateWorldLayout.resolve(size[0], size[1], nativeBounds[0], nativeBounds[1], count, 0);
					boolean[] visited = new boolean[count];
					for (int page = 0; page < initial.pages(); page++) {
						var layout = HgnCreateWorldLayout.resolve(size[0], size[1], nativeBounds[0], nativeBounds[1], count, page);
						List<HgnRect> widgets = new ArrayList<>(List.of(layout.title(), layout.nameLabel(), layout.name()));
						if (layout.pages() > 1) widgets.addAll(List.of(layout.previous(), layout.next()));
						for (var slot : layout.actions()) {
							check(!visited[slot.index()], "Repeated action");
							visited[slot.index()] = true;
							widgets.add(slot.bounds());
							check(slot.bounds().height() >= 20 && slot.bounds().width() >= 140, "Native control too small");
							check(slot.index() / layout.capacity() == page, "Focus-page index mismatch");
						}
						for (int index = 0; index < widgets.size(); index++) {
							HgnRect rect = widgets.get(index);
							check(rect.width() > 0 && rect.height() > 0 && rect.x() >= 0 && rect.y() >= nativeBounds[0]
								&& rect.right() <= size[0] && rect.bottom() <= layout.footer().y(), "Outside available tab viewport " + size[0] + "x" + size[1]);
							for (int next = index + 1; next < widgets.size(); next++) check(!overlap(rect, widgets.get(next)), "Widget overlap");
						}
						check(layout.name().equals(initial.name()) && layout.footer().equals(initial.footer()), "Pinned identity or native footer moved while paging");
					}
					for (boolean found : visited) check(found, "Unknown action lost");
					check(HgnCreateWorldLayout.resolve(size[0], size[1], nativeBounds[0], nativeBounds[1], count, -1).page() == 0, "Negative page");
					check(HgnCreateWorldLayout.resolve(size[0], size[1], nativeBounds[0], nativeBounds[1], count, 999).page() == initial.pages() - 1, "Unclamped page");
					cases++;
				}
			}
		}
		check(HgnCreateWorldLayout.resolve(320, 180, 24, 32, 3, 0).pages() == 1, "Native stable Game controls should fit compact layout");
		verifyIdentityAndFocus();
		int additional = verifyOtherTabs();
		verifyPairing();
		System.out.println("Create World Game/World/More layout: " + (cases + additional) + " viewport/roster cases plus grouped identity, translated labels, resize, null focus, fallback and stale-page checks passed; no native context or creation actions exercised.");
	}

	private static int verifyOtherTabs() {
		int cases = 0;
		for (Kind kind : List.of(Kind.WORLD, Kind.MORE)) {
			for (int[] size : new int[][] {{320, 180}, {427, 180}, {320, 240}, {320, 319}, {320, 320},
				{759, 359}, {760, 360}, {836, 471}, {1280, 720}}) {
				for (int extras : new int[] {0, 1, 18, 40}) {
					for (int[] nativeBounds : new int[][] {{24, 32}, {28, 40}}) {
						Widget seedLabel = new Widget("Welt-Seed"), seed = new Widget("Welt-Seed");
						List<Widget> pinned = kind == Kind.WORLD ? List.of(seedLabel, seed) : List.of();
						List<Row<Widget>> rows = new ArrayList<>();
						if (kind == Kind.WORLD) {
							rows.add(new Row<>(RowKind.PAIR, List.of(new Widget("World preset"), new Widget("Customize"))));
							rows.add(new Row<>(RowKind.LABELED, List.of(new Widget("Translated structures label"), new Widget("On"))));
							rows.add(new Row<>(RowKind.LABELED, List.of(new Widget("Translated bonus label"), new Widget("Off"))));
							rows.get(2).widgets().get(1).active = false;
						} else for (String name : List.of("Game Rules", "Experiments", "Data Packs"))
							rows.add(new Row<>(RowKind.SINGLE, List.of(new Widget(name))));
						for (int index = 0; index < extras; index++) rows.add(new Row<>(RowKind.SINGLE, List.of(new Widget("Unknown mod control " + index))));
						List<Widget> roster = new ArrayList<>(pinned);
						for (var row : rows) roster.addAll(row.widgets());
						var state = new HgnCreateWorldPageState<>(kind, roster, pinned, rows);
						state.arrange(size[0], size[1], nativeBounds[0], nativeBounds[1], 0, true, null);
						check(state.layout().page() == 0, "Null World/More focus changed page");
						List<Widget> visited = new ArrayList<>(pinned);
						int pageCount = state.layout().pages();
						for (int page = 0; page < pageCount; page++) {
							state.arrange(size[0], size[1], nativeBounds[0], nativeBounds[1], page, false, null);
							var layout = state.layout();
							List<HgnRect> bounds = new ArrayList<>(List.of(layout.title()));
							if (kind == Kind.WORLD) bounds.addAll(List.of(layout.nameLabel(), layout.name()));
							if (layout.pages() > 1) bounds.addAll(List.of(layout.previous(), layout.next()));
							for (var slot : layout.actions()) {
								var row = rows.get(slot.index());
								check(state.visible().containsAll(row.widgets()), "Native label/control pair split across pages");
								for (Widget widget : row.widgets()) check(!visited.contains(widget), "Repeated grouped native widget");
								visited.addAll(row.widgets());
								bounds.addAll(row.bounds(slot.bounds()));
								check(slot.frame().bottom() <= layout.content().bottom(), "Native action card escaped content frame");
							}
							for (int index = 0; index < bounds.size(); index++) {
								var rect = bounds.get(index);
								check(rect.width() > 0 && rect.height() > 0 && rect.x() >= 0 && rect.y() >= nativeBounds[0]
									&& rect.right() <= size[0] && rect.bottom() <= layout.footer().y(), "Grouped widget outside viewport");
								for (int next = index + 1; next < bounds.size(); next++) check(!overlap(rect, bounds.get(next)), "Grouped widget overlap");
							}
						}
						check(visited.size() == roster.size() && visited.containsAll(roster), "Unknown World/More control lost");
						for (var widget : roster) {
							state.arrange(1280, 720, 24, 32, 0, true, widget);
							state.arrange(320, 180, 28, 40, 0, true, widget);
							check(state.visible().contains(widget), "Grouped resize lost focused control or label");
							check(widget.value.equals("unchanged native draft") && widget.callback != null, "Grouped native state mutated");
						}
						if (kind == Kind.WORLD) check(!rows.get(2).widgets().get(1).active, "Hardcore/debug switch condition overridden");
						var before = state.layout();
						state.dispose();
						state.arrange(320, 180, 24, 32, 999, false, null);
						check(before == state.layout(), "Disposed native tab accepted paging");
						cases++;
					}
				}
			}
		}
		return cases;
	}

	private static void verifyPairing() {
		var label = new HgnRect(10, 85, 200, 9);
		var first = new HgnRect(240, 80, 44, 20);
		var second = new HgnRect(240, 108, 44, 20);
		check(HgnCreateWorldPageState.pairedSwitchIndex(label, List.of(first, second)) == 0, "Native centered label not paired");
		check(HgnCreateWorldPageState.pairedSwitchIndex(label, List.of(second, first)) == 1, "Pairing depends on roster order");
		check(HgnCreateWorldPageState.pairedSwitchIndex(label, List.of()) == -1, "Missing switch not rejected");
		check(HgnCreateWorldPageState.pairedSwitchIndex(label, List.of(first, first)) == -1, "Ambiguous modded switch row not rejected");
		check(HgnCreateWorldPageState.pairedSwitchIndex(new HgnRect(10, 100, 100, 8), List.of(first, second)) == -1, "Adjacent rows were paired");
		Widget a = new Widget("label"), b = new Widget("switch");
		try {
			new HgnCreateWorldPageState<>(Kind.MORE, List.of(a, b), List.of(), List.of(new Row<>(RowKind.SINGLE, List.of(a))));
			throw new AssertionError("Missing unknown control accepted");
		} catch (IllegalArgumentException expected) { }
		try {
			new HgnCreateWorldPageState<>(Kind.MORE, List.of(a, b), List.of(a), List.of(new Row<>(RowKind.PAIR, List.of(a, b))));
			throw new AssertionError("Duplicate native identity accepted");
		} catch (IllegalArgumentException expected) { }
	}

	private static void verifyIdentityAndFocus() {
		for (String translation : List.of("World name", "Weltname", "世界名称", "An extremely long translated label that must not identify controls by displayed text")) {
			Widget label = new Widget(translation);
			Widget name = new Widget(translation);
			List<Widget> roster = new ArrayList<>(List.of(label, name));
			for (int index = 0; index < 37; index++) roster.add(new Widget("Unknown mod control " + index));
			roster.get(4).active = false;
			var state = new HgnCreateWorldPageState<>(roster, label, name);
			roster.clear();
			check(state.original().size() == 39, "Roster was not captured immutably");
			state.arrange(320, 180, 24, 32, 0, true, null);
			check(state.layout().page() == 0, "Null focus changed page or queried null-rejecting immutable list");
			for (var focus : state.actions()) {
				state.arrange(1280, 720, 24, 32, 0, true, focus);
				check(state.visible().contains(focus), "Wide focus lost");
				state.arrange(320, 180, 24, 32, 0, true, focus);
				check(state.visible().contains(focus), "Compact resize lost focused control");
				check(state.visible().contains(name) && state.visible().contains(label), "Name moved off page");
			}
			state.arrange(320, 180, 24, 32, 999, false, name);
			int last = state.layout().pages() - 1;
			check(state.layout().page() == last, "Explicit page navigation must not follow pinned focus");
			state.arrange(320, 180, 24, 32, 0, false, state.actions().getLast());
			check(state.layout().page() == 0, "Explicit navigation incorrectly followed old focus");
			for (var widget : state.original()) check(widget.value.equals("unchanged native draft") && widget.callback != null, "Native value/callback mutated");
			check(!state.original().get(4).active, "Native disabled condition overridden");
			var before = state.layout();
			var visible = state.visible();
			state.dispose();
			state.dispose();
			state.arrange(1280, 720, 24, 32, 10, true, null);
			check(state.disposed() && before == state.layout() && visible == state.visible(), "Stale disposed navigation changed layout");
		}
	}

	private static boolean overlap(HgnRect a, HgnRect b) {
		return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
	}

	private static void check(boolean success, String message) {
		if (!success) throw new AssertionError(message);
	}
}
