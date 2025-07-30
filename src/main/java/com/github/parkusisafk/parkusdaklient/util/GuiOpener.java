// parkusdaklient/util/GuiOpener.kt or .java
package com.github.parkusisafk.parkusdaklient.util;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import com.github.parkusisafk.parkusdaklient.gui.GuiTaskMenu;

public class GuiOpener {
    private static boolean openNextTick = false;

    public static void openGuiNextTick() {
        openNextTick = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && openNextTick) {
            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiTaskMenu(Minecraft.getMinecraft().thePlayer)
            );
            openNextTick = false;
        }
    }
}
