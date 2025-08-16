package com.github.parkusisafk.parkusdaklient.handlers;

import com.github.parkusisafk.parkusdaklient.macro.DetectedMacroCheck;
import com.github.parkusisafk.parkusdaklient.util.AimHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
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

import java.lang.reflect.Field;
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

    private float getCurBlockDamageMP() {
        try {
            Field f = mc.playerController.getClass().getDeclaredField("curBlockDamageMP");
            f.setAccessible(true); // allow access even though private
            return f.getFloat(mc.playerController);
        } catch (Exception e) {
            e.printStackTrace();
            return 0f;
        }
    }
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

    private float lastProgress = 0f;
    private int stuckTicks = 0;  // counts how long the block is "stuck"
    private final int STUCK_THRESHOLD_TICKS = 60; // 1 second at 20 TPS


    private boolean isMining = false;  // Track if left click is currently held
    public static boolean nextMiningTask = false;  // Optional: your logic to know next mining block
    int cooldownbetweenmining = 0;
    int buttockelapsed = 0;
    private BlockPos lastPos = null;



    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;
        if (mc.theWorld == null || mc.thePlayer == null || target == null) {
            cancel();
            releaseLeftClickIfHeld();
            return;
        }

        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)        // allow chat
                && !(mc.currentScreen instanceof GuiIngameMenu)) return;

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
                    DetectedMacroCheck.alert("Turning timed out");

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

            if (lastPos == null || !resultPos.equals(lastPos)) {
                mc.playerController.resetBlockRemoving();
                lastPos = resultPos;
            }


            // We're facing the block
            if (!isMining) {
                holdLeftClick();
                isMining = true;
            }

            mc.playerController.onPlayerDamageBlock(resultPos, result.sideHit);
            mc.thePlayer.swingItem();

            // === STUCK DETECTION ===
            float progress = getCurBlockDamageMP();
            if (progress <= 0.05f) { // stuck at start threshold
                stuckTicks++;
                if (stuckTicks >= STUCK_THRESHOLD_TICKS) {
                    mc.thePlayer.addChatMessage(
                            new ChatComponentText("§cBlock breaking appears stuck! Trying again"));
                    mc.playerController.resetBlockRemoving(); // neded try again
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0; // progress is moving
            }
            // === END STUCK DETECTION ===

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
                    "Unable to calculate best hit face for target, or target too far away: " +
                            target.getX() + "," + target.getY() + "," + target.getZ()));
            cancel();
            releaseLeftClickIfHeld();
            isMining = false;
        }
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

        final double REACH_DISTANCE = 4.2; // adjust for your case

        // Get player's eye position
        double px = player.posX;
        double py = player.posY + player.getEyeHeight();
        double pz = player.posZ;

        // Get target block center position
        double tx = target.getX() + 0.5;
        double ty = target.getY() + 0.5;
        double tz = target.getZ() + 0.5;

        // Euclidean distance
        double dist = Math.sqrt(
                Math.pow(tx - px, 2) +
                        Math.pow(ty - py, 2) +
                        Math.pow(tz - pz, 2)
        );

        // Return null if too far
        if (dist > REACH_DISTANCE) return null;

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
