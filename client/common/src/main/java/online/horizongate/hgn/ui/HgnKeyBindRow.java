package online.horizongate.hgn.ui;

import online.horizongate.hgn.screen.HgnKeyBindsScreen;

/** Marks only the original rows belonging to the dedicated HGN key-binding screen. */
public interface HgnKeyBindRow {
	void hgn$keyBindsOwner(HgnKeyBindsScreen owner);
	HgnKeyBindsScreen hgn$keyBindsOwner();
}
