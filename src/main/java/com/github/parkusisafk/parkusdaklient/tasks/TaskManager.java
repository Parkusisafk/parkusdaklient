package com.github.parkusisafk.parkusdaklient.tasks;

import com.github.parkusisafk.parkusdaklient.handlers.BlockBreakingHandler;
import com.github.parkusisafk.parkusdaklient.macro.MacroCheckDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private final List<Task> queue = new ArrayList<>();
    private List<Task> all = new ArrayList<>();    // new 'all' list for executeAll()

    private Task current = null;

    public void add(Task t) {
        if (t != null) queue.add(t);
    }

    public List<Task> getQueueSnapshot() {
        return new ArrayList<>(queue);
    }

    public Task getCurrent() {
        return current;
    }

    public void clear() {
        queue.clear();
        current = null;
        MacroCheckDetector.activeMacroDetection = false;
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lDisabled Macro Check Detection! §5(You ended macro task)"));

    }

    public int size() { return queue.size(); }



    public void executeAll(int times, List<Task> snapshot) {
        if (times < 1) times = 1;
        if (snapshot == null || snapshot.isEmpty()) return;

        List<Task> tempAll = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            for (Task t : snapshot) {
                Task clone = cloneTask(t);
                if (clone != null) tempAll.add(clone);
            }
        }
        this.all = tempAll;  // assign to field 'all'
        this.current = null;

        if (!all.isEmpty()) {
            MacroCheckDetector.activeMacroDetection = true;
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lEnabled Macro Check Detection! §5(You started macro task)"));


            current = all.remove(0);
            MacroCheckDetector.moving = current instanceof WalkToTask;

            if (!all.isEmpty() && all.get(0) instanceof  BreakTask) {
                BlockBreakingHandler.nextMiningTask = true;
            }
            stopexecution = false;
            current.start();
        }
    }

public static boolean stopexecution = false;
    public void update(){
        if (current == null ) return;


        current.update();
        if (current.isFinished()) {
            current = null;
            if(stopexecution){
                stopexecution = false;
                all.clear();
                System.out.println("Set All to empty!");
                MacroCheckDetector.activeMacroDetection = false;
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lDisabled Macro Check Detection! §5(You ended macro task)"));

                MacroCheckDetector.moving = false;
                BlockBreakingHandler.nextMiningTask = false;
                return;
            }

            // Only set flags *after* getting the next task
            if (!all.isEmpty()) {
                current = all.remove(0);

                // Set movement flag if this is a Walk task
                if(!(current instanceof  WalkToTask)){
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000); // wait 1k ms
                            MacroCheckDetector.moving = current instanceof WalkToTask;
                        } catch (InterruptedException h) {
                            h.printStackTrace();
                        }
                    }).start();
                } else MacroCheckDetector.moving = current instanceof WalkToTask;


                // Set mining flag if *next* task after current is Break
                if (!all.isEmpty() && all.get(0) instanceof BreakTask) {
                    BlockBreakingHandler.nextMiningTask = true;
                } else {
                    BlockBreakingHandler.nextMiningTask = false;
                }

                current.start();
            } else {
                // No more tasks: reset flags
                MacroCheckDetector.activeMacroDetection = false;
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§b[ParkusDaKlient] §d§lDisabled Macro Check Detection! §5(You ended macro task)"));

                MacroCheckDetector.moving = false;
                BlockBreakingHandler.nextMiningTask = false;
            }
        }
    }

    /** Remove and return the most recently queued task (or null if none). */
    public Task removeLast() {
        if (queue.isEmpty()) return null;
        return queue.remove(queue.size() - 1);
    }

    /** Remove and return the task at index (0-based), or null if out-of-bounds. */
    public Task removeAt(int index) {
        if (index < 0 || index >= queue.size()) return null;
        return queue.remove(index);
    }

    /** Move a task from index 'from' to index 'to' (both 0-based). No-op if invalid or same. */
    public void move(int from, int to) {
        if (from < 0 || from >= queue.size()) return;
        if (to   < 0 || to   >= queue.size()) return;
        if (from == to) return;

        Task t = queue.remove(from);
        queue.add(to, t);
    }


    private Task cloneTask(Task t) {
        if (t instanceof WalkToTask) {
            WalkToTask walk = (WalkToTask) t;
            return new WalkToTask(getTarget(walk), getTimeout(walk));
        }

        if (t instanceof BreakTask) {
            BreakTask b = (BreakTask) t;
            return new BreakTask(getTarget(b), getTimeout(b));
        }

        if (t instanceof TeleportTask) {
            TeleportTask tp = (TeleportTask) t;
            return new TeleportTask(getTarget(tp));
        }

        return null; // unknown task type
    }
    BlockPos getTarget(Task t) {
        return t.pos;
    }

    private int getTimeout(Task t) {
        try {
            Field f = t.getClass().getDeclaredField("timeoutTicks");
            f.setAccessible(true);
            return f.getInt(t);
        } catch (Exception e) {
            e.printStackTrace();
            return 200; // fallback default
        }
    }

}
