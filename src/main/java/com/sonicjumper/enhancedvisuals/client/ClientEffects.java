package com.sonicjumper.enhancedvisuals.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sonicjumper.enhancedvisuals.Base;
import com.sonicjumper.enhancedvisuals.ConfigCore;
import com.sonicjumper.enhancedvisuals.network.DamageNetwork;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.*;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.potion.PotionHelper;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public final class ClientEffects implements IResourceManagerReloadListener {
    public static final ClientEffects INSTANCE = new ClientEffects();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<>();
    private final Map<String, List<ResourceLocation>> textures = new HashMap<>();
    private final EffectPipeline pipeline = new EffectPipeline();
    private final Impulse blur = new Impulse(), hit = new Impulse(), drownHit = new Impulse(), pulse = new Impulse(), ringing = new Impulse(), glare = new Impulse();
    private final DamageMarks marks = new DamageMarks();
    private static final int FADE = 40;
    private World world;
    private EntityPlayer player;
    private float lastHealth, wetness, temperature = 1, brightness, slender, saturation = 1, deafness, drowning, drownProgress, drownLoss = 2, wounds;
    private boolean underwater, failed, logHits, muffling;
    private int ticks, heartbeat, drownTicks, woundColor, woundTick, submergedTicks;
    private DamageMarks.Mark woundMark;
    private ISound ringingSound;

    private ClientEffects() {}

    public boolean active() {
        return ConfigCore.enabled && mc.theWorld != null && mc.thePlayer != null && !mc.thePlayer.isDead
            && (ConfigCore.creative || !mc.thePlayer.capabilities.isCreativeMode);
    }

    private boolean enabled(String name) { return active() && ConfigCore.effect(name).enabled; }
    private float intensity(String name) { return ConfigCore.effect(name).intensity; }
    private int duration(String name, int ticks) { return Math.max(1, Math.round(ConfigCore.effect(name).duration * ticks)); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (world != mc.theWorld || player != mc.thePlayer) {
            reset();
            world = mc.theWorld;
            player = mc.thePlayer;
            if (player != null) { lastHealth = player.getHealth(); underwater = player.isInsideOfMaterial(Material.water); brightness = player.getBrightness(0); }
        }
        if (!active()) { reset(); return; }
        if (mc.isGamePaused()) return;
        ticks++;
        particles.removeIf(p -> --p.life <= 0 || !ConfigCore.effect(p.effect).enabled);
        for (int i = 0; i < particles.size() - ConfigCore.maxParticles; i++) fade(particles.get(i));
        blur.tick(); hit.tick(); drownHit.tick(); pulse.tick(); ringing.tick(); glare.tick();
        if (!ConfigCore.effect("explosion").enabled) { ringing.clear(); stopRinging(); }
        if (ringing.value(0) <= 0) stopRinging();
        // Let the blast itself be heard, then close in over 0.75 s; recovery follows the ringing.
        deafness = Math.min(ringing.value(0), deafness + 1 / 15F);

        float health = player.getHealth();
        if (health < lastHealth && !DamageNetwork.supported(mc.getNetHandler().getNetworkManager()))
            damage(new DamageNetwork.Damage(world.provider.dimensionId, player.isBurning() ? "onFire" : player.getAir() <= 0 ? "drown" : "generic",
                lastHealth - health, player.isBurning() ? DamageNetwork.FIRE : 0, "", "", "", ""));
        float lost = lastHealth - health;
        lastHealth = health;
        boolean inWater = player.isInsideOfMaterial(Material.water);
        if (underwater != inWater && enabled("splash")) {
            // Water runs off your face when you come up; bobbing at the surface does not count as coming up.
            if (!inWater && submergedTicks >= 10) add("splash", "water", 7, 100, 0xFFFFFF);
            blur.add(0.5F * intensity("splash"), duration("splash", 40));
        }
        underwater = inWater;
        submergedTicks = inWater ? submergedTicks + 1 : 0;
        // Water washes every mark off your screen, raindrops included.
        if (inWater) for (Particle p : particles) { p.persistent = false; p.life -= 5; }
        // Out of air, vision closes in from the edges; it starts dimming as the last bubbles go. It is paced by the time left
        // until drowning kills you, from your health and what the last hit took, so you black out just before, whatever your health.
        if (enabled("drowning") && inWater && player.getAir() <= 0) {
            drownTicks++;
            if (lost > 0) drownLoss = lost;
            int hitsLeft = Math.max(1, MathHelper.ceiling_float_int(health / Math.max(0.5F, drownLoss)));
            int ticksLeft = (hitsLeft - 1) * 20 + 20 + Math.max(-19, player.getAir());
            drownProgress = Math.max(drownProgress, drownTicks / (float) (drownTicks + Math.max(0, ticksLeft - 10)));
        } else { drownTicks = 0; drownProgress = 0; drownLoss = 2; }
        float air = MathHelper.clamp_float(player.getAir() / 100F, 0, 1);
        float blind = enabled("drowning") && inWater ? 0.25F * (1 - air) + 0.75F * drownProgress : 0;
        drowning = blind > drowning ? Math.min(blind, drowning + 0.1F) : Math.max(blind, drowning - 1F / duration("drowning", 30));
        muffling = SoundPhysicsBridge.muffle(dampening());
        wetness += ((player.isWet() ? 1 : 0) - wetness) * (player.isWet() ? 0.02F : 0.01F);
        int x = MathHelper.floor_double(player.posX), y = MathHelper.floor_double(player.posY), z = MathHelper.floor_double(player.posZ);
        if (enabled("rain") && !inWater && world.isRaining() && world.canBlockSeeTheSky(x, y, z)
            && world.getBiomeGenForCoords(x, z).canSpawnLightningBolt()) {
            float exposure = MathHelper.clamp_float((45 - player.rotationPitch) / 135F, 0, 1);
            if (random.nextFloat() < 0.12F * exposure * world.getRainStrength(1)) add("rain", "water", 1, 70, 0xFFFFFF);
        }
        if (enabled("sand") && player.onGround && player.isSprinting() && ticks % 5 == 0
            && world.getBlock(x, MathHelper.floor_double(player.boundingBox.minY) - 1, z) == Blocks.sand)
            add("sand", "sand", 1, 120, 0xFFFFFF);

        if (enabled("heartbeat") && health <= ConfigCore.heartbeatHealth) {
            float strength = MathHelper.clamp_float(1 - health / (ConfigCore.heartbeatHealth + 1F), 0, 1);
            if (--heartbeat <= 0) {
                heartbeat = Math.max(12, Math.round(health * 5 + 10));
                pulse.add(strength * intensity("heartbeat"), duration("heartbeat", 12));
                blur.add(strength * 0.4F * intensity("heartbeat"), duration("heartbeat", 12));
                sound("heartbeatOut", ConfigCore.heartbeatVolume);
            } else if (heartbeat == 5) sound("heartbeatIn", ConfigCore.heartbeatVolume);
        } else { heartbeat = 0; }

        float target = world.getBiomeGenForCoords(x, z).temperature + (player.isBurning() ? 2 : 0) - wetness * 0.2F;
        temperature += (target - temperature) * 0.002F;
        if (enabled("slender") && ticks % 5 == 0) {
            slender = 0;
            for (Object candidate : world.getEntitiesWithinAABB(EntityEnderman.class, player.boundingBox.expand(16, 16, 16))) {
                EntityEnderman enderman = (EntityEnderman) candidate;
                if (enderman.isScreaming() && player.canEntityBeSeen(enderman))
                    slender = Math.max(slender, MathHelper.clamp_float(1 - player.getDistanceToEntity(enderman) / 16, 0, 1));
            }
        }
        saturation = ConfigCore.hungerEnabled ? MathHelper.clamp_float(player.getFoodStats().getFoodLevel() / (float) ConfigCore.hungerThreshold, 0, 1) : 1;
        float light = player.getBrightness(0);
        if (ConfigCore.eyeAdaptation && light - brightness > 0.5F) glare.add(light - brightness, 20);
        brightness += (light - brightness) * 0.05F;
        // Persistent blood: one splat per half-heart lost to bleeding hits, until healed. Water washes it off; it bleeds back afterwards.
        // The hit arrives before the health update, so only cap wounds by the missing health once that has caught up.
        if (ticks - woundTick > 10) wounds = Math.min(wounds, player.getMaxHealth() - health);
        if (ConfigCore.persistentBlood && enabled("damage") && woundMark != null) {
            int wanted = Math.min(20, (int) Math.ceil(wounds));
            int present = 0;
            for (Particle p : particles) if (p.persistent) { if (present++ >= wanted) fade(p); else p.life = p.duration; }
            if (present < wanted && !inWater && ticks % 10 == 0 && particles.size() < ConfigCore.maxParticles) {
                Particle p = particle("damage", woundMark, 60, woundColor);
                if (p != null) { p.persistent = true; particles.add(p); }
            }
        } else for (Particle p : particles) if (p.persistent) fade(p);
    }

    /** Lets a particle run out its fade instead of vanishing. */
    private static void fade(Particle p) {
        p.persistent = false;
        p.life = Math.min(p.life, FADE);
    }

    public void damage(DamageNetwork.Damage hit) {
        if (!active() || !Float.isFinite(hit.amount) || hit.amount <= 0) return;
        float amount = Math.min(40, hit.amount);
        DamageMarks.Rule rule = marks.resolve(hit);
        if (logHits) log(hit, rule);
        if ("drown".equals(hit.type)) {
            if (enabled("drowning")) drownHit.add(Math.min(1, amount / 4) * intensity("drowning"), duration("drowning", 12));
        } else if (enabled("damage")) this.hit.add(Math.min(1, amount / 8) * intensity("damage"), duration("damage", 12));
        if (rule == null || rule.mark == null || !enabled("damage")) return;
        DamageMarks.Mark mark = rule.mark;
        int color = rule.color != null ? rule.color : mark.blood ? ConfigCore.bloodColor : mark.color;
        add("damage", mark, Math.min(rule.max, Math.max(1, amount * rule.amount)), duration("damage", mark.life), color);
        if (mark.blood) { wounds += amount; woundMark = mark; woundColor = color; woundTick = ticks; }
    }

    public void explosion(double x, double y, double z, float size) {
        if (!enabled("explosion") || !Float.isFinite(size) || size <= 0) return;
        // Vanilla's exposure damage (Explosion.doExplosionA), so range and cover match the actual hit.
        // The client player's posY is at eye height; the server measures from the feet.
        double dx = mc.thePlayer.posX - x, dy = mc.thePlayer.boundingBox.minY - y, dz = mc.thePlayer.posZ - z;
        double reach = size * 2, distance = Math.sqrt(dx * dx + dy * dy + dz * dz) / reach;
        if (distance >= 1) return;
        double exposure = (1 - distance) * world.getBlockDensity(Vec3.createVectorHelper(x, y, z), mc.thePlayer.boundingBox);
        if (exposure <= 0) return;
        float strength = (float) Math.min(1, ((exposure * exposure + exposure) / 2 * 7 * reach + 1) / 20);
        add("explosion", "dust", 30 * strength, 160, 0x888888);
        blur.add(strength * intensity("explosion"), duration("explosion", Math.round(10 + 10 * strength)));
        // Deafened at once, then hearing returns while the ringing fades: 2.5 s for a graze, 10 s for a direct hit.
        if (ringing.add(1, duration("explosion", Math.round(50 + 150 * strength))) && ConfigCore.ringingVolume > 0
            && (ringingSound == null || !mc.getSoundHandler().isSoundPlaying(ringingSound))) {
            ringingSound = new Ringing();
            mc.getSoundHandler().playSound(ringingSound);
        }
    }

    public void potion(int x, int y, int z, int data) {
        if (!enabled("potion") || mc.thePlayer.getDistanceSq(x, y, z) > 25) return;
        add("potion", "Overlay/potion", 1, 50, PotionHelper.func_77915_a(data, false));
    }

    /** How strongly other sounds are dampened: deafened by explosions, fading out while drowning. */
    private float dampening() {
        if (!active()) return 0;
        float amount = 0;
        if (ConfigCore.effect("explosion").enabled) amount = Math.max(amount, deafness * ConfigCore.attenuation);
        if (ConfigCore.effect("drowning").enabled) amount = Math.max(amount, drowning * ConfigCore.drowningMuffle);
        return amount;
    }

    /** Volume for everything but our own sounds. Sound Physics muffles instead when it can, so then it stays at full. */
    public float soundFactor() {
        return muffling ? 1 : Math.max(0.05F, 1 - dampening());
    }

    private ISound sound(String name, float volume) {
        if (volume <= 0) return null;
        ISound sound = new PositionedSoundRecord(new ResourceLocation(Base.MODID, name), volume, 1, 0, 0, 0) {
            @Override public AttenuationType getAttenuationType() { return AttenuationType.NONE; }
        };
        mc.getSoundHandler().playSound(sound);
        return sound;
    }
    private void stopRinging() { if (ringingSound != null) mc.getSoundHandler().stopSound(ringingSound); ringingSound = null; }

    /** Fades with the deafness envelope instead of cutting off; master category, so it is never dampened itself. */
    private final class Ringing extends MovingSound {
        Ringing() {
            super(new ResourceLocation(Base.MODID, "ringing"));
            field_147666_i = AttenuationType.NONE;
            volume = ConfigCore.ringingVolume * ringing.value(0);
        }
        @Override public void update() {
            volume = ConfigCore.ringingVolume * ringing.value(0);
            if (volume <= 0.005F) donePlaying = true;
        }
    }

    /** Adds marks from a set named in the damage marks file (splatter, dust, water...), or an overlay such as Overlay/potion. */
    private void add(String effect, String mark, float count, int life, int color) {
        add(effect, mark.startsWith("Overlay/") ? new DamageMarks.Mark(mark) : marks.mark(mark), count, duration(effect, life), color);
    }

    private void add(String effect, DamageMarks.Mark mark, float count, int life, int color) {
        if (!enabled(effect)) return;
        float wanted = Math.min(ConfigCore.maxParticles, count * ConfigCore.effect(effect).amount);
        int whole = (int) wanted;
        if (random.nextFloat() < wanted - whole) whole++;
        for (int i = 0; i < whole && particles.size() < ConfigCore.maxParticles; i++) {
            Particle p = particle(effect, mark, life, color);
            if (p != null) particles.add(p);
        }
    }

    private Particle particle(String effect, DamageMarks.Mark mark, int life, int color) {
        boolean overlay = mark.name.startsWith("Overlay/");
        List<ResourceLocation> choices = textures.get(overlay ? mark.name : mark.texture());
        if (choices == null || choices.isEmpty()) return null;
        return new Particle(effect, choices.get(random.nextInt(choices.size())), random.nextFloat(), random.nextFloat(),
            mark.minSize + random.nextFloat() * (mark.maxSize - mark.minSize), mark.stretch, random.nextFloat() * 360, life, color, overlay);
    }

    public void render(float partial) {
        if (!active() || (!ConfigCore.inMenus && mc.currentScreen != null) || mc.gameSettings.hideGUI) return;
        if (mc.isGamePaused()) partial = 0;
        float radius = ConfigCore.blurEnabled ? Math.max(blur.value(partial), drowning * 0.5F) * ConfigCore.maxBlur : 0;
        if (!failed && (radius > 0.01F || saturation < 0.999F) && OpenGlHelper.shadersSupported && OpenGlHelper.isFramebufferEnabled()) {
            try { pipeline.render(radius, saturation); }
            catch (Exception e) { failed = true; pipeline.close(); Base.log.error("Post-processing failed; overlays remain available. Reload resources to retry.", e); }
        }
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = resolution.getScaledWidth(), height = resolution.getScaledHeight();
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glEnable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (Particle p : particles) {
                float alpha = Math.min(1, (p.life - partial) / Math.min(40F, p.duration)) * intensity(p.effect);
                mc.getTextureManager().bindTexture(p.texture);
                color(p.color, alpha);
                GL11.glPushMatrix();
                try {
                    if (p.overlay) quad(0, 0, width, height);
                    else {
                        float size = Math.min(width, height) * p.size, w = size * p.stretch, h = size / p.stretch;
                        GL11.glTranslatef(p.x * width, p.y * height, 0);
                        GL11.glRotatef(p.rotation, 0, 0, 1);
                        quad(-w / 2, -h / 2, w, h);
                    }
                } finally { GL11.glPopMatrix(); }
            }
            overlay("damaged", Math.max(enabled("damage") ? hit.value(partial) : 0, enabled("drowning") ? drownHit.value(partial) : 0), width, height);
            overlay("lowhealth", enabled("heartbeat") ? pulse.value(partial) : 0, width, height);
            overlay("water", enabled("underwater") ? (underwater ? 0.3F : wetness * 0.15F) * intensity("underwater") : 0, width, height);
            overlay("heat", enabled("temperature") ? Math.max(0, temperature - 1) * 0.5F * intensity("temperature") : 0, width, height);
            overlay("ice", enabled("temperature") ? Math.max(0, 0.25F - temperature) * 2 * intensity("temperature") : 0, width, height);
            if (enabled("slender") && slender > 0) {
                List<ResourceLocation> frames = textures.get("Animation/slender");
                if (frames != null && !frames.isEmpty()) {
                    mc.getTextureManager().bindTexture(frames.get((ticks / 2) % frames.size()));
                    color(0xFFFFFF, slender * 0.5F * intensity("slender")); quad(0, 0, width, height);
                }
            }
            if (enabled("drowning") && drowning > 0.001F) blackout(width, height, drowning * intensity("drowning"));
            if (ConfigCore.eyeAdaptation && glare.value(partial) > 0) {
                GL11.glDisable(GL11.GL_TEXTURE_2D); color(0xFFFFFF, glare.value(partial) * 0.3F); quad(0, 0, width, height);
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glPopAttrib(); GL11.glMatrixMode(mode);
        }
    }

    /** Darkness closing in from the edges; at full strength only a dim spot is left in the middle. */
    private void blackout(int width, int height, float level) {
        level = MathHelper.clamp_float(level, 0, 1);
        float cx = width / 2F, cy = height / 2F, reach = (float) Math.sqrt(cx * cx + cy * cy);
        float clear = reach * Math.max(0, 1.05F - level), edge = clear + reach * 0.5F, far = reach * 1.5F;
        float alpha = Math.min(1, level * 1.4F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int i = 0; i < 48; i++) {
            double a0 = Math.PI * 2 * i / 48, a1 = Math.PI * 2 * (i + 1) / 48;
            ring(t, cx, cy, a0, a1, clear, 0, edge, alpha);
            ring(t, cx, cy, a0, a1, edge, alpha, far, alpha);
        }
        t.draw();
        // The last stretch of drowning dims what is left of the view as well.
        float dim = Math.max(0, (level - 0.7F) / 0.3F) * 0.85F;
        if (dim > 0) { GL11.glColor4f(0, 0, 0, dim); quad(0, 0, width, height); }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void ring(Tessellator t, float cx, float cy, double a0, double a1, float inner, float innerAlpha, float outer, float outerAlpha) {
        t.setColorRGBA_F(0, 0, 0, innerAlpha);
        t.addVertex(cx + Math.cos(a0) * inner, cy + Math.sin(a0) * inner, 0);
        t.setColorRGBA_F(0, 0, 0, outerAlpha);
        t.addVertex(cx + Math.cos(a0) * outer, cy + Math.sin(a0) * outer, 0);
        t.addVertex(cx + Math.cos(a1) * outer, cy + Math.sin(a1) * outer, 0);
        t.setColorRGBA_F(0, 0, 0, innerAlpha);
        t.addVertex(cx + Math.cos(a1) * inner, cy + Math.sin(a1) * inner, 0);
    }

    private void overlay(String name, float alpha, int width, int height) {
        List<ResourceLocation> list = textures.get("Overlay/" + name);
        if (alpha <= 0 || list == null || list.isEmpty()) return;
        mc.getTextureManager().bindTexture(list.get(0)); color(0xFFFFFF, alpha); quad(0, 0, width, height);
    }
    private void color(int rgb, float alpha) {
        GL11.glColor4f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F,
            MathHelper.clamp_float(alpha * ConfigCore.masterOpacity, 0, 1));
    }
    private void quad(float x, float y, float width, float height) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + height, 0, 0, 1); t.addVertexWithUV(x + width, y + height, 0, 1, 1);
        t.addVertexWithUV(x + width, y, 0, 1, 0); t.addVertexWithUV(x, y, 0, 0, 0); t.draw();
    }

    public void reset() {
        particles.clear(); blur.clear(); hit.clear(); drownHit.clear(); pulse.clear(); ringing.clear(); glare.clear();
        wetness = slender = deafness = drowning = drownProgress = wounds = 0; drownLoss = 2; saturation = temperature = 1; ticks = heartbeat = drownTicks = 0;
        woundMark = null;
        stopRinging(); pipeline.close();
        if (muffling) { SoundPhysicsBridge.muffle(0); muffling = false; }
    }

    public void reloadMarks() { marks.load(cpw.mods.fml.common.Loader.instance().getConfigDir()); }

    @Override public void onResourceManagerReload(IResourceManager manager) {
        reset(); failed = false; textures.clear(); reloadMarks();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                manager.getResource(new ResourceLocation(Base.MODID, "visuals.txt")).getInputStream(), StandardCharsets.UTF_8))) {
            String path;
            while ((path = reader.readLine()) != null) {
                String[] parts = path.split("/");
                if (parts.length != 5 || !path.endsWith(".png")) continue;
                ResourceLocation texture = new ResourceLocation(Base.MODID, path);
                textures.computeIfAbsent(parts[2] + "/" + parts[3], key -> new ArrayList<>()).add(texture);
            }
        } catch (Exception e) { Base.log.error("Could not load visual texture index", e); }
    }

    @SubscribeEvent public void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Base.MODID.equals(event.modID)) { ConfigCore.reload(); reset(); failed = false; reloadMarks(); }
    }

    private void log(DamageNetwork.Damage hit, DamageMarks.Rule rule) {
        List<String> flags = new ArrayList<>();
        for (int i = 0; i < DamageNetwork.FLAG_NAMES.length; i++) if (hit.has(1 << i)) flags.add(DamageNetwork.FLAG_NAMES[i]);
        String result = rule == null || rule.mark == null ? "none" : rule.mark.name
            + (rule.color != null ? String.format(" #%06X", rule.color) : rule.mark.blood ? " blood" : "");
        mc.thePlayer.addChatMessage(new ChatComponentText(String.format("damage=%s %s entity=%s attacker=%s weapon=%s%s amount=%.1f -> %s",
            hit.type, flags, dash(hit.entity), dash(hit.attacker), dash(hit.item), hit.groups.isEmpty() ? "" : " [" + hit.groups + "]",
            hit.amount, result)));
    }
    private static String dash(String value) { return value.isEmpty() ? "-" : value; }

    /** Toggles printing every hit's damage details and the mark it got, for writing damage mark rules. */
    public boolean toggleLog() { return logHits = !logHits; }

    public void preview(String name) {
        if (!active()) return;
        if (name.equals("clear")) reset();
        else if (name.equals("damage")) damage(new DamageNetwork.Damage(world.provider.dimensionId, "mob", 6, 0, "Zombie", "Zombie", "", ""));
        else if (name.equals("drowning")) { drowning = 1; drownHit.add(intensity("drowning"), duration("drowning", 12)); }
        else if (name.equals("explosion")) explosion(player.posX, player.posY, player.posZ, 4);
        else if (name.equals("heartbeat")) { pulse.add(1, 40); blur.add(0.5F, 40); sound("heartbeatOut", ConfigCore.heartbeatVolume); }
        else if (name.equals("splash") || name.equals("rain")) { add(name, "water", 8, 100, 0xFFFFFF); blur.add(0.6F, 40); }
        else if (name.equals("sand")) add(name, "sand", 8, 150, 0xFFFFFF);
        else if (name.equals("potion")) add(name, "Overlay/potion", 1, 80, 0x8055FF);
    }

    private static final class Particle {
        final String effect;
        final ResourceLocation texture;
        final float x, y, size, stretch, rotation;
        final int duration, color;
        final boolean overlay;
        int life;
        boolean persistent;
        Particle(String effect, ResourceLocation texture, float x, float y, float size, float stretch, float rotation, int life, int color, boolean overlay) {
            this.effect = effect; this.texture = texture; this.x = x; this.y = y; this.size = size; this.stretch = stretch; this.rotation = rotation;
            this.life = this.duration = life; this.color = color; this.overlay = overlay;
        }
    }
}
