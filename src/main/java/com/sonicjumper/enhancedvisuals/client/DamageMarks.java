package com.sonicjumper.enhancedvisuals.client;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sonicjumper.enhancedvisuals.Base;
import com.sonicjumper.enhancedvisuals.network.DamageNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Which marks a hit leaves, from config/sonicvisuals-damage.json. The most specific matching rule wins: more conditions
 * beat fewer, an exact entity or item beats a group, a subclass beats its parent, and ties go to the earlier rule.
 */
public final class DamageMarks {
    static final String FILE = "sonicvisuals-damage.json";
    private final Map<String, Mark> marks = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public void load(File configDir) {
        File file = new File(configDir, FILE);
        try {
            if (!file.exists()) {
                try (InputStream in = defaults()) { Files.copy(in, file.toPath()); }
            }
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) { parse(reader); }
        } catch (Exception e) {
            Base.log.error("Could not read {}; using the built-in damage marks", file, e);
            try (Reader reader = new InputStreamReader(defaults(), StandardCharsets.UTF_8)) { parse(reader); }
            catch (Exception inner) { Base.log.error("Could not read the built-in damage marks", inner); }
        }
    }

    private static InputStream defaults() {
        return DamageMarks.class.getResourceAsStream("/assets/" + Base.MODID + "/damage.json");
    }

    private void parse(Reader reader) {
        JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
        Map<String, Mark> newMarks = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("marks").entrySet()) {
            JsonObject json = entry.getValue().getAsJsonObject();
            Mark mark = new Mark(entry.getKey());
            mark.blood = json.has("blood") && json.get("blood").getAsBoolean();
            if (json.has("color")) mark.color = color(json.get("color"));
            if (json.has("size")) {
                JsonArray size = json.getAsJsonArray("size");
                mark.minSize = size.get(0).getAsFloat();
                mark.maxSize = size.get(size.size() - 1).getAsFloat();
            }
            if (json.has("stretch")) mark.stretch = json.get("stretch").getAsFloat();
            if (json.has("life")) mark.life = json.get("life").getAsInt();
            newMarks.put(mark.name, mark);
        }
        List<Rule> newRules = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("rules")) {
            JsonObject json = element.getAsJsonObject();
            Rule rule = new Rule();
            rule.damage = strings(json.get("damage"));
            rule.entity = strings(json.get("entity"));
            rule.attacker = strings(json.get("attacker"));
            rule.weapon = strings(json.get("weapon"));
            for (int i = 0; i < DamageNetwork.FLAG_NAMES.length; i++) {
                JsonElement flag = json.get(DamageNetwork.FLAG_NAMES[i]);
                if (flag == null) continue;
                rule.flagMask |= 1 << i;
                if (flag.getAsBoolean()) rule.flagValues |= 1 << i;
            }
            String mark = json.has("mark") ? json.get("mark").getAsString() : "splatter";
            if (!mark.equals("none") && !newMarks.containsKey(mark)) newMarks.put(mark, new Mark(mark));
            rule.mark = mark.equals("none") ? null : newMarks.get(mark);
            rule.color = json.has("color") ? color(json.get("color")) : null;
            rule.amount = json.has("amount") ? json.get("amount").getAsFloat() : 1;
            rule.max = json.has("max") ? json.get("max").getAsInt() : 40;
            newRules.add(rule);
        }
        marks.clear(); marks.putAll(newMarks);
        rules.clear(); rules.addAll(newRules);
    }

    /** A mark set by name; unknown names still work as long as their textures exist. */
    public Mark mark(String name) { return marks.computeIfAbsent(name, Mark::new); }

    /** The most specific rule matching this hit, or null when none does. */
    public Rule resolve(DamageNetwork.Damage hit) {
        Class<?> entity = entityClass(hit.entity), attacker = entityClass(hit.attacker);
        Set<String> groups = new HashSet<>(Arrays.asList(hit.groups.split(",")));
        groups.remove("");
        Rule best = null;
        int bestScore = -1;
        for (Rule rule : rules) {
            // A damage type or property check outranks a bare @living, but not a named entity.
            int conditions = Integer.bitCount(rule.flagMask), score = 3 * conditions;
            if ((hit.flags & rule.flagMask) != rule.flagValues) continue;
            if (rule.damage != null) {
                if (!rule.damage.contains(hit.type)) continue;
                conditions++; score += 3;
            }
            if (rule.entity != null) {
                int match = matchEntity(rule.entity, hit.entity, entity);
                if (match < 0) continue;
                conditions++; score += match;
            }
            if (rule.attacker != null) {
                int match = matchEntity(rule.attacker, hit.attacker, attacker);
                if (match < 0) continue;
                conditions++; score += match;
            }
            if (rule.weapon != null) {
                int match = matchWeapon(rule.weapon, hit.item, groups);
                if (match < 0) continue;
                conditions++; score += match;
            }
            score += conditions * 100;
            if (score > bestScore) { best = rule; bestScore = score; }
        }
        return best;
    }

    /** How closely an entity entry matches: deeper classes are more specific. -1 when nothing matches. */
    private static int matchEntity(List<String> wanted, String name, Class<?> type) {
        int best = -1;
        for (String entry : wanted) {
            if (entry.equals("@none")) { if (name.isEmpty()) best = Math.max(best, 1); continue; }
            if (entry.equals(name)) { best = Math.max(best, type == null ? 20 : depth(type)); continue; }
            if (type == null) continue;
            Class<?> wantedType = entry.equals("@living") ? EntityLivingBase.class : entry.equals("@projectile") ? IProjectile.class
                : entry.startsWith("@") ? null : entityClass(entry);
            if (wantedType != null && wantedType.isAssignableFrom(type)) best = Math.max(best, depth(wantedType));
        }
        return best;
    }

    private static int matchWeapon(List<String> wanted, String item, Set<String> groups) {
        int best = -1;
        for (String entry : wanted) {
            if (entry.equals("@none")) { if (item.isEmpty()) best = Math.max(best, 10); }
            else if (entry.equals("@any")) { if (!item.isEmpty()) best = Math.max(best, 15); }
            else if (entry.startsWith("@")) { if (groups.contains(entry.substring(1))) best = Math.max(best, 20); }
            else if (entry.equals(item)) best = Math.max(best, 30);
        }
        return best;
    }

    private static int depth(Class<?> type) {
        if (type.isInterface()) return 1;
        int depth = 0;
        for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) depth++;
        return depth;
    }

    /** Registered entity names, Player, or full class names. */
    private static Class<?> entityClass(String name) {
        if (name.isEmpty() || name.startsWith("@")) return null;
        if (name.equals("Player")) return EntityPlayer.class;
        Object registered = EntityList.stringToClassMapping.get(name);
        if (registered instanceof Class) return (Class<?>) registered;
        if (name.indexOf('.') < 0) return null;
        try { return Class.forName(name, false, DamageMarks.class.getClassLoader()); }
        catch (ClassNotFoundException | LinkageError e) { return null; }
    }

    private static List<String> strings(JsonElement json) {
        if (json == null) return null;
        List<String> result = new ArrayList<>();
        if (json.isJsonArray()) for (JsonElement element : json.getAsJsonArray()) result.add(element.getAsString());
        else result.add(json.getAsString());
        return result;
    }

    private static int color(JsonElement json) {
        return Integer.parseInt(json.getAsString().replace("#", ""), 16) & 0xFFFFFF;
    }

    public static final class Mark {
        public final String name;
        public boolean blood;
        public int color = 0xFFFFFF, life = 240;
        public float minSize = 0.08F, maxSize = 0.26F, stretch = 1;
        Mark(String name) { this.name = name; }
        public String texture() { return "Splat/" + name; }
    }

    public static final class Rule {
        List<String> damage, entity, attacker, weapon;
        int flagMask, flagValues;
        public Mark mark;
        public Integer color;
        public float amount;
        public int max;
    }
}
