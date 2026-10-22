package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction;
import com.jvn.villagerretaliation.dialogue.Knowledge;
import com.jvn.villagerretaliation.dialogue.KnowledgeSavedData;
import com.jvn.villagerretaliation.dialogue.QuestAftermathCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_content")
@PrefixGameTestTemplate(false)
public final class NarrativeContentGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("villagerretaliation:last_light");
    private static final ResourceLocation SIGNAL = ResourceLocation.parse("villagerretaliation:watch_signal");

    @GameTest(template = "empty")
    public static void lastLightMeetsBuiltInContentContracts(GameTestHelper helper) {
        com.jvn.villagerretaliation.gametest.BuiltInQuestContentGameTests.generateBuiltInQuestContentTests().stream()
                .filter(test -> test.testName().equals("builtinquestcontentgametests.quest.last_light"))
                .findFirst().orElseThrow().run(helper);
    }

    @GameTest(template = "empty")
    public static void punctualDeliveryConsumesSuppliesAndPaysOnce(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            helper.assertValueEqual(fixture.action(QuestAction.START), "started", "timed delivery starts");
            helper.assertTrue(new Knowledge.Condition(SIGNAL, "speaker", "witnessed", "").matches(fixture.context()), "quest giver knows the signals");
            fixture.player.getInventory().setItem(9, new ItemStack(Items.TORCH, 8));
            helper.assertValueEqual(fixture.action(QuestAction.TURN_IN), "completed", "on-time hand-in succeeds");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.TORCH), 0, "torches are consumed");
            helper.assertValueEqual(fixture.player.totalExperience - fixture.experience, 50, "punctual bonus is paid");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.EMERALD), 2, "base reward is paid");
            fixture.action(QuestAction.TURN_IN);
            helper.assertValueEqual(fixture.player.totalExperience - fixture.experience, 50, "repeated hand-in cannot pay twice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deadlineOpensRecoveryAndSurvivesReload(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            helper.assertValueEqual(fixture.action(QuestAction.START), "started", "timed delivery starts");
            fixture.player.getInventory().setItem(9, new ItemStack(Items.TORCH, 8));
            fixture.time(fixture.progress().startedGameTime() + 12000);
            fixture.player.tickCount = 20;
            VillagerQuestService.onPlayerTick(fixture.player);
            helper.assertValueEqual(fixture.progress().currentStage(), "repair", "deadline selects the recovery route");
            helper.assertValueEqual(fixture.progress().state(), VillagerQuestSavedData.QuestState.ACTIVE, "missing the deadline keeps the story active");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.TORCH), 8, "timeout preserves carried supplies");
            fixture.reload();
            helper.assertValueEqual(fixture.progress().currentStage(), "repair", "recovery route survives reload");
            helper.assertValueEqual(fixture.action(QuestAction.TURN_IN), "missing_objectives", "old delivery supplies do not complete recovery");
            fixture.player.getInventory().setItem(9, new ItemStack(Items.TORCH, 16));
            fixture.player.getInventory().setItem(10, new ItemStack(Items.PAPER, 4));
            helper.assertValueEqual(fixture.action(QuestAction.TURN_IN), "completed", "replacement watch can be supplied");
            helper.assertValueEqual(fixture.player.totalExperience - fixture.experience, 25, "recovery does not pay the punctual bonus");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.TORCH), 0, "replacement torches are consumed");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.PAPER), 0, "route paper is consumed");
            helper.assertValueEqual(VillagerQuestFacts.get(helper.getLevel()).variable(
                    QuestScopeKey.quest(fixture.player.getUUID(), QUEST), "outcome").orElseThrow(), "late", "late outcome is remembered");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aftermathMatchesTheOriginalGiverAcrossRepeatRunsAndReload(GameTestHelper helper) {
        var data = new VillagerQuestSavedData();
        UUID player = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        var progress = data.getOrCreate(player, QUEST);
        progress.start(first, Level.OVERWORLD, null, 0);
        progress.complete(100, true);
        var aftermath = new QuestAftermathCondition(QUEST, 1, null);
        helper.assertFalse(aftermath.remembers(progress, first, 24099), "delayed aftermath waits a full day");
        helper.assertTrue(aftermath.remembers(progress, first, 24100), "aftermath becomes available at its boundary");
        helper.assertFalse(aftermath.remembers(progress, second, 24100), "strangers cannot claim another giver's quest");
        progress.start(second, Level.OVERWORLD, null, 25000);
        data = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
        helper.assertTrue(aftermath.remembers(data.get(player, QUEST), first, 26000), "repeat runs and reload retain earlier giver history");
        helper.assertFalse(aftermath.remembers(data.get(UUID.randomUUID(), QUEST), first, 26000), "another player has no aftermath");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void watchSignalsRequireTrustBeforeFullDisclosure(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var tree = ResourceLocation.parse("villagerretaliation:watch_signals");
            helper.assertTrue(DialogueTreeService.startEntry(fixture.context(), tree, "ask").isEmpty(), "uninformed villagers cannot reveal the code");
            fixture.action(QuestAction.START);
            helper.assertTrue(DialogueTreeService.startEntry(fixture.context(), tree, "ask").isPresent(), "informed villager offers a hint");
            helper.assertTrue(new Knowledge.Condition(SIGNAL, "player", "partial", "speaker").matches(fixture.context()), "untrusted player learns a partial account");
            helper.assertFalse(DialogueTreeService.activeOptions(fixture.context()).orElseThrow().stream()
                    .anyMatch(option -> option.label().equals("You can trust me with it.")), "full disclosure is hidden without trust");
            DialogueTreeService.leaveActiveSession(fixture.context());
            com.jvn.villagerretaliation.reputation.VillagerReputationManager.setReputationForDebug(helper.getLevel(), fixture.villager, fixture.player.getUUID(), 25);
            DialogueTreeService.startEntry(fixture.context(), tree, "ask").orElseThrow();
            var response = DialogueTreeService.activeOptions(fixture.context()).orElseThrow().stream()
                    .filter(option -> option.label().equals("You can trust me with it.")).findFirst().orElseThrow();
            helper.assertTrue(DialogueTreeService.handleDialogueOption(fixture.context(), response).isPresent(), "trusted response executes");
            helper.assertTrue(new Knowledge.Condition(SIGNAL, "player", "complete", "speaker").matches(fixture.context()), "trusted player learns the complete code");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final Villager villager;
        private final VillagerQuestSavedData quests;
        private final VillagerQuestFacts facts;
        private final KnowledgeSavedData knowledge;
        private final long originalTime;
        private final int experience;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            quests = VillagerQuestSavedData.get(helper.getLevel());
            facts = VillagerQuestFacts.get(helper.getLevel());
            knowledge = KnowledgeSavedData.get(helper.getLevel());
            originalTime = helper.getLevel().getGameTime();
            var storage = helper.getLevel().getServer().overworld().getDataStorage();
            storage.set("villagerretaliation_quests", new VillagerQuestSavedData());
            storage.set("villagerretaliation_quest_facts", new VillagerQuestFacts());
            storage.set("villagerretaliation_knowledge", new KnowledgeSavedData());
            player = helper.makeMockServerPlayerInLevel();
            experience = player.totalExperience;
            villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER));
            player.setPos(villager.position());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        }

        private com.jvn.villagerretaliation.dialogue.DialogueContext context() {
            return VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        }
        private String action(QuestAction action) { return VillagerQuestService.performAction(context(), QUEST, action).orElseThrow().status(); }
        private VillagerQuestSavedData.QuestProgress progress() { return VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), QUEST); }
        private void time(long time) { ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(time); }
        private void reload() {
            var storage = helper.getLevel().getServer().overworld().getDataStorage();
            var registries = helper.getLevel().registryAccess();
            storage.set("villagerretaliation_quests", VillagerQuestSavedData.load(VillagerQuestSavedData.get(helper.getLevel()).save(new CompoundTag(), registries), registries));
            storage.set("villagerretaliation_quest_facts", VillagerQuestFacts.load(VillagerQuestFacts.get(helper.getLevel()).save(new CompoundTag(), registries), registries));
        }
        @Override public void close() {
            DialogueTreeService.leaveActiveSession(context());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            time(originalTime);
            var storage = helper.getLevel().getServer().overworld().getDataStorage();
            storage.set("villagerretaliation_quests", quests);
            storage.set("villagerretaliation_quest_facts", facts);
            storage.set("villagerretaliation_knowledge", knowledge);
        }
    }
}
