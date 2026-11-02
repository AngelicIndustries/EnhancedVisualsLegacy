package com.sonicjumper.enhancedvisuals.gui;

import java.util.Set;
import java.util.ArrayList;
import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import com.sonicjumper.enhancedvisuals.Base;
import com.sonicjumper.enhancedvisuals.ConfigCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

public final class ConfigGuiFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft minecraft) {}
    @Override public Class<? extends GuiScreen> mainConfigGuiClass() { return Screen.class; }
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }
    @Override public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) { return null; }

    public static final class Screen extends GuiConfig {
        public Screen(GuiScreen parent) { super(parent, elements(), Base.MODID, false, false, "Enhanced Visuals Legacy"); }
        private static java.util.List<IConfigElement> elements() {
            java.util.List<IConfigElement> result = new ArrayList<>();
            for (String name : ConfigCore.config.getCategoryNames())
                if (!ConfigCore.config.getCategory(name).isChild()) result.add(new ConfigElement(ConfigCore.config.getCategory(name)));
            return result;
        }
    }
}
