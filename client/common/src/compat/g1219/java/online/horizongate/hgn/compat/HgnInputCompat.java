package online.horizongate.hgn.compat;

/** Adapts event-based mouse input without reflective field names. */
public final class HgnInputCompat {
	private HgnInputCompat() {}
	public static double x(Object event) { return ((net.minecraft.client.input.MouseButtonEvent) event).x(); }
	public static double y(Object event) { return ((net.minecraft.client.input.MouseButtonEvent) event).y(); }
	public static int button(Object event) { return ((net.minecraft.client.input.MouseButtonEvent) event).button(); }
}
