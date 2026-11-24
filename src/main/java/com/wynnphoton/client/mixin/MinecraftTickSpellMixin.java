package com.wynnphoton.client.mixin;

import com.wynnphoton.client.SpellDebug;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Experimental, debug only: drives the spell captures ({@link SpellDebug}) once per client tick. */
@Mixin(Minecraft.class)
abstract class MinecraftTickSpellMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void wynnPhoton$spellDebugTick(CallbackInfo ci) {
		SpellDebug.tick((Minecraft) (Object) this);
	}
}
