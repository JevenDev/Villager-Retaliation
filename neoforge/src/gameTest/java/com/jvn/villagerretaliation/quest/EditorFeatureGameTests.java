package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.DialogueItemTransfer;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringValidation;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.item.QuestItems;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressModel;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("editor_features")
@PrefixGameTestTemplate(false)
public final class EditorFeatureGameTests {
    private EditorFeatureGameTests() { }

    @GameTest(template = "empty", batch = "editor_features")
    public static void distinctVillagersSurviveReloadAndDeniedDispatch(GameTestHelper helper) {
        var id = ResourceLocation.parse("test:distinct");
        var definition = QuestDefinitionFixtures.read(id, json("""
                {"objectives":[{"id":"trades","type":"trade","count":3,"distinct_villagers":true}],
                 "stages":{"active":{"objectives":["trades"]}}}
                """), id);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, 0);
        progress.setCurrentStage("active");
        var first = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var second = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 1));
        var third = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 1));
        var context = evaluation(helper, definition, progress);
        helper.assertFalse(QuestObjectiveDispatchService.dispatch(context, QuestObjectiveEvent.trade(first, offer()), false, objective -> {}),
                "denied multiplayer execution cannot reserve a villager or increment a counter");
        helper.assertTrue(QuestObjectiveDispatchService.dispatch(context, QuestObjectiveEvent.trade(first, offer()), objective -> {}), "first villager counts");
        helper.assertFalse(QuestObjectiveDispatchService.dispatch(context, QuestObjectiveEvent.trade(first, offer()), objective -> {}), "repeat villager does not count");
        var saved = progress.saveObjectiveTracking();
        var loaded = new VillagerQuestSavedData.QuestProgress();
        loaded.start(UUID.randomUUID(), helper.getLevel().dimension(), null, 0);
        loaded.setCurrentStage("active");
        loaded.loadObjectiveTracking(saved);
        loaded.addObjectiveCounter("trades", progress.objectiveCounter("trades"));
        var reloaded = evaluation(helper, definition, loaded);
        helper.assertFalse(QuestObjectiveDispatchService.dispatch(reloaded, QuestObjectiveEvent.trade(first, offer()), objective -> {}), "saved UUIDs prevent recounting");
        QuestObjectiveDispatchService.dispatch(reloaded, QuestObjectiveEvent.trade(second, offer()), objective -> {});
        QuestObjectiveDispatchService.dispatch(reloaded, QuestObjectiveEvent.trade(third, offer()), objective -> {});
        helper.assertValueEqual(loaded.objectiveCounter("trades"), 3, "three different UUIDs count");
        helper.assertTrue(loaded.objectiveComplete("trades"), "distinct objective completes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void maintainedConditionsResetOnFailureGapsAndReload(GameTestHelper helper) {
        var progress = new QuestProgressModel();
        progress.sampleCondition("safe", true, 10, 5);
        progress.sampleCondition("safe", true, 11, 5);
        progress.sampleCondition("safe", true, 12, 5);
        helper.assertValueEqual(progress.maintainedTicks("safe"), 2L, "only observed consecutive ticks count");
        progress.sampleCondition("safe", false, 13, 5);
        helper.assertValueEqual(progress.maintainedTicks("safe"), 0L, "a failed condition resets time");
        progress.sampleCondition("safe", true, 14, 5);
        progress.sampleCondition("safe", true, 30, 5);
        helper.assertValueEqual(progress.maintainedTicks("safe"), 0L, "unobserved gaps do not count");
        progress.sampleCondition("safe", true, 31, 5);
        var loaded = new QuestProgressModel();
        loaded.loadObjectiveTracking(progress.saveObjectiveTracking());
        loaded.sampleCondition("safe", true, 32, 5);
        helper.assertValueEqual(loaded.maintainedTicks("safe"), 0L, "reload interrupts continuous maintenance");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void taskGroupsSupportAllAndThresholdProgress(GameTestHelper helper) {
        var id = ResourceLocation.parse("test:group");
        var definition = QuestDefinitionFixtures.read(id, json("""
                {"objectives":[{"id":"one","type":"fact","key":"one","value":"yes","optional":true},
                 {"id":"two","type":"fact","key":"two","value":"yes","optional":true},
                 {"id":"some","type":"condition","requirements":["one","two"],"required_count":1},
                 {"id":"all","type":"condition","requirements":["one","two"]}]}
                """), id);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.markObjectiveComplete("one");
        var context = evaluation(helper, definition, progress);
        helper.assertTrue(QuestObjectiveRegistry.evaluate(context, definition.objectives().get(2)).orElseThrow().complete(), "one of two completes threshold");
        var all = QuestObjectiveRegistry.evaluate(context, definition.objectives().get(3)).orElseThrow();
        helper.assertFalse(all.complete(), "all still needs both tasks");
        helper.assertValueEqual(all.progress(), 0.5F, "tracker shows half the requirements");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void maintainedGroupsRecheckConditionsAfterIndividualCompletion(GameTestHelper helper) {
        var id = ResourceLocation.parse("test:maintained_group");
        var definition = QuestDefinitionFixtures.read(id, json("""
                {"objectives":[{"id":"safe","type":"fact","key":"safe","value":"yes","optional":true},
                 {"id":"restore","type":"condition","requirements":["safe"],"maintain_ticks":20}]}
                """), id);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.markObjectiveComplete("safe");
        var context = evaluation(helper, definition, progress);
        helper.assertFalse(QuestObjectiveRegistry.maintainedConditionMet(context, definition.objectives().get(1)),
                "a condition that stopped holding cannot be sustained using its earlier completion flag");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void itemIdentityAndUnexposedComponentsSurviveRoundTrip(GameTestHelper helper) {
        var id = ResourceLocation.parse("test:relic");
        var definition = json("""
                {"stack":{"id":"minecraft:iron_sword","count":1,"components":{"minecraft:custom_data":{"story":7},"minecraft:unbreakable":{}}},
                 "name":"<gold><b>Village relic</b></gold>","lore":["<i>Keep this safe.</i>"],"damage":12}
                """);
        var stack = QuestItems.create(helper.getLevel().registryAccess(), id, definition);
        helper.assertTrue(stack.has(DataComponents.UNBREAKABLE), "basic form preserves advanced components");
        helper.assertValueEqual(stack.getDamageValue(), 12, "authored damage applies");
        helper.assertValueEqual(stack.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("story"), 7, "existing custom data remains");
        var renamed = new ItemStack(Items.IRON_SWORD);
        renamed.set(DataComponents.CUSTOM_NAME, stack.getHoverName());
        helper.assertFalse(QuestItems.matches(renamed, id), "name alone never grants quest identity");
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed relic"));
        var loaded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(QuestItems.matches(loaded, id), "identity survives rename and serialization");
        var expected = QuestItems.create(helper.getLevel().registryAccess(), id, definition);
        helper.assertFalse(DialogueItemTransfer.matches(renamed, expected, json("{\"matching\":\"item\"}")), "dialogue identity matching rejects an ordinary renamed item");
        helper.assertTrue(DialogueItemTransfer.matches(loaded, expected, json("{\"matching\":\"item\"}")), "identity matching permits a renamed genuine item");
        helper.assertFalse(DialogueItemTransfer.matches(loaded, expected, json("{\"matching\":\"components\"}")), "component matching compares the full component set");
        try (var ignored = QuestItems.preview(java.util.Map.of(id, definition))) {
            var objective = VillagerQuestResources.readObjective(id, json("{\"id\":\"relic\",\"type\":\"item_check\",\"quest_item\":\"test:relic\"}"),
                    0, id, helper.getLevel().registryAccess()).orElseThrow();
            helper.assertTrue(com.jvn.villagerretaliation.quest.inventory.QuestInventoryService.matchesObjectiveItemStack(objective, loaded, objective.item()),
                    "quest objectives use the same stable identity");
            helper.assertFalse(com.jvn.villagerretaliation.quest.inventory.QuestInventoryService.matchesObjectiveItemStack(objective, renamed, objective.item()),
                    "quest objectives reject names without identity");
        }
        boolean rejected = false;
        try { QuestItems.text("<wave>Animated</wave>"); } catch (IllegalArgumentException invalidStyle) { rejected = true; }
        helper.assertTrue(rejected, "ordinary tooltips reject animated styling");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void inventoryLimitsAndRemovalExclusionsAreRespected(GameTestHelper helper) {
        var inventory = new ArrayList<ItemStack>();
        inventory.add(new ItemStack(Items.IRON_SWORD));
        helper.assertFalse(DialogueItemTransfer.insert(inventory, new ItemStack(Items.IRON_SWORD), 64), "unstackable equipment needs another slot");
        inventory.set(0, new ItemStack(Items.BREAD, 63));
        helper.assertFalse(DialogueItemTransfer.insert(inventory, new ItemStack(Items.BREAD, 2), 64), "full destination cannot hold all items");
        var exclusions = json("{\"excluded_slots\":[2],\"excluded_items\":[\"minecraft:emerald\"]}");
        helper.assertFalse(DialogueItemTransfer.eligible(new ItemStack(Items.IRON_SWORD), 0, exclusions), "equipment is protected by default");
        helper.assertFalse(DialogueItemTransfer.eligible(new ItemStack(Items.EMERALD), 0, exclusions), "configured valuable item is protected");
        helper.assertFalse(DialogueItemTransfer.eligible(new ItemStack(Items.BREAD), 2, exclusions), "excluded slots are protected");
        var named = new ItemStack(Items.BREAD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Valuable"));
        helper.assertFalse(DialogueItemTransfer.eligible(named, 0, exclusions), "named items are protected by default");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void dialogueGiveIsIdempotentAcrossActionReloadAndPlayers(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var action = action("""
                {"type":"item_transfer","operation_id":"give-%s","direction":"give_player","selection":"specific","item":"minecraft:bread","quantity":3}
                """.formatted(UUID.randomUUID()));
        var context = DialogueContext.forPlayer(player);
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "ordinary dialogue gives items without a quest");
        var savedPlayer = player.saveWithoutId(new CompoundTag());
        player.getInventory().clearContent();
        player.load(savedPlayer);
        var restored = SceneContinuation.loadAction(SceneContinuation.saveAction(action));
        helper.assertTrue(VillagerActionRegistry.execute(context, restored, java.util.Map.of()).success(), "repeat execution is safely satisfied");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 3, "reload and double click do not duplicate");
        var peer = helper.makeMockServerPlayerInLevel();
        peer.setUUID(UUID.randomUUID());
        helper.assertTrue(VillagerActionRegistry.execute(DialogueContext.forPlayer(peer), restored, java.util.Map.of()).success(), "another player owns an independent receipt");
        helper.assertValueEqual(peer.getInventory().countItem(Items.BREAD), 3, "peer receives their own items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void randomSelectionIsFrozenWhileDestinationIsFull(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        for (int slot = 0; slot < 36; slot++) player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        String operationId = "pool-" + UUID.randomUUID();
        var action = action("""
                {"type":"item_transfer","operation_id":"%s","direction":"give_player","selection":"pool",
                 "pool":[{"item":"minecraft:bread"},{"item":"minecraft:emerald"}],"quantity":1,"on_full":"fail"}
                """.formatted(operationId));
        var context = DialogueContext.forPlayer(player);
        helper.assertFalse(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "full inventory fails without dropping items");
        var operation = UUID.nameUUIDFromBytes(("dialogue-transfer/" + player.getUUID() + "/" + operationId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var path = helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data/villagerretaliation/dialogue_transfers/" + operation + ".dat");
        var plan = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var selected = ItemStack.parse(player.registryAccess(), plan.getCompound("Selected")).orElseThrow();
        player.getInventory().items.set(0, ItemStack.EMPTY);
        helper.assertTrue(VillagerActionRegistry.execute(context, SceneContinuation.loadAction(SceneContinuation.saveAction(action)), java.util.Map.of()).success(), "saved selection retries after capacity is available");
        helper.assertValueEqual(player.getInventory().countItem(selected.getItem()), 1, "retry delivers the original roll");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void missingPaymentRetriesAndProtectedSlotsStayUntouched(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var action = action("""
                {"type":"item_transfer","operation_id":"take-%s","direction":"take_player","selection":"pool",
                 "pool":[{"item":"minecraft:bread"}],"quantity":2,"destination":"consume","excluded_slots":[0]}
                """.formatted(UUID.randomUUID()));
        var context = DialogueContext.forPlayer(player);
        player.getInventory().items.set(0, new ItemStack(Items.BREAD, 4));
        helper.assertFalse(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "protected bread is not eligible");
        player.getInventory().items.set(1, new ItemStack(Items.BREAD, 2));
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "same selected payment retries when supplied");
        helper.assertValueEqual(player.getInventory().items.get(0).getCount(), 4, "protected identical stack stays intact");
        helper.assertTrue(player.getInventory().items.get(1).isEmpty(), "only the selected eligible slot is removed");
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "repeating payment is satisfied");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 4, "payment does not repeat");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void advancementCriteriaAreValidatedAndRepeatedGrantsAreSafe(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var id = ResourceLocation.parse("minecraft:story/mine_stone");
        var holder = helper.getLevel().getServer().getAdvancements().get(id);
        String criterion = holder.value().criteria().keySet().iterator().next();
        var action = action("{\"type\":\"advancement\",\"advancement\":\"minecraft:story/mine_stone\",\"criterion\":\"" + criterion + "\"}");
        var context = DialogueContext.forPlayer(player);
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "criterion can be granted");
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "repeated criterion is safely satisfied");
        helper.assertFalse(VillagerActionRegistry.execute(context, action("{\"type\":\"advancement\",\"advancement\":\"minecraft:story/mine_stone\",\"criterion\":\"missing\"}"), java.util.Map.of()).success(), "unknown criterion cannot grant");
        helper.assertTrue(player.getAdvancements().getOrStartProgress(holder).getCriterion(criterion).isDone(), "vanilla progress owns criterion completion");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void authoredResourcesSurviveExportAndReimport(GameTestHelper helper) throws Exception {
        var directory = Files.createTempDirectory("vr-editor-features-");
        try {
            var workspace = AuthoringWorkspace.create(directory.resolve("workspace"));
            var item = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quest_items/relic.json",
                    json("{\"stack\":{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_data\":{\"chapter\":2}}},\"name\":\"<b>Relic</b>\",\"lore\":[\"An old story.\"]}"));
            var advancement = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/advancement/restored.json", json("""
                    {"display":{"title":"Village restored","description":"Help the village.","icon":{"id":"minecraft:emerald"},"frame":"goal","show_toast":true,"announce_to_chat":false,"hidden":true},
                     "criteria":{"granted":{"trigger":"minecraft:impossible"}},"requirements":[["granted"]]}
                    """));
            workspace.add(item);
            workspace.add(advancement);
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:restoration");
            quest.edit("Configure custom objectives", root -> {
                var stage = root.getAsJsonArray("stages").get(0).getAsJsonObject();
                stage.add("objectives", JsonParser.parseString("""
                        [{"id":"trade","type":"trade","count":3,"distinct_villagers":true,"optional":true},
                         {"id":"relic","type":"item_check","quest_item":"test:relic","count":1,"consume":false,"optional":true},
                         {"id":"restore","type":"condition","requirements":["trade","relic"],"required_count":1,"maintain_ticks":20}]
                        """));
                root.add("lifecycle", json("""
                        {"on_complete":{"actions":[{"type":"advancement","advancement":"test:restored","criterion":"granted"},
                         {"type":"item_transfer","operation_id":"quest-relic","direction":"give_player","selection":"specific","quest_item":"test:relic","quantity":1}]}}
                        """));
            });
            var tree = workspace.createDocument(DefinitionKind.TREE, "test:transfer");
            tree.edit("Configure ordinary dialogue transfer", root -> {
                var node = root.getAsJsonObject("nodes").entrySet().iterator().next().getValue().getAsJsonObject();
                node.add("actions", JsonParser.parseString("""
                        [{"type":"item_transfer","operation_id":"dialogue-relic","direction":"give_player","selection":"pool",
                          "pool":[{"quest_item":"test:relic"},{"item":"minecraft:bread"}],"quantity":1,"on_full":"fail"}]
                        """));
            });
            var issues = AuthoringValidation.validate(workspace.documents(), helper.getLevel().registryAccess());
            helper.assertFalse(issues.stream().anyMatch(value -> value.severity() == AuthoringDiagnostic.Severity.ERROR), "native resources validate: " + issues);
            workspace.exportPack(directory.resolve("pack"));
            var imported = AuthoringWorkspace.create(directory.resolve("imported"));
            imported.importPack(directory.resolve("pack"));
            for (var expected : workspace.documents()) {
                var actual = imported.documents().stream().filter(value -> value.resourcePath().equals(expected.resourcePath())).findFirst().orElseThrow();
                helper.assertValueEqual(actual.json(), expected.json(), "export preserves all components and display settings");
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void villagerTransfersRespectCapacityAndEntityReload(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 5));
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var inventory = new ArrayList<>(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(villager));
        for (int slot = 0; slot < inventory.size(); slot++) inventory.set(slot, new ItemStack(Items.STONE, 64));
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(villager, inventory);
        var action = action("""
                {"type":"item_transfer","operation_id":"villager-%s","direction":"take_player","selection":"specific",
                 "item":"minecraft:emerald","quantity":2,"destination":"villager","on_full":"fail"}
                """.formatted(UUID.randomUUID()));
        var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        helper.assertFalse(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "full villager inventory blocks the transfer");
        helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "failed capacity check takes nothing");
        player.getInventory().items.set(2, player.getInventory().items.get(0));
        player.getInventory().items.set(0, ItemStack.EMPTY);
        inventory.set(0, ItemStack.EMPTY);
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(villager, inventory);
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "villager receives the payment after space is available");
        helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 3, "player gives the configured quantity");
        var savedVillager = villager.saveWithoutId(new CompoundTag());
        villager.getInventory().clearContent();
        villager.load(savedVillager);
        helper.assertTrue(VillagerActionRegistry.execute(context, SceneContinuation.loadAction(SceneContinuation.saveAction(action)), java.util.Map.of()).success(),
                "saved player and villager receipts safely replay");
        helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 3, "player is not charged twice");
        helper.assertValueEqual(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(villager).stream().filter(stack -> stack.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum(), 2, "villager does not receive duplicate items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void fullInventorySkipPermanentlyCompletesTransfer(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        for (int slot = 0; slot < 36; slot++) player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        var action = action("""
                {"type":"item_transfer","operation_id":"skip-%s","direction":"give_player","selection":"specific",
                 "item":"minecraft:bread","quantity":1,"on_full":"skip"}
                """.formatted(UUID.randomUUID()));
        var context = DialogueContext.forPlayer(player);
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "skip policy finishes the operation");
        player.getInventory().items.set(0, ItemStack.EMPTY);
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "reopening skipped transfer remains satisfied");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 0, "skipped transfer cannot grant later");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void questlineGrantsWaitForEveryQuestInAnyOrder(GameTestHelper helper) throws Exception {
        var first = compile("test:line_first", "{\"on_complete\":{\"actions\":[{\"type\":\"advancement\",\"advancement\":\"minecraft:story/upgrade_tools\",\"questline\":\"editor_line\"}]}}", "[]");
        var second = compile("test:line_second", "{}", "[]");
        try (var ignored = VillagerQuestResources.preserveTestOverride()) {
            VillagerQuestResources.installCompiledTestCatalog(helper.getLevel().getServer(), List.of(first, second));
            var player = helper.makeMockServerPlayerInLevel();
            var context = DialogueContext.forPlayer(player);
            var data = VillagerQuestSavedData.get(helper.getLevel());
            var firstProgress = data.getOrCreate(player.getUUID(), first.id());
            firstProgress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, 0);
            firstProgress.complete(1, false);
            var holder = helper.getLevel().getServer().getAdvancements().get(ResourceLocation.parse("minecraft:story/upgrade_tools"));
            com.jvn.villagerretaliation.action.AdvancementGrant.onQuestCompleted(context);
            helper.assertFalse(player.getAdvancements().getOrStartProgress(holder).isDone(), "one completed quest cannot complete the questline");
            var secondProgress = data.getOrCreate(player.getUUID(), second.id());
            secondProgress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, 0);
            secondProgress.complete(2, false);
            com.jvn.villagerretaliation.action.AdvancementGrant.onQuestCompleted(context);
            helper.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone(), "finishing another quest revisits the earlier questline declaration");
            var criterion = player.getAdvancements().getOrStartProgress(holder).getCriterion(holder.value().criteria().keySet().iterator().next());
            var obtained = criterion.getObtained();
            com.jvn.villagerretaliation.action.AdvancementGrant.onQuestCompleted(context);
            helper.assertValueEqual(criterion.getObtained(), obtained, "repeated completion scans do not replay criteria");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void randomRemovalProtectsExistingQuestProofItems(GameTestHelper helper) throws Exception {
        var quest = compile("test:proof", "{}", "[{\"id\":\"proof\",\"type\":\"item_check\",\"item\":\"minecraft:paper\"}]");
        try (var ignored = VillagerQuestResources.preserveTestOverride()) {
            VillagerQuestResources.installCompiledTestCatalog(helper.getLevel().getServer(), List.of(quest));
            var player = helper.makeMockServerPlayerInLevel();
            var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(player.getUUID(), quest.id());
            progress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, 0);
            player.getInventory().items.set(0, new ItemStack(Items.PAPER));
            player.getInventory().items.set(1, new ItemStack(Items.BREAD));
            var transfer = action("""
                    {"type":"item_transfer","operation_id":"random-%s","direction":"take_player",
                     "selection":"random_inventory","quantity":1,"destination":"consume"}
                    """.formatted(UUID.randomUUID()));
            helper.assertTrue(VillagerActionRegistry.execute(DialogueContext.forPlayer(player), transfer, java.util.Map.of()).success(), "random removal finds an eligible ordinary item");
            helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), 1, "existing quest objectives protect their proof items");
            helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 0, "only the eligible bread was consumed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void fullQuestSaveKeepsDistinctTrackingAndInterruptsMaintenance(GameTestHelper helper) {
        var data = new VillagerQuestSavedData();
        var player = UUID.randomUUID();
        var quest = ResourceLocation.parse("test:saved_objectives");
        var progress = data.getOrCreate(player, quest);
        progress.start(UUID.randomUUID(), helper.getLevel().dimension(), null, 0);
        var villager = UUID.randomUUID();
        progress.recordObjectiveVillager("trades", villager);
        progress.addObjectiveCounter("trades", 1);
        progress.sampleCondition("safe", true, 1, 20);
        progress.sampleCondition("safe", true, 2, 20);
        var loaded = VillagerQuestSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()).get(player, quest);
        helper.assertValueEqual(loaded.objectiveCounter("trades"), 1, "normal save codec preserves the counter");
        helper.assertFalse(loaded.recordObjectiveVillager("trades", villager), "normal save codec preserves distinct UUIDs");
        var replacement = UUID.randomUUID();
        helper.assertTrue(loaded.replaceVillagerIdentity(villager, replacement), "identity recovery remaps objective references");
        helper.assertFalse(loaded.recordObjectiveVillager("trades", replacement), "a restored villager cannot count as a new distinct individual");
        loaded.sampleCondition("safe", true, 3, 20);
        helper.assertValueEqual(loaded.maintainedTicks("safe"), 0L, "normal world reload interrupts continuous time");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "editor_features")
    public static void anotherPlayersOpenInventoryBlocksVillagerTransfer(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var peer = helper.makeMockServerPlayerInLevel();
        peer.setUUID(UUID.randomUUID());
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        player.getInventory().items.set(0, new ItemStack(Items.BREAD, 2));
        var action = action("""
                {"type":"item_transfer","operation_id":"busy-%s","direction":"take_player",
                 "selection":"specific","item":"minecraft:bread","quantity":1,"destination":"villager"}
                """.formatted(UUID.randomUUID()));
        var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var menu = new com.jvn.villagerretaliation.inventory.VillagerInventoryMenu(0, peer.getInventory(), villager,
                com.jvn.villagerretaliation.inventory.VillagerInventoryMenu.ViewMode.PERSONAL, true, false);
        try {
            helper.assertTrue(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.hasOpenInventory(villager), "peer owns a mutable inventory view");
            helper.assertFalse(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "dialogue cannot bypass an open inventory view");
            helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 2, "a blocked transfer charges nothing");
        } finally { menu.removed(peer); }
        helper.assertTrue(VillagerActionRegistry.execute(context, action, java.util.Map.of()).success(), "saved transfer retries after peer closes inventory");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 1, "only one payment is applied after closure");
        helper.succeed();
    }

    private static com.jvn.villagerretaliation.quest.compiled.CompiledQuest compile(String id, String lifecycle, String objectives) {
        var root = json("""
                {"schema":"villagerretaliation:quest/v3","id":"%s","localization_prefix":"test.quest.editor",
                 "metadata":{"questline":"editor_line"},"provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"start","stages":[{"id":"start","objectives":%s}],"lifecycle":%s}
                """.formatted(id, objectives, lifecycle));
        var envelope = com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/editor/quest.json"), root).orElseThrow();
        return QuestV2Compiler.compile(com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }

    private static QuestObjectiveEvaluationContext evaluation(GameTestHelper helper, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
        return new QuestObjectiveEvaluationContext(null, null, helper.getLevel(), definition, progress, null, null, null, objective -> false, objective -> true, null);
    }
    private static VillagerActionDefinition action(String raw) {
        return VillagerActionDefinition.readObjects(ResourceLocation.parse("test:editor_features"), "test", List.of(json(raw)), null).getFirst();
    }
    private static net.minecraft.world.item.trading.MerchantOffer offer() {
        return new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 10, 1, 0);
    }
    private static JsonObject json(String raw) { return JsonParser.parseString(raw).getAsJsonObject(); }
}
