package com.github.parkusisafk.parkusdaklient.handlers;

import com.github.parkusisafk.parkusdaklient.util.AimHelper;
import com.github.parkusisafk.parkusdaklient.util.AimHelper.YawPitch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

/**
 * Stable auto-break:
 * - Smooth randomized turning (shared logic) with face-center aiming when close.
 * - Reachability + line-of-sight check; uses the actual hit face for onPlayerDamageBlock.
 * - Timeout and "unreachable" timeout with chat feedback.
 */
public class BlockBreakingHandler {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rng = new Random();

    private BlockPos target = null;
    private IBlockState originalState = null;
    private boolean active = false;

    // rotation speed ranges (deg per tick)
    private float yawMin = 6.0f, yawRand = 10.0f;     // 2.5..6.0
    private float pitchMin = 6.0f, pitchRand = 10.0f; // 2..5
    private float deadzoneDeg = 10.0f;
    private double nearDistSq = 4.0; // very close if within 2 blocks
    private float slowFactorNear = 0.5f;

    // timers
    private int timeoutTicks = 0;
    private int ticksElapsed = 0;
    private int unreachableTicks = 0;
    private int unreachableTimeout = 20 * 3; // 3s of unreachable before giving up

    public BlockBreakingHandler() { MinecraftForge.EVENT_BUS.register(this); }

    public boolean isActive() { return active; }

    public void startBreaking(BlockPos pos) {
        startBreaking(pos, 20 * 8); // default 8s timeout
    }

    public void startBreaking(BlockPos pos, int timeoutTicks) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        target = pos;
        originalState = mc.theWorld.getBlockState(pos);
        active = true;
        this.timeoutTicks = Math.max(0, timeoutTicks);
        this.ticksElapsed = 0;
        this.unreachableTicks = 0;

        mc.thePlayer.addChatMessage(new ChatComponentText(
                "Breaking: " + originalState.getBlock().getLocalizedName() +
                        " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ() +
                        (timeoutTicks > 0 ? " (timeout " + (timeoutTicks / 20.0) + "s)" : "")
        ));
    }

    public void cancel() {
        active = false;
        target = null;
        originalState = null;
        timeoutTicks = 0;
        ticksElapsed = 0;
        unreachableTicks = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;
        if (mc.theWorld == null || mc.thePlayer == null || target == null) { cancel(); return; }

        // Optional safety: don't act with GUI open
        if (mc.currentScreen != null) return;

        // Aim smoothly & randomly at target (center normally; face center when close)
        facePosSmooth(target);

        // Reachability & line-of-sight; also gives us the correct hit face
        EnumFacing face = getReachableFace(target);
        if (face == null) {
            unreachableTicks++;
            if (unreachableTicks >= unreachableTimeout) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "§eBlock unreachable (timed out): " +
                                target.getX() + "," + target.getY() + "," + target.getZ()
                ));
                cancel();
            }
            return; // wait until reachable
        }
        unreachableTicks = 0;

        // Damage the block as if holding LMB
        mc.playerController.onPlayerDamageBlock(target, face);
        if ((ticksElapsed & 3) == 0) mc.thePlayer.swingItem();

        // Completion: air or state changed
        IBlockState current = mc.theWorld.getBlockState(target);
        if (current.getBlock() == Blocks.air || !current.equals(originalState)) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "Block broken/changed at " + target.getX() + "," + target.getY() + "," + target.getZ()));
            cancel();
            return;
        }

        // Global timeout
        ticksElapsed++;
        if (timeoutTicks > 0 && ticksElapsed >= timeoutTicks) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§eOH SHIT IS THIS A MACRO CHECK??? Breaking timed out after " + (timeoutTicks / 20.0) + "s @ " +
                            target.getX() + "," + target.getY() + "," + target.getZ()));
            cancel();
        }
    }

    /** Stable, smooth, randomized turn; avoids 360° snaps and flip-flop near target. */
    private void facePosSmooth(BlockPos p) {
        Vec3 center = AimHelper.blockCenter(p);
        Vec3 face   = AimHelper.nearestFaceCenterToPlayer(p);

        double ex = mc.thePlayer.posX;
        double ey = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double ez = mc.thePlayer.posZ;

        double distSqCenter = eyeDistSq(ex, ey, ez, center);
        boolean veryClose = distSqCenter < nearDistSq;
        Vec3 aim = veryClose ? face : center;

        YawPitch goal = AimHelper.lookAtPoint(ex, ey, ez, aim.xCoord, aim.yCoord, aim.zCoord);

        float yawErr   = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchErr = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);

        if (veryClose && yawErr < deadzoneDeg && pitchErr < deadzoneDeg) return;

        float yawSpeed   = yawMin   + rng.nextFloat() * yawRand;
        float pitchSpeed = pitchMin + rng.nextFloat() * pitchRand;
        if (veryClose) { yawSpeed *= slowFactorNear; pitchSpeed *= slowFactorNear; }

        float nextYaw   = AimHelper.stepYawTowards(mc.thePlayer.rotationYaw,   goal.yaw,   yawSpeed);
        float nextPitch = AimHelper.stepPitchTowards(mc.thePlayer.rotationPitch, goal.pitch, pitchSpeed);

        float seamDelta = Math.abs(MathHelper.wrapAngleTo180_float(nextYaw - mc.thePlayer.rotationYaw));
        if (seamDelta > 170f) mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;

        mc.thePlayer.rotationYaw   = nextYaw;
        mc.thePlayer.rotationPitch = nextPitch;
    }

    /** Return hit face if target is within reach and line of sight; otherwise null. */
    private EnumFacing getReachableFace(BlockPos pos) {
        if (mc.thePlayer == null || mc.playerController == null) return null;

        float reach = getReachDistance();
        Vec3 eye = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);

        // Trace toward the aim point we're actually rotating to (use face center when close)
        Vec3 center = AimHelper.blockCenter(pos);
        double distSqCenter = eye.squareDistanceTo(center);
        boolean veryClose = distSqCenter < nearDistSq;
        Vec3 aim = veryClose ? AimHelper.nearestFaceCenterToPlayer(pos) : center;

        if (eye.squareDistanceTo(aim) > (double)(reach * reach)) return null;

        // Ray trace (stopOnLiquid=false, ignoreNoBB=true, returnLastUncollidable=false)
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eye, aim, false, true, false);
        if (mop == null) {
            // Nothing in between; choose a face based on player direction
            return mc.thePlayer.getHorizontalFacing().getOpposite();
        }
        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(mop.getBlockPos())) {
            return mop.sideHit;
        }
        return null; // obstructed
    }

    private float getReachDistance() {
        try {
            float r = mc.playerController.getBlockReachDistance();
            if (r > 0) return r;
        } catch (Throwable ignored) {}
        return mc.playerController != null && mc.playerController.isInCreativeMode() ? 5.0f : 4.5f;
    }

    private static double eyeDistSq(double ex, double ey, double ez, Vec3 t) {
        double dx = t.xCoord - ex, dy = t.yCoord - ey, dz = t.zCoord - ez;
        return dx*dx + dy*dy + dz*dz;
    }
}
