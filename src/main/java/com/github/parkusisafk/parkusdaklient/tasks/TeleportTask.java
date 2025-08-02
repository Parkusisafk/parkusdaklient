package com.github.parkusisafk.parkusdaklient.tasks;

import com.github.parkusisafk.parkusdaklient.util.AimHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.util.ChatComponentText;
import com.github.parkusisafk.parkusdaklient.macro.MacroCheckDetector;
import java.util.Random;

public class TeleportTask extends Task {
    private final Minecraft mc = Minecraft.getMinecraft();

    private boolean started = false;
    private int tickCounter = 0;
    private boolean sneaking = false;
    private final Random rng = new Random();

    // Turning speed params
    private float yawMin = 6.0f, yawRand = 10.0f;
    private float pitchMin = 6.0f, pitchRand = 10.0f;

    // Behavior near target
    private float deadzoneDeg = 10.0f;       // if yaw/pitch error < this, skip correction
    private double nearDistSq = 4.0;         // within 2 blocks radius
    private float slowFactorNear = 0.5f;     // slow turning when near

    public TeleportTask(BlockPos pos) {
        super(pos);
    }

    @Override
    public String getDescription() {
        return "Teleport to: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public void start() {
        MacroCheckDetector.INSTANCE.setTeleporting(true);
        started = true;
        tickCounter = 0;

        mc.thePlayer.inventory.currentItem = 3; // switch to hotbar slot 4 (index 3)
    }
    private float targetYawSpeed = 8f;
    private float targetPitchSpeed = 8f;

    boolean teleported = false;
    int bogcounter = 0;
    @Override
    public void update() {
        if (!started || mc.thePlayer == null) return;

        // Calculate if very close to target (for slower turning)
        if(!isFacingTarget(pos) && !teleported){
            facePosSmooth(pos);
        }
        else{
            bogcounter++;
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null) {
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
                teleported = true;

            }
        }

        tickCounter++;

        if(bogcounter == 5){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            sneaking = false;
        } else if (bogcounter == 10) {
            mc.thePlayer.inventory.currentItem = 0;
        }

            // Start sneaking on tick 3
        if (tickCounter == 3) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            MacroCheckDetector.INSTANCE.setTeleporting(true);
            sneaking = true;
        }
        // Right click on tick 5 (only if holding item)
//        else if (tickCounter == 7) {
//            ItemStack held = mc.thePlayer.getHeldItem();
//            if (held != null) {
//                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
//            }
//        }
        // Stop sneaking on tick 8

        // Switch back to slot 1 on tick 10

        // Timeout warning at tick 40 if not reached destination
        if (tickCounter ==150 && !isAtDestination()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[TeleportTask] IS THIS A MACRO CHECK??? Timeout: Unable to reach destination."));
            MacroCheckDetector.INSTANCE.setTeleporting(false);

        }
    }
    private void facePosSmooth(BlockPos p) {
        Vec3 center = AimHelper.blockCenter(p);
        Vec3 face   = AimHelper.nearestFaceCenterToPlayer(p);

        double ex = mc.thePlayer.posX;
        double ey = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double ez = mc.thePlayer.posZ;

        double distSqCenter = eyeDistSq(ex, ey, ez, center);
        boolean veryClose = distSqCenter < nearDistSq;
        Vec3 aim = veryClose ? face : center;

        AimHelper.YawPitch goal = AimHelper.lookAtPoint(ex, ey, ez, aim.xCoord, aim.yCoord, aim.zCoord);

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
    private static double eyeDistSq(double ex, double ey, double ez, Vec3 t) {
        double dx = t.xCoord - ex, dy = t.yCoord - ey, dz = t.zCoord - ez;
        return dx*dx + dy*dy + dz*dz;
    }
    private boolean isFacingTarget(BlockPos target) {
        Vec3 center = AimHelper.blockCenter(target);

        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;

        AimHelper.YawPitch goal = AimHelper.lookAtPoint(eyeX, eyeY, eyeZ, center.xCoord, center.yCoord, center.zCoord);

        float yawErr   = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchErr = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);

        // Define a small threshold (degrees) for acceptable aim error before moving forward
        final float aimingThreshold = 5.0f;

        return yawErr < aimingThreshold && pitchErr < aimingThreshold;
    }

    @Override
    public boolean isFinished() {
        if (started && bogcounter > 15 && isAtDestination()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[TeleportTask] Successfully teleported."));
            MacroCheckDetector.INSTANCE.setTeleporting(false);
            return true;
        }
        return false;
    }

    private boolean isAtDestination() {
        if (mc.thePlayer == null) return false;
        double dx = mc.thePlayer.posX - (pos.getX() + 0.5);
        double dy = mc.thePlayer.posY - pos.getY();
        double dz = mc.thePlayer.posZ - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz < nearDistSq;
    }
}
