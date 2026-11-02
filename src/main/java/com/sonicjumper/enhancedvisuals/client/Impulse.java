package com.sonicjumper.enhancedvisuals.client;

/** A bounded tick-based envelope. Overlapping events merge so neither gets weaker or shorter. */
public final class Impulse {
    private float peak;
    private int remaining, duration;
    /** Returns false when the current envelope already covers this event. */
    public boolean add(float strength, int ticks) {
        if (!Float.isFinite(strength) || strength <= 0 || ticks <= 0) return false;
        strength = Math.min(1, strength);
        ticks = Math.min(1200, ticks);
        float current = value(0);
        if (current >= strength && remaining >= ticks) return false;
        peak = Math.max(current, strength);
        remaining = duration = Math.max(remaining, ticks);
        return true;
    }
    public void tick() { if (remaining > 0) remaining--; }
    public float value(float partial) { return duration == 0 ? 0 : peak * Math.max(0, remaining - partial) / duration; }
    public void clear() { peak = 0; remaining = duration = 0; }
}
