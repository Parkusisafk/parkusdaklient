package com.github.parkusisafk.parkusdaklient.handlers;

import com.github.parkusisafk.parkusdaklient.util.AimHelper;
import com.github.parkusisafk.parkusdaklient.util.AimHelper.YawPitch;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Stable auto-break:
 * - Smooth randomized turning (shared logic) with face-center aiming when close.
 * - Reachability + line-of-sight check; uses the actual hit face for onPlayerDamageBlock.
 * - Timeout and "unreachable" timeout with chat feedback.
 */
public class BlockBreakingHandler {
    public static final BlockBreakingHandler INSTANCE = new BlockBreakingHandler();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rng = new Random();

    private BlockPos target = null;
    private IBlockState originalState = null;
    private boolean active = false;

    // rotation speed ranges (deg per tick)
    private float yawMin = 8.0f, yawRand = 8.0f;     // 2.5..6.0
    private float pitchMin = 8.0f, pitchRand = 8.0f; // 2..5
    private float deadzoneDeg = 0.5f;
    private double nearDistSq = 4.0; // very close if within 2 blocks
    private float slowFactorNear = 0.5f;

    // timers
    private int timeoutTicks = 0;
    private int ticksElapsed = 0;
    private int unreachableTicks = 0;
    private int unreachableTimeout = 20 * 3; // 3s of unreachable before giving up

    private Vec3 debugLineStart = null;
    private Vec3 debugLineEnd = null;



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
        this.debugLineStart = null;
        this.debugLineEnd = null;

        active = false;
        target = null;
        originalState = null;
        timeoutTicks = 0;
        ticksElapsed = 0;
        unreachableTicks = 0;
    }

    private boolean startedBreaking = false;
    private int nextSwingTick = 0;
    private final Random rand = new Random();
    private void facePosSmooth(MovingObjectPosition hit) {
        if (hit == null) return;

        BlockPos pos = hit.getBlockPos();
        EnumFacing face = hit.sideHit;

        Vec3 aimPoint = getAimPointSlightlyInsideFace(pos, face); // ✅ now using correct face

        double ex = mc.thePlayer.posX;
        double ey = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double ez = mc.thePlayer.posZ;

        double distSqCenter = eyeDistSq(ex, ey, ez, aimPoint);
        boolean veryClose = distSqCenter < nearDistSq;

        AimHelper.YawPitch goal = AimHelper.lookAtPoint(ex, ey, ez,
                aimPoint.xCoord, aimPoint.yCoord, aimPoint.zCoord);

        float yawErr = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchErr = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);

        if (veryClose && yawErr < deadzoneDeg && pitchErr < deadzoneDeg) return;

        float yawSpeed = yawMin + rng.nextFloat() * yawRand;
        float pitchSpeed = pitchMin + rng.nextFloat() * pitchRand;
        if (veryClose) {
            yawSpeed *= slowFactorNear;
            pitchSpeed *= slowFactorNear;
        }

        float nextYaw = AimHelper.stepYawTowards(mc.thePlayer.rotationYaw, goal.yaw, yawSpeed);
        float nextPitch = AimHelper.stepPitchTowards(mc.thePlayer.rotationPitch, goal.pitch, pitchSpeed);

        float seamDelta = Math.abs(MathHelper.wrapAngleTo180_float(nextYaw - mc.thePlayer.rotationYaw));
        if (seamDelta > 170f) mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;

        mc.thePlayer.rotationYaw = nextYaw;
        mc.thePlayer.rotationPitch = nextPitch;
    }

    private boolean isFacingTarget(BlockPos target, Vec3 faceCenter) {
        // Player eye position
        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;

        // Desired yaw/pitch to face the visible face center
        AimHelper.YawPitch goal = AimHelper.lookAtPoint(eyeX, eyeY, eyeZ, faceCenter.xCoord, faceCenter.yCoord, faceCenter.zCoord);

        float yawErr   = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchErr = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);

        final float aimingThreshold = 5.0f; // allow some leeway

        // Optional: also check raytrace intersection with bounding box (for more accuracy)
        IBlockState state = mc.theWorld.getBlockState(target);
        AxisAlignedBB blockBB = state.getBlock().getCollisionBoundingBox(mc.theWorld, target, state);

        if (blockBB == null) {
            // If no bounding box, fallback to angular check only
            return yawErr < aimingThreshold && pitchErr < aimingThreshold;
        }

        AxisAlignedBB worldBB = blockBB.offset(target.getX(), target.getY(), target.getZ());

        Vec3 eyePos = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 lookVec = mc.thePlayer.getLookVec();

        // Extend look vector far out (e.g., 5 blocks)
        Vec3 lookEnd = eyePos.addVector(lookVec.xCoord * 5, lookVec.yCoord * 5, lookVec.zCoord * 5);

        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, lookEnd);

        if (mop != null && mop.getBlockPos().equals(target)) {
            // Ray hits the target block - facing it
            return true;
        }

        // Fallback to angular check if raytrace fails
        return yawErr < aimingThreshold && pitchErr < aimingThreshold;
    }
    public static Vec3 getAimPointSlightlyInsideFace(BlockPos pos, EnumFacing face) {
        // Center of block + face offset (just inside face)
        return new Vec3(pos).addVector(0.5, 0.5, 0.5)
                .addVector(face.getFrontOffsetX() * 0.49,
                        face.getFrontOffsetY() * 0.49,
                        face.getFrontOffsetZ() * 0.49);
    }



    private boolean simpleAimCheck(BlockPos pos) {
        Vec3 center = new Vec3(pos).addVector(0.5, 0.5, 0.5);
        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;
        AimHelper.YawPitch goal = AimHelper.lookAtPoint(eyeX, eyeY, eyeZ, center.xCoord, center.yCoord, center.zCoord);

        float yawErr = Math.abs(MathHelper.wrapAngleTo180_float(goal.yaw - mc.thePlayer.rotationYaw));
        float pitchErr = Math.abs(goal.pitch - mc.thePlayer.rotationPitch);

        final float aimingThreshold = 12.0f; // looser threshold, 12 degrees
        return yawErr < aimingThreshold && pitchErr < aimingThreshold;
    }


    private void holdLeftClick() {
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
        }
    }

    private void releaseLeftClickIfHeld() {
        if (mc.gameSettings.keyBindAttack.isKeyDown()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        }
    }

    private boolean faceUntilFacingTarget(Vec3 aimPoint) {
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



    private boolean isMining = false;  // Track if left click is currently held
    public static boolean nextMiningTask = false;  // Optional: your logic to know next mining block
    int cooldownbetweenmining = 0;
    int buttockelapsed = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;
        if (mc.theWorld == null || mc.thePlayer == null || target == null) {
            cancel();
            releaseLeftClickIfHeld();
            return;
        }

        if (mc.currentScreen != null) return;

        if(cooldownbetweenmining > 0){ cooldownbetweenmining--; return;}

        AimPoint result = getReachableVisibleFace(mc.thePlayer, mc.theWorld, target);

        if (result != null) {
            BlockPos resultPos = result.getBlockPos();

            // Debug: draw line from eye to aim point
            this.debugLineStart = mc.thePlayer.getPositionEyes(1.0f);
            this.debugLineEnd = result.vec;

            if (!faceUntilFacingTarget(result.vec)) {
                if (isMining && !nextMiningTask) {
                    releaseLeftClickIfHeld();
                    isMining = false;
                }
                buttockelapsed++;
                if(buttockelapsed>60){
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                            "§eTurning timed out after " + (buttockelapsed / 20.0) + "s @ " +
                                    resultPos.getX() + "," + resultPos.getY() + "," + resultPos.getZ()));
                    cancel();
                    releaseLeftClickIfHeld();
                    isMining = false;
                    buttockelapsed = 0;
                }
                return;
            }
            buttockelapsed = 0;
            ticksElapsed++;
            if (ticksElapsed >= 20 * 8) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "§eBreaking timed out after " + (ticksElapsed / 20.0) + "s @ " +
                                resultPos.getX() + "," + resultPos.getY() + "," + resultPos.getZ()));
                cancel();
                releaseLeftClickIfHeld();
                isMining = false;
            }


            // We're facing the block
            if (!isMining) {
                holdLeftClick();
                isMining = true;
            }

            mc.playerController.onPlayerDamageBlock(resultPos, result.sideHit);
            if ((ticksElapsed & 3) == 0) mc.thePlayer.swingItem();

            IBlockState current = mc.theWorld.getBlockState(resultPos);
            if (current.getBlock() == Blocks.air || !current.equals(originalState)) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "Block broken/changed at " + resultPos.getX() + "," + resultPos.getY() + "," + resultPos.getZ()));
                cooldownbetweenmining = 3;
                cancel();

                if (!nextMiningTask) {
                    releaseLeftClickIfHeld();
                    isMining = false;
                } else {
                    isMining = false;
                }
                return;
            }



        } else {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "Unable to calculate best hit face for target: " +
                            target.getX() + "," + target.getY() + "," + target.getZ()));
            cancel();
            releaseLeftClickIfHeld();
            isMining = false;
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

    public static class AimPoint {
        public final Vec3 vec;
        public final EnumFacing sideHit;

        public AimPoint(Vec3 vec, EnumFacing sideHit) {
            this.vec = vec;
            this.sideHit = sideHit;
        }

        public BlockPos getBlockPos() {
            return new BlockPos(vec);
        }
    }
    public static AimPoint getReachableVisibleFace(EntityPlayer player, World world, BlockPos target) {
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
                    return new AimPoint(aimPoint, face);
                }
            }
        }

        System.out.println("[getReachableVisibleFace] No valid aim point found for block " + target);
        return null;
    }


    public static Vec3 getPerpendicular(Vec3 vec) {
        // Picks a perpendicular vector that's not parallel to the input
        if (Math.abs(vec.xCoord) < 0.1 && Math.abs(vec.zCoord) < 0.1) {
            return new Vec3(1, 0, 0); // if mostly vertical
        } else {
            return new Vec3(0, 1, 0); // otherwise horizontal
        }
    }








    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (debugLineStart == null || debugLineEnd == null) return;

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(2.0F);
        GL11.glColor3f(1.0F, 0.0F, 0.0F);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(debugLineStart.xCoord - dx, debugLineStart.yCoord - dy, debugLineStart.zCoord - dz);
        GL11.glVertex3d(debugLineEnd.xCoord - dx, debugLineEnd.yCoord - dy, debugLineEnd.zCoord - dz);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }



}
