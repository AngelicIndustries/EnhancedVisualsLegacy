package com.sonicjumper.enhancedvisuals;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = Base.MODID, name = "Enhanced Visuals Legacy", version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*",
    dependencies = "required-after:unimixins", guiFactory = "com.sonicjumper.enhancedvisuals.gui.ConfigGuiFactory",
    customProperties = {
        @Mod.CustomProperty(k = "license", v = "GPLv3"),
        @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/AngelicIndustries/EnhancedVisualsLegacy/issues"),
        @Mod.CustomProperty(k = "iconFile", v = "assets/sonicvisuals/logo.png") })
public class Base {
    public static final String MODID = "sonicvisuals";
    public static Logger log;
    @SidedProxy(clientSide = "com.sonicjumper.enhancedvisuals.ClientProxy",
        serverSide = "com.sonicjumper.enhancedvisuals.ServerProxy")
    public static ServerProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        ConfigCore.load(event.getSuggestedConfigurationFile());
        com.sonicjumper.enhancedvisuals.network.DamageNetwork.init();
        proxy.init();
    }
}
