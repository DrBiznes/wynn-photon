package com.wynnphoton;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cross-file checks on the patched pack that a broken ID move would fail, built from the real Photon release. */
class PatchedPackConsistencyTest {
	private static final Path PHOTON_ZIP = Path.of(System.getProperty("wynnphoton.photonZip"));

	@TempDir
	static Path game;
	static Path shaders;
	static Map<Integer, List<String>> blockIds;

	@BeforeAll
	static void buildPack() throws IOException {
		Path shaderpacks = Files.createDirectories(game.resolve("shaderpacks"));
		Path config = Files.createDirectories(game.resolve("config"));
		Files.copy(PHOTON_ZIP, shaderpacks.resolve("photon_v1.4.zip"));
		PatchBundle bundle = PatchBundle.loadFromClasspath();
		assertEquals(PhotonInstaller.Status.INSTALLED,
				new PhotonInstaller(shaderpacks, config, bundle, WynnPhotonConfig.DEFAULTS).run());
		shaders = shaderpacks.resolve(bundle.outputName()).resolve("shaders");
		blockIds = readIds(shaders.resolve("block.properties"), "block");
	}

	/** id -> entries of the modern (pre-"#else") part of a Photon id properties file. */
	private static Map<Integer, List<String>> readIds(Path file, String prefix) throws IOException {
		Map<Integer, List<String>> ids = new HashMap<>();
		Pattern line = Pattern.compile("^" + prefix + "\\.(\\d+)\\s*=(.*)$");
		for (String text : Files.readAllLines(file)) {
			if (text.startsWith("#else")) {
				break;
			}
			Matcher m = line.matcher(text);
			if (m.matches()) {
				List<String> entries = new ArrayList<>();
				for (String entry : m.group(2).trim().split("\\s+")) {
					if (!entry.isEmpty()) {
						entries.add(entry);
					}
				}
				ids.put(Integer.parseInt(m.group(1)), entries);
			}
		}
		return ids;
	}

	/**
	 * Photonics resolves every {@code *100xx} group in ph_lights.json through the block ID map, and one group with no
	 * blocks makes it reject the pack's whole light list ("Could not find light_block with name ...").
	 */
	@Test
	void everyPhotonicsLightGroupHasBlocks() throws IOException {
		String lights = Files.readString(shaders.resolve("ph_lights.json"));
		Matcher m = Pattern.compile("`\\*(\\d+)`").matcher(lights);
		TreeSet<Integer> empty = new TreeSet<>();
		while (m.find()) {
			int id = Integer.parseInt(m.group(1));
			if (blockIds.getOrDefault(id, List.of()).isEmpty()) {
				empty.add(id);
			}
		}
		assertTrue(empty.isEmpty(), "ph_lights.json groups with no blocks in block.properties: " + empty);
	}

	/**
	 * Iris keeps the first ID a block state gets, so a block left on its old line after a move would silently keep
	 * the old material. Every exact entry must appear on one line only, apart from duplicates Photon itself ships.
	 */
	@Test
	void noBlockEntryIsMappedTwice() {
		// Photon v1.4 lists redstone_wire under both 10058 and 10080 (the first one wins).
		Set<String> upstreamDuplicates = Set.of("redstone_wire");
		Map<String, Integer> seen = new HashMap<>();
		List<String> duplicates = new ArrayList<>();
		blockIds.forEach((id, entries) -> {
			for (String entry : entries) {
				if (upstreamDuplicates.contains(entry)) {
					continue;
				}
				Integer previous = seen.putIfAbsent(entry, id);
				if (previous != null && previous.intValue() != id) {
					duplicates.add(entry + " (" + previous + ", " + id + ")");
				}
			}
		});
		assertTrue(duplicates.isEmpty(), "block entries on more than one line: " + duplicates);
	}

	@Test
	void wynnBlocksSitWhereTheChangelogSays() {
		assertTrue(blockIds.get(10079).contains("redstone_torch:lit=true"));
		assertTrue(blockIds.get(10073).contains("repeater:delay=4:powered=true"));
		assertTrue(blockIds.get(10018).contains("comparator"));
		assertTrue(blockIds.get(10077).contains("petrified_oak_slab:type=double"));
		assertTrue(blockIds.get(10076).contains("emerald_block"));
		assertTrue(blockIds.get(10097).contains("vine:east=true:north=true:south=true:west=false"));
		assertEquals(List.of("obsidian"), blockIds.get(10011));
		// 10054 was Photon's enchanting table light; 0.4.0 reuses the slot for glowstone. The table's faint purple light
		// moved to 10068 (the brown modded light slot, merged into orange) in 0.6.0.
		assertEquals(List.of("glowstone"), blockIds.get(10054));
		assertEquals(List.of("enchanting_table"), blockIds.get(10068));
		assertTrue(blockIds.get(10070).contains("luminax:brown_luminax_block"), "brown modded lights merged into orange");
		// The 0.6.0 texture audit: Wynncraft wood, and lava cauldrons stay on lava
		assertTrue(blockIds.get(10029).containsAll(List.of("piston", "tnt", "purpur_block", "water_cauldron")));
		assertTrue(blockIds.get(10030).containsAll(List.of("piston_head", "purpur_slab", "iron_door")));
		assertFalse(blockIds.get(10018).contains("iron_door"), "iron doors left transparent metal");
		assertTrue(blockIds.get(10039).contains("lava_cauldron"));
		assertEquals(List.of("magma_block"), blockIds.get(10067));
		assertEquals(List.of("iron_block"), blockIds.get(10099));
		assertTrue(blockIds.get(10098).contains("quartz_block"));
		assertTrue(blockIds.get(10104).contains("repeater:powered=false"));
		assertFalse(blockIds.get(10058).contains("redstone_wire"), "redstone dust has no light");
		assertEquals(List.of("chest", "trapped_chest"), blockIds.get(10105));
		assertFalse(blockIds.get(10030).contains("chest"), "chests left transparent wood");
	}

	/**
	 * Every option on the Wynn-Photon settings page must be defined in settings.glsl and named in the English lang
	 * file, and its strengths must be sliders, or Iris shows a raw or broken entry.
	 */
	@Test
	void wynnSettingsPageIsComplete() throws IOException {
		String properties = Files.readString(shaders.resolve("shaders.properties"));
		String settings = Files.readString(shaders.resolve("settings.glsl"));
		String lang = Files.readString(shaders.resolve("lang/en_US.lang"));
		Matcher screen = Pattern.compile("(?m)^screen\\s*=.*\\[wynn_photon\\]").matcher(properties);
		assertTrue(screen.find(), "main screen links the Wynn-Photon page");
		Matcher page = Pattern.compile("(?m)^screen\\.wynn_photon\\s*=(.*)$").matcher(properties);
		assertTrue(page.find(), "screen.wynn_photon is defined");
		Matcher sliders = Pattern.compile("(?m)^sliders\\s*=(.*)$").matcher(properties);
		assertTrue(sliders.find());
		List<String> sliderList = List.of(sliders.group(1).trim().split("\\s+"));
		assertTrue(lang.contains("screen.wynn_photon "), "page title in en_US.lang");
		List<String> problems = new ArrayList<>();
		for (String option : page.group(1).trim().split("\\s+")) {
			if (option.startsWith("<")) {
				continue;
			}
			if (!Pattern.compile("(?m)^\\s*(//)?#define " + option + "\\b").matcher(settings).find()) {
				problems.add(option + " not in settings.glsl");
			}
			if (!lang.contains("option." + option + " ")) {
				problems.add(option + " not in en_US.lang");
			}
			if (option.endsWith("_I") && !sliderList.contains(option)) {
				problems.add(option + " is not a slider");
			}
		}
		assertTrue(problems.isEmpty(), String.join(", ", problems));
	}
}
