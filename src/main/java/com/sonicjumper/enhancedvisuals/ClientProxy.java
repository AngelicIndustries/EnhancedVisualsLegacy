package com.sonicjumper.enhancedvisuals;

import com.sonicjumper.enhancedvisuals.client.ClientEffects;
import com.sonicjumper.enhancedvisuals.network.DamageNetwork;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends ServerProxy {
    @Override public void init() {
        ClientEffects effects = ClientEffects.INSTANCE;
        MinecraftForge.EVENT_BUS.register(effects);
        FMLCommonHandler.instance().bus().register(effects);
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(effects);
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new com.sonicjumper.enhancedvisuals.client.PreviewCommand());
    }

    @Override public void damage(DamageNetwork.Damage damage, MessageContext context) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        mc.func_152344_a(() -> {
            if (world != null && mc.theWorld == world && mc.getNetHandler() == context.netHandler
                && world.provider.dimensionId == damage.dimension)
                ClientEffects.INSTANCE.damage(damage);
        });
    }
}
