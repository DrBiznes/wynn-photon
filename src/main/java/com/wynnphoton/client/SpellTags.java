package com.wynnphoton.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.wynnphoton.SpellTable;
import com.wynnphoton.client.mixin.SpriteContentsAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.zip.CRC32;

import static com.wynnphoton.WynnPhotonPreLaunch.LOGGER;

/**
 * Experimental: tags Wynncraft spell models for the shader pack.
 *
 * <p>Wynncraft draws its spell models (Meteor, Ice Snake, Totem, ...) as items on display entities. They are all
 * {@code minecraft:potion}, {@code diamond_sword} or {@code oak_boat} told apart by {@code custom_model_data}, which Iris's
 * {@code item.properties} can't match, so the shader can't tell a meteor from a potion. Each spell has its own
 * textures, though: a few named ones ({@code wynn/spell/...}), and hashed ones ({@code item/w<n>}) for the rigs of
 * small {@code oak_boat} parts that most spells are, which are matched by a hash of their pixels. The spell table
 * ({@link SpellTable}) lists them. When an item layer is submitted, this looks at its quads' sprites, and if one is a
 * known spell texture it overrides the item ID Iris hands the shader ({@code currentRenderedItemId}) with the spell
 * kind's ID. The shader then makes the spell glow and lights the area around it.
 *
 * <p>Iris is reached by reflection, as everywhere else in the mod: without Iris this does nothing.
 */
public final class SpellTags {
	/**
	 * Debug: with a file named {@code wynn-photon-spell-debug} in the config folder, every texture drawn on an item
	 * model within {@link #DEBUG_RANGE} blocks of the camera is logged once, and again in each capture after a cast
	 * ({@link SpellDebug}), so a spell's textures can be found by casting it and reading the log. The spell table's
	 * candidates are used too. Declared first: the table needs it.
	 */
	static final boolean DEBUG = Files.exists(
			FabricLoader.getInstance().getConfigDir().resolve("wynn-photon-spell-debug"));
	private static final float DEBUG_RANGE = 32;
	private static final Set<Identifier> DEBUG_SEEN = new HashSet<>();

	/** The spell table ({@link SpellTable#RESOURCE}); empty if it can't be read. */
	private static final SpellTable TABLE = loadTable();

	/** Each sprite's item ID (0 for none), worked out once. Weak, so reloaded atlases drop the old sprites. */
	private static final Map<SpriteContents, Integer> CACHE = new WeakHashMap<>();

	private static final Object NOT_LOOKED_UP = new Object();
	private static Object capturedState = NOT_LOOKED_UP;
	private static MethodHandle setCurrentRenderedItem;

	private SpellTags() {
	}

	/** The shader ID of the first spell texture among {@code quads}, or 0. */
	public static int spellId(List<BakedQuad> quads) {
		TextureAtlasSprite last = null;
		for (int i = 0, n = quads.size(); i < n; i++) {
			TextureAtlasSprite sprite = quads.get(i).sprite();
			if (sprite == last) {
				continue; // Quads of one element share a sprite
			}
			last = sprite;
			int id = spriteId(sprite.contents());
			if (id != 0) {
				return id;
			}
		}
		return 0;
	}

	private static SpellTable loadTable() {
		try {
			SpellTable table = SpellTable.loadFromClasspath(DEBUG);
			LOGGER.info("Spell table: {} named and {} hashed spell textures{}", table.sprites().size(),
					table.pixels().size(), DEBUG ? ", with candidates (spell debug is on)" : "");
			return table;
		} catch (IOException e) {
			LOGGER.warn("Could not read the spell table; spell models won't glow: {}", e.getMessage());
			return SpellTable.EMPTY;
		}
	}

	private static int spriteId(SpriteContents contents) {
		Integer cached = CACHE.get(contents);
		if (cached != null) {
			return cached;
		}
		SpellTable.Entry entry = entry(contents);
		int result = entry == null ? 0 : entry.id();
		CACHE.put(contents, result);
		return result;
	}

	/** The sprite's spell table entry, or null. Hashes the pixels of Wynncraft's hashed textures only. */
	static SpellTable.Entry entry(SpriteContents contents) {
		SpellTable.Entry entry = TABLE.sprites().get(contents.name().toString());
		if (entry == null && contents.name().getPath().startsWith("item/w")) {
			entry = TABLE.pixels().get(pixelHash(contents));
		}
		return entry;
	}

	/**
	 * CRC-32 of the sprite's whole image, every animation frame, row by row, each pixel as big-endian ARGB. 0 when
	 * the image can't be read.
	 */
	static int pixelHash(SpriteContents contents) {
		try {
			NativeImage image = ((SpriteContentsAccessor) contents).wynnPhoton$originalImage();
			CRC32 crc = new CRC32();
			byte[] argb = new byte[4];
			for (int y = 0, h = image.getHeight(); y < h; y++) {
				for (int x = 0, w = image.getWidth(); x < w; x++) {
					int pixel = image.getPixel(x, y);
					argb[0] = (byte) (pixel >>> 24);
					argb[1] = (byte) (pixel >>> 16);
					argb[2] = (byte) (pixel >>> 8);
					argb[3] = (byte) pixel;
					crc.update(argb);
				}
			}
			return (int) crc.getValue();
		} catch (RuntimeException e) {
			return 0; // A closed or missing image
		}
	}

	/** Debug: logs the layer's textures not seen before, if the debug file exists and the layer is near the camera. */
	public static void debugLog(List<BakedQuad> quads, Matrix4f pose) {
		if (!DEBUG || quads.isEmpty()) {
			return;
		}
		Vector3f at = pose.getTranslation(new Vector3f());
		float distance = at.length();
		if (distance > DEBUG_RANGE) {
			return; // Far away, or a GUI item (GUI poses are in screen pixels)
		}
		TextureAtlasSprite last = null;
		for (int i = 0, n = quads.size(); i < n; i++) {
			TextureAtlasSprite sprite = quads.get(i).sprite();
			if (sprite == last) {
				continue;
			}
			last = sprite;
			SpriteContents contents = sprite.contents();
			boolean first = DEBUG_SEEN.add(contents.name());
			boolean firstInCapture = SpellDebug.seenInCapture(contents.name());
			if (first || firstInCapture) {
				SpellTable.Entry entry = entry(contents);
				SpellDebug.log(String.format(Locale.ROOT,
						"texture %s %dx%d%s quads=%d at %.1f %.1f %.1f (distance %.1f) pixels=%08x spell=%s",
						contents.name(), contents.width(), contents.height(), contents.isAnimated() ? " animated" : "",
						n, at.x, at.y, at.z, distance, pixelHash(contents),
						entry == null ? "-" : entry.id() + (entry.candidate() ? " (candidate) " : " ") + entry.name()));
			}
		}
	}

	/** Makes Iris tag the item about to be submitted with {@code id}. */
	public static void setRenderedItem(int id) {
		if (capturedState == NOT_LOOKED_UP) {
			lookUpIris();
		}
		if (capturedState == null) {
			return;
		}
		try {
			setCurrentRenderedItem.invoke(capturedState, id);
		} catch (Throwable e) {
			LOGGER.warn("Could not tag a spell model for the shader pack, giving up: {}", e.toString());
			capturedState = null;
		}
	}

	private static void lookUpIris() {
		capturedState = null;
		try {
			Class<?> state = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
			Object instance = state.getField("INSTANCE").get(null);
			setCurrentRenderedItem = MethodHandles.publicLookup()
					.findVirtual(state, "setCurrentRenderedItem", MethodType.methodType(void.class, int.class));
			capturedState = instance;
		} catch (ClassNotFoundException e) {
			// No Iris: no shader pack to tag spells for.
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Iris has changed; spell models won't glow: {}", e.toString());
		}
	}
}
