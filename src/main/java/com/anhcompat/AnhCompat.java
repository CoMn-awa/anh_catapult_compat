package com.anhcompat;

import net.neoforged.fml.ModList;

/**
 * Runtime mod-presence helpers, kept free of direct references to optional mods.
 */
public final class AnhCompat {
    private AnhCompat() {
    }

    public static boolean anhLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded("aero_no_horizon");
    }

    public static boolean gearworkLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded("gearwork");
    }
}
