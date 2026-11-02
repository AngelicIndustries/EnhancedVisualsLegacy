package com.sonicjumper.enhancedvisuals.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import com.sonicjumper.enhancedvisuals.Base;
import cpw.mods.fml.common.Loader;

/** Real muffling through Sound Physics' low-pass filters, when a version with setMuffle is installed and EFX works. */
final class SoundPhysicsBridge {
    private static MethodHandle setMuffle;
    private static boolean looked;

    private SoundPhysicsBridge() {}

    /** Returns false when Sound Physics cannot muffle, so the caller lowers volume instead. */
    static boolean muffle(float amount) {
        if (!looked) {
            looked = true;
            if (Loader.isModLoaded("soundphysics")) {
                try {
                    Class<?> soundPhysics = Class.forName("com.sonicether.soundphysics.SoundPhysics");
                    setMuffle = MethodHandles.publicLookup().findStatic(soundPhysics, "setMuffle",
                        MethodType.methodType(boolean.class, String.class, float.class));
                    Base.log.info("Muffling sounds through Sound Physics");
                } catch (ReflectiveOperationException e) {
                    Base.log.info("This Sound Physics version cannot muffle sounds; lowering their volume instead");
                }
            }
        }
        if (setMuffle == null) return false;
        try {
            return (boolean) setMuffle.invokeExact(Base.MODID, amount);
        } catch (Throwable e) {
            Base.log.error("Sound Physics muffling failed; lowering volume instead", e);
            setMuffle = null;
            return false;
        }
    }
}
