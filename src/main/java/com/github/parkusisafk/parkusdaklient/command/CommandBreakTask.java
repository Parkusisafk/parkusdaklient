package com.github.parkusisafk.parkusdaklient.command;

import com.github.parkusisafk.parkusdaklient.SkyblockMod;
import com.github.parkusisafk.parkusdaklient.tasks.BreakTask;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.*;

/**
 * BFS-based block breaker with virtualAir simulation.
 * - Finds all reachable blocks in layers (treating queued blocks as air)
 * - Orders them for breaking
 * - Avoids first-layer-only limitation
 */
public class CommandBreakTask {

    private static final boolean DEBUG = false;
    private static final double SERVER_SAFE_REACH = 4;
    private static final double MAX_REACH_CAP = 4.25;
    private static final int GRID = 6;
    private static final int DEFAULT_SCAN_RADIUS = 7;
    private static final int MAX_QUEUE = 400;

    public static void run(Set<Block> targetBlocks) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        EntityPlayerSP player = mc.thePlayer;
        if (mc == null || world == null || player == null) return;

        BlockPos startPos = player.getPosition();
        int scanRadius = DEFAULT_SCAN_RADIUS;

        Queue<BlockPos> toCheck = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> virtualAir = new HashSet<>();
        List<BlockPos> orderedBlocks = new ArrayList<>();

        double reach = getClientReach();

        // Seed BFS with reachable blocks
        for (BlockPos.MutableBlockPos mpos : BlockPos.getAllInBoxMutable(
                startPos.add(-scanRadius, -scanRadius, -scanRadius),
                startPos.add(scanRadius, scanRadius, scanRadius))) {
            BlockPos pos = new BlockPos(mpos.getX(), mpos.getY(), mpos.getZ());

            if (!world.isBlockLoaded(pos)) continue;
            if (!targetBlocks.contains(world.getBlockState(pos).getBlock())) continue;

            AimPoint ap = getReachableVisibleFace(player, world, pos, reach, virtualAir);
            if (ap != null) {
                toCheck.add(pos);
                visited.add(pos);
                virtualAir.add(pos); // treat as air for BFS
                if (DEBUG) System.out.println("[BreakTask] Seeded " + pos);
            }
        }

        // BFS with virtualAir propagation
        while (!toCheck.isEmpty()) {
            BlockPos current = toCheck.poll();
            orderedBlocks.add(current);

            if (orderedBlocks.size() >= MAX_QUEUE) break;

            for (EnumFacing dir : EnumFacing.values()) {
                BlockPos neighbor = current.offset(dir);
                if (visited.contains(neighbor)) continue;
                if (!world.isBlockLoaded(neighbor)) continue;
                if (!withinRadius(neighbor, startPos, scanRadius)) continue;

                if (!targetBlocks.contains(world.getBlockState(neighbor).getBlock())) continue;

                AimPoint ap = getReachableVisibleFace(player, world, neighbor, reach, virtualAir);
                if (ap != null) {
                    toCheck.add(neighbor);
                    visited.add(neighbor);
                    virtualAir.add(neighbor);
                    if (DEBUG) System.out.println("[BreakTask] Queued neighbor " + neighbor);
                } else {
                    visited.add(neighbor); // mark blocked by other blocks
                    if (DEBUG) System.out.println("[BreakTask] Neighbor blocked " + neighbor);
                }
            }
        }

        if (orderedBlocks.isEmpty()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("No blocks found"));
            return;
        }

        // Sort nearest-first (optional)
        orderedBlocks.sort(Comparator.comparingDouble(
                b -> player.getDistanceSq(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)
        ));

        int queued = 0;
        for (BlockPos pos : orderedBlocks) {
            if (queued >= MAX_QUEUE) break;
            SkyblockMod.taskManager.add(new BreakTask(pos));
            queued++;
        }

        mc.thePlayer.addChatMessage(new ChatComponentText("Queued " + queued + " block(s) for breaking."));
    }

    private static boolean withinRadius(BlockPos a, BlockPos center, int radius) {
        long dx = a.getX() - center.getX();
        long dy = a.getY() - center.getY();
        long dz = a.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }

    private static double getClientReach() {
        try {
            float r = Minecraft.getMinecraft().playerController.getBlockReachDistance();
            if (r > 1.0F && r < MAX_REACH_CAP) return Math.min(r, MAX_REACH_CAP);
        } catch (Throwable ignored) {}
        return SERVER_SAFE_REACH;
    }

    // --------------------- AimPoint ---------------------
    public static class AimPoint {
        private final BlockPos blockPos;
        private final EnumFacing face;
        private final Vec3 hitVec;

        public AimPoint(BlockPos blockPos, EnumFacing face, Vec3 hitVec) {
            this.blockPos = blockPos;
            this.face = face;
            this.hitVec = hitVec;
        }

        public BlockPos getBlockPos() { return blockPos; }
        public EnumFacing getFace() { return face; }
        public Vec3 getHitVec() { return hitVec; }
    }

    // --------------------- Raytrace with virtualAir ---------------------
    public static AimPoint getReachableVisibleFace(EntityPlayerSP player, World world, BlockPos pos, double reach, Set<BlockPos> virtualAir) {
        Vec3 eye = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        AxisAlignedBB bb = null;
        try { bb = block.getSelectedBoundingBox(world, pos); } catch (Throwable ignored) {}
        if (bb == null) {
            try { bb = block.getCollisionBoundingBox(world, pos, state); } catch (Throwable ignored) {}
        }
        if (bb == null) bb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1);

        // reject distant
        double cx = Math.max(bb.minX, Math.min(eye.xCoord, bb.maxX));
        double cy = Math.max(bb.minY, Math.min(eye.yCoord, bb.maxY));
        double cz = Math.max(bb.minZ, Math.min(eye.zCoord, bb.maxZ));
        if (eye.squareDistanceTo(new Vec3(cx, cy, cz)) > reach*reach) return null;

        Vec3 center = new Vec3((bb.minX + bb.maxX)*0.5, (bb.minY + bb.maxY)*0.5, (bb.minZ + bb.maxZ)*0.5);
        Vec3 toEye = eye.subtract(center);
        EnumFacing primary = EnumFacing.getFacingFromVector((float)toEye.xCoord, (float)toEye.yCoord, (float)toEye.zCoord);

        List<EnumFacing> faces = new ArrayList<>(Arrays.asList(EnumFacing.values()));
        faces.sort((a,b) -> {
            if(a==primary) return -1;
            if(b==primary) return 1;
            if(a==primary.getOpposite()) return 1;
            if(b==primary.getOpposite()) return -1;
            return 0;
        });

        boolean isThin = (bb.maxX-bb.minX)<0.2 || (bb.maxY-bb.minY)<0.2 || (bb.maxZ-bb.minZ)<0.2;
        final double EPS = 0.001;

        for (EnumFacing face : faces) {
            for (int u=0; u<GRID; u++) {
                for (int v=0; v<GRID; v++) {
                    double fu = (u+0.5)/GRID, fv = (v+0.5)/GRID;
                    double fx=0, fy=0, fz=0;
                    switch(face.getAxis()){
                        case X: fx = (face==EnumFacing.EAST)?bb.maxX:bb.minX; fy=bb.minY+fu*(bb.maxY-bb.minY); fz=bb.minZ+fv*(bb.maxZ-bb.minZ); break;
                        case Y: fy = (face==EnumFacing.UP)?bb.maxY:bb.minY; fx=bb.minX+fu*(bb.maxX-bb.minX); fz=bb.minZ+fv*(bb.maxZ-bb.minZ); break;
                        default: fz = (face==EnumFacing.SOUTH)?bb.maxZ:bb.minZ; fx=bb.minX+fu*(bb.maxX-bb.minX); fy=bb.minY+fv*(bb.maxY-bb.minY); break;
                    }

                    Vec3 facePoint = new Vec3(fx, fy, fz);
                    Vec3 dir = facePoint.subtract(eye);
                    double len = dir.lengthVector();
                    if(len<1e-6) dir = new Vec3(face.getDirectionVec().getX()*0.01,face.getDirectionVec().getY()*0.01,face.getDirectionVec().getZ()*0.01);
                    len = dir.lengthVector();
                    Vec3 end = eye.addVector(dir.xCoord/len*(len+EPS), dir.yCoord/len*(len+EPS), dir.zCoord/len*(len+EPS));

                    MovingObjectPosition mop=null;
                    try { mop = world.rayTraceBlocks(eye,end,false,true,false); } catch(Throwable t){ try{ mop=world.rayTraceBlocks(eye,end); } catch(Throwable ignored){} }
                    if(mop==null) continue;

                    BlockPos hitPos = mop.getBlockPos();
                    // allow virtualAir to be ignored
                    if(!pos.equals(hitPos) && !virtualAir.contains(hitPos)) continue;

                    boolean faceMatches = mop.sideHit==face || isThin || eye.squareDistanceTo(mop.hitVec)<=0.6*0.6;
                    if(!faceMatches) continue;

                    if(eye.squareDistanceTo(mop.hitVec)>reach*reach) continue;

                    return new AimPoint(pos,mop.sideHit,mop.hitVec);
                }
            }
        }
        return null;
    }
}
