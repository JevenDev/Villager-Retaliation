package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.QuestTimer;
import net.minecraft.nbt.CompoundTag;

public final class QuestTimerState {
    private long elapsed;
    private long updatedAt;
    private boolean paused;
    private boolean warned;
    private boolean handled;

    public QuestTimerState(long time) { updatedAt = time; }

    public long elapsed() { return elapsed; }
    public boolean handled() { return handled; }
    public void handle() { handled = true; }
    public long remaining(QuestTimer timer) { return Math.max(0, timer.durationTicks() - elapsed); }
    public boolean elapsed(QuestTimer timer) { return elapsed >= timer.durationTicks(); }
    public boolean elapsedAt(QuestTimer timer, long time) {
        return elapsed(timer) || !paused && time > updatedAt && time - updatedAt >= remaining(timer);
    }

    public boolean update(long time, boolean running) {
        long previous = elapsed;
        if (!paused && running && time > updatedAt)
            elapsed = elapsed > Long.MAX_VALUE - (time - updatedAt) ? Long.MAX_VALUE : elapsed + time - updatedAt;
        updatedAt = time;
        paused = !running;
        return previous != elapsed;
    }

    public void resume(long time) { updatedAt = time; paused = false; }

    public boolean warn(QuestTimer timer) {
        if (warned || timer.warningTicks() == 0 || remaining(timer) > timer.warningTicks() || elapsed(timer)) return false;
        warned = true;
        return true;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Elapsed", elapsed);
        tag.putLong("UpdatedAt", updatedAt);
        tag.putBoolean("Paused", paused);
        tag.putBoolean("Warned", warned);
        tag.putBoolean("Handled", handled);
        return tag;
    }

    public static QuestTimerState load(CompoundTag tag) {
        if (tag.getLong("Elapsed") < 0) throw new IllegalArgumentException("Invalid saved quest timer.");
        var result = new QuestTimerState(tag.getLong("UpdatedAt"));
        result.elapsed = tag.getLong("Elapsed");
        result.paused = tag.getBoolean("Paused");
        result.warned = tag.getBoolean("Warned");
        result.handled = tag.getBoolean("Handled");
        return result;
    }
}
