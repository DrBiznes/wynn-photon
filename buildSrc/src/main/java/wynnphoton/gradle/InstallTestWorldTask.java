package wynnphoton.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Installs the Wynn-Photon test world into a Minecraft instance's {@code saves/}.
 *
 * <ul>
 *   <li>Writes a creative superflat {@code level.dat} the first time (or with {@code fresh}). It is written in the
 *       1.21.1 format and Minecraft upgrades it on first load, which is sturdier than guessing the newest format.</li>
 *   <li>Replaces the gallery datapack every time. The datapack rebuilds the gallery whenever the world loads.</li>
 *   <li>Copies the Wynncraft server resource pack to the world's {@code resources.zip}, which Minecraft offers to
 *       load when the world opens. It takes the newest one Minecraft cached in {@code downloads/} when joining
 *       Wynncraft, from the target instance or else from {@link #getPackSourceInstances()}.</li>
 *   <li>Points Iris at the dev shader pack.</li>
 * </ul>
 *
 * <p>With {@code voidWorld} it makes the map world instead: a superflat world of air, in spectator mode, with no
 * datapack, and a copy of a Wynncraft Voxy cache ({@code voxyCache}) as the world's own Voxy storage, so Voxy draws
 * Wynncraft's LODs around the player. The cache is only ever read, and copied again only with {@code fresh}.</p>
 *
 * It only ever deletes or overwrites a world folder that carries its own marker file.
 */
@DisableCachingByDefault(because = "Writes into a Minecraft instance outside the build")
public abstract class InstallTestWorldTask extends DefaultTask {
	private static final String MARKER = "wynn-photon-test-world.txt";
	private static final String DATAPACK_NAME = "wynn_photon_test";

	/** The Minecraft instance folder, the one that holds {@code saves/}, {@code config/} and {@code downloads/}. */
	@Internal
	public abstract DirectoryProperty getInstanceDir();

	@InputDirectory
	@Optional
	public abstract DirectoryProperty getDatapackDir();

	/** A world of air in spectator mode, for the Voxy map world. */
	@Input
	@Optional
	public abstract Property<Boolean> getVoidWorld();

	/** A closed Voxy world storage (the folder holding {@code CURRENT}) to install as the world's overworld LODs. */
	@Internal
	public abstract DirectoryProperty getVoxyCache();

	/** Other Minecraft instances to take the cached Wynncraft resource pack from, tried in order. */
	@Internal
	public abstract ConfigurableFileCollection getPackSourceInstances();

	@Input
	public abstract Property<String> getWorldName();

	/** The shader pack folder name Iris should select. */
	@Input
	public abstract Property<String> getShaderPack();

	/** Delete the world first (including anything built or changed in it) and write a new level.dat. */
	@Input
	public abstract Property<Boolean> getFresh();

	@TaskAction
	public void install() throws IOException {
		Path instance = getInstanceDir().get().getAsFile().toPath();
		Path world = instance.resolve("saves").resolve(getWorldName().get());

		if (Files.exists(world) && !Files.exists(world.resolve(MARKER))) {
			throw new GradleException(world + " exists but was not made by installTestWorld. Rename it or pick another worldName.");
		}
		if (getFresh().get() && Files.exists(world)) {
			getLogger().lifecycle("Deleting {}", world);
			deleteRecursively(world);
		}
		Files.createDirectories(world);
		Files.writeString(world.resolve(MARKER),
				"Made by the Wynn-Photon Gradle task installTestWorld. It may delete or overwrite this world.\n");

		boolean voidWorld = getVoidWorld().getOrElse(false);
		Path levelDat = world.resolve("level.dat");
		if (!Files.exists(levelDat)) {
			writeLevelDat(levelDat, getWorldName().get(), voidWorld, getDatapackDir().isPresent());
			getLogger().lifecycle("Wrote a new level.dat");
		}

		if (getDatapackDir().isPresent()) {
			Path datapack = world.resolve("datapacks").resolve(DATAPACK_NAME);
			if (Files.exists(datapack)) {
				deleteRecursively(datapack);
			}
			copyRecursively(getDatapackDir().get().getAsFile().toPath(), datapack);
		}
		if (getVoxyCache().isPresent()) {
			installVoxyCache(getVoxyCache().get().getAsFile().toPath(), world);
		}

		installResourcePack(instance, world);
		selectShaderPack(instance);

		getLogger().lifecycle("Installed the test world at {}", world);
	}

	// ------------------------------------------------------------------ Voxy

	/**
	 * Copies a Voxy storage to {@code <world>/voxy/<id>/storage}, the place Voxy looks for this world's overworld,
	 * with the storage's {@code config.json} beside it. Skipped if it is there already.
	 */
	private void installVoxyCache(Path storage, Path world) throws IOException {
		Path target = world.resolve("voxy").resolve(voxyWorldId(SEED, "minecraft:overworld")).resolve("storage");
		if (Files.exists(target.resolve("CURRENT"))) {
			getLogger().lifecycle("Voxy LODs already installed ({})", target.getParent().getFileName());
			return;
		}
		if (!Files.isRegularFile(storage.resolve("CURRENT"))) {
			throw new GradleException(storage + " is not a Voxy storage (no CURRENT file). Set voxyCache in "
					+ "local.properties to <instance>/.voxy/saves/play.wynncraft.com/<world id>/storage.");
		}
		Path lock = storage.resolve("LOCK");
		if (Files.exists(lock)) {
			try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.WRITE);
				 FileLock held = channel.tryLock()) {
				if (held == null) {
					throw new GradleException("The Voxy cache is in use. Close Minecraft first: " + storage);
				}
			} catch (IOException e) {
				throw new GradleException("The Voxy cache is in use. Close Minecraft first: " + storage, e);
			}
		}
		getLogger().lifecycle("Copying the Voxy LODs from {}", storage);
		Files.createDirectories(target);
		try (Stream<Path> files = Files.list(storage)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				String name = file.getFileName().toString();
				if (Files.isRegularFile(file) && !name.equals("LOCK") && !name.startsWith("LOG.old.")) {
					Files.copy(file, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		Path config = storage.getParent().getParent().resolve("config.json");
		if (Files.isRegularFile(config)) {
			Files.copy(config, world.resolve("voxy").resolve("config.json"), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Voxy's folder name for a world: the first 32 hex digits of the SHA-256 of the biome seed (Minecraft's
	 * obfuscated seed) followed by the dimension's resource key, as Voxy's {@code WorldIdentifier.getWorldId} writes.
	 */
	static String voxyWorldId(long seed, String dimension) {
		try {
			ByteBuffer seedBytes = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(seed);
			byte[] hashed = MessageDigest.getInstance("SHA-256").digest(seedBytes.array());
			long biomeSeed = ByteBuffer.wrap(hashed).order(ByteOrder.LITTLE_ENDIAN).getLong();
			String key = biomeSeed + "ResourceKey[minecraft:dimension / " + dimension + "]";
			byte[] id = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(id).substring(0, 32);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------ resource pack

	private static final Pattern LOG_FILE = Pattern.compile("\"url\":\"https://rp-cdn\\.wynncraft\\.com/[^\"]*\".*?\"file\":\\{\"name\":\"([^\"]+)\"");
	private static final Pattern LOG_FILE_REVERSED = Pattern.compile("\"file\":\\{\"name\":\"([^\"]+)\".*?\"url\":\"https://rp-cdn\\.wynncraft\\.com/");

	/** Copies the newest Wynncraft server pack Minecraft cached in {@code downloads/} to the world's resources.zip. */
	private void installResourcePack(Path instance, Path world) throws IOException {
		List<Path> instances = new ArrayList<>();
		instances.add(instance);
		getPackSourceInstances().getFiles().forEach(f -> instances.add(f.toPath()));
		for (Path candidate : instances) {
			Path pack = newestWynncraftPack(candidate);
			if (pack != null) {
				copyResourcePack(pack, world);
				return;
			}
		}
		Path existing = world.resolve("resources.zip");
		if (Files.exists(existing)) {
			getLogger().warn("No cached Wynncraft resource pack found, so the world keeps the one it has.");
		} else {
			getLogger().warn("No cached Wynncraft resource pack found in {}. The world works without it, but blocks look vanilla. "
					+ "Join Wynncraft once with one of those Minecraft instances, or set wynncraftInstanceDir in local.properties.", instances);
		}
	}

	/** The newest Wynncraft server pack Minecraft cached in an instance's downloads/, or null. */
	private static Path newestWynncraftPack(Path instance) throws IOException {
		Path log = instance.resolve("downloads").resolve("log.json");
		if (!Files.isRegularFile(log)) {
			return null;
		}
		String newest = null;
		for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
			Matcher m = LOG_FILE.matcher(line);
			Matcher r = LOG_FILE_REVERSED.matcher(line);
			if (m.find()) {
				newest = m.group(1);
			} else if (r.find()) {
				newest = r.group(1);
			}
		}
		if (newest == null) {
			return null;
		}
		Path source = instance.resolve("downloads").resolve(newest.replace("\\\\", "/"));
		return Files.isRegularFile(source) ? source : null;
	}

	private void copyResourcePack(Path source, Path world) throws IOException {
		Path target = world.resolve("resources.zip");
		if (!Files.exists(target) || Files.size(target) != Files.size(source)) {
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
		getLogger().lifecycle("Resource pack: {}", source.getFileName());
	}

	// ------------------------------------------------------------------ Iris

	private void selectShaderPack(Path instance) throws IOException {
		String pack = getShaderPack().get();
		if (!Files.isDirectory(instance.resolve("shaderpacks").resolve(pack))) {
			throw new GradleException("Shader pack " + pack + " is not installed. Run installDevPack first.");
		}
		Path iris = instance.resolve("config").resolve("iris.properties");
		List<String> lines = Files.exists(iris) ? new ArrayList<>(Files.readAllLines(iris, StandardCharsets.UTF_8)) : new ArrayList<>();
		String previous = set(lines, "shaderPack", pack);
		set(lines, "enableShaders", "true");
		Files.createDirectories(iris.getParent());
		Files.write(iris, lines, StandardCharsets.UTF_8);
		if (previous != null && !pack.equals(previous)) {
			getLogger().lifecycle("Iris now uses {} (was {}). Switch back in Iris's shader menu when you are done.", pack, previous);
		}
	}

	/** Sets {@code key=value} in a properties file's lines and returns the old value, or null. */
	private static String set(List<String> lines, String key, String value) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.startsWith(key + "=")) {
				lines.set(i, key + "=" + value);
				return line.substring(key.length() + 1);
			}
		}
		lines.add(key + "=" + value);
		return null;
	}

	// ------------------------------------------------------------------ level.dat

	private static final int DATA_VERSION_1_21_1 = 3955;
	private static final long SEED = 0L;

	private static void writeLevelDat(Path file, String name, boolean voidWorld, boolean datapack) throws IOException {
		Map<String, Object> gameRules = new LinkedHashMap<>();
		// 1.21.1 names; Minecraft's data fixers rename them for newer versions.
		gameRules.put("doDaylightCycle", "false");
		gameRules.put("doWeatherCycle", "false");
		gameRules.put("doMobSpawning", "false");
		gameRules.put("doPatrolSpawning", "false");
		gameRules.put("doTraderSpawning", "false");
		gameRules.put("doWardenSpawning", "false");
		gameRules.put("doInsomnia", "false");
		gameRules.put("doFireTick", "false");
		gameRules.put("mobGriefing", "false");
		gameRules.put("doVinesSpread", "false");
		// No random ticks: crops keep their age, farmland stays wet, vines don't spread
		gameRules.put("randomTickSpeed", "0");
		gameRules.put("spawnRadius", "0");

		Map<String, Object> flat = new LinkedHashMap<>();
		flat.put("biome", "minecraft:plains");
		flat.put("features", (byte) 0);
		flat.put("lakes", (byte) 0);
		flat.put("layers", voidWorld
				? List.of(layer("minecraft:air", 1))
				: List.of(layer("minecraft:bedrock", 1), layer("minecraft:dirt", 2), layer("minecraft:grass_block", 1)));
		flat.put("structure_overrides", List.of());

		Map<String, Object> dimensions = new LinkedHashMap<>();
		dimensions.put("minecraft:overworld", Map.of(
				"type", "minecraft:overworld",
				"generator", Map.of("type", "minecraft:flat", "settings", flat)));
		dimensions.put("minecraft:the_nether", Map.of(
				"type", "minecraft:the_nether",
				"generator", Map.of("type", "minecraft:noise", "settings", "minecraft:nether",
						"biome_source", Map.of("type", "minecraft:multi_noise", "preset", "minecraft:nether"))));
		dimensions.put("minecraft:the_end", Map.of(
				"type", "minecraft:the_end",
				"generator", Map.of("type", "minecraft:noise", "settings", "minecraft:end",
						"biome_source", Map.of("type", "minecraft:the_end"))));

		Map<String, Object> worldGen = new LinkedHashMap<>();
		worldGen.put("seed", SEED);
		worldGen.put("generate_features", (byte) 0);
		worldGen.put("bonus_chest", (byte) 0);
		worldGen.put("dimensions", dimensions);

		Map<String, Object> version = new LinkedHashMap<>();
		version.put("Id", DATA_VERSION_1_21_1);
		version.put("Name", "1.21.1");
		version.put("Series", "main");
		version.put("Snapshot", (byte) 0);

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("DataVersion", DATA_VERSION_1_21_1);
		data.put("version", 19133);
		data.put("Version", version);
		data.put("LevelName", name);
		data.put("GameType", voidWorld ? 3 : 1); // spectator in the air, else creative
		data.put("Difficulty", (byte) 0); // peaceful
		data.put("DifficultyLocked", (byte) 0);
		data.put("hardcore", (byte) 0);
		data.put("allowCommands", (byte) 1);
		data.put("initialized", (byte) 1);
		data.put("Time", 0L);
		data.put("DayTime", 6000L); // noon
		data.put("LastPlayed", System.currentTimeMillis());
		data.put("raining", (byte) 0);
		data.put("thundering", (byte) 0);
		data.put("clearWeatherTime", 1_000_000);
		if (voidWorld) {
			// South of Wynncraft's Mount Wynn, facing it
			data.put("SpawnX", -183);
			data.put("SpawnY", 240);
			data.put("SpawnZ", -1764);
			data.put("SpawnAngle", 180.0f);
		} else {
			// Between the daylight row (north, z = -8) and the dark hall (south, z = 16), facing north
			data.put("SpawnX", 30);
			data.put("SpawnY", -60);
			data.put("SpawnZ", 4);
			data.put("SpawnAngle", 180.0f);
		}
		data.put("GameRules", gameRules);
		data.put("DataPacks", Map.of("Enabled", datapack ? List.of("vanilla", "file/" + DATAPACK_NAME) : List.of("vanilla"),
				"Disabled", List.of()));
		data.put("WorldGenSettings", worldGen);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeByte(10);
			out.writeUTF("");
			writeCompound(out, Map.of("Data", data));
		}
		try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
			out.write(bytes.toByteArray());
		}
	}

	private static Map<String, Object> layer(String block, int height) {
		Map<String, Object> layer = new LinkedHashMap<>();
		layer.put("block", block);
		layer.put("height", height);
		return layer;
	}

	// Minimal NBT writer: byte, int, long, float, string, list and compound are all level.dat needs.
	private static int tagType(Object value) {
		if (value instanceof Byte) return 1;
		if (value instanceof Integer) return 3;
		if (value instanceof Long) return 4;
		if (value instanceof Float) return 5;
		if (value instanceof String) return 8;
		if (value instanceof List) return 9;
		if (value instanceof Map) return 10;
		throw new IllegalArgumentException("No NBT type for " + value.getClass());
	}

	@SuppressWarnings("unchecked")
	private static void writePayload(DataOutputStream out, Object value) throws IOException {
		switch (tagType(value)) {
			case 1 -> out.writeByte((Byte) value);
			case 3 -> out.writeInt((Integer) value);
			case 4 -> out.writeLong((Long) value);
			case 5 -> out.writeFloat((Float) value);
			case 8 -> out.writeUTF((String) value);
			case 9 -> {
				List<Object> list = (List<Object>) value;
				out.writeByte(list.isEmpty() ? 0 : tagType(list.get(0)));
				out.writeInt(list.size());
				for (Object item : list) {
					writePayload(out, item);
				}
			}
			case 10 -> writeCompound(out, (Map<String, Object>) value);
			default -> throw new IllegalStateException();
		}
	}

	private static void writeCompound(DataOutputStream out, Map<String, Object> compound) throws IOException {
		for (Map.Entry<String, Object> entry : compound.entrySet()) {
			out.writeByte(tagType(entry.getValue()));
			out.writeUTF(entry.getKey());
			writePayload(out, entry.getValue());
		}
		out.writeByte(0);
	}

	// ------------------------------------------------------------------ files

	private static void copyRecursively(Path from, Path to) throws IOException {
		try (Stream<Path> files = Files.walk(from)) {
			for (Path source : (Iterable<Path>) files::iterator) {
				Path target = to.resolve(from.relativize(source).toString());
				if (Files.isDirectory(source)) {
					Files.createDirectories(target);
				} else {
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteRecursively(Path dir) throws IOException {
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}
}
