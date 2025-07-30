package com.github.parkusisafk.parkusdaklient.handlers;

import com.github.parkusisafk.parkusdaklient.util.AimHelper;
import com.github.parkusisafk.parkusdaklient.util.AimHelper.YawPitch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Auto-walk with stable, smooth, randomized turning toward a target BlockPos.
 * - Wraps ONLY the delta (no absolute yaw wrapping), avoids 360° snaps.
 * - Deadzone + slowdown when close; optionally aim at nearest face for stability.
 * - Won't fight real user input (only releases W if we pressed it).
 */
public class MoveForwardHandler {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rng = new Random();

    private int ticksRemaining = 0;
    private BooleanSupplier keepMoving = null;
    private int safetyCap = 0;
    private BlockPos lookTarget = null;

    private boolean wePressedForward = false;

    // Turning speed (degrees/tick), randomized each tick: min + rng*rand
    private float yawMin = 6.0f,  yawRand = 10.0f;   // 2..5
    private float pitchMin = 6.0f, pitchRand = 10.0f; // 2..5

    // Behavior near target
    private float deadzoneDeg = 10.0f;       // if yaw/pitch error < this, skip correction
    private double nearDistSq = 4.0;        // "very close" ≈ within 2 blocks
    private float slowFactorNear = 0.5f;    // slow turning when near

    public MoveForwardHandler() { MinecraftForge.EVENT_BUS.register(this); }

    public void cancel() {
        setForward(false, true);
        ticksRemaining = 0;
        keepMoving = null;
        safetyCap = 0;
        lookTarget = null;
    }

    /** Move forward for exactly n ticks (no rotation). */
    public void moveForTicks(int ticks) {
        ticksRemaining = Math.max(ticksRemaining, ticks);
        keepMoving = null;
        safetyCap = 0;
        lookTarget = null;
    }

    /** Move while condition holds; also face target each tick with smooth randomized rotation. */
    public void moveWhileFacing(BooleanSupplier cond, int safetyCapTicks, BlockPos target) {
        keepMoving = cond;
        safetyCap = safetyCapTicks;
        lookTarget = target;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.thePlayer == null || mc.theWorld == null) { cancel(); return; }

        // Safer: do not auto-walk when GUI is open
        if (mc.currentScreen != null) { setForward(false, false); return; }

        boolean shouldMove = false;

        if (ticksRemaining > 0) { shouldMove = true; ticksRemaining--; }

        if (keepMoving != null) {
            if (keepMoving.getAsBoolean()) {
                shouldMove = true;
                if (safetyCap > 0) safetyCap--; else cancel();
            } else {
                cancel();
            }
        }

        if (shouldMove) {
            if (lookTarget != null) facePosSmooth(lookTarget);
            setForward(true, false);
        } else {
            setForward(false, false);
        }
    }

    /** Press/release forward key (W) without fighting real user input. */
    private void setForward(boolean press, boolean forceRelease) {
        int forwardKey = mc.gameSettings.keyBindForward.getKeyCode();
        if (press) {
            if (!Keyboard.isKeyDown(forwardKey)) {
                KeyBinding.setKeyBindState(forwardKey, true);
                wePressedForward = true;
            }
        } else {
            if ((wePressedForward && !Keyboard.isKeyDown(forwardKey)) || forceRelease) {
                KeyBinding.setKeyBindState(forwardKey, false);
                wePressedForward = false;
            }
        }
    }

    /** Stable, smooth, randomized turn toward block; no 360° snaps. */
    private void facePosSmooth(BlockPos target) {
        // Choose aim point: center normally; nearest face center when very close
        Vec3 center = AimHelper.blockCenter(target);
        Vec3 face = AimHelper.nearestFaceCenterToPlayer(target);

        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;

        double distSqCenter = eyeDistSq(eyeX, eyeY, eyeZ, center);
        boolean veryClose = distSqCenter < nearDistSq;

        Vec3 aim = veryClose ? face : center;
        YawPitch goal = AimHelper.lookAtPoint(eyeX, eyeY, eyeZ, aim.xCoord, aim.yCoord, aim.zCoord);

        float yawErr   = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchErr = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);

        // Deadzone: if already very close and angles small, skip correction this tick
        if (veryClose && yawErr < deadzoneDeg && pitchErr < deadzoneDeg) return;

        // Random per-tick speed, slowed when very close
        float yawSpeed   = yawMin   + rng.nextFloat() * yawRand;
        float pitchSpeed = pitchMin + rng.nextFloat() * pitchRand;
        if (veryClose) { yawSpeed *= slowFactorNear; pitchSpeed *= slowFactorNear; }

        float nextYaw   = AimHelper.stepYawTowards(mc.thePlayer.rotationYaw,   goal.yaw,   yawSpeed);
        float nextPitch = AimHelper.stepPitchTowards(mc.thePlayer.rotationPitch, goal.pitch, pitchSpeed);

        // If we cross the -180/180 seam with a large step, keep interpolation tight
        float seamDelta = Math.abs(MathHelper.wrapAngleTo180_float(nextYaw - mc.thePlayer.rotationYaw));
        if (seamDelta > 170f) {
            mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;
        }

        mc.thePlayer.rotationYaw   = nextYaw;
        mc.thePlayer.rotationPitch = nextPitch;
    }

    private static double eyeDistSq(double ex, double ey, double ez, Vec3 target) {
        double dx = target.xCoord - ex;
        double dy = target.yCoord - ey;
        double dz = target.zCoord - ez;
        return dx*dx + dy*dy + dz*dz;
    }
}
