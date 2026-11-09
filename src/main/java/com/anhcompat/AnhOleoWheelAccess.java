package com.anhcompat;

import net.minecraft.core.Direction;

/**
 * Accessors implemented on the Aero No Horizon oleo wheel block entity by the
 * companion mixin. Everything the catapult needs to describe the strut in the
 * same terms Control Engineering already uses for Gearwork landing gear.
 */
public interface AnhOleoWheelAccess {
    float anhcompat$radius();

    float anhcompat$travel();

    /** Current compression in blocks, in the range {@code [0, travel]}. */
    float anhcompat$compression();

    float anhcompat$horizontalOffset();

    float anhcompat$axialOffset();

    float anhcompat$steeringValue();

    float anhcompat$speed();

    boolean anhcompat$stowed();

    Direction anhcompat$axleFacing();

    /** Compression requested by an active catapult lease, or {@link Double#NaN} when idle. */
    double anhcompat$leaseCompressionOrNaN();
}
