package com.wynnphoton.lighttest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A parsed case list ({@code testworld/light_test.txt}, {@code sky_test.txt}, {@code map_test.txt}). The header of
 * {@code light_test.txt} describes the format.
 */
final class LightTestCases {
	/** The worlds a case file can be shot in: the gallery test world or the Wynncraft map world (Voxy LODs). */
	enum World { GALLERY, MAP }

	/**
	 * A camera. {@code aim} is a yaw in degrees, or {@code sun} or {@code moon} to look straight at it, in which case
	 * {@code pitch} is added to the pitch that does (positive looks lower).
	 */
	record Spot(String name, double x, double y, double z, String aim, float pitch, Map<String, String> options) {
		boolean aimed() {
			return aim.equals("sun") || aim.equals("moon");
		}

		float yaw() {
			return Float.parseFloat(aim);
		}

		/** Field of view in degrees, or 0 for the player's own. */
		int fov() {
			return Integer.parseInt(options.getOrDefault("fov", "0"));
		}

		String biome() {
			return options.get("biome");
		}

		long settleMillis(long fallback) {
			return Long.parseLong(options.getOrDefault("settle", Long.toString(fallback)));
		}

		boolean appliesTo(Time time, OptionSet set) {
			return listed("times", time.name()) && listed("sets", set.name());
		}

		private boolean listed(String key, String name) {
			String list = options.get(key);
			return list == null || Set.of(list.split(",")).contains(name);
		}
	}

	/** {@code flows}: the daylight cycle runs while the time is shot (it stands still otherwise). */
	record Time(String name, int ticks, String weather, boolean flows) {}

	record OptionSet(String name, Map<String, String> options) {}

	record Compare(String a, String b, String title) {}

	private static final Set<String> SPOT_OPTIONS = Set.of("fov", "times", "sets", "biome", "settle");
	private static final Set<String> WEATHERS = Set.of("clear", "rain", "thunder");

	World world = World.GALLERY;
	/** The vanilla render distance in chunks for the run, or 0 for the player's own. */
	int renderDistance;
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
					case "world" -> cases.world = World.valueOf(p[1].toUpperCase(Locale.ROOT));
					case "renderdistance" -> cases.renderDistance = Integer.parseInt(p[1]);
					case "spot" -> {
						Map<String, String> options = keyValues(p, 7);
						for (String key : options.keySet()) {
							if (!SPOT_OPTIONS.contains(key)) {
								throw new IllegalArgumentException("unknown spot option " + key);
							}
						}
						Spot spot = new Spot(p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]),
								Double.parseDouble(p[4]), p[5], Float.parseFloat(p[6]), options);
						if (!spot.aimed()) {
							spot.yaw();
						}
						cases.spots.add(spot);
					}
					case "time" -> {
						Map<String, String> timeOptions = keyValues(p, 3);
						String weather = timeOptions.getOrDefault("weather", "clear");
						if (!WEATHERS.contains(weather)) {
							throw new IllegalArgumentException("unknown weather " + weather);
						}
						cases.times.add(new Time(p[1], Integer.parseInt(p[2]), weather,
								"on".equals(timeOptions.get("flow"))));
					}
					case "set" -> cases.sets.add(new OptionSet(p[1], keyValues(p, 2)));
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

	/** {@code KEY=value} words from index {@code from} on. */
	private static Map<String, String> keyValues(String[] words, int from) {
		Map<String, String> values = new LinkedHashMap<>();
		for (String word : Arrays.copyOfRange(words, Math.min(from, words.length), words.length)) {
			int eq = word.indexOf('=');
			if (eq <= 0) {
				throw new IllegalArgumentException("expected KEY=value, got " + word);
			}
			values.put(word.substring(0, eq), word.substring(eq + 1));
		}
		return values;
	}

	static String shotName(Spot spot, Time time, OptionSet set) {
		return spot.name() + "__" + time.name() + "__" + set.name() + ".png";
	}
}
