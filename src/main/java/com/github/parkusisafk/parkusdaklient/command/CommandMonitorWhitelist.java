package com.github.parkusisafk.parkusdaklient.command;

import com.github.parkusisafk.parkusdaklient.macro.MacroCheckDetector;
import net.minecraft.block.Block;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandMonitorWhitelist implements ICommand {
    @Override public String getCommandName() { return "monitorwhitelist"; }
    @Override public String getCommandUsage(ICommandSender sender) { return ".monitorwhitelist <blockname>"; }
    @Override public List<String> getCommandAliases() { return Arrays.asList("monitorwhitelist"); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText("§cUsage: .monitorwhitelist <blockname>"));
            return;
        }
        Block block = Block.getBlockFromName(args[0]);
        if (block != null) {
            MacroCheckDetector.whitelistBlock(block);
            sender.addChatMessage(new ChatComponentText("§aWhitelisted: " + block.getLocalizedName()));
        } else {
            sender.addChatMessage(new ChatComponentText("§cUnknown block: " + args[0]));
        }
    }

    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return Collections.emptyList(); // no tab completion for now
    }
    @Override public boolean isUsernameIndex(String[] args, int index) { return false; }
    @Override public int compareTo(ICommand o) { return getCommandName().compareTo(o.getCommandName()); }
}
