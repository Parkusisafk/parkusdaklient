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
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.*;

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
    private float yawMin = 12.0f, yawRand = 8.0f;     // 2.5..6.0
    private float pitchMin = 12.0f, pitchRand = 8.0f; // 2..5
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
    private MovingObjectPosition lockedAim = null;



    private BlockPos lockedPos = null;
    private EnumFacing lockedFace = null;
    private Vec3 lockedHitVec = null;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;
        if (mc.theWorld == null || mc.thePlayer == null || target == null) {
            cancel();
            releaseLeftClickIfHeld();
            isMining = false;
            return;
        }

        // Ignore GUI except chat/game menu
        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)
                && !(mc.currentScreen instanceof GuiIngameMenu)) return;

        // Mining cooldown
        if (cooldownbetweenmining > 0) {
            cooldownbetweenmining--;
            return;
        }

        // Lock onto block if new target or no lock
        if (lockedPos == null || !target.equals(lockedPos)) {
            AimPoint result = getReachableVisibleFace(mc.thePlayer, mc.theWorld, target);
            if (result == null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "Unable to aim at target: " + target));
                cancel();
                releaseLeftClickIfHeld();
                isMining = false;
                return;
            }
            lockedPos = result.getBlockPos();
            lockedFace = result.face;
            lockedHitVec = result.hitVec;
            isMining = false;
        }

        // Debug line
        this.debugLineStart = mc.thePlayer.getPositionEyes(1.0f);
        this.debugLineEnd = lockedHitVec;

        // Rotate to face locked target
        if (!faceUntilFacingTarget(lockedHitVec)) {
            releaseLeftClickIfHeld();
            isMining = false;
            buttockelapsed++;
            if (buttockelapsed > 60) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "§eTurning timed out after " + (buttockelapsed / 20.0) + "s @ " + lockedPos));
                DetectedMacroCheck.alert("Turning timed out");
                cancel();
                buttockelapsed = 0;
            }
            return;
        }
        buttockelapsed = 0;

        ticksElapsed++;

        // Timeout check
        if (ticksElapsed >= 20 * 8) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§eBreaking timed out after " + (ticksElapsed / 20.0) + "s @ " + lockedPos));
            cancel();
            releaseLeftClickIfHeld();
            isMining = false;
            return;
        }

        // Start breaking if not already
        if (!isMining) {
            mc.thePlayer.sendQueue.addToSendQueue(
                    new C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
                            lockedPos,
                            lockedFace
                    )
            );
            holdLeftClick(); // cosmetic
            isMining = true;
        }

        // Randomized mining tick (1 or 2 tick interval)
        //int randDelay = 1 + mc.theWorld.rand.nextInt(2); // 1 or 2
        //if (ticksElapsed % randDelay == 0) {
            mc.playerController.onPlayerDamageBlock(lockedPos, lockedFace);
            mc.thePlayer.swingItem();
//}
        // Occasionally release click (cosmetic only)
        if (mc.theWorld.rand.nextInt(40) == 0) {
            releaseLeftClickIfHeld();
            isMining = false;
        }

        // Detect stuck progress
        float progress = getCurBlockDamageMP();
        if (progress <= 0.05f) {
            stuckTicks++;
            if (stuckTicks >= 40) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "§cBlock breaking appears stuck, retrying"));
                stuckTicks = 0;
                releaseLeftClickIfHeld();
                isMining = false;
            }
        } else {
            stuckTicks = 0;
        }

        // Detect block broken
        IBlockState current = mc.theWorld.getBlockState(lockedPos);
        if (current.getBlock() == Blocks.air || !current.equals(originalState)) {
            mc.thePlayer.sendQueue.addToSendQueue(
                    new C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                            lockedPos,
                            lockedFace
                    )
            );
            releaseLeftClickIfHeld();
            isMining = false;
            cooldownbetweenmining = 1;
            cancel();
            lastPos = null;
            target = null;
            lockedPos = null;
            lockedFace = null;
            lockedHitVec = null;
            stuckTicks = 0;
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "Block broken at " + lockedPos));
        }
    }





    // small container class (keep or adapt to your project)
    public static class AimPoint {
        private final BlockPos blockPos;
        private final EnumFacing face;
        private final Vec3 hitVec;

        public AimPoint(BlockPos blockPos, EnumFacing face, Vec3 hitVec) {
            this.blockPos = blockPos;
            this.face = face;
            this.hitVec = hitVec;
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }

        public EnumFacing getFace() {
            return face;
        }

        public Vec3 getHitVec() {
            return hitVec;
        }
    }

    /**
     * Robust 1.8.9-compatible raytrace to find a reachable visible face/point on a block.
     * Returns an AimPoint(hitVec, face) or null if none found within reach.
     */
    public static AimPoint getReachableVisibleFace(EntityPlayer player, World world, BlockPos pos) {
        final boolean DEBUG = false;
        final double SERVER_SAFE_REACH = 4;   // client-side conservative reach for custom servers
        final double MAX_REACH_CAP = 4.25;
        final double EPS = 0.001;               // nudge past face; increase to 0.01 if too many inside-block misses
        final int GRID = 6;                     // sample resolution per face

        if (player == null || world == null || pos == null) return null;

        // eye position
        Vec3 eye = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);

        // choose reach: try vanilla playerController; fallback to conservative value, clamp to MAX_REACH_CAP
        double reach;
        try {
            // PlayerControllerMP / PlayerControllerSP in 1.8.9 often expose getBlockReachDistance()
            float r = Minecraft.getMinecraft().playerController.getBlockReachDistance();
            if (r > 1.0F && r < MAX_REACH_CAP) reach = r;
            else reach = SERVER_SAFE_REACH;
        } catch (Throwable t) {
            reach = SERVER_SAFE_REACH;
        }
        reach = Math.min(reach, MAX_REACH_CAP);

        // block AABB (prefer selected box, fallback to collision box or full cube)
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        AxisAlignedBB bb = null;
        try {
            bb = block.getSelectedBoundingBox(world, pos);
        } catch (Throwable ignored) {}
        if (bb == null) {
            try {
                AxisAlignedBB coll = block.getCollisionBoundingBox(world, pos, state);
                if (coll != null) bb = coll;
            } catch (Throwable ignored) {}
        }
        if (bb == null) bb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1);

        if (DEBUG) System.out.println("[getReachableVisibleFace] AABB=" + bb);

        // quick reject by distance from eye to nearest point in AABB
        double cx = Math.max(bb.minX, Math.min(eye.xCoord, bb.maxX));
        double cy = Math.max(bb.minY, Math.min(eye.yCoord, bb.maxY));
        double cz = Math.max(bb.minZ, Math.min(eye.zCoord, bb.maxZ));
        double sqDistToBox = eye.squareDistanceTo(new Vec3(cx, cy, cz));
        if (sqDistToBox > reach * reach) {
            if (DEBUG) System.out.println("  → rejected by reach (to AABB) dist=" + Math.sqrt(sqDistToBox));
            return null;
        }

        // face ordering: primary face is the one pointing TO the eye
        Vec3 center = new Vec3((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, (bb.minZ + bb.maxZ) * 0.5);
        Vec3 toEye = eye.subtract(center);
        EnumFacing primary = EnumFacing.getFacingFromVector((float) toEye.xCoord, (float) toEye.yCoord, (float) toEye.zCoord);

        List<EnumFacing> faces = new ArrayList<EnumFacing>(Arrays.asList(EnumFacing.values()));
        faces.sort(new Comparator<EnumFacing>() {
            @Override public int compare(EnumFacing a, EnumFacing b) {
                if (a == primary) return -1;
                if (b == primary) return 1;
                if (a == primary.getOpposite()) return 1;
                if (b == primary.getOpposite()) return -1;
                return 0;
            }
        });

        // detect thin AABB to relax face checks
        double sizeX = bb.maxX - bb.minX;
        double sizeY = bb.maxY - bb.minY;
        double sizeZ = bb.maxZ - bb.minZ;
        final double THIN_THRESHOLD = 0.2;
        boolean isThin = sizeX < THIN_THRESHOLD || sizeY < THIN_THRESHOLD || sizeZ < THIN_THRESHOLD;

        // iterate faces and sample
        for (EnumFacing face : faces) {
            for (int u = 0; u < GRID; u++) {
                for (int v = 0; v < GRID; v++) {
                    double fu = (u + 0.5) / (double) GRID;
                    double fv = (v + 0.5) / (double) GRID;

                    double fx=0, fy=0, fz=0;
                    switch (face.getAxis()) {
                        case X:
                            fx = (face == EnumFacing.EAST) ? bb.maxX : bb.minX;
                            fy = bb.minY + fu * (bb.maxY - bb.minY);
                            fz = bb.minZ + fv * (bb.maxZ - bb.minZ);
                            break;
                        case Y:
                            fy = (face == EnumFacing.UP) ? bb.maxY : bb.minY;
                            fx = bb.minX + fu * (bb.maxX - bb.minX);
                            fz = bb.minZ + fv * (bb.maxZ - bb.minZ);
                            break;
                        default: // Z
                            fz = (face == EnumFacing.SOUTH) ? bb.maxZ : bb.minZ;
                            fx = bb.minX + fu * (bb.maxX - bb.minX);
                            fy = bb.minY + fv * (bb.maxY - bb.minY);
                            break;
                    }

                    Vec3 facePoint = new Vec3(fx, fy, fz);

                    // direction from eye to facePoint, normalized
                    Vec3 dir = facePoint.subtract(eye);
                    double dirLen = dir.lengthVector();
                    if (dirLen < 1e-6) {
                        // eye almost exactly at point - use face normal small offset
                        dir = new Vec3(face.getDirectionVec().getX() * 0.01,
                                face.getDirectionVec().getY() * 0.01,
                                face.getDirectionVec().getZ() * 0.01);
                        dirLen = dir.lengthVector();
                    }
                    Vec3 dirNorm = new Vec3(dir.xCoord / dirLen, dir.yCoord / dirLen, dir.zCoord / dirLen);

                    // endpoint slightly past the face so the ray crosses INTO the block (avoids start/end-inside problems)
                    double endpointDistance = dirLen + EPS;
                    Vec3 end = eye.addVector(dirNorm.xCoord * endpointDistance,
                            dirNorm.yCoord * endpointDistance,
                            dirNorm.zCoord * endpointDistance);

                    // try raytrace (1.8.9 overloads available)
                    MovingObjectPosition mop = null;
                    try {
                        // try the more detailed signature: (start, end, stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock)
                        mop = world.rayTraceBlocks(eye, end, false, true, false);
                    } catch (Throwable t) {
                        try {
                            mop = world.rayTraceBlocks(eye, end);
                        } catch (Throwable ignored) {}
                    }

                    if (mop == null) {
                        if (DEBUG) System.out.println("  ❌ ray missed (face " + face + ")");
                        continue;
                    }

                    // ensure block position matched
                    BlockPos hitPos;
                    try {
                        hitPos = mop.getBlockPos();
                    } catch (Throwable t) {
                        // some builds expose the field directly
                        hitPos = (mop.getBlockPos() != null) ? mop.getBlockPos() : pos;
                    }
                    if (!pos.equals(hitPos)) {
                        if (DEBUG) System.out.println("  ❌ hit wrong block " + hitPos + " expected " + pos);
                        continue;
                    }

                    // face check: allow a bit of slop for thin or very close hits
                    boolean faceMatches = (mop.sideHit == face);
                    double sqDistHit = eye.squareDistanceTo(mop.hitVec);
                    boolean veryClose = sqDistHit <= 0.6 * 0.6;

                    if (!faceMatches && !isThin && !veryClose) {
                        if (DEBUG) System.out.println("  ❌ face mismatch (got " + mop.sideHit + " expected " + face + ")");
                        continue;
                    }

                    // enforce reach using the actual hit vector
                    if (sqDistHit > reach * reach) {
                        if (DEBUG) System.out.println("  ❌ beyond reach dist=" + Math.sqrt(sqDistHit) + " reach=" + reach);
                        continue;
                    }

                    if (DEBUG) System.out.println("  ✅ valid hit at " + mop.hitVec + " face " + mop.sideHit);
                    return new AimPoint(mop.getBlockPos(), mop.sideHit, mop.hitVec);
                }
            }
        }

        if (DEBUG) System.out.println("  → no aim point found for " + pos);
        return null;
    }



        private static EnumFacing[] orderedFaces(EnumFacing primary) {
        EnumFacing[] all = EnumFacing.values();
        Arrays.sort(all, (a, b) -> {
            int ra = (a == primary) ? 0 : (a == primary.getOpposite() ? 1 : 2);
            int rb = (b == primary) ? 0 : (b == primary.getOpposite() ? 1 : 2);
            return Integer.compare(ra, rb);
        });
        return all;
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
