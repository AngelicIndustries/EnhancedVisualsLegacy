package com.sonicjumper.enhancedvisuals.mixins.early;

import com.sonicjumper.enhancedvisuals.client.ClientEffects;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = 1100)
public abstract class MixinEntityRenderer {
    // The world and shaderpack have finished; HUD and menus have not been drawn yet.
    @Inject(method = "updateCameraAndRender", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/EntityRenderer;renderEndNanoTime:J",
        opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER), require = 1)
    private void enhancedvisuals$render(float partialTicks, CallbackInfo ci) { ClientEffects.INSTANCE.render(partialTicks); }
}
