package com.github.parkusisafk.parkusdaklient.mixin;

import com.github.parkusisafk.parkusdaklient.SkyblockMod;
import com.github.parkusisafk.parkusdaklient.command.CommandBreakTask;
import com.github.parkusisafk.parkusdaklient.macro.MacroCheckDetector;
import com.github.parkusisafk.parkusdaklient.tasks.Task;
import com.github.parkusisafk.parkusdaklient.util.GuiOpener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {
    static {
        System.out.println("[Mixin] MixinEntityPlayerSP loaded!");
    }
Minecraft mc = Minecraft.getMinecraft();
    @Inject(method = "func_71165_d", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSendChatMessage(String msg, CallbackInfo ci) {
        if (msg.equalsIgnoreCase(".mymenu")) {
            System.out.println(".mymenu is called by player");
            GuiOpener.openGuiNextTick();
            System.out.println(".mymenu is called by player!!!");
             ci.cancel(); // Don't send to server */
        }
        else if(msg.contains(".macrocheck") || msg.contains(".mc")){
            MacroCheckDetector.activeMacroDetection = true;
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lEnabled Macro Check Detection!"));
            try {
                int mode = Integer.parseInt(msg.trim().split(" ")[1]);
                new Thread(() -> {
                    try {
                        Thread.sleep(400);

                        com.github.parkusisafk.parkusdaklient.command.CommandMacroCheck.run(mode);

                        Thread.sleep(400);

                        MacroCheckDetector.activeMacroDetection = false;
                        mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lDisabled Macro Check Detection!"));
                    } catch (Exception f) {
                        f.printStackTrace();
                    }



                }).start(); // Start the new thread

            } catch (Exception e) {
                String[] parts = msg.trim().split(" ");
                if (parts.length == 1) {
                    // Run in a background thread to prevent game freezing
                    new Thread(() -> {
                        try {
                            for (int nigga = 1; nigga != 8; nigga++) {
                                Thread.sleep(500);
                                com.github.parkusisafk.parkusdaklient.command.CommandMacroCheck.run(nigga);
                                Thread.sleep(2000); // Sleep 2 seconds between checks
                            }
                        } catch (InterruptedException f) {
                            f.printStackTrace();
                        }

                        MacroCheckDetector.activeMacroDetection = false;
                        mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lDisabled Macro Check Detection!"));

                    }).start(); // Start the new thread
                }

                else mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cUsage: .macrocheck <1-7>"));
            }
            ci.cancel(); // prevent command from reaching server
        }
        else if(msg.contains(".allowteleport")){
            MacroCheckDetector.INSTANCE.setTeleporting(true);
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §dDisabled Teleport Macro Check"));
            ci.cancel();
        }
        else if(msg.contains(".disallowteleport")){
            MacroCheckDetector.INSTANCE.setTeleporting(false);
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §dEnabled Teleport Macro Check"));

            ci.cancel();

        }
        else if (msg.startsWith(".monitorwhitelist ") || msg.startsWith(".mw")) {
            String[] parts = msg.trim().split(" ");
            if (parts.length < 2) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cUsage: .monitorwhitelist <blockname>"));
                ci.cancel();
                return;
            }

            Block block = Block.getBlockFromName(parts[1]);
            if (block == null || block instanceof BlockAir) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cInvalid block: " + parts[1]));
            } else {
                MacroCheckDetector.whitelistBlock(block);
                boolean added = true;

                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] " +
                        (added ? "§aAdded to whitelist: §f" + block : "§cAlready whitelisted.")));            }
            ci.cancel();
        }

        else if (msg.startsWith(".removemonitorwhitelist ") || msg.startsWith(".rmw")) {
            String[] parts = msg.trim().split(" ");
            if (parts.length < 2) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cUsage: .removemonitorwhitelist <blockname>"));
                ci.cancel();
                return;
            }

            Block block = Block.getBlockFromName(parts[1]);
            if (block == null || block instanceof BlockAir) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cInvalid block: " + parts[1]));
            } else if (MacroCheckDetector.removeWhitelistedBlock(block)) {
                boolean removed = true;
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] " +
                        (removed ? "§aRemoved from whitelist: §f" + block : "§cNot in whitelist.")));            } else {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cBlock not in whitelist: " + parts[1]));
            }
            ci.cancel();
        } else if (msg.equalsIgnoreCase(".getblockname")) {
            Block lookingAt = null;
            try {
                MovingObjectPosition mop = mc.objectMouseOver;
                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    BlockPos pos = mop.getBlockPos();
                    lookingAt = mc.theWorld.getBlockState(pos).getBlock();
                    String regName = Block.blockRegistry.getNameForObject(lookingAt).toString(); // registry name
                    String displayName = lookingAt.getLocalizedName(); // display name

                    mc.thePlayer.addChatMessage(new ChatComponentText(
                            "§b[ParkusDaKlient] §eBlock: §a" + regName + " §7(" + displayName + ")"
                    ));
                } else {
                    mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cYou're not looking at a block."));
                }
            } catch (Exception e) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cFailed to get block name."));
                e.printStackTrace();
            }
            ci.cancel();
        } else if (msg.equalsIgnoreCase(".listmonitorwhitelist") || msg.equalsIgnoreCase(".lmw")) {
            List<Block> whitelist = MacroCheckDetector.getWhitelistedBlocks();
            if (whitelist.isEmpty()) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §7No blocks are whitelisted."));
            } else {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §7Whitelisted blocks:"));
                for (Block block : whitelist) {
                    String regName = Block.blockRegistry.getNameForObject(block).toString();
                    mc.thePlayer.addChatMessage(new ChatComponentText("§7- §a" + regName));
                }
            }
            ci.cancel();
        } else if (msg.equalsIgnoreCase(".allowmacrodetection") || msg.equalsIgnoreCase(".amd")){
            MacroCheckDetector.activeMacroDetection = true;
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lEnabled Macro Check Detection!"));
            ci.cancel();
        } else if (msg.equalsIgnoreCase(".disallowmacrodetection") || msg.equalsIgnoreCase(".dmd")){
            MacroCheckDetector.activeMacroDetection = false;
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lDisabled Macro Check Detection!"));
            ci.cancel();
        } else if (msg.startsWith(".breaktask")){

            String[] parts = msg.trim().split(" ");
            if (parts.length < 2) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cUsage: .breaktask <blockname>"));
                ci.cancel();
                return;
            }


            Set<Block> targetBlocks = new HashSet<>();

            for (String xxxyyy : parts){
                if(Objects.equals(xxxyyy.toLowerCase(), ".breaktask")) continue;
                Block block = Block.getBlockFromName(xxxyyy);
                if (block == null || block == Blocks.air) {
                    mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cInvalid Block Name " + xxxyyy + "!"));
                    ci.cancel();
                    return;
                }
                targetBlocks.add(block);

            }
            CommandBreakTask.run(targetBlocks);
            ci.cancel();

        } else if (msg.equalsIgnoreCase(".gem")){
            Set<Block> targetBlocks = new HashSet<>();
            targetBlocks.add(Block.getBlockFromName("stained_glass"));
            targetBlocks.add(Block.getBlockFromName("stained_glass_pane"));
            targetBlocks.add(Block.getBlockFromName("glass_pane"));
            targetBlocks.add(Block.getBlockFromName("glass"));
            CommandBreakTask.run(targetBlocks);
            ci.cancel();

        } else if (msg.startsWith(".run")){
            String[] parts = msg.trim().split(" ");

            if(parts.length == 1){
                List<Task> snapshot = SkyblockMod.taskManager.getQueueSnapshot();
                if (snapshot.isEmpty()) {
                    mc.thePlayer.addChatMessage(new ChatComponentText("§7No tasks queued."));
                    return;
                }
                // Close first, then start on next tick to ensure handlers see GUI closed
                mc.displayGuiScreen(null);
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                        () -> SkyblockMod.taskManager.executeAll(1,snapshot)
                );

            }
            else{
                try {
                    int loops = Integer.parseInt(parts[1]);
                    if (loops < 1) loops = 1;
                    if (loops > 5000) loops = 5000; // safety cap

                    List<Task> snapshot = SkyblockMod.taskManager.getQueueSnapshot();
                    if (snapshot.isEmpty()) {
                        mc.thePlayer.addChatMessage(new ChatComponentText("§7No tasks queued."));
                        return;
                    }

                    mc.displayGuiScreen(null);
                    final int loopsFinal = loops;
                    net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                        mc.thePlayer.addChatMessage(new ChatComponentText("§aExecuting tasks x" + loopsFinal));
                        SkyblockMod.taskManager.executeAll(loopsFinal, snapshot);
                    });

                } catch (Exception e) {
                    mc.thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §cUsage: .run <number of times>"));

                }

            }


        }



    }
}