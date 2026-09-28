package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.CandidateArbitrator;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextVariant;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsagePolicy;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsageSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;
import com.jvn.villagerretaliation.util.ContentTagDomain;
import com.jvn.villagerretaliation.util.ContentTagQuery;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class DialogueRuntimeGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private DialogueRuntimeGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "dialogue_runtime")
    public static void arbitrationUsesPriorityChanceWeightAndFallback(GameTestHelper helper) {
        List<CandidateArbitrator.Candidate<String>> candidates = List.of(
                CandidateArbitrator.Candidate.eligible("miss", "miss", 10, 0.0D, 100),
                CandidateArbitrator.Candidate.eligible("disabled", "disabled", 5, 1.0D, 0),
                CandidateArbitrator.Candidate.eligible("fallback", "fallback", 0, 1.0D, 1));
        String selected = CandidateArbitrator.select(candidates, RandomSource.create(7L), ignored -> true).orElse("");
        helper.assertValueEqual(selected, "fallback", "high-priority chance miss and zero weight must fall through");
        helper.assertValueEqual(
                CandidateArbitrator.selectOrFallback(
                        List.<CandidateArbitrator.Candidate<String>>of(), RandomSource.create(7L),
                        ignored -> true, () -> "explicit"),
                "explicit", "explicit fallback");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "dialogue_runtime")
    public static void richVariantsPreserveLegacyAndStableIds(GameTestHelper helper) {
        List<DialogueTextVariant> legacy = DialogueTextVariant.read(
                VillagerRetaliation.id("variant_test"), "test", "legacy", "owner",
                JsonParser.parseString("[\"hello\"]"), null,
                DialogueEntryMetadata.EMPTY, DialogueUsagePolicy.DEFAULT);
        List<DialogueTextVariant> rich = DialogueTextVariant.read(
                VillagerRetaliation.id("variant_test"), "test", "rich", "owner",
                JsonParser.parseString("[{\"id\":\"stable\",\"text\":\"hello\",\"weight\":4,\"chance\":0.5,\"once\":true}]"),
                null, DialogueEntryMetadata.EMPTY, DialogueUsagePolicy.DEFAULT);
        helper.assertValueEqual(legacy.getFirst().id(), "owner", "single legacy string id");
        helper.assertValueEqual(rich.getFirst().id(), "owner#stable", "explicit stable variant id");
        helper.assertValueEqual(rich.getFirst().weight(), 4, "variant weight");
        helper.assertValueEqual(rich.getFirst().usage().maxUses(), 1, "variant once policy");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "dialogue_runtime")
    public static void triggerPayloadSnapshotIsDurableAndQueryable(GameTestHelper helper) {
        QuestTriggerContext snapshot = new QuestTriggerContext(
                QuestDefinition.TriggerEvent.GIFT, 42L, "before_reward",
                Map.of("item", "minecraft:emerald", "item_count", "3", "reputation", "12"), null);
        QuestTriggerContext loaded = QuestTriggerContext.load(snapshot.save());
        DialogueCondition condition = new DialogueCondition.TriggerPayload(
                Set.of("gift"), Map.of(), Map.of("item", Set.of("minecraft:emerald")),
                Map.of("gift_reaction", Set.of("disliked")), 10, 20);
        helper.assertTrue(loaded != null && DialogueCondition.matchesAll(null, loaded, List.of(condition)),
                "saved trigger payload should retain event, stage and scalar values");
        helper.assertValueEqual(loaded.stage(), "before_reward", "dispatch-stage snapshot");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "dialogue_runtime")
    public static void typedTagQueriesKeepDomainsSeparate(GameTestHelper helper) {
        ContentTagQuery classification = new ContentTagQuery(
                ContentTagDomain.CLASSIFICATION, Set.of("quest"), Set.of("tone.calm"), Set.of("blocked"));
        ContentTagQuery routing = new ContentTagQuery(
                ContentTagDomain.ROUTING, Set.of("quest"), Set.of(), Set.of());
        helper.assertTrue(classification.matches(Set.of("quest", "tone.calm")), "classification any/all query");
        helper.assertFalse(classification.matches(Set.of("quest", "tone.calm", "blocked")), "classification not query");
        helper.assertValueEqual(routing.domain(), ContentTagDomain.ROUTING, "routing domain remains explicit");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "dialogue_runtime")
    public static void authoredVariantsUsePersonalContextAndIgnoreIneligibleFreshText(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "DialogueReader"));
        var first = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        var second = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        first.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)).getCenter());
        second.setPos(first.position());
        var event = VillagerRetaliation.id("personal_context_test");
        com.jvn.villagerretaliation.village.VillageEventMemory.remember(level, event, first.blockPosition(), first, player,
                com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.VILLAGER);
        var firstContext = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(level, player, first);
        var secondContext = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(level, player, second);
        var authored = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.lines(firstContext).stream()
                .flatMap(line -> line.textVariants().stream()).filter(variant -> !variant.text().isBlank()).findFirst().orElseThrow();
        var personal = new DialogueCondition.Memory(Set.of(event), DialogueCondition.MemorySource.THIS_VILLAGER, true,
                DialogueCondition.MemoryKind.EVENT_TAG);
        var remembered = new DialogueTextVariant("personal_line", authored.text(), authored.textKey(), 1, 1, 1.0D,
                List.of(personal), authored.metadata(), DialogueUsagePolicy.DEFAULT);
        var child = new DialogueTextVariant("child_line", authored.text(), authored.textKey(), 0, 1, 1.0D,
                List.of(new DialogueCondition.VillagerAge(true, false)), authored.metadata(), DialogueUsagePolicy.DEFAULT);
        helper.assertTrue(DialogueTextVariant.select(List.of(remembered), firstContext, List.of()).isPresent(),
                "an individual event makes the authored response available to its owner");
        helper.assertTrue(DialogueTextVariant.select(List.of(remembered), secondContext, List.of()).isEmpty(),
                "a neighboring resident cannot speak as the event owner");
        helper.assertFalse(DialogueTextVariant.hasFresh(List.of(remembered, child), firstContext, List.of("personal_line")),
                "an ineligible child line cannot defeat adult repeat avoidance");
        helper.assertValueEqual(remembered.text(), authored.text(), "selection retains the datapack author's exact text");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "dialogue_runtime")
    public static void scopedUsageLedgerPersistsCountsAndCooldownTime(GameTestHelper helper) {
        DialogueUsageSavedData data = new DialogueUsageSavedData();
        data.remember("world", "line", 40L);
        data.remember("world", "line", 55L);
        DialogueUsageSavedData loaded = DialogueUsageSavedData.load(
                data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.usage("world", "line").count(), 2, "durable scoped count");
        helper.assertValueEqual(loaded.usage("world", "line").lastUsedGameTime(), 55L, "durable scoped time");
        helper.succeed();
    }
}
