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
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.*;

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
                if (!faceUntilFacingTarget(result.getHitVec())) {
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
            mc.thePlayer.inventory.currentItem = 0;
            new Thread(() -> {
                try{
                    Thread.sleep(1000);
                    MacroCheckDetector.INSTANCE.setTeleporting(false);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return true;
        } else if (failed){
            MacroCheckDetector.INSTANCE.setTeleporting(false);
            SkyblockMod.moveForwardHandler.cancel();
            SkyblockMod.blockBreakingHandler.cancel();
            mc.thePlayer.addChatMessage(new ChatComponentText("§eStopped actions, failed to teleport :("));
            TaskManager.stopexecution = true;
            DetectedMacroCheck.alert("Teleportation timed out (failed)");

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
public static BlockBreakingHandler.AimPoint getReachableVisibleFace(EntityPlayer player, World world, BlockPos pos) {
    final boolean DEBUG = false;
    final double SERVER_SAFE_REACH = 70;   // client-side conservative reach for custom servers
    final double MAX_REACH_CAP = 70;
    final double EPS = 0.001;               // nudge past face; increase to 0.01 if too many inside-block misses
    final int GRID = 6;                     // sample resolution per face

    if (player == null || world == null || pos == null) return null;

    // eye position
    Vec3 eye = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);

    // choose reach: try vanilla playerController; fallback to conservative value, clamp to MAX_REACH_CAP
    double reach;
    try {
        // PlayerControllerMP / PlayerControllerSP in 1.8.9 often expose getBlockReachDistance()
        float r = 70; //cuz teleporting
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
                return new BlockBreakingHandler.AimPoint(mop.getBlockPos(), mop.sideHit, mop.hitVec);
            }
        }
    }

    if (DEBUG) System.out.println("  → no aim point found for " + pos);
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
