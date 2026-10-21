import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;

/** Confirms the bundled UI font itself contains the Latin glyphs used by client headings. */
public final class HgnBundledFontTest {
	public static void main(String[] args) throws Exception {
		if (args.length == 0) throw new AssertionError("Expected bundled HGN font paths");
		for (String value : args) {
			Path path = Path.of(value);
			try (var input = Files.newInputStream(path)) {
				Font font = Font.createFont(Font.TRUETYPE_FONT, input);
				String sample = "HGN012a";
				int missing = font.canDisplayUpTo(sample);
				if (missing != -1) {
					throw new AssertionError(path.getFileName() + " lacks Latin glyph " + sample.charAt(missing));
				}
			}
		}
		System.out.println("Bundled HGN TrueType fonts contain representative Latin glyphs.");
	}
}
