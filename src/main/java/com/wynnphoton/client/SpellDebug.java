package com.wynnphoton.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.wynnphoton.WynnPhotonPreLaunch.LOGGER;

/**
 * Experimental, debug only (the {@code wynn-photon-spell-debug} file in the config folder, see {@link SpellTags}): the
 * spell survey's tooling. Everything goes to {@code wynn-photon-spells/spells-<time>.log} in the game folder, and to
 * the game log with a {@code [spell debug]} prefix.
 *
 * <ul>
 * <li><b>Cast markers.</b> With Wynntils installed, every spell Wynntils sees cast is logged with its name and the
 * class (base or reskin), through Wynntils' {@code SpellEvent.Cast}. Wynntils is reached by reflection and is not
 * needed.</li>
 * <li><b>Captures.</b> A cast, or (without Wynntils, or before its event arrives) a new display entity or armor stand
 * near the player, starts a capture: for as long as entities keep appearing or changing, then {@link #QUIET_TICKS}
 * more, every display entity and armor stand within {@link #RANGE} blocks is logged when it appears, changes (its
 * item, transformation, interpolation and teleport times) and goes. The textures drawn on item models during a
 * capture are logged again, once per capture.</li>
 * <li><b>Replays.</b> Each capture is also written as a datapack function, {@code wynn_spell_capture:c<n>/start},
 * under {@code wynn-photon-spells/datapack}: run it in a world with the Wynncraft resource pack and the capture
 * plays back where you stand, tick by tick, so the spell can be looked at in the dev client.</li>
 * </ul>
 */
public final class SpellDebug {
	private static final double RANGE = 24;
	private static final double AUTO_RANGE = 12;
	private static final int QUIET_TICKS = 60;
	private static final int MAX_TICKS = 600;
	/** A Wynntils cast this soon after an automatic capture started names that capture. */
	private static final int RENAME_TICKS = 20;

	/** NBT keys of a saved entity that a replay doesn't want. */
	private static final Set<String> VOLATILE_KEYS = Set.of("Pos", "Motion", "UUID", "OnGround", "fall_distance",
			"FallDistance", "Fire", "Air", "PortalCooldown", "Invulnerable", "HasVisualFire", "Tags", "Rotation",
			"Brain", "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount", "attributes",
			"active_effects", "FallFlying", "sleeping_pos");

	private static PrintWriter out;
	private static Path folder;
	private static boolean wynntilsHooked;
	private static int wynntilsTries;

	private static long tick;
	/** The level the player was in last tick, and when they arrived, so joining a world starts no capture. */
	private static Object lastLevel;
	private static long arrived;
	private static final int ARRIVAL_TICKS = 100;
	/** Entities seen near the player, so a new one can start a capture. */
	private static final Set<Integer> known = new HashSet<>();

	private static Capture capture;
	private static int captures;

	private SpellDebug() {
	}

	private static final class Capture {
		final int number;
		String label;
		final long start;
		final Vec3 anchor;
		long lastChange;
		final Map<Integer, Tracked> entities = new LinkedHashMap<>();
		final Set<Identifier> textures = new HashSet<>();
		/** Replay commands per tick since the start, run at the anchor. */
		final Map<Integer, List<String>> commands = new HashMap<>();
		int nextIndex;

		Capture(int number, String label, long start, Vec3 anchor) {
			this.number = number;
			this.label = label;
			this.start = start;
			this.anchor = anchor;
			this.lastChange = start;
		}

		void command(String command) {
			commands.computeIfAbsent((int) (tick - start), k -> new ArrayList<>()).add(command);
		}

		String tag(int index) {
			return "wsc_" + number + "_" + index;
		}
	}

	private static final class Tracked {
		final int index;
		CompoundTag data;
		Vec3 pos;
		float yRot, xRot;

		Tracked(int index) {
			this.index = index;
		}
	}

	public static boolean isCapturing() {
		return capture != null;
	}

	/** True the first time the texture is seen in the current capture. */
	static boolean seenInCapture(Identifier texture) {
		return capture != null && capture.textures.add(texture);
	}

	/** Writes a line to the spell log and the game log. */
	static synchronized void log(String line) {
		String stamped = capture != null
				? String.format(Locale.ROOT, "[c%d +%d] %s", capture.number, tick - capture.start, line)
				: line;
		LOGGER.info("[spell debug] {}", stamped);
		if (out == null && folder == null) {
			open();
		}
		if (out != null) {
			out.println(stamped);
			out.flush();
		}
	}

	private static void open() {
		try {
			folder = Minecraft.getInstance().gameDirectory.toPath().resolve("wynn-photon-spells");
			Files.createDirectories(folder);
			String name = "spells-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
					+ ".log";
			out = new PrintWriter(Files.newBufferedWriter(folder.resolve(name), StandardCharsets.UTF_8));
			LOGGER.info("[spell debug] Logging spells to {}", folder.resolve(name));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[spell debug] Could not open the spell log: {}", e.toString());
		}
	}

	/** Called at the end of every client tick. */
	public static void tick(Minecraft minecraft) {
		if (!SpellTags.DEBUG) {
			return;
		}
		tick++;
		if (!wynntilsHooked && wynntilsTries < 20 && tick % 100 == 0) {
			hookWynntils();
		}
		if (minecraft.level == null || minecraft.player == null) {
			known.clear();
			lastLevel = null;
			if (capture != null) {
				finish();
			}
			return;
		}
		if (minecraft.level != lastLevel) {
			lastLevel = minecraft.level;
			arrived = tick;
			known.clear();
		}
		Vec3 player = minecraft.player.position();
		Set<Integer> present = new HashSet<>();
		List<Entity> nearby = new ArrayList<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof Display || entity instanceof ArmorStand)) {
				continue;
			}
			double distance = entity.position().distanceTo(player);
			if (distance > RANGE) {
				continue;
			}
			present.add(entity.getId());
			nearby.add(entity);
			if (capture == null && !known.contains(entity.getId()) && distance <= AUTO_RANGE
					&& tick - arrived > ARRIVAL_TICKS) {
				start("auto (" + EntityType.getKey(entity.getType()).getPath() + " appeared)", player);
			}
		}
		known.retainAll(present);
		known.addAll(present);

		if (capture == null) {
			return;
		}
		for (Entity entity : nearby) {
			track(entity);
		}
		for (var iterator = capture.entities.entrySet().iterator(); iterator.hasNext(); ) {
			var entry = iterator.next();
			if (!present.contains(entry.getKey())) {
				Tracked tracked = entry.getValue();
				log("gone #" + tracked.index);
				capture.command("kill @e[tag=" + capture.tag(tracked.index) + "]");
				capture.lastChange = tick;
				iterator.remove();
			}
		}
		if (tick - capture.lastChange > QUIET_TICKS || tick - capture.start > MAX_TICKS) {
			finish();
		}
	}

	private static void start(String label, Vec3 anchor) {
		captures++;
		capture = new Capture(captures, label, tick, anchor);
		log(String.format(Locale.ROOT, "capture %d started: %s, player at %.2f %.2f %.2f", captures, label, anchor.x,
				anchor.y, anchor.z));
	}

	private static void track(Entity entity) {
		Tracked tracked = capture.entities.get(entity.getId());
		CompoundTag data = save(entity);
		InterpolationHandler interpolation = entity.getInterpolation();
		boolean moving = interpolation != null && interpolation.hasActiveInterpolation();
		Vec3 pos = moving ? interpolation.position() : entity.position();
		float yRot = moving ? interpolation.yRot() : entity.getYRot();
		float xRot = moving ? interpolation.xRot() : entity.getXRot();
		Vec3 rel = pos.subtract(capture.anchor);
		String type = EntityType.getKey(entity.getType()).toString();

		if (tracked == null) {
			tracked = new Tracked(capture.nextIndex++);
			capture.entities.put(entity.getId(), tracked);
			log(String.format(Locale.ROOT, "new #%d %s at %+.2f %+.2f %+.2f rot %.1f %.1f %s", tracked.index, type,
					rel.x, rel.y, rel.z, yRot, xRot, describe(data)));
			CompoundTag summon = data.copy();
			ListTag tags = new ListTag();
			tags.add(StringTag.valueOf("wsc"));
			tags.add(StringTag.valueOf("wsc_" + capture.number));
			tags.add(StringTag.valueOf(capture.tag(tracked.index)));
			summon.put("Tags", tags);
			ListTag rotation = new ListTag();
			rotation.add(net.minecraft.nbt.FloatTag.valueOf(yRot));
			rotation.add(net.minecraft.nbt.FloatTag.valueOf(xRot));
			summon.put("Rotation", rotation);
			capture.command(String.format(Locale.ROOT, "summon %s %s %s", type, relative(rel), summon));
			capture.lastChange = tick;
		} else {
			CompoundTag changed = new CompoundTag();
			for (String key : data.keySet()) {
				Tag value = data.get(key);
				if (!value.equals(tracked.data.get(key))) {
					changed.put(key, value.copy());
				}
			}
			if (!changed.isEmpty()) {
				// A new transformation only interpolates when the start is sent again. Entities don't save it, and
				// Wynncraft, like commands, starts each interpolation at once
				if (changed.contains("transformation") && entity instanceof Display) {
					changed.putInt("start_interpolation", 0);
				}
				log(String.format(Locale.ROOT, "change #%d %s", tracked.index, describe(changed)));
				capture.command("data merge entity @e[tag=" + capture.tag(tracked.index) + ",limit=1] " + changed);
				capture.lastChange = tick;
			}
			if (pos.distanceToSqr(tracked.pos) > 1e-6 || yRot != tracked.yRot || xRot != tracked.xRot) {
				log(String.format(Locale.ROOT, "move #%d to %+.2f %+.2f %+.2f rot %.1f %.1f", tracked.index, rel.x,
						rel.y, rel.z, yRot, xRot));
				capture.command(String.format(Locale.ROOT, "tp @e[tag=%s,limit=1] %s %.2f %.2f",
						capture.tag(tracked.index), relative(rel), yRot, xRot));
				capture.lastChange = tick;
			}
		}
		tracked.data = data;
		tracked.pos = pos;
		tracked.yRot = yRot;
		tracked.xRot = xRot;
	}

	private static String relative(Vec3 rel) {
		return String.format(Locale.ROOT, "~%.3f ~%.3f ~%.3f", rel.x, rel.y, rel.z);
	}

	/** The entity's saved data without what a replay doesn't want (position, UUID, health, ...). */
	private static CompoundTag save(Entity entity) {
		try {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
					entity.registryAccess());
			entity.saveWithoutId(output);
			CompoundTag tag = output.buildResult();
			for (String key : VOLATILE_KEYS) {
				tag.remove(key);
			}
			return tag;
		} catch (RuntimeException e) {
			CompoundTag tag = new CompoundTag();
			tag.putString("wynn_photon_error", e.toString());
			return tag;
		}
	}

	/** The interesting part of an entity's data for the log: its item (with custom_model_data) and the rest. */
	private static String describe(CompoundTag data) {
		String text = data.toString();
		return text.length() > 1500 ? text.substring(0, 1500) + "..." : text;
	}

	private static void finish() {
		Capture done = capture;
		log(String.format(Locale.ROOT, "capture %d ended after %d ticks: %s, %d entities, %d textures", done.number,
				tick - done.start, done.label, done.nextIndex, done.textures.size()));
		capture = null;
		writeReplay(done);
	}

	/** Writes the capture as wynn_spell_capture:c<n>/start (and one function per tick) in the replay datapack. */
	private static void writeReplay(Capture done) {
		if (folder == null) {
			return;
		}
		try {
			Path pack = folder.resolve("datapack");
			Files.createDirectories(pack);
			Path meta = pack.resolve("pack.mcmeta");
			if (!Files.exists(meta)) {
				Files.writeString(meta, "{\"pack\":{\"description\":\"Wynn-Photon spell captures\","
						+ "\"min_format\":[94,1],\"max_format\":94}}\n");
			}
			String dir = "c" + done.number;
			Path functions = pack.resolve("data/wynn_spell_capture/function/" + dir);
			Files.createDirectories(functions);
			String anchor = "wsc_anchor_" + done.number;
			List<String> start = new ArrayList<>();
			start.add("# Capture " + done.number + ": " + done.label);
			start.add("kill @e[tag=wsc_" + done.number + "]");
			start.add("kill @e[tag=" + anchor + "]");
			start.add("summon marker ~ ~ ~ {Tags:[\"wsc\",\"wsc_" + done.number + "\",\"" + anchor + "\"]}");
			int last = 0;
			for (var entry : done.commands.entrySet()) {
				int t = entry.getKey();
				last = Math.max(last, t);
				List<String> lines = new ArrayList<>();
				for (String command : entry.getValue()) {
					lines.add("execute at @e[tag=" + anchor + ",limit=1] run " + command);
				}
				Files.write(functions.resolve("t" + t + ".mcfunction"), lines, StandardCharsets.UTF_8);
				if (t == 0) {
					start.add("function wynn_spell_capture:" + dir + "/t0");
				} else {
					start.add("schedule function wynn_spell_capture:" + dir + "/t" + t + " " + t + "t append");
				}
			}
			start.add("schedule function wynn_spell_capture:" + dir + "/stop " + (last + 40) + "t append");
			Files.write(functions.resolve("start.mcfunction"), start, StandardCharsets.UTF_8);
			Files.write(functions.resolve("stop.mcfunction"), List.of("kill @e[tag=wsc_" + done.number + "]"),
					StandardCharsets.UTF_8);
			log("replay: /function wynn_spell_capture:" + dir + "/start (datapack in " + pack + ")");
		} catch (IOException | RuntimeException e) {
			log("could not write the replay: " + e);
		}
	}

	/** Called by Wynntils (through {@link #hookWynntils}) on every spell cast it sees. */
	private static void onCast(String spell) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.player == null) {
				return;
			}
			if (capture != null && tick - capture.start <= RENAME_TICKS && capture.label.startsWith("auto")) {
				capture.label = spell;
				log("cast " + spell + " (names this capture)");
			} else {
				log("cast " + spell);
				if (capture == null) {
					start(spell, minecraft.player.position());
				}
			}
		});
	}

	/**
	 * Listens to Wynntils' {@code SpellEvent.Cast} by reflection: Wynntils keeps its NeoForge event bus in a private
	 * static field of {@code WynntilsMod}, which is set when Wynntils starts, so this is retried for a while.
	 */
	private static void hookWynntils() {
		wynntilsTries++;
		try {
			Class<?> mod = Class.forName("com.wynntils.core.WynntilsMod");
			Field field = mod.getDeclaredField("eventBus");
			field.setAccessible(true);
			Object bus = field.get(null);
			if (bus == null) {
				return; // Not started yet
			}
			Class<?> busType = Class.forName("net.neoforged.bus.api.IEventBus");
			Class<?> castType = Class.forName("com.wynntils.models.spells.event.SpellEvent$Cast");
			Method addListener = busType.getMethod("addListener", Class.class, Consumer.class);
			Consumer<Object> listener = SpellDebug::onWynntilsCast;
			addListener.invoke(bus, castType, listener);
			wynntilsHooked = true;
			log("listening to Wynntils' spell casts");
		} catch (ClassNotFoundException e) {
			wynntilsTries = Integer.MAX_VALUE; // No Wynntils
			log("no Wynntils: captures start when a display entity appears near the player");
		} catch (ReflectiveOperationException | RuntimeException e) {
			log("could not listen to Wynntils' spell casts: " + e);
		}
	}

	private static void onWynntilsCast(Object event) {
		String spell;
		try {
			Object type = event.getClass().getMethod("getSpellType").invoke(event);
			spell = (String) type.getClass().getMethod("getName").invoke(type);
		} catch (ReflectiveOperationException | RuntimeException e) {
			spell = "a spell (" + e + ")";
		}
		onCast(spell + " as " + className());
	}

	/** The player's class as Wynntils knows it, with the reskin's name ("Dark Wizard"), or "?". */
	private static String className() {
		try {
			Object character = Class.forName("com.wynntils.core.components.Models").getField("Character").get(null);
			boolean reskinned = (boolean) character.getClass().getMethod("isReskinned").invoke(character);
			Object classType = character.getClass().getMethod("getClassType").invoke(character);
			String name = (String) classType.getClass().getMethod("getActualName", boolean.class)
					.invoke(classType, reskinned);
			return name + (reskinned ? " (reskin)" : " (base)");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "?";
		}
	}
}
