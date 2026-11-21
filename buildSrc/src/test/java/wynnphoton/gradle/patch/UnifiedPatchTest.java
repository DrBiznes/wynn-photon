package wynnphoton.gradle.patch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedPatchTest {
	private static final List<String> PATCH = List.of(
			"--- a/shaders/test.glsl",
			"+++ b/shaders/test.glsl",
			"@@ -2,3 +2,3 @@",
			" b",
			"-c",
			"+C",
			" d");

	@Test
	void appliesAtTheExpectedLine() throws PatchException {
		UnifiedPatch patch = UnifiedPatch.parse(PATCH).getFirst();
		assertEquals(List.of("a", "b", "C", "d", "e"), patch.apply(List.of("a", "b", "c", "d", "e")));
	}

	@Test
	void findsAHunkThatMovedBecauseOfEarlierChanges() throws PatchException {
		UnifiedPatch patch = UnifiedPatch.parse(PATCH).getFirst();
		assertEquals(List.of("new", "new", "a", "b", "C", "d"), patch.apply(List.of("new", "new", "a", "b", "c", "d")));
	}

	@Test
	void refusesAFileWhoseContextChanged() throws PatchException {
		UnifiedPatch patch = UnifiedPatch.parse(PATCH).getFirst();
		PatchException e = assertThrows(PatchException.class, () -> patch.apply(List.of("a", "b", "x", "d")));
		assertTrue(e.getMessage().contains("hunk #1"), e.getMessage());
	}

	@Test
	void treatsATrimmedBlankContextLineAsBlank() throws PatchException {
		UnifiedPatch patch = UnifiedPatch.parse(List.of(
				"--- a/f", "+++ b/f", "@@ -1,3 +1,3 @@", " x", "", "-y", "+Y")).getFirst();
		assertEquals(List.of("x", "", "Y"), patch.apply(List.of("x", "", "y")));
	}

	@Test
	void rejectsGarbageInsideAHunk() {
		assertThrows(PatchException.class, () -> UnifiedPatch.parse(List.of(
				"--- a/f", "+++ b/f", "@@ -1,2 +1,2 @@", "x", "-y", "+Y")));
	}

	@Test
	void keepsCrlfLineEndings() {
		TextFile file = TextFile.parse("a\r\nb\r\n");
		assertEquals("a\r\nB\r\n", file.withLines(List.of("a", "B")).render());
	}
}
