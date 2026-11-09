package com.anhcompat.mixin;

import com.anhcompat.AnhCompat;
import com.anhcompat.AnhWheelAdapter;
import com.github.aero_no_horizon.trackwork.content.track.OleoWheelBlockEntity;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkSuspensionMount;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkWheelAdapter;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkWheelConstraintData;
import com.verr1.synaxis.foundation.physics.BodyId;
import com.verr1.synaxis.foundation.physics.PhysicsBodyView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds Aero No Horizon oleo wheels into the wheel catalogue that the Control
 * Engineering catapult reads. If Gearwork is installed both catalogues are
 * merged; if it is absent the catalogue is served by Aero No Horizon only.
 */
@Mixin(value = GearworkWheelAdapter.class, remap = false)
public abstract class GearworkWheelAdapterMixin {
    @Inject(method = "available", at = @At("RETURN"), cancellable = true, require = 0)
    private static void anhcompat$available(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && AnhCompat.anhLoaded()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "scanAll", at = @At("HEAD"), cancellable = true, require = 0)
    private static void anhcompat$scanAllHead(
            ServerLevel level, PhysicsBodyView body, CallbackInfoReturnable<GearworkWheelAdapter.ScanResult> cir) {
        if (!AnhCompat.gearworkLoaded()) {
            cir.setReturnValue(AnhWheelAdapter.scan(level, body));
        }
    }

    @Inject(method = "scanAll", at = @At("RETURN"), cancellable = true, require = 0)
    private static void anhcompat$scanAllReturn(
            ServerLevel level, PhysicsBodyView body, CallbackInfoReturnable<GearworkWheelAdapter.ScanResult> cir) {
        GearworkWheelAdapter.ScanResult original = cir.getReturnValue();
        GearworkWheelAdapter.ScanResult extra = AnhWheelAdapter.scan(level, body);
        if (extra.wheels().isEmpty()) {
            return;
        }

        List<GearworkWheelConstraintData> merged = new ArrayList<>(original.wheels());
        merged.addAll(extra.wheels());

        EnumMap<GearworkWheelAdapter.SkipReason, Integer> skips =
                new EnumMap<>(GearworkWheelAdapter.SkipReason.class);
        skips.putAll(original.skipCounts());
        for (Map.Entry<GearworkWheelAdapter.SkipReason, Integer> entry : extra.skipCounts().entrySet()) {
            skips.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        String failure = original.firstException().isEmpty()
                ? extra.firstException()
                : original.firstException();
        cir.setReturnValue(new GearworkWheelAdapter.ScanResult(
                merged,
                original.leaseCount() + extra.leaseCount(),
                skips,
                failure));
    }

    @Inject(method = "readSuspensionMount", at = @At("HEAD"), cancellable = true, require = 0)
    private static void anhcompat$readSuspensionMount(
            ServerLevel level,
            PhysicsBodyView body,
            BlockPos mountPos,
            CallbackInfoReturnable<Optional<GearworkSuspensionMount>> cir) {
        if (level != null && mountPos != null && level.hasChunkAt(mountPos)) {
            BlockEntity blockEntity = level.getBlockEntity(mountPos);
            if (blockEntity instanceof OleoWheelBlockEntity) {
                cir.setReturnValue(AnhWheelAdapter.suspensionMount(level, body, mountPos));
                return;
            }
        }
        if (!AnhCompat.gearworkLoaded()) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "sumSupportWeights", at = @At("HEAD"), cancellable = true, require = 0)
    private static void anhcompat$sumSupportWeightsHead(
            ServerLevel level, BodyId body, CallbackInfoReturnable<Double> cir) {
        if (!AnhCompat.gearworkLoaded()) {
            cir.setReturnValue(AnhWheelAdapter.supportWeight(level, body));
        }
    }

    @Inject(method = "sumSupportWeights", at = @At("RETURN"), cancellable = true, require = 0)
    private static void anhcompat$sumSupportWeightsReturn(
            ServerLevel level, BodyId body, CallbackInfoReturnable<Double> cir) {
        double extra = AnhWheelAdapter.supportWeight(level, body);
        if (extra > 0.0) {
            cir.setReturnValue(cir.getReturnValueD() + extra);
        }
    }

    @Inject(method = "isDeployed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void anhcompat$isDeployed(
            ServerLevel level, BlockPos mountPos, CallbackInfoReturnable<Boolean> cir) {
        if (level != null && mountPos != null && level.hasChunkAt(mountPos)) {
            BlockEntity blockEntity = level.getBlockEntity(mountPos);
            if (blockEntity instanceof OleoWheelBlockEntity oleo) {
                cir.setReturnValue(!oleo.isRemoved() && !oleo.isStowed());
                return;
            }
        }
        if (!AnhCompat.gearworkLoaded()) {
            cir.setReturnValue(false);
        }
    }
}
