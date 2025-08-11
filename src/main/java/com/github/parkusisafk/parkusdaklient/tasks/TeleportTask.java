package com.github.parkusisafk.parkusdaklient.tasks;

import com.github.parkusisafk.parkusdaklient.SkyblockMod;
import com.github.parkusisafk.parkusdaklient.handlers.BlockBreakingHandler;
import com.github.parkusisafk.parkusdaklient.macro.DetectedMacroCheck;
import com.github.parkusisafk.parkusdaklient.macro.MacroCheckDetector;
import com.github.parkusisafk.parkusdaklient.util.AimHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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
    private float deadzoneDeg = 0.2f;       // if yaw/pitch error < this, skip correction
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
    int buttockelapsed = 0;
    boolean failed = false;
    @Override
    public void update() {
        if (!started || mc.thePlayer == null) return;

        // Calculate if very close to target (for slower turning)
        BlockBreakingHandler.AimPoint result = getReachableVisibleFace(mc.thePlayer, mc.theWorld, pos);

        if (result != null) {
            BlockPos resultPos = result.getBlockPos();

            if(!teleported) {
                if (!faceUntilFacingTarget(result.vec)) {
                    buttockelapsed++;
                    if (buttockelapsed > 60) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(
                                "§eTurning timed out after " + (buttockelapsed / 20.0) + "s @ " +
                                        resultPos.getX() + "," + resultPos.getY() + "," + resultPos.getZ()));
                        DetectedMacroCheck.alert("Turning timed out");
                        failed = true;
                    }
                    return;
                }
            }
            tickCounter++;

            //click use on tick 15
            if(tickCounter == 15) {
                ItemStack held = mc.thePlayer.getHeldItem();
                if (held != null) {
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
                    teleported = true;

                }
            }


//release sneak on tick 25
            if(tickCounter == 25){
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
                sneaking = false;
            } else if (bogcounter == 27) {
                mc.thePlayer.inventory.currentItem = 0;
                //go back to 1
            }

            // Start sneaking on tick 6
            if (tickCounter == 6) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
                MacroCheckDetector.INSTANCE.setTeleporting(true);
                sneaking = true;
            }

            if (tickCounter ==150 && !isAtDestination()) {
                mc.thePlayer.addChatMessage(new ChatComponentText("[TeleportTask] IS THIS A MACRO CHECK??? Timeout: Unable to reach destination."));
                MacroCheckDetector.INSTANCE.setTeleporting(false);
                DetectedMacroCheck.alert("Teleporting timed out??");
                failed = true;

            }


        } else{
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "Unable to calculate best hit face for target: " +
                            pos.getX() + "," + pos.getY() + "," + pos.getZ()));
            DetectedMacroCheck.alert("Teleportation unable to calculate teleport face");
            MacroCheckDetector.INSTANCE.setTeleporting(false);
            SkyblockMod.moveForwardHandler.cancel();
            SkyblockMod.blockBreakingHandler.cancel();
            mc.thePlayer.addChatMessage(new ChatComponentText("§eStopped actions."));
            failed = true;

        }


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
        if (started && tickCounter > 30 && isAtDestination()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[TeleportTask] Successfully teleported."));
            MacroCheckDetector.INSTANCE.setTeleporting(false);
            return true;
        } else if (failed){
            MacroCheckDetector.INSTANCE.setTeleporting(false);
            SkyblockMod.moveForwardHandler.cancel();
            SkyblockMod.blockBreakingHandler.cancel();
            mc.thePlayer.addChatMessage(new ChatComponentText("§eStopped actions, failed to teleport :("));
            TaskManager.stopexecution = true;
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


    public static BlockBreakingHandler.AimPoint getReachableVisibleFace(EntityPlayer player, World world, BlockPos target) {
        Vec3 eyePos = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        IBlockState state = world.getBlockState(target);
        Block block = state.getBlock();

        // Check if block is breakable: hardness >= 0 and player can harvest it (basic check)


        // Thin blocks to skip face matching
        Set<Block> thinBlocks = new HashSet<>(Arrays.asList(
                Blocks.glass_pane,
                Blocks.iron_bars
                // add more thin blocks here if needed
        ));

        boolean skipFaceCheck = thinBlocks.contains(block);

        System.out.println("[getReachableVisibleFace] Scanning block " + block.getLocalizedName() + " at " + target + " (skip face check: " + skipFaceCheck + ")");

        // Scan 6x6 points on each face
        for (EnumFacing face : EnumFacing.values()) {
            for (int u = 0; u < 6; u++) {
                for (int v = 0; v < 6; v++) {
                    // fractions from 0.08 to 0.92 roughly (center of grid cell)
                    double fu = (u + 0.5) / 6.0;
                    double fv = (v + 0.5) / 6.0;

                    // Calculate coordinates of point on face, slightly inside (0.49 offset)
                    double x = target.getX() + 0.5;
                    double y = target.getY() + 0.5;
                    double z = target.getZ() + 0.5;

                    // Assign offsets based on which axis the face lies on
                    switch (face.getAxis()) {
                        case X:
                            x += face.getFrontOffsetX() * 0.49;
                            y += (fu - 0.5);
                            z += (fv - 0.5);
                            break;
                        case Y:
                            y += face.getFrontOffsetY() * 0.49;
                            x += (fu - 0.5);
                            z += (fv - 0.5);
                            break;
                        case Z:
                            z += face.getFrontOffsetZ() * 0.49;
                            x += (fu - 0.5);
                            y += (fv - 0.5);
                            break;
                    }

                    Vec3 aimPoint = new Vec3(x, y, z);
                    //System.out.println("[getReachableVisibleFace] Raytracing to point " + aimPoint + " on face " + face);

                    MovingObjectPosition hit = world.rayTraceBlocks(eyePos, aimPoint);

                    if (hit == null) {
                        //    System.out.println("  ❌ Hit nothing");
                        continue;
                    }

                    if (!hit.getBlockPos().equals(target)) {
                        //    System.out.println("  ❌ Hit wrong block at " + hit.getBlockPos());
                        continue;
                    }

                    if (!skipFaceCheck) {
                        // For full blocks: Minecraft raytrace returns opposite face from aim direction
                        if (hit.sideHit != face.getOpposite()) {
                            //    System.out.println("  ❌ Hit wrong face: got " + hit.sideHit + ", expected " + face.getOpposite());
                            continue;
                        }
                    } else {
                        // For thin blocks, skip face check
                        //    System.out.println("  ℹ️ Skipping face check for thin block");
                    }

                    // Passed all checks - found best aim point
                    System.out.println("  ✅ Valid aim point found at " + aimPoint + " on face " + face);
                    return new BlockBreakingHandler.AimPoint(aimPoint, face);
                }
            }
        }

        System.out.println("[getReachableVisibleFace] No valid aim point found for block " + target);
        return null;
    }
    boolean faceUntilFacingTarget(Vec3 aimPoint) {
        if (aimPoint == null) return false;

        Vec3 eye = new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ
        );

        // Sanity check: is this aim point actually visible?


        AimHelper.YawPitch goal = AimHelper.lookAtPoint(
                eye.xCoord, eye.yCoord, eye.zCoord,
                aimPoint.xCoord, aimPoint.yCoord, aimPoint.zCoord
        );

        float yawErr = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchErr = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);
        boolean facing = yawErr < deadzoneDeg && pitchErr < deadzoneDeg;
        if (facing) return true;

        // Smooth rotate toward goal
        float yawSpeed = yawMin + rng.nextFloat() * yawRand;
        float pitchSpeed = pitchMin + rng.nextFloat() * pitchRand;

        float nextYaw = AimHelper.stepYawTowards(mc.thePlayer.rotationYaw, goal.yaw, yawSpeed);
        float nextPitch = AimHelper.stepPitchTowards(mc.thePlayer.rotationPitch, goal.pitch, pitchSpeed);

        // Fix for yaw seam wrap
        float seamDelta = Math.abs(MathHelper.wrapAngleTo180_float(nextYaw - mc.thePlayer.rotationYaw));
        if (seamDelta > 170f) mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;

        mc.thePlayer.rotationYaw = nextYaw;
        mc.thePlayer.rotationPitch = nextPitch;

        return false;
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
}
