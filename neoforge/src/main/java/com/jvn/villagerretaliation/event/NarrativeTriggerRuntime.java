package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class NarrativeTriggerRuntime {
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public void clear() { this.inFlight.clear(); }

    public boolean run(String key, boolean repeatable, long cooldown, long gameTime,
            TriggerHistory history, BooleanSupplier effect) {
        long last = history.lastRunGameTime(key);
        if (!repeatable && last > 0L || cooldown > 0L && last > 0L && gameTime - last < cooldown) return false;
        if (!this.inFlight.add(key)) return false;
        try {
            if (!effect.getAsBoolean()) return false;
            history.markRun(key, gameTime);
            return true;
        } finally {
            this.inFlight.remove(key);
        }
    }

    public boolean fire(DialogueRuntimeContext context, String key, boolean repeatable, long cooldown,
            TriggerHistory history, List<DialogueCondition> conditions, List<VillagerActionDefinition> actions,
            Map<String, String> replacements) {
        if (!DialogueCondition.matchesAll(context, conditions)) return false;
        return run(key, repeatable, cooldown, context.level().getGameTime(), history, () -> {
            var outcome = context.runtime().actions(context, actions, replacements, "trigger/" + key, null).outcome();
            return outcome.suspended() || outcome.legacyResult().ran();
        });
    }
}
