package com.wynnphoton;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsMigrationTest {
	@Test
	void renamesChangesAndRemovesSettings() throws IOException {
		SettingsMigration migration = SettingsMigration.parse("""
				# comment
				OLD_GLOW -> GLOW_I
				MODE=fancy -> MODE=2
				GONE ->
				SHADOWS=off ->
				""");

		// Renamed settings move to the end; Iris doesn't care about the order.
		assertEquals("SHADOWS=on\nKEPT=true\nGLOW_I=1.5\nMODE=2\n",
				migration.apply("OLD_GLOW=1.5\nMODE=fancy\nGONE=7\nSHADOWS=on\nKEPT=true\n"));
		assertEquals("KEPT=true\n", migration.apply("SHADOWS=off\nKEPT=true\n"));
	}

	@Test
	void chainsRenamesAndKeepsSettingsAlreadyUnderTheNewName() throws IOException {
		SettingsMigration migration = SettingsMigration.parse("A -> B\nB -> C\n");

		assertEquals("C=1\n", migration.apply("A=1\n"));
		assertEquals("C=new\n", migration.apply("A=old\nC=new\n"));
	}

	@Test
	void theShippedRulesParse() throws IOException {
		SettingsMigration.loadFromClasspath();
	}

	@Test
	void rejectsBrokenRules() {
		assertThrows(IOException.class, () -> SettingsMigration.parse("NO_ARROW\n"));
		assertThrows(IOException.class, () -> SettingsMigration.parse("A -> B=2\n"));
	}
}
