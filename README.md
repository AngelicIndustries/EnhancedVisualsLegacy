# Enhanced Visuals Legacy

Damage, explosion and environmental screen effects for Minecraft 1.7.10, based on [Enhanced Visuals](https://github.com/CreativeMD/EnhancedVisuals) with features from its newer versions.

![logo](images/logo_small.png)

![forge](images/badges/forge.png)
[![cord](images/badges/cord.png)](https://discord.gg/xAWCqGrguG)
[![hub](images/badges/github.png)](https://github.com/AngelicIndustries/EnhancedVisualsLegacy/releases)

<!-- Not published yet
[![curse](images/badges/curse.png)](https://www.curseforge.com/minecraft/mc-mods/enhanced-visuals-legacy)
[![modrinth](images/badges/modrinth.png)](https://modrinth.com/mod/enhanced-visuals-legacy)
[![mcmodcn](images/badges/mcmodcn.png)](https://www.mcmod.cn/class/PROJECT_ID.html)
[![67](images/badges/67.png)](https://67minecraft.com/mod/enhanced-visuals-legacy)
[![maven](images/badges/maven.png)](https://maven.angelic.industries/#/releases/industries/angelic/enhancedvisuals/EnhancedVisualsLegacy)
-->

## Features

* Marks that depend on what hit you: slashes from swords and zombies, punctures from arrows, spiders and cactus, impacts from falls and golems, burns from fire, and tinted marks from poison and wither.
* `config/sonicvisuals-damage.json` maps any damage, modded damage included, to marks, colors or no marks at all. Rules can check the damage type, its properties, the entity that hit you, the one responsible and the weapon it held; the most specific rule wins. `/evtest log` shows what each hit was.
* Explosions leave dust on the screen and deafen you: other sounds drop away and return as the ringing in your ears fades.
* Drowning closes your vision in from the edges and fades out other sounds until you surface.
* With [Sound Physics](https://github.com/AngelicIndustries/Sound-Physics) 1.1.3 or later, explosions and drowning truly muffle sounds instead of only making them quieter.
* A heartbeat with a red pulse at low health, and the world loses its color as you starve.
* Water running down the screen when you surface, raindrops that collect when you are out in the rain, sand when sprinting on sand, and splash potion tints. Diving washes marks off.
* Optional gameplay blur, persistent blood that washes off in water, temperature overlays and glare.
* Each effect can be enabled and tuned separately. Preview any effect with `/evtest`.
* Works on servers without the mod. With it, the server reports exact damage types.

Configure through **Mods > Enhanced Visuals Legacy > Config**, where every category describes itself, or `config/sonicvisuals.cfg`. Existing blur and heartbeat settings are retained. Replaces the original Enhanced Visuals jar; both use mod ID `sonicvisuals`. Splatter textures can be replaced with a resource pack.

## Dependencies

* [UniMixins](https://github.com/LegacyModdingMC/UniMixins) [![curse](images/icons/curse.png)](https://www.curseforge.com/minecraft/mc-mods/unimixins) [![modrinth](images/icons/modrinth.png)](https://modrinth.com/mod/unimixins/versions) [![git](images/icons/git.png)](https://github.com/LegacyModdingMC/UniMixins/releases)

## Building

```sh
./gradlew build
```

## Credits

* [Sonicjumper](https://github.com/Sonicjumper) for Enhanced Visuals and its artwork.
* [CreativeMD](https://github.com/CreativeMD/EnhancedVisuals) for maintaining it and for the explosion, ringing and rain behavior of later versions.
* The [GTNH buildscript](https://github.com/GTNewHorizons/ExampleMod1.7.10), Forge and MCP teams.

## License

[GPLv3](LICENSE). The blur shaders come from [Blur Legacy](https://github.com/AngelicIndustries/BlurLegacy) under the MIT license.

## Buy me some creatine

* [ko-fi.com](https://ko-fi.com/jackisasubtlejoke)
* Monero: `893tQ56jWt7czBsqAGPq8J5BDnYVCg2tvKpvwTcMY1LS79iDabopdxoUzNLEZtRTH4ewAcKLJ4DM4V41fvrJGHgeKArxwmJ`
