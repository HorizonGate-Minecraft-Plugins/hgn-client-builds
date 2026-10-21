package online.horizongate.hgn.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.HgnClient;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnEditWorldAccessor;
import online.horizongate.hgn.mixin.HgnEditWorldRenderAccessor;
import online.horizongate.hgn.mixin.HgnFontWidgetAccessor;
import online.horizongate.hgn.screen.HgnEditWorldScreen;

/** Uses only a session-lock lease on the already prepared disposable CI fixture; no world action. */
public final class HgnEditWorldSmokeCheck {
	private HgnEditWorldSmokeCheck() {
	}

	private record State(Component label, boolean active, boolean visible, Tooltip tooltip) {
	}

	public static void run() {
		if (!Boolean.getBoolean("hgn.smokeWorldFixture")) {
			HgnClient.LOGGER.info("HGN_EDIT_WORLD_SMOKE_SKIPPED: no explicit disposable fixture lease permission");
			return;
		}
		try {
			var fixture = HgnWorldMenuSmokeFixture.requirePrepared();
			var minecraft = Minecraft.getInstance();
			var parent = minecraft.screen;
			Path data = fixture.directory().resolve("level.dat");
			Path icon = fixture.directory().resolve("icon.png");
			byte[] dataBefore = Files.readAllBytes(data);
			byte[] iconBefore = optionalBytes(icon);
			AtomicInteger callbacks = new AtomicInteger();
			BooleanConsumer callback = value -> callbacks.incrementAndGet();
			try (var access = minecraft.getLevelSource().validateAndCreateAccess(fixture.summary().getLevelId())) {
				for (int[] size : new int[][] {{320, 180}, {320, 300}, {427, 240}, {800, 360}, {836, 471}, {1280, 720}}) {
					EditWorldScreen screen = EditWorldScreen.create(minecraft, access, callback);
					var host = (HgnEditWorldScreen.Host) screen;
					var ownership = (HgnEditWorldAccessor) screen;
					List<AbstractWidget> nativeWidgets = host.hgn$editWorldWidgets();
					if (nativeWidgets.size() != 9 || nativeWidgets.stream().filter(widget -> widget instanceof Button).count() != 7
						|| nativeWidgets.stream().filter(widget -> widget instanceof StringWidget).count() != 1) {
						throw new AssertionError("Original Edit World roster changed");
					}
					Map<AbstractWidget, State> states = new IdentityHashMap<>();
					for (var widget : nativeWidgets) states.put(widget, new State(widget.getMessage(), widget.active, widget.visible, tooltip(widget)));
					String name = host.hgn$editWorldName().getValue();
					HgnCompat.initScreen(screen, size[0], size[1]);
					if (screen.getFocused() != host.hgn$editWorldName()) throw new AssertionError("Native initial name focus lost");
					var composition = host.hgn$editWorldComposition();
					if (composition == null || !composition.nativeWidgets().equals(nativeWidgets)) throw new AssertionError("Native editor not composed in place");
					var visited = java.util.Collections.newSetFromMap(new IdentityHashMap<AbstractWidget, Boolean>());
					for (int page = 0; page < composition.pageCount(); page++) {
						composition.showPage(page);
						assertBounds(screen);
						List<AbstractWidget> visible = nativeWidgets.stream().filter(screen.children()::contains).toList();
						for (var widget : visible) {
							visited.add(widget);
							if (widget.active && widget.visible && !(widget instanceof StringWidget)) {
								composition.showPage(page);
								screen.setFocused(widget);
								HgnCompat.resizeScreen(screen, 1280, 720);
								HgnCompat.resizeScreen(screen, size[0], size[1]);
								if (screen.getFocused() != widget || !widget.isFocused() || !screen.children().contains(widget)) {
									throw new AssertionError("Edit World focused control lost on resize");
								}
							}
						}
					}
					if (visited.size() != nativeWidgets.size()) throw new AssertionError("Native world action is unreachable");
					screen.setFocused(null);
					composition.showPage(0);
					HgnCompat.resizeScreen(screen, size[0], size[1]);
					if (screen.getFocused() != null) throw new AssertionError("Unfocused Edit World resize claimed focus");
					ownership.hgn$initEditWorld();
					if (!composition.disposed() || host.hgn$editWorldComposition() == composition) throw new AssertionError("Reinitialization retained stale composition");
					List<?> childrenAfter = List.copyOf(screen.children());
					composition.showPage(999);
					composition.arrange();
					composition.dispose();
					if (!childrenAfter.equals(screen.children())) throw new AssertionError("Disposed composition changed the editor");
					if (host.hgn$editWorldComposition().pageCount() > 1) {
						var navigation = screen.children().stream().filter(child -> child instanceof online.horizongate.hgn.ui.widget.HgnButton)
							.map(child -> (AbstractWidget) child).filter(widget -> widget.active).findFirst().orElseThrow();
						screen.setFocused(navigation);
						ownership.hgn$initEditWorld();
						if (screen.getFocused() != host.hgn$editWorldName() || screen.children().contains(navigation)) {
							throw new AssertionError("Reinitialization retained removed navigation focus");
						}
					}
					if (!host.hgn$editWorldWidgets().equals(nativeWidgets) || !host.hgn$editWorldName().getValue().equals(name)
						|| ownership.hgn$editWorldStorage() != access || ownership.hgn$editWorldCallback() != callback
						|| minecraft.screen != parent || callbacks.get() != 0) throw new AssertionError("Native edit ownership/state changed");
					states.forEach((widget, state) -> {
						if (!widget.getMessage().equals(state.label()) || widget.active != state.active() || widget.visible != state.visible()
							|| tooltip(widget) != state.tooltip()) throw new AssertionError("Native edit label/state/tooltip changed");
					});
					assertBounds(screen);
					var renderables = ((HgnEditWorldRenderAccessor) screen).hgn$editWorldRenderables();
					if (renderables.size() != screen.children().size() || new java.util.HashSet<>(renderables).size() != renderables.size()) {
						throw new AssertionError("Edit World render registrations duplicated on reinit");
					}
				}
			} finally {
				if (!Arrays.equals(dataBefore, Files.readAllBytes(data)) || !Arrays.equals(iconBefore, optionalBytes(icon))) {
					throw new AssertionError("Detached Edit World inspection changed fixture metadata or icon");
				}
			}
			HgnClient.LOGGER.info("HGN_EDIT_WORLD_SMOKE_OK: existing disposable fixture session-lock lease only; native roster, storage/callback identity, name, state, focus, paging, resize and reinit preserved; no input, rendering, folder, backup, optimize, reset, rename, Save/Cancel or world launch exercised");
		} catch (Exception exception) {
			throw new AssertionError("Unable to inspect detached Edit World with the existing CI fixture", exception);
		}
	}

	private static byte[] optionalBytes(Path path) throws java.io.IOException {
		if (Files.isSymbolicLink(path)) throw new java.io.IOException("Fixture icon must not be a symlink");
		return Files.exists(path) ? Files.readAllBytes(path) : null;
	}

	private static Tooltip tooltip(AbstractWidget widget) {
		return ((HgnFontWidgetAccessor) widget).hgn$fontTooltipHolder().get();
	}

	private static void assertBounds(EditWorldScreen screen) {
		List<AbstractWidget> widgets = screen.children().stream().filter(child -> child instanceof AbstractWidget)
			.map(child -> (AbstractWidget) child).toList();
		for (int index = 0; index < widgets.size(); index++) {
			var widget = widgets.get(index);
			if (widget.getX() < 0 || widget.getY() < 0 || widget.getWidth() <= 0 || widget.getHeight() <= 0
				|| widget.getX() + widget.getWidth() > screen.width || widget.getY() + widget.getHeight() > screen.height) {
				throw new AssertionError("Edit World control outside viewport");
			}
			for (int next = index + 1; next < widgets.size(); next++) {
				var other = widgets.get(next);
				if (widget.getX() < other.getX() + other.getWidth() && widget.getX() + widget.getWidth() > other.getX()
					&& widget.getY() < other.getY() + other.getHeight() && widget.getY() + widget.getHeight() > other.getY()) {
					throw new AssertionError("Edit World controls overlap");
				}
			}
		}
	}
}
