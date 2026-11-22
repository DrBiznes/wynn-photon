package com.wynnphoton.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;

import static com.wynnphoton.WynnPhotonPreLaunch.LOGGER;

/**
 * Experimental: tags Wynncraft spell models for the shader pack.
 *
 * <p>Wynncraft draws its spell models (Meteor, Ice Snake, Totem, ...) as items on display entities. They are all
 * {@code minecraft:potion} or {@code diamond_sword} told apart by {@code custom_model_data}, which Iris's
 * {@code item.properties} can't match, so the shader can't tell a meteor from a potion. Each spell's textures do have
 * their own names, though. When an item layer is submitted, this looks at its quads' sprites, and if one is a known
 * spell texture it overrides the item ID Iris hands the shader ({@code currentRenderedItemId}) with the spell's own
 * ID. The shader then makes the spell's fire emissive and lights the area around it.
 *
 * <p>Iris is reached by reflection, as everywhere else in the mod: without Iris this does nothing.
 */
public final class SpellTags {
	/** Sprite name to the item ID the shader sees. Keep in step with the WYNN_SPELL_* IDs in the shader pack. */
	private static final Map<Identifier, Integer> SPELL_SPRITES = Map.of(
			Identifier.withDefaultNamespace("wynn/spell/meteor_0_a"), 10106, // Meteor, orange fire
			Identifier.withDefaultNamespace("wynn/spell/meteor_1_a"), 10107 // Meteor, purple (void) fire
	);

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
			Integer id = SPELL_SPRITES.get(sprite.contents().name());
			if (id != null) {
				return id;
			}
		}
		return 0;
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
