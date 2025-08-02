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

public class CommandRemoveMonitorWhitelist implements ICommand {
    @Override public String getCommandName() { return "removemonitorwhitelist"; }
    @Override public String getCommandUsage(ICommandSender sender) { return ".removemonitorwhitelist <blockname>"; }
    @Override public List<String> getCommandAliases() { return Arrays.asList("removemonitorwhitelist"); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText("§cUsage: .removemonitorwhitelist <blockname>"));
            return;
        }
        Block block = Block.getBlockFromName(args[0]);
        if (block != null && MacroCheckDetector.removeWhitelistedBlock(block)) {
            sender.addChatMessage(new ChatComponentText("§aRemoved from whitelist: " + block.getLocalizedName()));
        } else {
            sender.addChatMessage(new ChatComponentText("§cBlock not in whitelist: " + args[0]));
        }
    }

    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return Collections.emptyList(); // or suggest block names if you want
    }
    @Override public boolean isUsernameIndex(String[] args, int index) { return false; }
    @Override public int compareTo(ICommand o) { return getCommandName().compareTo(o.getCommandName()); }
}

