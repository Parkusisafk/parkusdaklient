package com.github.parkusisafk.parkusdaklient.tasks;

import com.github.parkusisafk.parkusdaklient.SkyblockMod;
import net.minecraft.util.BlockPos;

import java.util.Random;

public class WalkToTask extends Task {

    private boolean started = false;
    private static final Random RNG = new Random();

    // Stop distance (random 1.0..2.0 blocks)
    private final double stopRadiusSq;
    // Timeout in ticks (default 30s)
    private final int timeoutTicks;
    private int elapsed = 0;
    private boolean done = false;
    private boolean arrived = false;

    public WalkToTask(BlockPos pos) {
        this(pos, 20 * 10); // 10 seconds default
    }

    public WalkToTask(BlockPos pos, int timeoutTicks) {
        super(pos);
        double stopRadius = 1.0 + RNG.nextDouble() * 1.0; // 1.0..2.0 blocks
        this.stopRadiusSq = stopRadius * stopRadius;
        this.timeoutTicks = Math.max(0, timeoutTicks);
    }

    @Override
    public String getDescription() {
        double stopRadius = Math.sqrt(stopRadiusSq);
        return String.format("Walk To: %d, %d, %d (stop ≤ %.2f m, timeout %ss)",
                pos.getX(), pos.getY(), pos.getZ(), stopRadius, timeoutTicks / 20);
    }

    @Override
    public void start() {
        started = true;
        // Try walking until within stopRadius, with a safety cap equal to our timeout
        SkyblockMod.moveForwardHandler.moveWhileFacing(
                () -> mc.thePlayer != null && mc.thePlayer.getDistanceSqToCenter(pos) > stopRadiusSq,
                timeoutTicks,
                pos
        );
    }

    @Override
    public void update() {
        if (!started || done || mc.thePlayer == null) return;

        double d2 = mc.thePlayer.getDistanceSqToCenter(pos);
        if (d2 <= stopRadiusSq) {
            arrived = true;
            done = true;
            SkyblockMod.moveForwardHandler.cancel(); // release W and clear look target
            return;
        }

        // Timeout fallback so the task doesn't hang if movement stopped
        elapsed++;
        if (timeoutTicks > 0 && elapsed >= timeoutTicks) {
            done = true;
            SkyblockMod.moveForwardHandler.cancel();
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    String.format("§eOH SHIT IS THIS A MACRO CHECK??? Walk timed out after %ss to %d,%d,%d",
                            timeoutTicks / 20, pos.getX(), pos.getY(), pos.getZ())
            ));
        }
    }

    @Override
    public boolean isFinished() {
        return done;
    }
}