package com.wynnphoton;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellTableTest {
	@Test
	void theShippedTableLoads() throws IOException {
		SpellTable table = SpellTable.loadFromClasspath(true);
		assertEquals(10128, table.sprites().get("minecraft:wynn/spell/meteor_0_a").id());
		assertEquals(10131, table.pixels().get(0x09101c92).id());
		SpellTable released = SpellTable.loadFromClasspath(false);
		assertTrue(released.sprites().values().stream().noneMatch(SpellTable.Entry::candidate));
		assertTrue(released.pixels().values().stream().noneMatch(SpellTable.Entry::candidate));
	}

	@Test
	void parsesNamesHashesAndCandidates() throws IOException {
		String text = """
				# comment
				10128 sprite wynn/spell/meteor_0_a  Meteor
				10130 pixels 57f6d84a  Mage Meteor
				? 10140 pixels ffffffff  Maybe Heal
				""";
		SpellTable all = SpellTable.parse(text, true);
		assertEquals("Meteor", all.sprites().get("minecraft:wynn/spell/meteor_0_a").name());
		assertEquals(10130, all.pixels().get(0x57f6d84a).id());
		assertTrue(all.pixels().get(0xffffffff).candidate());
		assertFalse(SpellTable.parse(text, false).pixels().containsKey(0xffffffff));
	}

	@Test
	void rejectsBadLines() {
		assertThrows(IOException.class, () -> SpellTable.parse("10106 pixels 00000001 old ID", true));
		assertThrows(IOException.class, () -> SpellTable.parse("10128 colour 00000001", true));
		assertThrows(IOException.class, () -> SpellTable.parse("10128 pixels zz", true));
		assertThrows(IOException.class, () -> SpellTable.parse("10128 pixels 01\n10129 pixels 01", true));
	}
}
