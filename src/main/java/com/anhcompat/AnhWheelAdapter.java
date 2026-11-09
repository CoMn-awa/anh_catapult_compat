package com.anhcompat;

import com.github.aero_no_horizon.trackwork.content.track.OleoWheelBlockEntity;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkSuspensionMount;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkSuspensionParameters;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkVisualAxleGeometry;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkWheelAdapter;
import com.pb7ap8lthh.controlengineering.integration.gearwork.synaxis.physics.GearworkWheelConstraintData;
import com.verr1.synaxis.foundation.physics.BodyId;
import com.verr1.synaxis.foundation.physics.PhysicsBodyView;
import com.verr1.synaxis.foundation.physics.SynaxisPhysics;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

/**
 * Translates Aero No Horizon oleo wheels into the wheel/suspension descriptors
 * Control Engineering already understands for Gearwork landing gear.
 */
public final class AnhWheelAdapter {
    private static final double SUPPORT_WEIGHT = 1.0;
    // Mirrors Gearwork's landing gear profile so the strut behaves the same way.
    private static final double TARGET_COMPRESSION_RATIO = 0.45;
    private static final double DAMPING_RATIO = 1.05;
    private static final double WHEEL_WIDTH = 0.45;

    private static final Vector3d MODEL_DOWN = new Vector3d(0.0, -1.0, 0.0);
    private static final Vector3d MODEL_EAST = new Vector3d(1.0, 0.0, 0.0);
    private static final Vector3d MODEL_SOUTH = new Vector3d(0.0, 0.0, 1.0);

    private AnhWheelAdapter() {
    }

    public static boolean isAnhWheel(BlockEntity blockEntity) {
        return blockEntity instanceof OleoWheelBlockEntity;
    }

    public static GearworkWheelAdapter.ScanResult scan(ServerLevel level, PhysicsBodyView body) {
        List<GearworkWheelConstraintData> wheels = new ArrayList<>();
        EnumMap<GearworkWheelAdapter.SkipReason, Integer> skips =
                new EnumMap<>(GearworkWheelAdapter.SkipReason.class);
        String firstFailure = "";

        if (level == null || !isUsableBody(level, body)) {
            skips.merge(GearworkWheelAdapter.SkipReason.INVALID_BODY, 1, Integer::sum);
            return new GearworkWheelAdapter.ScanResult(List.of(), 0, skips, firstFailure);
        }

        for (OleoWheelBlockEntity oleo : AnhWheelRegistry.snapshot(level)) {
            if (oleo.isRemoved()) {
                continue;
            }
            try {
                if (!(oleo instanceof AnhOleoWheelAccess)) {
                    skips.merge(GearworkWheelAdapter.SkipReason.INCOMPATIBLE_API, 1, Integer::sum);
                    continue;
                }
                BlockPos pos = oleo.getBlockPos();
                if (!level.hasChunkAt(pos)) {
                    skips.merge(GearworkWheelAdapter.SkipReason.MOUNT_UNLOADED, 1, Integer::sum);
                    continue;
                }
                if (oleo.isStowed()) {
                    skips.merge(GearworkWheelAdapter.SkipReason.NOT_DEPLOYED, 1, Integer::sum);
                    continue;
                }
                if (!bodyMatches(level, body.bodyId(), pos)) {
                    skips.merge(GearworkWheelAdapter.SkipReason.BODY_MISMATCH, 1, Integer::sum);
                    continue;
                }
                Optional<GearworkWheelConstraintData> wheel = wheel(level, body, oleo);
                if (wheel.isPresent()) {
                    wheels.add(wheel.get());
                } else {
                    skips.merge(GearworkWheelAdapter.SkipReason.INVALID_SAMPLE, 1, Integer::sum);
                }
            } catch (RuntimeException failure) {
                skips.merge(GearworkWheelAdapter.SkipReason.SCAN_EXCEPTION, 1, Integer::sum);
                if (firstFailure.isEmpty()) {
                    firstFailure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                }
            }
        }

        return new GearworkWheelAdapter.ScanResult(wheels, wheels.size(), skips, firstFailure);
    }

    public static Optional<GearworkWheelConstraintData> wheel(
            ServerLevel level, PhysicsBodyView body, OleoWheelBlockEntity oleo) {
        Geometry geometry = geometry(level, body, oleo);
        if (geometry == null) {
            return Optional.empty();
        }
        AnhOleoWheelAccess access = (AnhOleoWheelAccess) (Object) oleo;
        try {
            Vector3d anchorModel = new Vector3d(
                    geometry.pos.getX() + 0.5, geometry.pos.getY() + 0.5, geometry.pos.getZ() + 0.5);
            Vector3d anchorBody = body.modelToBodyPosition(anchorModel);
            Vector3d wheelCenterBody = body.modelToBodyPosition(geometry.axleModel);
            Vector3d wheelCenterWorld = body.modelToWorldPosition(geometry.axleModel);
            Vector3d suspensionWorld = body.bodyToWorldDirection(geometry.suspensionAxisBody);
            Vector3d driveWorld = body.bodyToWorldDirection(geometry.driveAxisBody);
            Vector3d axleWorld = body.bodyToWorldDirection(geometry.axleAxisBody);
            double rpm = finiteOrZero(access.anhcompat$speed());
            double steer = finiteOrZero(access.anhcompat$steeringValue());

            return Optional.of(new GearworkWheelConstraintData(
                    body.bodyId(),
                    geometry.pos,
                    anchorBody,
                    wheelCenterBody,
                    wheelCenterWorld,
                    new Vector3d(geometry.axleModel),
                    geometry.suspensionAxisBody,
                    geometry.driveAxisBody,
                    geometry.axleAxisBody,
                    suspensionWorld,
                    driveWorld,
                    axleWorld,
                    geometry.radius,
                    geometry.radius,
                    WHEEL_WIDTH,
                    geometry.travel,
                    geometry.compression,
                    geometry.compression > 1.0E-4,
                    rpm,
                    steer,
                    level.getGameTime()));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    public static Optional<GearworkSuspensionMount> suspensionMount(
            ServerLevel level, PhysicsBodyView body, BlockPos mountPos) {
        if (level == null || mountPos == null || !level.hasChunkAt(mountPos)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(mountPos);
        if (!(blockEntity instanceof OleoWheelBlockEntity oleo) || oleo.isRemoved()) {
            return Optional.empty();
        }
        Geometry geometry = geometry(level, body, oleo);
        if (geometry == null || !geometry.pos.equals(mountPos)) {
            return Optional.empty();
        }
        try {
            GearworkSuspensionParameters parameters = new GearworkSuspensionParameters(
                    geometry.travel, SUPPORT_WEIGHT, TARGET_COMPRESSION_RATIO, DAMPING_RATIO);
            return Optional.of(new GearworkSuspensionMount(
                    body.bodyId(),
                    geometry.pos,
                    geometry.visual,
                    geometry.suspensionAxisBody,
                    geometry.axleAxisBody,
                    parameters,
                    geometry.radius));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    public static double supportWeight(ServerLevel level, BodyId bodyId) {
        if (level == null || bodyId == null || bodyId.isGround()) {
            return 0.0;
        }
        double total = 0.0;
        for (OleoWheelBlockEntity oleo : AnhWheelRegistry.snapshot(level)) {
            try {
                if (oleo.isRemoved() || oleo.isStowed() || !(oleo instanceof AnhOleoWheelAccess)) {
                    continue;
                }
                if (bodyMatches(level, bodyId, oleo.getBlockPos())) {
                    total += SUPPORT_WEIGHT;
                }
            } catch (RuntimeException ignored) {
                // Ignore wheels that cannot be resolved right now.
            }
        }
        return total;
    }

    public static boolean isDeployed(ServerLevel level, BlockPos mountPos) {
        if (level == null || mountPos == null || !level.hasChunkAt(mountPos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(mountPos);
        return blockEntity instanceof OleoWheelBlockEntity oleo
                && !oleo.isRemoved()
                && !oleo.isStowed();
    }

    private static Geometry geometry(ServerLevel level, PhysicsBodyView body, OleoWheelBlockEntity oleo) {
        if (level == null || !isUsableBody(level, body) || !(oleo instanceof AnhOleoWheelAccess access)) {
            return null;
        }
        BlockPos pos = oleo.getBlockPos();
        if (!level.hasChunkAt(pos) || oleo.isRemoved() || access.anhcompat$stowed()) {
            return null;
        }
        if (!bodyMatches(level, body.bodyId(), pos)) {
            return null;
        }

        Direction axleFacing = access.anhcompat$axleFacing();
        if (axleFacing == null || !axleFacing.getAxis().isHorizontal()) {
            return null;
        }
        double radius = access.anhcompat$radius();
        double travel = access.anhcompat$travel();
        if (!Double.isFinite(radius) || radius <= 0.0 || !Double.isFinite(travel) || travel <= 0.0) {
            return null;
        }
        double compression = clamp(access.anhcompat$compression(), 0.0, travel);

        GearworkVisualAxleGeometry.Mount visual;
        try {
            // Aero No Horizon reports wheel travel as the axle distance below the block
            // centre offset by radius - 0.5, so the mount pivot sits radius - 0.5 below
            // the block centre while Gearwork anchors the oleo at the block centre.
            visual = new GearworkVisualAxleGeometry.Mount(
                    axleFacing,
                    access.anhcompat$horizontalOffset(),
                    access.anhcompat$axialOffset(),
                    radius - 0.5,
                    radius,
                    travel);
        } catch (RuntimeException failure) {
            return null;
        }

        Vector3d axleDirectionModel = new Vector3d(axleFacing.getStepX(), 0.0, axleFacing.getStepZ());
        Vector3d forwardModel = axleFacing.getAxis() == Direction.Axis.X ? MODEL_SOUTH : MODEL_EAST;
        Vector3d suspensionAxisBody = toBodyDirection(body, MODEL_DOWN);
        Vector3d axleAxisBody = toBodyDirection(body, axleDirectionModel);
        Vector3d driveAxisBody = toBodyDirection(body, forwardModel);
        if (suspensionAxisBody == null || axleAxisBody == null || driveAxisBody == null) {
            return null;
        }
        if (Math.abs(suspensionAxisBody.dot(axleAxisBody)) > 0.99) {
            return null;
        }

        return new Geometry(
                pos,
                visual,
                visual.axleModel(pos, compression),
                suspensionAxisBody,
                axleAxisBody,
                driveAxisBody,
                radius,
                travel,
                compression);
    }

    private static boolean isUsableBody(ServerLevel level, PhysicsBodyView body) {
        if (body == null) {
            return false;
        }
        BodyId bodyId = body.bodyId();
        return bodyId != null
                && !bodyId.isGround()
                && bodyId.dimension().equals(level.dimension());
    }

    private static boolean bodyMatches(ServerLevel level, BodyId bodyId, BlockPos pos) {
        return SynaxisPhysics.bodyAt(level, pos).filter(bodyId::equals).isPresent();
    }

    private static Vector3d toBodyDirection(PhysicsBodyView body, Vector3d modelDirection) {
        Vector3d world = body.modelToWorld().transformDirection(modelDirection, new Vector3d());
        Vector3d bodyDirection = body.worldToBody().transformDirection(world, new Vector3d());
        if (!bodyDirection.isFinite() || bodyDirection.lengthSquared() < 1.0E-12) {
            return null;
        }
        return bodyDirection.normalize();
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Geometry(
            BlockPos pos,
            GearworkVisualAxleGeometry.Mount visual,
            Vector3d axleModel,
            Vector3d suspensionAxisBody,
            Vector3d axleAxisBody,
            Vector3d driveAxisBody,
            double radius,
            double travel,
            double compression) {
    }
}
