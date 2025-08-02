package com.github.parkusisafk.parkusdaklient.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public class DetectedMacroCheck {
    public static void alert(String reason) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(
                new ChatComponentText("§cMACRO CHECK POSSIBLE: §f" + reason)
        );
    }
}
