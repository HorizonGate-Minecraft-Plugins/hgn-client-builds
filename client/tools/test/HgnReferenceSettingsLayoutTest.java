import online.horizongate.hgn.ui.HgnDashboardLayout;
import online.horizongate.hgn.ui.HgnRect;
import online.horizongate.hgn.ui.HgnReferenceSettingsLayout;
import online.horizongate.hgn.ui.HgnReferenceShellLayout;
import online.horizongate.hgn.ui.HgnSettingsLayout;
import online.horizongate.hgn.ui.HgnSettingsCategory;

/** Keeps the wide settings reference usable without sacrificing compact layouts. */
public final class HgnReferenceSettingsLayoutTest {
	public static void main(String[] args) {
		if (HgnReferenceSettingsLayout.fits(320, 240)
			|| HgnReferenceSettingsLayout.fits(500, 280)) {
			throw new AssertionError("Small settings windows must use the compact layout");
		}
		int cases = 0;
		for (int[] viewport : new int[][] {{640, 360}, {836, 471}, {1280, 720}, {1672, 941}}) {
			int width = viewport[0];
			int height = viewport[1];
			if (!HgnReferenceSettingsLayout.fits(width, height)) {
				throw new AssertionError("Wide reference layout was not selected");
			}
			HgnDashboardLayout canvas = HgnDashboardLayout.resolve(width, height);
			HgnReferenceSettingsLayout reference = new HgnReferenceSettingsLayout(canvas);
			HgnReferenceShellLayout shell = new HgnReferenceShellLayout(canvas);
			HgnSettingsLayout controls = reference.controls(4);
			checkCategoryTabs(controls, width, height, reference);
			for (HgnRect rect : new HgnRect[] {controls.panel(), controls.tabs(), controls.content(),
				controls.footer(), reference.heading(), reference.preview(), reference.previewImage(),
				reference.profile(), reference.account(), reference.playerPreview(),
				reference.playerPreviewModel(),
				reference.systemInfo(), reference.quickActions()}) {
				within(rect, width, height);
			}
			if (reference.playerPreviewModel().x() < reference.playerPreview().x()
				|| reference.playerPreviewModel().right() > reference.playerPreview().right()
				|| reference.playerPreviewModel().bottom() > canvas.rect(695, 203, 126, 1).y()) {
				throw new AssertionError("Player model overlaps the drag caption at "
					+ width + "x" + height);
			}
			if (shell.rail().right() >= controls.panel().x()
				|| controls.panel().right() >= reference.profile().x()
				|| reference.preview().x() < controls.content().right()
				|| reference.previewImage().right() > reference.preview().right()) {
				throw new AssertionError("Settings shell columns overlap at " + width + "x" + height);
			}
			if (controls.rowsPerPage() != 4 || controls.pages(4) != 1) {
				throw new AssertionError("Video's four real controls must fit without scrolling");
			}
			for (int index = 0; index < 3; index++) {
				HgnRect action = controls.footerButton(index, 3);
				within(action, width, height);
				if (action.x() < controls.footer().x() || action.right() > controls.footer().right()
					|| action.width() < 80 || action.height() < 18) {
					throw new AssertionError("Video, Minecraft and Done actions must remain usable");
				}
				if (index > 0 && controls.footerButton(index - 1, 3).right() >= action.x()) {
					throw new AssertionError("Settings footer actions overlap");
				}
			}
			for (int index = 0; index < 4; index++) {
				HgnRect row = controls.row(index);
				HgnRect value = reference.settingValue(row);
				within(row, width, height);
				within(value, width, height);
				if (row.x() < controls.content().x() || row.right() > controls.content().right()
					|| row.y() < controls.content().y() || row.bottom() > controls.content().bottom()) {
					throw new AssertionError("Settings row escaped content at " + width + "x" + height);
				}
				if (value.x() <= row.x() + canvas.size(115)
					|| value.right() > row.right() || value.y() < row.y()
					|| value.bottom() > row.bottom()) {
					throw new AssertionError("Settings value overlaps label or escapes row");
				}
			}
			cases++;
		}
		for (int[] viewport : new int[][] {{320, 180}, {320, 200}, {427, 240},
			{480, 270}, {550, 352}, {320, 480}}) {
			for (int items : new int[] {1, 4}) {
				HgnSettingsLayout compact = HgnSettingsLayout.resolveCategories(viewport[0], viewport[1], items);
				checkCategoryTabs(compact, viewport[0], viewport[1], null);
				if (compact.row(compact.rowsPerPage() - 1).bottom() > compact.content().bottom()
					|| compact.row(compact.rowsPerPage() - 1).bottom() >= compact.footer().y()) {
					throw new AssertionError("Compact category rows overlap footer at "
						+ viewport[0] + "x" + viewport[1] + " with " + items + " items");
				}
			}
		}
		System.out.println("Reference settings: " + cases
			+ " wide and six compact GUI viewports fit ten category tabs and controls.");
	}

	private static void checkCategoryTabs(HgnSettingsLayout controls, int width, int height,
		HgnReferenceSettingsLayout reference) {
		if (HgnSettingsCategory.values().length != 10) {
			throw new AssertionError("Settings must expose the reference's ten categories");
		}
		boolean[] reached = new boolean[20];
		for (HgnSettingsCategory category : HgnSettingsCategory.values()) {
			for (int entryIndex = 0; entryIndex < category.entryCount(); entryIndex++) {
				int entry = category.entryAt(entryIndex);
				if (entry < 0 || entry >= reached.length || reached[entry]) {
					throw new AssertionError("Settings entry is duplicated or invalid: " + entry);
				}
				reached[entry] = true;
			}
		}
		for (boolean entry : reached) if (!entry) {
			throw new AssertionError("A persisted setting or native destination is unreachable");
		}
		for (int index = 0; index < 10; index++) {
			HgnRect tab = reference == null ? controls.categoryTab(index) : reference.categoryTab(index);
			within(tab, width, height);
			if (tab.width() < (reference == null ? 40 : 28)
				|| tab.height() < (reference == null ? 10 : 18)
				|| tab.bottom() >= controls.sectionTitleY()) {
				throw new AssertionError("Category tab is too small or overlaps its section at "
					+ width + "x" + height + ": " + tab);
			}
			if (reference != null && (tab.y() != reference.categoryTab(0).y()
				|| tab.x() < controls.tabs().x() || tab.right() > controls.tabs().right())) {
				throw new AssertionError("Wide category escaped its single-row strip");
			}
			for (int previous = 0; previous < index; previous++) {
				HgnRect other = reference == null
					? controls.categoryTab(previous) : reference.categoryTab(previous);
				if (tab.x() < other.right() && tab.right() > other.x()
					&& tab.y() < other.bottom() && tab.bottom() > other.y()) {
					throw new AssertionError("Settings category tabs overlap at " + width + "x" + height);
				}
			}
		}
		if (reference == null && controls.pages(4) > 1
			&& (controls.categoryTab(9).bottom() > controls.categoryPageButton(false).y()
				|| controls.categoryPageButton(false).bottom() > controls.content().y())) {
			throw new AssertionError("Page control overlaps category tabs at " + width + "x" + height);
		}
	}

	private static void within(HgnRect rect, int width, int height) {
		if (rect.x() < 0 || rect.y() < 0 || rect.right() > width || rect.bottom() > height) {
			throw new AssertionError("Settings element outside viewport: " + rect);
		}
	}
}
