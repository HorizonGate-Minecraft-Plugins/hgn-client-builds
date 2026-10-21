import online.horizongate.hgn.ui.HgnHeroPreviewPolicy;

/** Keeps the extra menu model out of compact, low-quality and slow frames. */
public final class HgnHeroPreviewPolicyTest {
	public static void main(String[] args) {
		HgnHeroPreviewPolicy policy = new HgnHeroPreviewPolicy();
		check(!policy.shouldRender(false, false, 120), "Compact menu rendered a hero");
		check(!policy.shouldRender(true, true, 120), "LOW scene quality rendered a hero");
		check(!policy.shouldRender(true, false, 0), "Unknown FPS rendered a hero");
		check(!policy.shouldRender(true, false, 44), "Hero started below safe FPS");
		check(policy.shouldRender(true, false, 45), "Healthy wide menu omitted hero");
		check(policy.shouldRender(true, false, 30), "Transient FPS dip flickered hero");
		check(!policy.shouldRender(true, false, 23), "Slow menu retained extra render");
		check(policy.shouldRender(true, false, 120), "One slow sample permanently hid the hero");
		check(!policy.shouldRender(true, false, 23), "Slow menu retained extra render");
		check(!policy.shouldRender(true, false, 23), "Slow menu retained extra render");
		check(!policy.shouldRender(true, false, 23), "Sustained slow menu retained extra render");
		check(!policy.shouldRender(true, false, 120), "Hero resumed after sustained slow frames");
		check(new HgnHeroPreviewPolicy().shouldRender(true, false, 120),
			"Fresh menu cannot recover after a prior slow screen");
		System.out.println("Hero preview policy: compact, quality and FPS gates passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
