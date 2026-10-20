package com.jvn.villagerretaliation.event;

public interface TriggerHistory {
    long lastRunGameTime(String key);
    void markRun(String key, long gameTime);
}
