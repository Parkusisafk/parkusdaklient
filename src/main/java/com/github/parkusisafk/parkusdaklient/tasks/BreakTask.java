package com.github.parkusisafk.parkusdaklient.tasks;

import com.github.parkusisafk.parkusdaklient.SkyblockMod;
import net.minecraft.util.BlockPos;

public class BreakTask extends Task {
    private boolean started = false;
    private boolean finished = false;
    private final int timeoutTicks; // NEW

    public BreakTask(BlockPos pos) {
        this(pos, 20 * 8); // default 8 seconds
    }

    public BreakTask(BlockPos pos, int timeoutTicks) { // NEW overload
        super(pos);
        this.timeoutTicks = Math.max(0, timeoutTicks);
    }

    @Override
    public void start() {
        started = true;
        finished = false;
        SkyblockMod.blockBreakingHandler.startBreaking(pos, timeoutTicks); // use timeout
    }

    @Override
    public void update() {
        // When handler stops (either success or timeout), we’re done
        if (started && !SkyblockMod.blockBreakingHandler.isActive()) {
            finished = true;
        }
    }

    @Override
    public boolean isFinished() {
        return started && finished;
    }

    @Override
    public String getDescription() {
        return "Break: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() +
                " (≤ " + (timeoutTicks / 20.0) + "s)";
    }
}

