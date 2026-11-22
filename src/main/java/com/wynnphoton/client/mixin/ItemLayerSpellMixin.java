package com.wynnphoton.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wynnphoton.client.SpellTags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Experimental: see {@link SpellTags}. Iris (and WynnIris) set the rendered item's ID at the head of {@code submit},
 * and Iris copies it into the item node when {@code submitItem} is called. Overriding it just before that call tags
 * only this layer; Iris resets the ID when {@code submit} returns.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
abstract class ItemLayerSpellMixin {
	@Shadow
	@Final
	private List<BakedQuad> quads;

	@Inject(
			method = "submit",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"
			)
	)
	private void wynnPhoton$tagSpell(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
			int outlineColor, CallbackInfo ci) {
		int id = SpellTags.spellId(quads);
		if (id != 0) {
			SpellTags.setRenderedItem(id);
		}
	}
}
