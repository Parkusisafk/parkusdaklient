package com.github.parkusisafk.parkusdaklient.command;

import com.github.parkusisafk.parkusdaklient.SkyblockMod;
import com.github.parkusisafk.parkusdaklient.tasks.BreakTask;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.*;

public class CommandBreakTask{



    public static void run(Set<Block> targetBlocks) {




        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        EntityPlayerSP player = mc.thePlayer;
        BlockPos startPos = player.getPosition();

        int scanRadius = 7;
        Queue<BlockPos> toCheck = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> orderedBlocks = new ArrayList<>();

        // Seed queue with reachable + visible + matching blocks
        for (BlockPos pos : BlockPos.getAllInBoxMutable(
                startPos.add(-scanRadius, -scanRadius, -scanRadius),
                startPos.add(scanRadius, scanRadius, scanRadius))) {

            if (!world.isBlockLoaded(pos)) continue;
            if (!targetBlocks.contains(world.getBlockState(pos).getBlock())) continue;

            EnumFacing face = getReachableVisibleFace(pos, player);
            if (face != null) {
                toCheck.add(new BlockPos(pos));
                visited.add(new BlockPos(pos));
            }
        }

        // BFS discovery of all connected matching blocks
        while (!toCheck.isEmpty()) {
            BlockPos current = toCheck.poll();
            orderedBlocks.add(current);

            for (EnumFacing dir : EnumFacing.values()) {
                BlockPos neighbor = current.offset(dir);
                if (visited.contains(neighbor)) continue;
                if (!world.isBlockLoaded(neighbor)) continue;

                if (targetBlocks.contains(world.getBlockState(neighbor).getBlock())
                        && withinRadius(neighbor, startPos, scanRadius)) {
                    toCheck.add(neighbor);
                    visited.add(neighbor);
                }
            }
        }

        if (orderedBlocks.isEmpty()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("No blocks found"));
            return;
        }


        for (BlockPos pos : orderedBlocks) {
            SkyblockMod.taskManager.add(new BreakTask(pos));
        }
        mc.thePlayer.addChatMessage(new ChatComponentText("Queued " + orderedBlocks.size() + " block(s) for breaking."));

    }

    private static boolean withinRadius(BlockPos a, BlockPos center, int radius) {
        return a.distanceSq(center) <= radius * radius;
    }

    private static EnumFacing getReachableVisibleFace(BlockPos pos, EntityPlayerSP player) {
        World world = player.worldObj;
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(face);
            if (world.isAirBlock(neighbor)) {
                Vec3 eyePos = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
                Vec3 hitVec = new Vec3(pos).addVector(0.5, 0.5, 0.5)
                        .addVector(face.getFrontOffsetX() * 0.5,
                                face.getFrontOffsetY() * 0.5,
                                face.getFrontOffsetZ() * 0.5);
                MovingObjectPosition mop = world.rayTraceBlocks(eyePos, hitVec);
                if (mop == null || mop.getBlockPos().equals(pos)) {
                    return face;
                }
            }
        }
        return null;
    }
}

