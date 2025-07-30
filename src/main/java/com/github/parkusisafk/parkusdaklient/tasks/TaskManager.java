package com.github.parkusisafk.parkusdaklient.tasks;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private final List<Task> queue = new ArrayList<>();
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
    }

    public int size() { return queue.size(); }

    public void executeAll() {
        if (current == null && !queue.isEmpty()) {
            current = queue.remove(0);
            current.start();
        }
    }

    public void update() {
        if (current == null) return;
        current.update();
        if (current.isFinished()) {
            current = null;
            if (!queue.isEmpty()) {
                current = queue.remove(0);
                current.start();
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
}
