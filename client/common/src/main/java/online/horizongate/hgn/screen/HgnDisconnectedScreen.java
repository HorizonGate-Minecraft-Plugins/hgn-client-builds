package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.mixin.HgnDisconnectedTextAccessor;
import online.horizongate.hgn.ui.HgnDisconnectedLayout;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** In-place layout only. Native return, restricted-account, report and confirmation callbacks remain intact. */
public final class HgnDisconnectedScreen {
	public interface Host {
		DisconnectionDetails hgn$disconnectDetails();
		Screen hgn$disconnectParent();
		void hgn$disconnectAdd(AbstractWidget widget);
		void hgn$disconnectRemove(AbstractWidget widget);
		HgnDisconnectedScreen hgn$disconnectComposition();
	}

	private final DisconnectedScreen screen;
	private final Host host;
	private final List<AbstractWidget> original;
	private final List<Button> actions;
	private final StringWidget title;
	private final MultiLineTextWidget reason;
	private final OptionalInt nativeWrap;
	private final Map<AbstractWidget, HgnRect> nativeBounds = new IdentityHashMap<>();
	private final HgnDisconnectedLayout.Pages pages = new HgnDisconnectedLayout.Pages();
	private final ReasonViewport viewport;
	private final HgnButton previous;
	private final HgnButton next;
	private final StringWidget pageLabel;
	private HgnDisconnectedLayout layout;

	public static HgnDisconnectedScreen attach(DisconnectedScreen screen, Host host) {
		List<AbstractWidget> original = new ArrayList<>();
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget widget)) return null;
			original.add(widget);
		}
		Set<AbstractWidget> identities = Collections.newSetFromMap(new IdentityHashMap<>());
		identities.addAll(original);
		if (identities.size() != original.size()) return null;
		var titles = original.stream().filter(widget -> widget.getClass() == StringWidget.class
			&& widget.getMessage().equals(screen.getTitle())).toList();
		var reasons = original.stream().filter(widget -> widget.getClass() == MultiLineTextWidget.class
			&& widget.getMessage().equals(host.hgn$disconnectDetails().reason()) && !widget.active && widget.visible).toList();
		var actions = original.stream().filter(HgnCompat::isNativePlainButton).map(Button.class::cast).toList();
		int expectedActions = 1 + (host.hgn$disconnectDetails().report().isPresent() ? 1 : 0)
			+ (host.hgn$disconnectDetails().bugReportLink().isPresent() ? 1 : 0);
		if (titles.size() != 1 || reasons.size() != 1 || actions.size() + 2 != original.size()
			|| actions.size() != expectedActions
			|| !HgnDisconnectedLayout.supported(screen.width, screen.height, actions.size())) return null;
		if (((HgnDisconnectedTextAccessor) reasons.getFirst()).hgn$disconnectMaxRows().isPresent()) return null;
		return new HgnDisconnectedScreen(screen, host, original, (StringWidget) titles.getFirst(),
			(MultiLineTextWidget) reasons.getFirst(), actions);
	}

	private HgnDisconnectedScreen(DisconnectedScreen screen, Host host, List<AbstractWidget> original,
		StringWidget title, MultiLineTextWidget reason, List<Button> actions) {
		this.screen = screen;
		this.host = host;
		this.original = List.copyOf(original);
		this.title = title;
		this.reason = reason;
		this.actions = List.copyOf(actions);
		nativeWrap = ((HgnDisconnectedTextAccessor) reason).hgn$disconnectMaxWidth();
		for (var widget : original) nativeBounds.put(widget, bounds(widget));
		viewport = new ReasonViewport();
		previous = HgnButton.centered(0, 0, 70, 20, Component.literal("Previous"), false, ignored -> choose(pages.page() - 1));
		next = HgnButton.centered(0, 0, 70, 20, Component.literal("Next"), false, ignored -> choose(pages.page() + 1));
		previous.setTooltip(Tooltip.create(Component.literal("Previous reason page")));
		next.setTooltip(Tooltip.create(Component.literal("Next reason page")));
		pageLabel = new StringWidget(Component.empty(), Minecraft.getInstance().font);
	}

	public boolean owns() { return !pages.disposed() && host.hgn$disconnectComposition() == this; }
	public HgnDisconnectedLayout layout() { return layout; }
	public int pageCount() { return pages.count(); }
	public List<Button> nativeActions() { return actions; }
	public MultiLineTextWidget nativeReason() { return reason; }

	public void arrange() {
		if (!owns() || !HgnDisconnectedLayout.supported(screen.width, screen.height, actions.size())) return;
		int lineHeight = Minecraft.getInstance().font.lineHeight;
		layout = HgnDisconnectedLayout.resolve(screen.width, screen.height, actions.size(), lineHeight);
		place(title, layout.title());
		for (int index = 0; index < actions.size(); index++) place(actions.get(index), layout.actions().get(index));
		reason.setMaxWidth(layout.reason().width());
		pages.resize((reason.getHeight() + lineHeight - 1) / lineHeight, layout.reason().height() / lineHeight);
		place(viewport, layout.reason());
		place(previous, layout.previous());
		place(next, layout.next());
		place(pageLabel, layout.pageLabel());
		if (screen.children().contains(reason)) host.hgn$disconnectRemove(reason);
		for (var widget : List.of(viewport, previous, next, pageLabel)) if (!screen.children().contains(widget)) host.hgn$disconnectAdd(widget);
		updatePage();
	}

	private void choose(int page) {
		if (!owns() || Minecraft.getInstance().screen != screen) return;
		int old = pages.page();
		pages.choose(page);
		updatePage();
		if (old != pages.page()) HgnCompat.narrateSystemNow(pageMessage());
	}

	private Component pageMessage() { return Component.translatable("book.pageIndicator", pages.page() + 1, pages.count()); }

	private void updatePage() {
		previous.active = pages.page() > 0;
		next.active = pages.page() + 1 < pages.count();
		pageLabel.setMessage(pageMessage());
		place(pageLabel, layout.pageLabel());
		reason.setX(layout.reason().centerX() - reason.getWidth() / 2);
		reason.setY(layout.reason().y() - pages.firstLine() * Minecraft.getInstance().font.lineHeight);
	}

	public void dispose() { dispose(true); }
	public void disposeForReinit() { dispose(false); }

	private void dispose(boolean restoreRegistration) {
		if (pages.disposed()) return;
		boolean owner = owns();
		pages.dispose();
		if (!owner) return;
		var focused = screen.getFocused();
		for (var widget : List.of(viewport, previous, next, pageLabel)) host.hgn$disconnectRemove(widget);
		((HgnDisconnectedTextAccessor) reason).hgn$disconnectMaxWidth(nativeWrap);
		for (var widget : original) {
			var bounds = nativeBounds.get(widget);
			if (widget == reason) { widget.setX(bounds.x()); widget.setY(bounds.y()); }
			else place(widget, bounds);
			if (restoreRegistration && !screen.children().contains(widget)) host.hgn$disconnectAdd(widget);
		}
		if (restoreRegistration && (focused == previous || focused == next)) screen.setFocused(actions.getLast());
	}

	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!owns() || layout == null) return;
		HgnJoin.observe(screen);
		HgnScreenBackdrop.render(screen, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, layout.header(), true);
		HgnSurface.panel(graphics, layout.reasonPanel(), false);
		HgnSurface.panel(graphics, layout.footer(), false);
		if (!layout.compact()) HgnTypography.fitted(graphics, Minecraft.getInstance().font, "HGN / CONNECTION ENDED",
			layout.header().x() + 8, layout.header().y() + 10, layout.header().width() - 16, 0.85f, HgnPalette.CYAN);
	}

	/** Delegates painting to the exact original text widget; no text replacement or flattened styling. */
	private final class ReasonViewport extends AbstractWidget {
		private ReasonViewport() {
			super(0, 0, 1, 1, reason.getMessage());
			active = false;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			if (!owns()) return;
			graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
			try { reason.render(graphics, layout.reason().contains(mouseX, mouseY) ? mouseX : Integer.MIN_VALUE, mouseY, partialTick); }
			finally { graphics.disableScissor(); }
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, reason.getMessage());
		}
	}

	private static HgnRect bounds(AbstractWidget widget) { return new HgnRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()); }
	private static void place(AbstractWidget widget, HgnRect bounds) {
		widget.setX(bounds.x()); widget.setY(bounds.y()); widget.setWidth(bounds.width()); widget.setHeight(bounds.height());
	}
}
