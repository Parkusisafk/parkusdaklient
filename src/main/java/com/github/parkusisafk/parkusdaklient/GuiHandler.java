package com.github.parkusisafk.parkusdaklient;

import com.github.parkusisafk.parkusdaklient.gui.GuiTaskMenu;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int MY_MENU_ID = 1;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null; // no server container for this GUI
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        System.out.println("getClientGuiElement called with ID: " + ID);
        if (ID == MY_MENU_ID) {
            System.out.println("Opening GuiMyMenu");
            return new GuiTaskMenu(player);
        }
        return null;
    }

}
