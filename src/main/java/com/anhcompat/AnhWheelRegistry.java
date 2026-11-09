package com.anhcompat;

import com.github.aero_no_horizon.trackwork.content.track.OleoWheelBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;

/**
 * Tracks live oleo wheel block entities so the catapult can enumerate them
 * without walking every chunk of a sub-level.
 */
public final class AnhWheelRegistry {
    private static final Set<OleoWheelBlockEntity> WHEELS = ConcurrentHashMap.newKeySet();

    private AnhWheelRegistry() {
    }

    public static void register(OleoWheelBlockEntity wheel) {
        Level level = wheel.getLevel();
        if (level != null && !level.isClientSide) {
            WHEELS.add(wheel);
        }
    }

    public static List<OleoWheelBlockEntity> snapshot(Level level) {
        List<OleoWheelBlockEntity> result = new ArrayList<>();
        for (OleoWheelBlockEntity wheel : WHEELS) {
            if (wheel.isRemoved()) {
                WHEELS.remove(wheel);
                continue;
            }
            if (wheel.getLevel() == level) {
                result.add(wheel);
            }
        }
        return result;
    }
}
