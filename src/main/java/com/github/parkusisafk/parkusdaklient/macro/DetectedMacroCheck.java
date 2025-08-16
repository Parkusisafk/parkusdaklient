package com.github.parkusisafk.parkusdaklient.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.awt.*;



public class DetectedMacroCheck {
    public static void requestFocusWindows() {

        try {

            // This causes the MC window to "flash" on taskbar
            Toolkit.getDefaultToolkit().beep();
            java.awt.Frame frame = org.lwjgl.opengl.Display.getParent() instanceof java.awt.Canvas
                    ? (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(org.lwjgl.opengl.Display.getParent())
                    : null;
            if (frame != null) {
                frame.toFront();
                frame.requestFocus();
            }
        } catch (Exception ignored) {}
    }

    private static void mightbecheck() {
        new Thread(() -> {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 10_000) { // 10 seconds
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (Minecraft.getMinecraft().thePlayer != null) {
                        Minecraft.getMinecraft().thePlayer.playSound("random.anvil_land", 1.0F, 1.0F);
                    }
                });

                try {
                    Thread.sleep(200); // play 5 per second
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    public static void confirmcheck() {
        new Thread(() -> {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 20_000) { // 20 seconds
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (Minecraft.getMinecraft().thePlayer != null) {
                        Minecraft.getMinecraft().thePlayer.playSound("random.anvil_break", 2.0F, 0.8F);
                    }
                });

                try {
                    Thread.sleep(111); // play ~9 per second
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
    public static void alert(String reason) {
        requestFocusWindows();

        Minecraft.getMinecraft().thePlayer.addChatMessage(
                new ChatComponentText("§cMACRO CHECK POSSIBLE: §f" + reason)
        );

        if(reason.contains("invisible") || reason.contains("entity") || reason.contains("Teleporting timed out") || reason.contains("Teleportation unable to calculate teleport face")){
            //may be check

            mightbecheck();
        } else{

            confirmcheck();
        }
    }


    //may be check
    //entity is invisible
    //unusual visible entity
    //Teleporting timed out
    //Teleportation unable to calculate teleport face


    //definitely check
    //turning timed out
    //sharp turn
    //unexpected teleport
    //new suspicious block
    //block changed
    //hotbar slot changed unexpectedly
    //unnatural movement direction

}
