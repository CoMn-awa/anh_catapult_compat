package com.anhcompat.mixin;

import com.anhcompat.AnhOleoWheelAccess;
import com.anhcompat.AnhWheelRegistry;
import com.github.aero_no_horizon.trackwork.content.track.OleoWheelBlock;
import com.github.aero_no_horizon.trackwork.content.track.OleoWheelBlockEntity;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkSuspensionOverrideAccess;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkSuspensionOverrides;
import com.verr1.synaxis.foundation.physics.BodyId;
import com.verr1.synaxis.foundation.physics.SynaxisPhysics;
import com.verr1.synaxis.foundation.physics.WorldBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the Aero No Horizon oleo wheel behave like a Gearwork oleo mount:
 * exposes its geometry to the catapult, publishes the catapult-driven compression
 * to the wheel travel, and disables the native strut force while captured.
 */
@Mixin(value = OleoWheelBlockEntity.class, remap = false)
public abstract class OleoWheelBlockEntityMixin
        implements AnhOleoWheelAccess, GearworkSuspensionOverrideAccess {
    @Unique
    private GearworkSuspensionOverrides.Slot anhcompat$slot;

    @Unique
    private double anhcompat$frozenCompression = Double.NaN;

    @Shadow
    private float suspensionTravel;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void anhcompat$trackWheel(CallbackInfo ci) {
        OleoWheelBlockEntity self = (OleoWheelBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && !level.isClientSide) {
            AnhWheelRegistry.register(self);
        }
    }

    @Inject(method = "sampleVisualWheelTravel", at = @At("RETURN"), cancellable = true, require = 0)
    private void anhcompat$applyLeaseCompression(CallbackInfoReturnable<Float> cir) {
        double compression = anhcompat$leaseCompressionOrNaN();
        if (!Double.isNaN(compression)) {
            cir.setReturnValue((float) (suspensionTravel - compression + anhcompat$radius() - 0.5F));
        }
    }

    @Inject(method = "canApplySableOleoPhysics", at = @At("HEAD"), cancellable = true, require = 0)
    private void anhcompat$suppressNativeStrut(CallbackInfoReturnable<Boolean> cir) {
        if (!Double.isNaN(anhcompat$leaseCompressionOrNaN())) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public float anhcompat$radius() {
        return ((OleoWheelBlockEntity) (Object) this).getWheelRadius();
    }

    @Override
    public float anhcompat$travel() {
        return this.suspensionTravel;
    }

    @Override
    public float anhcompat$compression() {
        float travel = this.suspensionTravel;
        float radius = anhcompat$radius();
        float wheelTravel = ((OleoWheelBlockEntity) (Object) this).getWheelTravel(1.0F);
        return Math.max(0.0F, Math.min(travel, travel - wheelTravel + radius - 0.5F));
    }

    @Override
    public float anhcompat$horizontalOffset() {
        return ((OleoWheelBlockEntity) (Object) this).getPointHorizontalOffset();
    }

    @Override
    public float anhcompat$axialOffset() {
        return ((OleoWheelBlockEntity) (Object) this).getPointAxialOffset();
    }

    @Override
    public float anhcompat$steeringValue() {
        return ((OleoWheelBlockEntity) (Object) this).getSteeringValue();
    }

    @Override
    public float anhcompat$speed() {
        return ((OleoWheelBlockEntity) (Object) this).getSpeed();
    }

    @Override
    public boolean anhcompat$stowed() {
        return ((OleoWheelBlockEntity) (Object) this).isStowed();
    }

    @Override
    public Direction anhcompat$axleFacing() {
        return ((OleoWheelBlockEntity) (Object) this).getBlockState().getValue(OleoWheelBlock.AXLE_FACING);
    }

    @Override
    public double anhcompat$leaseCompressionOrNaN() {
        GearworkSuspensionOverrides.Slot slot = this.anhcompat$slot;
        OleoWheelBlockEntity self = (OleoWheelBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (slot == null || level == null || level.isClientSide) {
            this.anhcompat$frozenCompression = Double.NaN;
            return Double.NaN;
        }
        BlockPos pos = self.getBlockPos();
        BodyId body = SynaxisPhysics.bodyAt(level, pos).orElse(null);
        if (body == null || body.isGround()) {
            this.anhcompat$frozenCompression = Double.NaN;
            return Double.NaN;
        }
        // The catapult's suspension simulation publishes a live compression through the
        // slot, but the default (hard hinge) capture mode never switches the lease to
        // simulating: it only claims it. Reading the slot alone therefore reports "no
        // lease" in that mode, which left the native strut running against the hinge.
        if (GearworkSuspensionOverrides.suppressesNativeSolve(body, WorldBlockPos.of(level, pos))) {
            double simulated = slot.compressionOr(Double.NaN);
            this.anhcompat$frozenCompression = simulated;
            return simulated;
        }
        if (GearworkSuspensionOverrides.claimed(body, pos)) {
            if (Double.isNaN(this.anhcompat$frozenCompression)) {
                this.anhcompat$frozenCompression = anhcompat$compression();
            }
            return this.anhcompat$frozenCompression;
        }
        this.anhcompat$frozenCompression = Double.NaN;
        return Double.NaN;
    }

    @Override
    public synchronized GearworkSuspensionOverrides.Slot controlEngineering$suspensionOverrideSlot() {
        if (this.anhcompat$slot == null) {
            this.anhcompat$slot = new GearworkSuspensionOverrides.Slot();
        }
        return this.anhcompat$slot;
    }
}
