package com.github.parkusisafk.parkusdaklient.tasks;

import com.github.parkusisafk.parkusdaklient.util.AimHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.util.ChatComponentText;

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
        started = true;
        tickCounter = 0;
        mc.thePlayer.inventory.currentItem = 3; // switch to hotbar slot 4 (index 3)
    }

    @Override
    public void update() {
        if (!started || mc.thePlayer == null) return;

        // Calculate if very close to target (for slower turning)
        double distSq = mc.thePlayer.getDistanceSqToCenter(pos);
        boolean veryClose = distSq < nearDistSq;

        // Calculate goal rotation to nearest face center of block
        Vec3 target = AimHelper.nearestFaceCenterToPlayer(pos);
        AimHelper.YawPitch goal = AimHelper.lookAtPoint(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ,
                target.xCoord,
                target.yCoord,
                target.zCoord
        );

        // Generate randomized turning speeds each tick
        float yawSpeed = yawMin + rng.nextFloat() * yawRand;
        float pitchSpeed = pitchMin + rng.nextFloat() * pitchRand;
        if (veryClose) {
            yawSpeed *= slowFactorNear;
            pitchSpeed *= slowFactorNear;
        }

        // Step yaw/pitch smoothly towards goal rotation
        float nextYaw = AimHelper.stepYawTowards(mc.thePlayer.rotationYaw, goal.yaw, yawSpeed);
        float nextPitch = AimHelper.stepPitchTowards(mc.thePlayer.rotationPitch, goal.pitch, pitchSpeed);

        // If we cross the -180/180 seam with a large step, reset prevRotationYaw for smooth interpolation
        float seamDelta = Math.abs(MathHelper.wrapAngleTo180_float(nextYaw - mc.thePlayer.rotationYaw));
        if (seamDelta > 170f) {
            mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;
        }

        mc.thePlayer.rotationYaw = nextYaw;
        mc.thePlayer.rotationPitch = nextPitch;

        // If very close and yaw/pitch error is less than deadzone, snap exactly to goal
        float yawError = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchError = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);
        if (veryClose && yawError < deadzoneDeg && pitchError < deadzoneDeg) {
            mc.thePlayer.rotationYaw = goal.yaw;
            mc.thePlayer.rotationPitch = goal.pitch;
        }

        tickCounter++;

        // Start sneaking on tick 3
        if (tickCounter == 3) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            sneaking = true;
        }
        // Right click on tick 5 (only if holding item)
        else if (tickCounter == 7) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null) {
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
            }
        }
        // Stop sneaking on tick 8
        else if (tickCounter == 18) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            sneaking = false;
        }
        // Switch back to slot 1 on tick 10
        else if (tickCounter == 22) {
            mc.thePlayer.inventory.currentItem = 0;
        }

        // Timeout warning at tick 40 if not reached destination
        if (tickCounter ==150 && !isAtDestination()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[TeleportTask] IS THIS A MACRO CHECK??? Timeout: Unable to reach destination."));
        }
    }

    @Override
    public boolean isFinished() {
        if (started && tickCounter > 22 && isAtDestination()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[TeleportTask] Successfully teleported."));
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
