package com.github.parkusisafk.parkusdaklient.macro;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.*;
import java.util.*;

public class MacroCheckDetector {
    public static final MacroCheckDetector INSTANCE = new MacroCheckDetector(); // singleton

    private final Minecraft mc = Minecraft.getMinecraft();

    private float lastYaw = Float.NaN, lastPitch = Float.NaN;
    private double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;

    private boolean isTeleporting = false;
    private final Map<BlockPos, Block> lastBlockSnapshot = new HashMap<>();


    public static boolean removeWhitelistedBlock(Block block) {
        return monitorWhitelist.remove(block);
    }
    private int blockTickCounter = 0;
    public void setTeleporting(boolean state) {
        isTeleporting = state;
    }
boolean initialised = false;

    private static final Set<Block> monitorWhitelist = new HashSet<>();
    private static final File whitelistFile = new File(Minecraft.getMinecraft().mcDataDir, "config/parkusdaklient/settings.txt");

    private static boolean loaded = false;

    public static void loadWhitelistFromFile() {
        if (loaded) return;
        loaded = true;

        if (!whitelistFile.exists()) {
            try {
                whitelistFile.createNewFile();

                // Write default blocks
                try (PrintWriter writer = new PrintWriter(whitelistFile)) {
                    writer.println("minecraft:stone");
                    writer.println("minecraft:dirt");
                    writer.println("minecraft:grass");
                    writer.println("minecraft:tallgrass");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        monitorWhitelist.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(whitelistFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Block block = Block.getBlockFromName(line.trim());
                    if (block != null) monitorWhitelist.add(block);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Block> getWhitelistedBlocks() {

        monitorWhitelist.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(whitelistFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Block block = Block.getBlockFromName(line.trim());
                    if (block != null) monitorWhitelist.add(block);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(monitorWhitelist);
    }
    public static void saveWhitelistToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(whitelistFile))) {
            for (Block name : monitorWhitelist) {
                ResourceLocation rl = Block.blockRegistry.getNameForObject(name);
                writer.write(rl.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static void whitelistBlock(Block block) {
        loadWhitelistFromFile(); // lazy-load
        String regName = Block.blockRegistry.getNameForObject(block).toString();
        monitorWhitelist.add(Block.getBlockFromName(regName));
        saveWhitelistToFile(); // persist
    }



    public static boolean isWhitelisted(Block block) {
        loadWhitelistFromFile();
        String regName = Block.blockRegistry.getNameForObject(block).toString();
        return monitorWhitelist.contains(regName);
    }

    public static Set<Block> getWhitelist() {
        loadWhitelistFromFile();
        return monitorWhitelist;
    }
    private static final Set<String> allowedEntities = new HashSet<>(Arrays.asList(
            "EntityWither"
    ));
    private List<BlockPos> getSurroundingBlockPositions(EntityPlayer player) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    positions.add(pos);
                }
            }
        }

        return positions;
    }
public static boolean activeMacroDetection = false;

    int lastSlot = -1;

    int continuousweirdvelocity = 0;
    int continuousweirdvelocityy = 0;
    public static boolean moving = false;
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if(!activeMacroDetection) {

            lastYaw = Float.NaN;
            lastPitch = Float.NaN;
            lastX = Double.NaN;
            lastY = Double.NaN;
            lastZ = Double.NaN;
            lastSlot = -1;
            lastBlockSnapshot.clear();
            return;

        }
        if(!initialised){
            loadWhitelistFromFile();
            initialised = true;
        }
        // sharp turn detection
        float yaw = mc.thePlayer.rotationYaw;
        float pitch = mc.thePlayer.rotationPitch;

        if (!Float.isNaN(lastYaw)) {
            float deltaYaw = Math.abs(yaw - lastYaw);
            float deltaPitch = Math.abs(pitch - lastPitch);
            if ((deltaYaw > 85f || deltaPitch > 85f) || (deltaPitch + deltaYaw) > 85f && !isTeleporting) {
                DetectedMacroCheck.alert("Sharp turn detected");
            }

        }

        lastYaw = yaw;
        lastPitch = pitch;

        // teleport detection
        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        if (!Double.isNaN(lastX) && !isTeleporting) {
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            if (dx * dx + dy * dy + dz * dz > 3) {
                DetectedMacroCheck.alert("Unexpected teleport");
            }
        }

        lastX = x;
        lastY = y;
        lastZ = z;


        blockTickCounter++;
        if (blockTickCounter >= 60) {
            blockTickCounter = 0;




            if (mc.thePlayer != null && mc.theWorld != null) {
                for (BlockPos pos : getSurroundingBlockPositions(mc.thePlayer)) {
                    Block current = mc.theWorld.getBlockState(pos).getBlock();
                    Block previous = lastBlockSnapshot.get(pos);

                    if (previous == null && !monitorWhitelist.contains(current) && current != Blocks.air) {
                        DetectedMacroCheck.alert("New suspicious block appeared: " + current.getLocalizedName());
                    } else if (previous != null && previous != current && !monitorWhitelist.contains(previous)) {
                        DetectedMacroCheck.alert("Block changed at " + pos + ": " + previous.getLocalizedName() + " -> " + current.getLocalizedName());
                    }

                    lastBlockSnapshot.put(pos, current);
                }

// Optional: cleanup
                lastBlockSnapshot.keySet().retainAll(getSurroundingBlockPositions(mc.thePlayer));


                EntityPlayerSP player = mc.thePlayer;
                World world = mc.theWorld;

                AxisAlignedBB scanBox = new AxisAlignedBB(
                        player.posX - 5, player.posY - 5, player.posZ - 5,
                        player.posX + 5, player.posY + 5, player.posZ + 5
                );

                List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, scanBox);
                entities.removeIf(e -> e == player); // ignore self

                for (Entity entity : entities) {
                    String type = entity.getClass().getSimpleName();

                    if (!allowedEntities.contains(type)) {
                        // Whitelist exceptions
                        boolean whitelisted = false;

                        // Iron Golem, Magma Cube, Goblins
                        if (type.equals("EntityIronGolem") || type.equals("EntityMagmaCube") || type.equals("Goblin")) {
                            whitelisted = true;
                        }

                        // Armor stand name check
                        if (entity instanceof EntityArmorStand && entity.hasCustomName()) {
                            String name = entity.getCustomNameTag().toLowerCase();
                            if (name.contains("automaton") || name.contains("yog") || name.contains("goblins")) {
                                whitelisted = true;
                            }
                        }

                        if (!whitelisted) {
                            // Check if player can see this entity
                            if (player.canEntityBeSeen(entity)) {
                                if (entity.isInvisible()) {
                                    DetectedMacroCheck.alert("⚠️ Entity is invisible: " + type);
                                } else {
                                    DetectedMacroCheck.alert("Unusual visible entity nearby: " + type);
                                }
                            }
                        }
                    }


                }
            }

        }

        int currentSlot = mc.thePlayer.inventory.currentItem;

        if (lastSlot != -1 && currentSlot != lastSlot) {
            if (!isTeleporting) {
                DetectedMacroCheck.alert("Hotbar slot changed unexpectedly (from " + lastSlot + " to " + currentSlot + ")");
            }
        }

        lastSlot = currentSlot;


        EntityPlayerSP player = mc.thePlayer;
        if(moving) {
// Get player motion vector (last tick)
            Vec3 velocity = new Vec3(player.motionX, player.motionY, player.motionZ);

// Ignore if not moving
            if (velocity.lengthVector() < 0.05) return;

// Get the player’s look direction (horizontal only)
            float yawRad = (float) Math.toRadians(player.rotationYaw);
            Vec3 forwardDir = new Vec3(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad)).normalize();

// Project motion onto horizontal plane
            Vec3 flatVelocity = new Vec3(player.motionX, 0, player.motionZ).normalize();

// Compute angle between look direction and velocity
            double dot = forwardDir.dotProduct(flatVelocity); // 1 = forward, 0 = perpendicular, -1 = backward

            if (dot < 0.7 && player.motionX != 0 && player.motionY != 0 && player.motionZ != 0) { // moving sideways or backward (threshold ~45° cone)
                if (!isTeleporting) {
                    continuousweirdvelocity++;
                    if(continuousweirdvelocity>15){
                        DetectedMacroCheck.alert("Unnatural movement direction detected.");
                        continuousweirdvelocity = 0;
                    }

                } else continuousweirdvelocity = 0;
            } else continuousweirdvelocity = 0;

        } else{
            double velocityMagnitude = Math.sqrt(
                    player.motionX * player.motionX +
                            player.motionY * player.motionY +
                            player.motionZ * player.motionZ
            );

            double threshold = 0.1; // tweak this small value as needed

            if (velocityMagnitude > threshold) {


                // player is moving
                System.out.println(velocityMagnitude);
                if(continuousweirdvelocityy==0) {
                    DetectedMacroCheck.alert("Unnatural movement direction detected while not moving.");

                    continuousweirdvelocityy = 15;
                } else continuousweirdvelocityy --;
            } else continuousweirdvelocityy=0;

        }
    }
}
