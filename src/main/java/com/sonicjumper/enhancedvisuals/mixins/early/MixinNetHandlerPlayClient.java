package com.sonicjumper.enhancedvisuals.mixins.early;

import com.sonicjumper.enhancedvisuals.client.ClientEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S28PacketEffect;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {
    @Inject(method = "handleExplosion", at = @At("RETURN"))
    private void enhancedvisuals$explosion(S27PacketExplosion packet, CallbackInfo ci) {
        enhancedvisuals$schedule(() -> ClientEffects.INSTANCE.explosion(packet.func_149148_f(), packet.func_149143_g(), packet.func_149145_h(), packet.func_149146_i()));
    }
    @Inject(method = "handleEffect", at = @At("RETURN"))
    private void enhancedvisuals$potion(S28PacketEffect packet, CallbackInfo ci) {
        if (packet.func_149242_d() == 2002)
            enhancedvisuals$schedule(() -> ClientEffects.INSTANCE.potion(packet.func_149240_f(), packet.func_149243_g(), packet.func_149239_h(), packet.func_149241_e()));
    }
    @Unique private void enhancedvisuals$schedule(Runnable task) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        Object handler = this;
        mc.func_152344_a(() -> {
            if (world != null && mc.theWorld == world && mc.getNetHandler() == handler) task.run();
        });
    }
}
