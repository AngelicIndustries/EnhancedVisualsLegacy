package com.sonicjumper.enhancedvisuals.mixins.early;

import com.sonicjumper.enhancedvisuals.client.ClientEffects;
import net.minecraft.client.audio.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class MixinSoundManager {
    @Shadow public abstract void setSoundCategoryVolume(SoundCategory category, float volume);
    @Unique private float enhancedvisuals$lastFactor = 1;

    @Inject(method = "getNormalizedVolume", at = @At("RETURN"), cancellable = true)
    private void enhancedvisuals$volume(ISound sound, SoundPoolEntry entry, SoundCategory category, CallbackInfoReturnable<Float> cir) {
        // Master includes UI sounds and our own heartbeat/ringing; leave those alone.
        if (category != SoundCategory.MASTER)
            cir.setReturnValue(cir.getReturnValue() * ClientEffects.INSTANCE.soundFactor());
    }

    @Inject(method = "updateAllSounds", at = @At("RETURN"))
    private void enhancedvisuals$refreshVolumes(CallbackInfo ci) {
        float factor = ClientEffects.INSTANCE.soundFactor();
        if (factor == enhancedvisuals$lastFactor) return;
        enhancedvisuals$lastFactor = factor;
        // Recompute from the original sound and current category setting, never from a cached source volume.
        for (SoundCategory category : SoundCategory.values())
            if (category != SoundCategory.MASTER) setSoundCategoryVolume(category, 1);
    }
}
