package com.github.parkusisafk.parkusdaklient.tasks;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

public abstract class Task {
    protected final Minecraft mc = Minecraft.getMinecraft();
    public final BlockPos pos;

    public Task(BlockPos pos) {
        this.pos = pos;
    }

    public abstract String getDescription();
    public abstract void start();
    public abstract void update();       // called each tick while active
    public abstract boolean isFinished();
}
