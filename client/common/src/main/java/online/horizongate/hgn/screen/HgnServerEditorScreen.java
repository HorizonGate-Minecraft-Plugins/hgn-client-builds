package online.horizongate.hgn.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import online.horizongate.hgn.compat.HgnCompat;
import online.horizongate.hgn.ui.HgnPalette;
import online.horizongate.hgn.ui.HgnScreenBackdrop;
import online.horizongate.hgn.ui.HgnSurface;
import online.horizongate.hgn.ui.dashboard.HgnTypography;
import online.horizongate.hgn.ui.widget.HgnButton;

/** Custom add/edit/direct-connection form; cancel never modifies the server list. */
public final class HgnServerEditorScreen extends Screen {
	private final Screen parent;
	private ServerList servers;
	private final ServerData original;
	private final boolean direct;
	private ServerData.ServerPackStatus packs;
	private EditBox name;
	private EditBox address;
	private HgnButton submit;
	private int left;
	private int top;
	private int panelWidth;
	private float scale;

	public HgnServerEditorScreen(Screen parent, ServerList servers, ServerData original, boolean direct) {
		super(Component.literal(direct ? "Direct connection" : original == null ? "Add server" : "Edit server"));
		this.parent = parent;
		this.servers = servers;
		this.original = original;
		this.direct = direct;
		packs = original == null ? ServerData.ServerPackStatus.PROMPT : original.getResourcePackStatus();
	}

	@Override
	protected void init() {
		String nameValue = name == null ? original == null ? "Minecraft Server" : original.name : name.getValue();
		String addressValue = address == null ? original == null ? "" : original.ip : address.getValue();
		panelWidth = Math.min(380, width - 24);
		left = (width - panelWidth) / 2;
		scale = Math.min(1f, (height - 16) / 224f);
		top = Math.max(8, (height - Math.round(224 * scale)) / 2);
		name = new EditBox(font, left + 16, y(55), panelWidth - 32, Math.round(22 * scale), Component.literal("Server name"));
		name.setMaxLength(128);
		name.setValue(nameValue);
		name.active = !direct;
		addRenderableWidget(name);
		address = new EditBox(font, left + 16, y(100), panelWidth - 32, Math.round(22 * scale), Component.literal("Server address"));
		address.setMaxLength(255);
		address.setValue(addressValue);
		addRenderableWidget(address);
		addRenderableWidget(HgnButton.centered(left + 16, y(143), panelWidth - 32, Math.round(24 * scale),
			packLabel(), false, button -> {
				packs = switch (packs) {
					case PROMPT -> ServerData.ServerPackStatus.ENABLED;
					case ENABLED -> ServerData.ServerPackStatus.DISABLED;
					case DISABLED -> ServerData.ServerPackStatus.PROMPT;
				};
				button.setMessage(packLabel());
			}));
		int half = (panelWidth - 40) / 2;
		addRenderableWidget(HgnButton.centered(left + 16, y(185), half, Math.round(24 * scale),
			Component.literal("Cancel"), false, ignored -> onClose()));
		submit = addRenderableWidget(HgnButton.centered(left + 24 + half, y(185), half, Math.round(24 * scale),
			Component.literal(direct ? "Join server" : "Save server"), true, ignored -> save()));
		name.setResponder(value -> validate());
		address.setResponder(value -> validate());
		validate();
		setInitialFocus(direct ? address : name);
	}

	private Component packLabel() {
		return Component.literal("Server resource packs: " + switch (packs) {
			case PROMPT -> "Ask";
			case ENABLED -> "Enabled";
			case DISABLED -> "Disabled";
		});
	}

	private void validate() {
		if (submit != null) submit.active = !name.getValue().isBlank()
			&& ServerAddress.isValidAddress(address.getValue().trim());
	}

	private void save() {
		validate();
		if (!submit.active) return;
		ServerData data = new ServerData(name.getValue().trim(), address.getValue().trim(), ServerData.Type.OTHER);
		if (original != null) data.copyFrom(original);
		data.name = name.getValue().trim();
		data.ip = address.getValue().trim();
		data.setResourcePackStatus(packs);
		if (direct) {
			HgnCompat.connect(parent, minecraft, data);
			return;
		}
		if (servers == null) { servers = new ServerList(minecraft); servers.load(); }
		if (original == null) servers.add(data, false);
		else original.copyFrom(data);
		servers.save();
		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HgnScreenBackdrop.render(this, graphics, mouseX, mouseY);
		HgnSurface.panel(graphics, left, top, panelWidth, Math.round(224 * scale), false);
		HgnTypography.text(graphics, font, "HGN / " + getTitle().getString(), left + 16, y(15), scale, HgnPalette.CYAN);
		HgnTypography.text(graphics, font, "Server name", left + 16, y(40), 0.85f * scale, HgnPalette.TEXT);
		HgnTypography.text(graphics, font, "Address (hostname or IP, optional :port)", left + 16, y(85), 0.8f * scale, HgnPalette.TEXT);
		if (!address.getValue().isBlank() && !ServerAddress.isValidAddress(address.getValue().trim())) {
			HgnTypography.text(graphics, font, "Enter a valid server address", left + 16, y(129), 0.72f * scale, HgnPalette.WARNING);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() { minecraft.setScreen(parent); }

	private int y(int offset) { return top + Math.round(offset * scale); }
}
