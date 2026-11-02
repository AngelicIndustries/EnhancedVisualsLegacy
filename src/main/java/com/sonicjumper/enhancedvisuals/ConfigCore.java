package com.sonicjumper.enhancedvisuals;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

public final class ConfigCore {
    public static Configuration config;
    public static boolean enabled, creative, inMenus, blurEnabled, hungerEnabled, persistentBlood, eyeAdaptation;
    public static int maxParticles, bloodColor, hungerThreshold, heartbeatHealth;
    public static float masterOpacity, maxBlur, heartbeatVolume, ringingVolume, attenuation, drowningMuffle;
    public static final Map<String, Effect> effects = new LinkedHashMap<>();

    /** Each effect, the shared settings it uses (intensity, duration, amount) and its description, shown as the category tooltip. */
    private static final String[][] EFFECTS = {
        { "damage", "ida", "Marks left on the screen when you get hurt, and a short red flash. Which marks a hit leaves (blood splatter, "
            + "slash, impact, pierce, burn...) and their color come from config/sonicvisuals-damage.json, where you can add rules for "
            + "modded damage, change colors or turn marks off. /evtest log shows what each hit was. Persistent blood keeps a splat for "
            + "every half-heart lost to bleeding until you heal; water washes it off for a while." },
        { "heartbeat", "id", "At low health a heartbeat plays and the edges of the screen pulse red, faster the lower your health." },
        { "explosion", "ida", "Explosions within reach cover the screen in dust and deafen you: other sounds drop away and come back as "
            + "the ringing in your ears fades. The strength follows the damage the blast would deal, so distance and cover matter." },
        { "drowning", "id", "When you run out of air your vision closes in from the edges and other sounds fade away until you "
            + "surface, and each drowning hit flashes red. It starts while the last bubbles go. With gameplay blur on, your sight "
            + "blurs as well." },
        { "splash", "ida", "Water running down the screen when you come up out of water, and a short blur when your head goes in or "
            + "comes out." },
        { "rain", "ida", "Raindrops collect on the screen while you are out in the rain, more when you look up. Diving washes them off." },
        { "sand", "ida", "Sand specks on the screen when you sprint over sand." },
        { "potion", "id", "A tint in the potion's color when a splash potion breaks next to you." },
        { "slender", "i", "Flickering static while an angry enderman you can see is close." },
        { "temperature", "i", "A heat haze in hot biomes or while burning, and frost at the edges in cold biomes. It builds up over "
            + "about half a minute; being wet cools you down." },
        { "underwater", "i", "A watery tint while your head is underwater, and a faint wet look for a while afterwards. Being underwater "
            + "also washes every mark off your screen." } };

    private ConfigCore() {}

    public static void load(File file) {
        config = new Configuration(file);
        reload();
    }

    public static void reload() {
        config.setCategoryComment("general", "Switches for the whole mod: turn every effect off, show effects in creative mode or "
            + "behind menus, cap how many marks can be on screen and set how opaque overlays are.");
        enabled = config.getBoolean("enabled", "general", true, "Enable visual effects.");
        creative = config.getBoolean("creative", "general", false, "Show effects in creative mode.");
        inMenus = config.getBoolean("inMenus", "general", true, "Keep showing gameplay effects behind menus.");
        maxParticles = config.getInt("maxParticles", "general", 128, 0, 512, "Most marks on screen at once.");
        masterOpacity = config.getFloat("opacity", "general", 0.7F, 0, 1, "Opacity of all marks and overlays.");
        // Carry over the original mod's blur and heartbeat settings, then drop its old keys.
        boolean oldBlur = config.hasKey("general", "Render Blur Flag") && config.get("general", "Render Blur Flag", false).getBoolean();
        int oldHealth = config.hasKey("options", "Heartbeat Start Health") ? (int) config.get("options", "Heartbeat Start Health", 6D).getDouble() : 6;
        for (String key : new String[] { "Render Blur Flag", "True Glow Flag", "Blur Quality" }) config.getCategory("general").remove(key);
        if (config.hasCategory("options")) config.removeCategory(config.getCategory("options"));

        config.setCategoryComment("blur", "Blur during gameplay, used by explosions, splashes, the heartbeat and drowning. Separate "
            + "from menu blur mods such as Blur. With it off, those effects keep everything except the blur.");
        blurEnabled = config.getBoolean("enabled", "blur", oldBlur, "Enable gameplay blur.");
        maxBlur = config.getFloat("maxRadius", "blur", 12, 0, 32, "Blur radius at full strength.");

        config.setCategoryComment("effects", "Every effect has its own category with an on/off switch and an intensity. Effects that "
            + "last a while also have a duration, and effects that leave marks an amount.");
        for (String[] spec : EFFECTS) {
            String name = spec[0], keys = spec[1], category = "effects." + name;
            config.setCategoryComment(category, spec[2]);
            Effect effect = effects.computeIfAbsent(name, key -> new Effect());
            effect.enabled = config.getBoolean("enabled", category, true, "Enable this effect.");
            effect.intensity = keys.contains("i") ? config.getFloat("intensity", category, 1, 0, 2, "How strong it looks: 1 is normal, 0 hides it.") : 1;
            effect.duration = keys.contains("d") ? config.getFloat("duration", category, 1, 0.1F, 5, "How long it lasts: 1 is normal.") : 1;
            effect.amount = keys.contains("a") ? config.getFloat("amount", category, 1, 0, 5, "How many marks it leaves: 1 is normal, fractions work.") : 1;
            ConfigCategory settings = config.getCategory(category);
            if (!keys.contains("i")) settings.remove("intensity");
            if (!keys.contains("d")) settings.remove("duration");
            if (!keys.contains("a")) settings.remove("amount");
        }
        heartbeatHealth = config.getInt("startHealth", "effects.heartbeat", oldHealth, 1, 100, "Health below which the heartbeat starts; two health is one heart.");
        heartbeatVolume = config.getFloat("volume", "effects.heartbeat", 0.35F, 0, 1, "Heartbeat volume; zero mutes it.");
        ringingVolume = config.getFloat("ringingVolume", "effects.explosion", 0.5F, 0, 1, "Ear ringing volume right after an explosion; it fades as hearing returns. Zero disables it.");
        attenuation = config.getFloat("soundAttenuation", "effects.explosion", 1, 0, 1, "How strongly other sounds are dampened right after an explosion: muffled with Sound Physics, quieter without it. Zero disables it.");
        drowningMuffle = config.getFloat("soundMuffle", "effects.drowning", 0.85F, 0, 1, "How strongly other sounds are dampened as you black out: muffled with Sound Physics, quieter without it. Zero disables it.");
        String color = config.getString("bloodColor", "effects.damage", "4D0303", "Blood color as six hexadecimal RGB digits. Rules in the damage marks file can override it.");
        try { bloodColor = Integer.parseInt(color.replace("#", ""), 16) & 0xFFFFFF; }
        catch (NumberFormatException e) { bloodColor = 0x4D0303; }
        persistentBlood = config.getBoolean("persistentBlood", "effects.damage", false, "Keep a blood splat for every half-heart lost to bleeding until you heal.");

        config.setCategoryComment("effects.hunger", "The world loses its color as your food bar empties, and is fully grey when it is empty.");
        hungerEnabled = config.getBoolean("enabled", "effects.hunger", true, "Desaturate the world when hungry.");
        hungerThreshold = config.getInt("threshold", "effects.hunger", 6, 1, 20, "Food level below which the color starts to fade; two is one drumstick.");
        config.setCategoryComment("effects.eyeAdaptation", "A short white glare when it suddenly gets much brighter, such as stepping "
            + "out of a dark cave into daylight.");
        eyeAdaptation = config.getBoolean("enabled", "effects.eyeAdaptation", false, "Enable the glare.");
        config.save();
    }

    public static Effect effect(String name) { return effects.get(name); }
    public static final class Effect {
        public boolean enabled;
        public float intensity, duration, amount;
    }
}
