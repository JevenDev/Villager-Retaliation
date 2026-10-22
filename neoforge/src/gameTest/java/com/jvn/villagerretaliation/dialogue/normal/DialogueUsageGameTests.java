package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_mechanics")
@PrefixGameTestTemplate(false)
public final class DialogueUsageGameTests {
    @GameTest(template = "empty")
    public static void usageLimitsSurviveHistoryChurnAndReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var prior = DialogueUsageSavedData.get(level);
        var ledger = new DialogueUsageSavedData();
        storage.set("villagerretaliation_dialogue_usage", ledger);
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        try {
            var context = VillagerInteractionService.createDialogueContext(level, player, villager);
            var once = new DialogueUsagePolicy(0, 1, false, DialogueUsagePolicy.Scope.WORLD);
            var twice = new DialogueUsagePolicy(0, 2, false, DialogueUsagePolicy.Scope.WORLD);
            var cooldown = new DialogueUsagePolicy(100000, 0, false, DialogueUsagePolicy.Scope.WORLD);
            DialogueUsageService.record(context, "test:once", once);
            DialogueUsageService.record(context, "test:twice", twice);
            DialogueUsageService.record(context, "test:twice", twice);
            DialogueUsageService.record(context, "test:cooldown", cooldown);
            ledger.remember("world", "paired_greeting:test", 1, false);
            for (int pass = 0; pass < 2; pass++) {
                for (int index = 0; index <= 16384; index++) ledger.remember("world", "recent:" + pass + ":" + index, index, false);
                var saved = ledger.save(new CompoundTag(), level.registryAccess());
                helper.assertValueEqual(saved.getList("Entries", Tag.TAG_COMPOUND).size(), 16388,
                        "repetition history has its own bound alongside four durable records");
                ledger = DialogueUsageSavedData.load(saved, level.registryAccess());
                storage.set("villagerretaliation_dialogue_usage", ledger);
                helper.assertFalse(DialogueUsageService.available(context, "test:once", once), "once-only usage stays exhausted");
                helper.assertFalse(DialogueUsageService.available(context, "test:twice", twice), "maximum usage stays exhausted");
                helper.assertFalse(DialogueUsageService.available(context, "test:cooldown", cooldown), "an active cooldown cannot be evicted");
                helper.assertValueEqual(ledger.usage("world", "paired_greeting:test").count(), 1, "paired greeting history remains durable");
                helper.assertValueEqual(ledger.usage("world", "recent:" + pass + ":0").count(), 0, "old repetition-only records are evicted");
            }
        } finally {
            storage.set("villagerretaliation_dialogue_usage", prior);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyAndPromotedUsageCannotLoseItsLimits(GameTestHelper helper) {
        var ledger = new DialogueUsageSavedData();
        ledger.remember("world", "test:legacy", 5);
        var saved = ledger.save(new CompoundTag(), helper.getLevel().registryAccess());
        saved.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).remove("Persistent");
        ledger = DialogueUsageSavedData.load(saved, helper.getLevel().registryAccess());
        ledger.remember("world", "test:promoted", 6, false);
        ledger.remember("world", "test:promoted", 7, true);
        ledger.remember("world", "test:promoted", 8, false);
        ledger.remember("world", "test:legacy", 9, false);
        for (int index = 0; index <= 16384; index++) ledger.remember("world", "recent:" + index, index, false);
        ledger = DialogueUsageSavedData.load(ledger.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(ledger.usage("world", "test:legacy").count(), 2, "unknown legacy policies are retained conservatively");
        helper.assertValueEqual(ledger.usage("world", "test:promoted").count(), 3, "removing a policy cannot discard its earlier durable count");
        helper.succeed();
    }
}
