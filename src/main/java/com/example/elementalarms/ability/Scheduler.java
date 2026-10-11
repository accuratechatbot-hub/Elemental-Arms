package com.example.elementalarms.ability;

import com.example.elementalarms.ElementalArms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-thread-only delayed task runner (ticked from the server tick event). */
public final class Scheduler {
    private static final class Task {
        int delay;
        final Runnable action;

        Task(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }

    private static final List<Task> ACTIVE = new ArrayList<>();
    private static final List<Task> INCOMING = new ArrayList<>();

    private Scheduler() {}

    public static void schedule(int delayTicks, Runnable action) {
        INCOMING.add(new Task(Math.max(0, delayTicks), action));
    }

    public static void tick() {
        if (!INCOMING.isEmpty()) {
            ACTIVE.addAll(INCOMING);
            INCOMING.clear();
        }
        if (ACTIVE.isEmpty()) return;

        List<Runnable> due = new ArrayList<>();
        Iterator<Task> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.delay <= 0) {
                due.add(t.action);
                it.remove();
            } else {
                t.delay--;
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Exception ex) {
                ElementalArms.LOGGER.error("Scheduled ability task failed", ex);
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        INCOMING.clear();
    }
}
