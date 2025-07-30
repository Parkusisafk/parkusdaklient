package com.github.parkusisafk.parkusdaklient.handlers;

import com.github.parkusisafk.parkusdaklient.tasks.TaskManager;
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
        taskManager.update(); // run current task if any
        // BlockBreakingHandler updates itself via its own tick subscription
    }
}
