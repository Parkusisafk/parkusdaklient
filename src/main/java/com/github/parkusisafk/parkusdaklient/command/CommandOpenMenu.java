package com.github.parkusisafk.parkusdaklient.command;

import com.github.parkusisafk.parkusdaklient.gui.GuiTaskMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

import java.util.Collections;
import java.util.List;
public class CommandOpenMenu implements ICommand {


        @Override
        public String getCommandName() {
            return "mymenu";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/mymenu";
        }

        @Override
        public List<String> getCommandAliases() {
            return Collections.singletonList("mymenu");
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().theWorld != null) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiTaskMenu(Minecraft.getMinecraft().thePlayer));
            } else {
                System.out.println("Client-side: World or player is null");
            }
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
            return null;
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return false;
        }

        @Override
        public int compareTo(ICommand o) {
            return this.getCommandName().compareTo(o.getCommandName());
        }
    }