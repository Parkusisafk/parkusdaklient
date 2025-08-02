package com.github.parkusisafk.parkusdaklient.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.lang.reflect.Method;
import java.util.Random;

public class CommandMacroCheck {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rng = new Random();

    public static void run(int mode) {
        EntityPlayerSP player = mc.thePlayer;

        switch (mode) {
            case 1: { // Random angle rotation
                float yaw = rng.nextFloat() * 360f - 180f;
                float pitch = rng.nextFloat() * 60f - 30f;
                player.rotationYaw = yaw;
                player.rotationPitch = pitch;
                msg("Turned to yaw=" + yaw + ", pitch=" + pitch);
                break;
            }
            case 2: { // Teleport (fake)
                double dx = rng.nextGaussian() * 6;
                double dz = rng.nextGaussian() * 6;
                double dy = rng.nextDouble() * 3;
                player.setPosition(player.posX + dx, player.posY + dy, player.posZ + dz);
                msg("Teleported you randomly.");
                break;
            }
            case 3: { // Spawn bedrock (client-side)
                for (int i = 0; i < 5; i++) {
                    double ox = rng.nextInt(3) - 1;
                    double oz = rng.nextInt(3) - 1;
                    BlockPos pos = new BlockPos(player.posX + ox, player.posY, player.posZ + oz);
                    mc.theWorld.setBlockState(pos, Blocks.bedrock.getDefaultState());
                }
                msg("Spawned bedrock blocks.");
                break;
            }
            case 4: { // Random hotbar
                int slot = rng.nextInt(9);
                player.inventory.currentItem = slot;
                msg("Swapped to hotbar slot " + slot);
                break;
            }
            case 5: { // Random velocity
                player.motionY = 0.7 + rng.nextDouble() * 0.5;
                player.motionX += rng.nextGaussian() * 0.3;
                player.motionZ += rng.nextGaussian() * 0.3;
                msg("Injected jump/velocity.");
                break;
            }
            case 6: { // Spawn fake player as invisible armor stand

                double x = player.posX + rng.nextDouble() * 6 - 3;
                double y = player.posY;
                double z = player.posZ + rng.nextDouble() * 6 - 3;

                EntityArmorStand stand = new EntityArmorStand(mc.theWorld, x, y, z);
                stand.setCustomNameTag("§cSuspicious Player");
                stand.setAlwaysRenderNameTag(true);
                stand.setInvisible(false); // Set to true if you want it fully invisible
                stand.setCurrentItemOrArmor(4, new ItemStack(Items.diamond_helmet)); // head item
                stand.setCurrentItemOrArmor(0, new ItemStack(Items.diamond_sword)); // main hand
                try {
                    Method noGrav = EntityArmorStand.class.getDeclaredMethod("setNoGravity", boolean.class);
                    noGrav.setAccessible(true);
                    noGrav.invoke(stand, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }                mc.theWorld.spawnEntityInWorld(stand);

                msg("Spawned fake player (armor stand) nearby.");
                break;
            }

            case 7: {
                msg("Nothing to check. Add more cases.");
                break;
            }
            default: {
                msg("Invalid .macrocheck mode.");
            }
        }
    }

    private static void msg(String text) {
        mc.thePlayer.addChatMessage(new ChatComponentText("§d[MacroCheck] " + text));
    }
}
