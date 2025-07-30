package com.github.parkusisafk.parkusdaklient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Shared aiming utilities: stable angle stepping, target points, etc.
 */
public final class AimHelper {

    private AimHelper() {}

    /** Compute yaw/pitch (degrees) needed to look from eye to target point. */
    public static YawPitch lookAtPoint(double fromX, double fromY, double fromZ,
                                       double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 90F;
        float pitch = (float)(-(Math.atan2(dy, distXZ) * (180D / Math.PI)));
        return new YawPitch(yaw, clampPitch(pitch));
    }

    /** Returns a point at the center of the block. */
    public static Vec3 blockCenter(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    /**
     * When very close, aiming at center can flip; aim at the nearest face center instead.
     * Chooses the face whose normal points most toward the player.
     */
    public static Vec3 nearestFaceCenterToPlayer(BlockPos p) {
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double pz = mc.thePlayer.posZ;

        double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
        double vx = px - cx, vy = py - cy, vz = pz - cz;

        // pick dominant axis in the horizontal plane (east/west vs north/south)
        if (Math.abs(vx) > Math.abs(vz)) {
            // east/west face
            double x = (vx > 0) ? p.getX() + 1.001 : p.getX() - 0.001;
            return new Vec3(x, cy, cz);
        } else {
            // north/south face
            double z = (vz > 0) ? p.getZ() + 1.001 : p.getZ() - 0.001;
            return new Vec3(cx, cy, z);
        }
    }

    /** Step current yaw toward target yaw by maxStep degrees along the shortest path. */
    public static float stepYawTowards(float currentYaw, float targetYaw, float maxStepDeg) {
        float delta = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw); // [-180,180]
        if (delta >  maxStepDeg) delta =  maxStepDeg;
        if (delta < -maxStepDeg) delta = -maxStepDeg;
        return currentYaw + delta; // do NOT wrap final absolute yaw
    }

    /** Step current pitch toward target pitch by maxStep degrees, clamped to [-90, 90]. */
    public static float stepPitchTowards(float currentPitch, float targetPitch, float maxStepDeg) {
        float delta = targetPitch - currentPitch; // pitch doesn't need 180-wrap
        if (delta >  maxStepDeg) delta =  maxStepDeg;
        if (delta < -maxStepDeg) delta = -maxStepDeg;
        float next = currentPitch + delta;
        return clampPitch(next);
    }

    public static float clampPitch(float pitch) {
        if (pitch >  90f) return  90f;
        if (pitch < -90f) return -90f;
        return pitch;
    }

    /** Simple tuple for yaw/pitch. */
    public static final class YawPitch {
        public final float yaw;
        public final float pitch;
        public YawPitch(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
