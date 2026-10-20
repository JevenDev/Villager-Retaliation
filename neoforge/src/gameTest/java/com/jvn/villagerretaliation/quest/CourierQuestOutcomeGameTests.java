package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.gametest.BuiltInQuestContentGameTests;
import com.jvn.villagerretaliation.quest.inventory.QuestInventoryService;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_outcomes")
@PrefixGameTestTemplate(false)
public final class CourierQuestOutcomeGameTests {
    private static final ResourceLocation DISPATCH = VillagerRetaliation.id("urgent_dispatch");
    private static final ResourceLocation RECOVERY = VillagerRetaliation.id("second_dispatch");

    private CourierQuestOutcomeGameTests() {
    }

    @GameTest(template = "empty", batch = "courier_content")
    public static void urgentDispatchMeetsBuiltInContentContracts(GameTestHelper helper) {
        BuiltInQuestContentGameTests.generateBuiltInQuestContentTests().stream()
                .filter(test -> test.testName().equals("builtinquestcontentgametests.quest.urgent_dispatch"))
                .findFirst().orElseThrow().run(helper);
    }

    @GameTest(template = "empty", batch = "courier_content")
    public static void secondDispatchMeetsBuiltInContentContracts(GameTestHelper helper) {
        BuiltInQuestContentGameTests.generateBuiltInQuestContentTests().stream()
                .filter(test -> test.testName().equals("builtinquestcontentgametests.quest.second_dispatch"))
                .findFirst().orElseThrow().run(helper);
    }

    @GameTest(template = "empty", batch = "isolated_courier_full")
    public static void fullDeliveryConsumesSuppliesAndPaysOnce(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            helper.assertFalse(fixture.recoveryAvailable(), "recovery is hidden before any dispatch");
            fixture.start(DISPATCH);
            fixture.choose("Prepare the full dispatch.");
            helper.assertFalse(fixture.recoveryAvailable(), "an active full dispatch does not unlock recovery");
            helper.assertValueEqual(fixture.action(DISPATCH, QuestAction.TURN_IN), "missing_objectives", "empty hand-in is rejected");
            fixture.supplies(8, 12, 1);
            helper.assertValueEqual(fixture.action(DISPATCH, QuestAction.TURN_IN), "missing_objectives", "wax shortage blocks full delivery");
            fixture.supplies(8, 12, 2);
            helper.assertValueEqual(fixture.experience(), 0, "carrying supplies does not pay the completion bonus");
            helper.assertValueEqual(fixture.action(DISPATCH, QuestAction.TURN_IN), "completed", "full dispatch completes");
            helper.assertValueEqual(fixture.experience(), 120, "full dispatch receives base and full-service payment");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.EMERALD), 4, "full delivery receives its larger cash payment");
            fixture.assertSupplies(0, 0, 0);
            helper.assertFalse(fixture.recoveryAvailable(), "full service leaves no replacement work");
            helper.assertValueEqual(fixture.outcome(), "full", "full service records its original outcome");
            fixture.reload();
            fixture.action(DISPATCH, QuestAction.TURN_IN);
            fixture.tick();
            helper.assertValueEqual(fixture.experience(), 120, "completion and bonus cannot replay after reload");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.EMERALD), 4, "full-service loot cannot replay after reload");
            helper.assertValueEqual(fixture.progress(DISPATCH).completionCount(), 1, "one completed departure");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_courier_partial")
    public static void partialDeliveryUnlocksUntimedRestitution(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.start(DISPATCH);
            fixture.choose("Send food. Copy the letters later.");
            helper.assertFalse(fixture.recoveryAvailable(), "selecting partial delivery alone grants no recovery");
            fixture.supplies(8, 12, 2);
            helper.assertValueEqual(fixture.action(DISPATCH, QuestAction.TURN_IN), "completed", "food-only dispatch completes");
            helper.assertValueEqual(fixture.experience(), 60, "partial delivery receives only its smaller payment");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.EMERALD), 2, "partial delivery receives only its base cash payment");
            fixture.assertSupplies(0, 12, 2);
            helper.assertTrue(fixture.recoveryAvailable(), "partial success opens replacement work");
            helper.assertValueEqual(fixture.outcome(), "partial", "partial success records unfinished letters");
            fixture.reload();
            helper.assertTrue(fixture.recoveryAvailable(), "partial outcome survives a saved-data reload");
            fixture.start(RECOVERY);
            helper.assertFalse(fixture.definition(RECOVERY).rules().expiration().enabled(), "restitution has no deadline");
            fixture.supplies(0, 12, 1);
            helper.assertValueEqual(fixture.action(RECOVERY, QuestAction.TURN_IN), "missing_objectives", "copies still require their wax");
            fixture.supplies(0, 12, 2);
            helper.assertValueEqual(fixture.action(RECOVERY, QuestAction.TURN_IN), "completed", "replacement dispatch completes");
            helper.assertValueEqual(fixture.experience(), 100, "partial delivery plus recovery pays less than full service");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.EMERALD), 3, "restitution does not outpay the successful departure");
            fixture.assertSupplies(0, 0, 0);
            helper.assertValueEqual(fixture.outcome(), "partial", "restitution preserves the original partial outcome");
            helper.assertTrue(fixture.facts().hasTag(QuestScopeKey.player(fixture.player.getUUID()),
                    VillagerRetaliation.id("dispatch_repaired")), "restitution records a durable repaired outcome");
            fixture.reload();
            helper.assertFalse(fixture.recoveryAvailable(), "completed restitution is no longer offered");
            fixture.action(RECOVERY, QuestAction.TURN_IN);
            helper.assertValueEqual(fixture.experience(), 100, "restitution cannot pay twice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_courier_failure")
    public static void reportingFailurePreservesItemsAndOpensRecovery(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.start(DISPATCH);
            fixture.supplies(8, 12, 2);
            fixture.choose("I cannot finish in time.");
            helper.assertValueEqual(fixture.progress(DISPATCH).state(), VillagerQuestSavedData.QuestState.ACTIVE,
                    "failure requires its authored confirmation");
            fixture.reply("Let the courier leave.");
            helper.assertValueEqual(fixture.progress(DISPATCH).state(), VillagerQuestSavedData.QuestState.FAILED,
                    "reported failure stays distinct from abandonment");
            fixture.assertUnpaidTerminal();
            helper.assertValueEqual(fixture.progress(DISPATCH).abandonCount(), 0, "failure is not abandonment");
            helper.assertValueEqual(fixture.outcome(), "failed", "on_fail records the original failure");
            fixture.assertSupplies(8, 12, 2);
            fixture.reload();
            helper.assertTrue(fixture.recoveryAvailable(), "reported failure opens replacement work after reload");
            helper.assertFalse(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, DISPATCH, false).started(),
                    "a failed departure cannot be restarted");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_courier_expiration")
    public static void deadlineExpiresAtBoundaryAndRecoveryDoesNotNeedOriginalProvider(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.start(DISPATCH);
            fixture.supplies(8, 12, 2);
            long started = fixture.progress(DISPATCH).startedGameTime();
            ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(started + 47_999L);
            fixture.tick();
            helper.assertValueEqual(fixture.progress(DISPATCH).state(), VillagerQuestSavedData.QuestState.ACTIVE,
                    "dispatch remains active before the deadline");
            var originalProvider = fixture.villager.getUUID();
            fixture.villager.discard();
            ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(started + 48_000L);
            fixture.tick();
            helper.assertValueEqual(fixture.progress(DISPATCH).state(), VillagerQuestSavedData.QuestState.EXPIRED,
                    "deadline produces expiration at its exact boundary");
            fixture.assertUnpaidTerminal();
            fixture.assertSupplies(8, 12, 2);
            fixture.reload();
            fixture.replaceProvider();
            helper.assertTrue(fixture.recoveryAvailable(), "saved expiration unlocks recovery without waiting for deferred issuer hooks");
            fixture.start(RECOVERY);
            helper.assertValueEqual(fixture.action(RECOVERY, QuestAction.ABANDON), "abandoned", "recovery can be put down");
            fixture.start(RECOVERY);
            helper.assertValueEqual(fixture.action(RECOVERY, QuestAction.TURN_IN), "completed", "another provider can finish replacement work");
            helper.assertValueEqual(fixture.experience(), 40, "expiration never grants the original payment");
            fixture.villager.discard();
            fixture.replaceProvider(originalProvider);
            fixture.tick();
            helper.assertValueEqual(fixture.outcome(), "expired", "deferred expiration records its original outcome");
            helper.assertTrue(fixture.facts().hasTag(QuestScopeKey.player(fixture.player.getUUID()),
                    VillagerRetaliation.id("dispatch_repaired")), "late expiry hooks cannot erase completed restitution");
            helper.assertFalse(fixture.recoveryAvailable(), "late expiry hooks cannot reopen completed restitution");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_courier_abandonment")
    public static void abandoningDepartureKeepsRecoveryAndDoesNotResetDeadline(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.start(DISPATCH);
            fixture.action(DISPATCH, QuestAction.ABANDON);
            helper.assertValueEqual(fixture.progress(DISPATCH).state(), VillagerQuestSavedData.QuestState.CONSUMED,
                    "voluntary abandonment permanently closes this departure");
            fixture.assertUnpaidTerminal();
            helper.assertValueEqual(fixture.progress(DISPATCH).abandonCount(), 1, "abandonment is recorded separately from failure");
            helper.assertValueEqual(fixture.outcome(), "abandoned", "on_abandon records voluntary abandonment");
            helper.assertTrue(fixture.recoveryAvailable(), "abandonment leaves an untimed way forward");
            helper.assertFalse(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, DISPATCH, false).started(),
                    "abandonment cannot reset the departure clock");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private Villager villager;
        private DialogueContext context;
        private final VillagerQuestSavedData originalQuests;
        private final VillagerQuestFacts originalFacts;
        private final long originalTime;
        private final int initialExperience;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            originalQuests = VillagerQuestSavedData.get(helper.getLevel());
            originalFacts = VillagerQuestFacts.get(helper.getLevel());
            originalTime = helper.getLevel().getGameTime();
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_quests", new VillagerQuestSavedData());
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_quest_facts", new VillagerQuestFacts());
            player = helper.makeMockServerPlayerInLevel();
            initialExperience = player.totalExperience;
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            replaceProvider();
            var prerequisite = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(player.getUUID(),
                    VillagerRetaliation.id("wax_sealed_route"));
            prerequisite.start(villager.getUUID(), helper.getLevel().dimension(), null, originalTime);
            prerequisite.complete(originalTime, true);
        }

        private void replaceProvider() {
            replaceProvider(null);
        }

        private void replaceProvider(java.util.UUID id) {
            villager = EntityType.VILLAGER.create(helper.getLevel());
            if (id != null) villager.setUUID(id);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
            villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER).setLevel(2));
            helper.assertTrue(helper.getLevel().addFreshEntity(villager), "courier provider spawns");
            player.moveTo(villager.getX(), villager.getY(), villager.getZ() + 1, 0, 0);
            context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        }

        private QuestDefinition definition(ResourceLocation id) {
            return VillagerQuestResources.quest(helper.getLevel().getServer(), id).orElseThrow();
        }

        private VillagerQuestSavedData.QuestProgress progress(ResourceLocation id) {
            return VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), id);
        }

        private VillagerQuestFacts facts() {
            return VillagerQuestFacts.get(helper.getLevel());
        }

        private String outcome() {
            return facts().variable(QuestScopeKey.quest(player.getUUID(), DISPATCH).asString(), "outcome").orElse("");
        }

        private boolean recoveryAvailable() {
            if (progress(RECOVERY) != null && progress(RECOVERY).completionCount() > 0) return false;
            return definition(RECOVERY).offer().conditions().stream().allMatch(condition -> condition.matches(context));
        }

        private void start(ResourceLocation id) {
            helper.assertValueEqual(action(id, QuestAction.START), "started", "authored quest can start");
        }

        private String action(ResourceLocation id, QuestAction action) {
            return VillagerQuestService.performAction(context, id, action).orElseThrow().status();
        }

        private void choose(String label) {
            var binding = VillagerQuestResources.questDialogueCatalog(helper.getLevel().getServer()).bindings(DISPATCH)
                    .stream().filter(value -> value.stageId().equals("prepare") && value.slot().equals("reminder")).findFirst().orElseThrow();
            helper.assertTrue(DialogueTreeService.startEntry(context, binding.treeId(), binding.entryId()).isPresent(),
                    "authored reminder opens");
            reply(label);
        }

        private void reply(String label) {
            var option = DialogueTreeService.activeOptions(context).orElseThrow().stream()
                    .filter(value -> value.label().equals(label)).findFirst().orElseThrow();
            helper.assertTrue(DialogueTreeService.handleDialogueOption(context, option).isPresent(), "authored reply executes");
        }

        private void supplies(int bread, int paper, int wax) {
            player.getInventory().setItem(9, new ItemStack(Items.BREAD, bread));
            player.getInventory().setItem(10, new ItemStack(Items.PAPER, paper));
            player.getInventory().setItem(11, new ItemStack(Items.HONEYCOMB, wax));
            QuestInventoryService.clearRuntimeState(player);
        }

        private void assertSupplies(int bread, int paper, int wax) {
            helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), bread, "bread consumption");
            helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), paper, "paper consumption");
            helper.assertValueEqual(player.getInventory().countItem(Items.HONEYCOMB), wax, "wax consumption");
        }

        private int experience() {
            return player.totalExperience - initialExperience;
        }

        private void tick() {
            player.tickCount = 20;
            VillagerQuestService.onPlayerTick(player);
        }

        private void assertUnpaidTerminal() {
            helper.assertValueEqual(experience(), 0, "unsuccessful departure grants no payment");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "unsuccessful departure grants no loot");
            helper.assertValueEqual(progress(DISPATCH).completionCount(), 0, "unsuccessful departure grants no completion");
        }

        private void reload() {
            var storage = helper.getLevel().getServer().overworld().getDataStorage();
            var registries = helper.getLevel().registryAccess();
            storage.set("villagerretaliation_quests", VillagerQuestSavedData.load(
                    VillagerQuestSavedData.get(helper.getLevel()).save(new CompoundTag(), registries), registries));
            storage.set("villagerretaliation_quest_facts", VillagerQuestFacts.load(
                    facts().save(new CompoundTag(), registries), registries));
        }

        @Override
        public void close() {
            DialogueTreeService.leaveActiveSession(context);
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            villager.discard();
            ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(originalTime);
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_quests", originalQuests);
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_quest_facts", originalFacts);
        }
    }
}
