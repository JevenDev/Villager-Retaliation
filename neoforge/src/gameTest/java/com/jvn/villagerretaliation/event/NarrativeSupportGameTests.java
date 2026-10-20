package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.memory.EventMemoryPolicy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_support")
@PrefixGameTestTemplate(false)
public final class NarrativeSupportGameTests {
    private NarrativeSupportGameTests() { }

    @GameTest(template = "empty")
    public static void triggerHistoryRetainsCooldownAndRejectsReentrantEffects(GameTestHelper helper) {
        var runtime = new NarrativeTriggerRuntime();
        var runs = new java.util.HashMap<String, Long>();
        var history = new TriggerHistory() {
            @Override public long lastRunGameTime(String key) { return runs.getOrDefault(key, 0L); }
            @Override public void markRun(String key, long time) { runs.put(key, time); }
        };
        helper.assertTrue(runtime.run("entity", true, 20, 100, history, () -> {
            helper.assertFalse(runtime.run("entity", true, 0, 100, history, () -> true), "recursive triggers cannot repeat effects");
            return true;
        }), "successful trigger stores its receipt");
        helper.assertFalse(runtime.run("entity", true, 20, 119, history, () -> true), "cooldown blocks early repeats");
        helper.assertTrue(runtime.run("entity", true, 20, 120, history, () -> true), "cooldown boundary allows a repeat");
        helper.assertFalse(runtime.run("entity", false, 0, 200, history, () -> true), "one-shot trigger honors existing history");
        helper.assertFalse(runtime.run("other", true, 0, 200, history, () -> false), "an unavailable effect does not consume its trigger");
        helper.assertFalse(runs.containsKey("other"), "unavailable effect has no receipt");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityMemoryActionsAndConditionsShareTheConfiguredStore(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, 1, 1, 1);
        var personal = new java.util.ArrayList<com.jvn.villagerretaliation.memory.EventMemory>();
        var group = new java.util.ArrayList<com.jvn.villagerretaliation.memory.EventMemory>();
        var store = new com.jvn.villagerretaliation.memory.EventMemoryAccess() {
            @Override public List<? extends com.jvn.villagerretaliation.memory.EventMemory> personal(java.util.UUID entity) { return personal; }
            @Override public List<? extends com.jvn.villagerretaliation.memory.EventMemory> group(String key) { return group; }
            @Override public boolean remember(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context,
                    net.minecraft.resources.ResourceLocation tag, boolean self, boolean communal) {
                var memory = new Memory(tag, context.level().getGameTime(), context.providerId(), context.player().getUUID());
                if (self) personal.add(memory);
                if (communal) group.add(memory);
                return self || communal;
            }
        };
        var context = new com.jvn.villagerretaliation.dialogue.EntityDialogueContext(helper.getLevel(), player, cow,
                "en_us", "test:herd", com.jvn.villagerretaliation.action.NarrativeActions.common().withMemory(store));
        var tag = net.minecraft.resources.ResourceLocation.parse("test:greeting");
        var action = com.jvn.villagerretaliation.action.VillagerActionDefinition.readListOrInline(tag, "actions",
                com.google.gson.JsonParser.parseString("{\"type\":\"memory\",\"memory_event\":\"test:greeting\"}").getAsJsonObject()).getFirst();
        helper.assertTrue(context.actions().execute(context, action, Map.of()).success(), "mob memory action uses the configured store");
        var condition = new com.jvn.villagerretaliation.dialogue.DialogueCondition.Memory(java.util.Set.of(tag),
                com.jvn.villagerretaliation.dialogue.DialogueCondition.MemorySource.THIS_VILLAGER, true,
                com.jvn.villagerretaliation.dialogue.DialogueCondition.MemoryKind.EVENT_TAG);
        helper.assertTrue(com.jvn.villagerretaliation.dialogue.DialogueCondition.matchesAll(context, List.of(condition)),
                "mob conditions read the memory written by its action");
        var other = new com.jvn.villagerretaliation.dialogue.DialogueCondition.Memory(java.util.Set.of(tag),
                com.jvn.villagerretaliation.dialogue.DialogueCondition.MemorySource.OTHER_VILLAGER, true,
                com.jvn.villagerretaliation.dialogue.DialogueCondition.MemoryKind.EVENT_TAG);
        helper.assertFalse(com.jvn.villagerretaliation.dialogue.DialogueCondition.matchesAll(context, List.of(other)),
                "other-entity memory excludes the current speaker");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityTriggersReadSavedEventPayloads(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, 1, 1, 1);
        var context = new com.jvn.villagerretaliation.dialogue.EntityDialogueContext(helper.getLevel(), player, cow, "en_us", "");
        var trigger = new com.jvn.villagerretaliation.quest.QuestTriggerContext(
                com.jvn.villagerretaliation.quest.QuestDefinition.TriggerEvent.BLOCK_BREAK,
                helper.getLevel().getGameTime(), "active", Map.of("block", "minecraft:stone"), context);
        var restored = com.jvn.villagerretaliation.quest.QuestTriggerContext.load(trigger.save()).withRuntimeContext(context);
        helper.assertTrue(restored.runtimeContext() == context && restored.dialogueContext() == null,
                "generic event snapshots retain the configured runtime without inventing a villager context");
        var history = new TriggerHistory() {
            private long last;
            @Override public long lastRunGameTime(String key) { return this.last; }
            @Override public void markRun(String key, long time) { this.last = time; }
        };
        var condition = new com.jvn.villagerretaliation.dialogue.DialogueCondition.TriggerPayload(java.util.Set.of(),
                Map.of("block", java.util.Set.of("minecraft:stone")), Map.of(), Map.of(), null, null);
        var actions = com.jvn.villagerretaliation.action.VillagerActionDefinition.readListOrInline(
                net.minecraft.resources.ResourceLocation.parse("test:trigger"), "actions",
                com.google.gson.JsonParser.parseString("{\"type\":\"experience\",\"amount\":3}").getAsJsonObject());
        int experience = player.totalExperience;
        helper.assertTrue(new NarrativeTriggerRuntime().fire(context, "stone", true, 0, history, List.of(condition),
                actions, Map.of(), restored), "entity trigger evaluates its captured event payload");
        helper.assertValueEqual(player.totalExperience, experience + 3, "matched entity trigger runs its common action");
        helper.succeed();
    }

    private record Memory(net.minecraft.resources.ResourceLocation tagId, long gameTime, java.util.UUID sourceId,
            java.util.UUID playerId) implements com.jvn.villagerretaliation.memory.EventMemory { }

    @GameTest(template = "empty")
    public static void eventMemoryRetentionIsIndependentOfVillageEventTypes(GameTestHelper helper) {
        var policy = new EventMemoryPolicy<Long>(3, 10, Long::longValue);
        var events = new ArrayDeque<Long>(List.of(7L, 3L, 5L, 1L));
        policy.normalize(events);
        helper.assertValueEqual(List.copyOf(events), List.of(3L, 5L, 7L), "normalization keeps the newest events");
        helper.assertFalse(policy.append(events, 7L, (bucket, event) -> bucket.contains(event)), "consumer deduplication avoids repeated events");
        helper.assertTrue(policy.append(events, 9L, null), "a new event is accepted");
        helper.assertValueEqual(List.copyOf(events), List.of(5L, 7L, 9L), "capacity removes the oldest event");
        helper.assertTrue(policy.prune(events, 18L), "expired events are removed");
        helper.assertValueEqual(List.copyOf(events), List.of(9L), "retention uses each event timestamp");
        helper.succeed();
    }
}
