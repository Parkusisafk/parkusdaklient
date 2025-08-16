package com.github.parkusisafk.parkusdaklient.handlers;

import com.github.parkusisafk.parkusdaklient.tasks.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ClientTickDispatcher {

    private final TaskManager taskManager;
    private final BlockBreakingHandler breaker;

    public ClientTickDispatcher(TaskManager taskManager, BlockBreakingHandler breaker) {
        this.taskManager = taskManager;
        this.breaker = breaker;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)        // allow chat
                && !(mc.currentScreen instanceof GuiIngameMenu)) return;
        taskManager.update(); // run current task if any
        // BlockBreakingHandler updates itself via its own tick subscription
    }
}
