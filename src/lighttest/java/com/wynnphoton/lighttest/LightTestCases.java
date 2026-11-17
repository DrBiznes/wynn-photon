package com.wynnphoton.lighttest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The parsed case list ({@code testworld/light_test.txt}). Its header describes the format. */
final class LightTestCases {
	record Spot(String name, double x, double y, double z, float yaw, float pitch) {}

	record Time(String name, int ticks) {}

	record OptionSet(String name, Map<String, String> options) {}

	record Compare(String a, String b, String title) {}

	final List<Spot> spots = new ArrayList<>();
	final List<Time> times = new ArrayList<>();
	final List<OptionSet> sets = new ArrayList<>();
	final List<Compare> compares = new ArrayList<>();

	static LightTestCases read(Path file) throws IOException {
		LightTestCases cases = new LightTestCases();
		int lineNumber = 0;
		for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			lineNumber++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] p = line.split("\\s+");
			try {
				switch (p[0]) {
					case "spot" -> cases.spots.add(new Spot(p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]),
							Double.parseDouble(p[4]), Float.parseFloat(p[5]), Float.parseFloat(p[6])));
					case "time" -> cases.times.add(new Time(p[1], Integer.parseInt(p[2])));
					case "set" -> {
						Map<String, String> options = new LinkedHashMap<>();
						for (String option : Arrays.copyOfRange(p, 2, p.length)) {
							int eq = option.indexOf('=');
							options.put(option.substring(0, eq), option.substring(eq + 1));
						}
						cases.sets.add(new OptionSet(p[1], options));
					}
					case "compare" -> cases.compares.add(new Compare(p[1], p[2],
							String.join(" ", Arrays.copyOfRange(p, 3, p.length))));
					default -> throw new IllegalArgumentException("unknown keyword " + p[0]);
				}
			} catch (RuntimeException e) {
				throw new IOException(file + ":" + lineNumber + ": " + e, e);
			}
		}
		return cases;
	}

	static String shotName(Spot spot, Time time, OptionSet set) {
		return spot.name() + "__" + time.name() + "__" + set.name() + ".png";
	}
}
