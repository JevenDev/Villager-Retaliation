package com.jvn.villagerretaliation.quest.tutorial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractLifecycle;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.TutorialContinuationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.work.FarmingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation_tutorials")
@PrefixGameTestTemplate(false)
public final class ClipboardTutorialGameTests {
    private ClipboardTutorialGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "clipboard")
    public static void shippedTutorialActionsSurviveRuntimeRegistryParsing(GameTestHelper helper) throws Exception {
        var authored = authoredTutorialActions("craftsman");
        var questId = TutorialService.questId(HiredVillagerRole.CRAFTSMAN);
        var parsed = VillagerActionDefinition.readObjects(questId, "shipped tutorial responses", authored, questId);
        helper.assertValueEqual(parsed.size(), authored.size(), "no shipped tutorial action is silently dropped");
        var operations = parsed.stream().map(VillagerActionDefinition::factValue).collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(operations.containsAll(java.util.Set.of("learn", "supplies", "recover")),
                "learning, provisions and recovery survive the real runtime parser");
        for (var operation : TutorialService.actionSchema().getAsJsonObject("properties")
                .getAsJsonObject("operation").getAsJsonArray("enum")) {
            var entry = new JsonObject();
            entry.addProperty("type", "tutorial"); entry.addProperty("role", "builder");
            entry.addProperty("operation", operation.getAsString()); entry.addProperty("step", "step_2_sequence_1");
            var builderId = TutorialService.questId(HiredVillagerRole.BUILDER);
            var result = VillagerActionDefinition.readObjects(builderId, "all supported tutorial operations", List.of(entry), builderId);
            helper.assertValueEqual(result.size(), 1, "schema-supported operation survives runtime registry: " + operation);
            helper.assertValueEqual(result.getFirst().factValue(), operation.getAsString(), "operation is retained exactly");
        }
        var invalid = JsonParser.parseString("{\"type\":\"tutorial\",\"role\":\"craftsman\",\"operation\":\"invented_operation\"}").getAsJsonObject();
        helper.assertTrue(VillagerActionDefinition.readObjects(questId, "invalid tutorial operation", List.of(invalid), questId).isEmpty(),
                "unsupported operations remain rejected");
        helper.succeed();
    }

    private static List<JsonObject> authoredTutorialActions(String role) throws java.io.IOException {
        String resource = "/data/villagerretaliation/quests/clipboard/clipboard_" + role + "/quest.json";
        try (var stream = java.util.Objects.requireNonNull(ClipboardTutorialGameTests.class.getResourceAsStream(resource), resource);
                var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            var result = new java.util.ArrayList<JsonObject>();
            collectTutorialActions(JsonParser.parseReader(reader), result);
            return List.copyOf(result);
        }
    }

    private static void collectTutorialActions(com.google.gson.JsonElement element, List<JsonObject> result) {
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive()
                    && object.get("type").getAsString().equals("tutorial")) result.add(object);
            else for (var child : object.entrySet()) collectTutorialActions(child.getValue(), result);
        } else if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) collectTutorialActions(child, result);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "clipboard")
    public static void filterExerciseRequiresAssignmentObservationAndReassignment(GameTestHelper helper) throws Exception {
        try (var f = fixture(helper)) {
            var run = rebind(f, HiredVillagerRole.CRAFTSMAN, VillagerProfession.TOOLSMITH);
            var level = helper.getLevel();
            var questId = TutorialService.questId(HiredVillagerRole.CRAFTSMAN);
            com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(level).getOrCreate(f.player.getUUID(), questId)
                    .start(f.worker.getUUID(), level.dimension(), null, level.getGameTime());
            var recipe = TutorialCraftingPlan.recipe(level);
            helper.assertTrue(recipe != null, "runtime oak-planks recipe available");
            var filter = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.RECIPE_FILTER.get());
            com.jvn.villagerretaliation.item.VillagerRecipeFilterData.setRecipe(filter, level, recipe.id());
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setListMode(filter,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.ListMode.DENY_MATCHING);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, filter);
            helper.assertFalse(TutorialFilterExercise.check(level, f.worker, run, "filter_excluded"),
                    "configured held filter and menu opening cannot count as assignment");
            helper.assertTrue(com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignHeldFilter(f.player, f.worker).assigned(),
                    "real server assignment succeeds");
            helper.assertTrue(TutorialFilterExercise.check(level, f.worker, run, "filter_excluded"), "installed exclusion counts");
            var corrected = com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignedFilter(f.worker).copyWithCount(1);
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setListMode(corrected,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.ListMode.ALLOW_MATCHING);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, corrected);
            com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignHeldFilter(f.player, f.worker);
            helper.assertFalse(TutorialFilterExercise.check(level, f.worker, run, "filter_corrected"),
                    "correction before observation does not skip the exercise");
            var excluded = com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignedFilter(f.worker).copyWithCount(1);
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setListMode(excluded,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.ListMode.DENY_MATCHING);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, excluded);
            com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignHeldFilter(f.player, f.worker);
            var dialogue = VillagerInteractionService.createDialogueContext(level, f.player, f.worker);
            var observation = VillagerActionDefinition.readObjects(questId, "authored filter observation",
                    authoredTutorialActions("craftsman"), questId).stream()
                    .filter(action -> action.factValue().equals("learn") && action.text().equals(TutorialFilterExercise.OBSERVED))
                    .findFirst().orElseThrow();
            helper.assertTrue(com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(dialogue, observation, Map.of()).success(),
                    "parsed shipped observation executes through the registry and checks the installed exclusion");
            var finalFilter = com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignedFilter(f.worker).copyWithCount(1);
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setListMode(finalFilter,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.ListMode.ALLOW_MATCHING);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, finalFilter);
            com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignHeldFilter(f.player, f.worker);
            helper.assertTrue(TutorialFilterExercise.check(level, f.worker, run, "filter_corrected"),
                    "correction must be committed after observation");
            helper.assertValueEqual(run.committedUnits("craft"), 0, "filter setup grants no productive work");
            helper.assertFalse(run.completed(), "filter exercise cannot complete the finite trial");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "clipboard")
    public static void personalJobBlockersSeparateEmptyScansStorageAndPaths(GameTestHelper helper) throws Exception {
        try (var f = fixture(helper)) {
            var session = HiredWorkSession.create(helper.getLevel(), f.worker, HiredVillagerRole.FARMING);
            for (String reason : List.of("no_targets", "storage_full_or_unavailable", "target_unreachable")) {
                var state = reason.equals("storage_full_or_unavailable")
                        ? com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.PAUSED_STORAGE_FULL
                        : com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.FAILED_COOLDOWN;
                var brain = new com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.Snapshot(
                        state, f.outputPos, null, reason, 100, "field_scan_full_no_targets", 0, "");
                var facts = com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(session, brain);
                String advice = com.jvn.villagerretaliation.interaction.PersonalWorkerDiagnostics.describe(session, brain, facts);
                helper.assertTrue(reason.equals("no_targets") ? advice.contains("scan alone")
                                : reason.equals("target_unreachable") ? advice.contains("safe walking route")
                                : advice.contains("Make room"),
                        "observed blocker has actionable advice without inventing a filter cause: " + advice);
            }
            var missingHoe = new com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.Snapshot(
                    com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.IDLE,
                    null, null, "missing_hoe", 100, "field_scan_full_no_targets", 0, "");
            helper.assertTrue(com.jvn.villagerretaliation.interaction.PersonalWorkerDiagnostics.describe(session, missingHoe,
                    com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(session, missingHoe)).contains("hoe"),
                    "specific role blocker is retained over generic scan advice");
            rebind(f, HiredVillagerRole.CRAFTSMAN, VillagerProfession.TOOLSMITH);
            var craftsman = HiredWorkSession.create(helper.getLevel(), f.worker, HiredVillagerRole.CRAFTSMAN);
            var missing = new com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.Snapshot(
                    com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.WAITING_FOR_MATERIALS,
                    null, null, "missing_craftsman_materials", 100, "", 0, "");
            helper.assertTrue(com.jvn.villagerretaliation.interaction.PersonalWorkerDiagnostics.describe(craftsman, missing,
                    com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(craftsman, missing)).contains("Add the recipe ingredients"),
                    "missing supplies provide the actual inventory or assigned Supplies repair");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "clipboard")
    public static void farmerProvesActualWorkAndDoesNotCountOldWheat(GameTestHelper helper) throws Exception {
        try (var f = fixture(helper)) {
            f.output.setItem(0, new ItemStack(Items.WHEAT, 9));
            helper.assertFalse(f.run.completed(), "old wheat in Output proves no productive work");
            var settings=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.worker);
            settings.putBoolean("Enabled",false);
            helper.assertFalse(TutorialWorkService.canTick(f.worker,HiredVillagerRole.FARMING),"actual paused work state suspends trial authority");
            settings.putBoolean("Enabled",true);
            f.work();
            helper.assertValueEqual(f.run.committedUnits("till"), 1, "actual till action recorded");
            helper.assertValueEqual(f.run.committedUnits("plant"), 1, "actual seed-consuming plant recorded");
            helper.assertValueEqual(f.run.committedUnits("harvest"), 1, "actual mature wheat harvest recorded");
            helper.assertValueEqual(f.run.committedUnits("replant"), 1, "actual replacement planting recorded");
            f.deliver();
            helper.assertTrue(f.run.completed(), "attributable delivery completes the finite work");
            helper.assertValueEqual(f.output.getItem(0).getCount(), 10, "exact real wheat adds to old stock without fabricating a reward");
            helper.assertFalse(TutorialWorkService.canTick(f.worker, HiredVillagerRole.FARMING), "closing dialogue freezes new free work");
            helper.assertFalse(HireContractStore.load(f.worker).orElseThrow().autoPayment(), "trial has no automatic payment");
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player), 0, "free trial costs no currency");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "clipboard")
    public static void fullOutputPausesAndOneAcceptedCurrentFeeConvertsOnce(GameTestHelper helper) throws Exception {
        try (var f = fixture(helper)) {
            for (int slot = 0; slot < f.output.getContainerSize(); slot++) f.output.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            f.work(); f.deliver();
            helper.assertFalse(f.run.completed(), "full Output cannot produce a delivery receipt");
            helper.assertValueEqual(f.run.committedUnits("harvest"), 1, "waiting for Output does not repeat harvest");
            f.output.setItem(0, ItemStack.EMPTY); f.output.setItem(1, ItemStack.EMPTY);
            // Exercise the normal explicit storage-retry boundary rather than waiting out its full-box cooldown.
            AssignedStorageService.clearStorageFailure(helper.getLevel(), f.worker, f.outputPos);
            f.deliver();
            helper.assertTrue(f.run.completed(), "clearing room resumes the original delivery");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), f.player, f.worker);
            var quoteAction = action("quote");
            var acceptAction = action("continue");
            helper.assertTrue(TutorialContinuationService.execute(context, quoteAction, f.run, f.worker).success(), "current quote shown without payment");
            var quote = f.run.snapshot().quote();
            helper.assertFalse(TutorialContinuationService.execute(context, acceptAction, f.run, f.worker).success(), "insufficient funds do not create debt or work");
            helper.assertValueEqual(f.run.state(), TutorialRun.State.AWAITING_CHOICE, "failed consent stays paused");
            f.player.getInventory().add(new ItemStack(VillagerCurrencyResources.primaryItem(helper.getLevel().getServer()), quote.total() + 5));
            helper.assertTrue(TutorialContinuationService.execute(context, acceptAction, f.run, f.worker).success(), "explicit accepted current quote converts");
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player), 5, "fee debited exactly once");
            helper.assertTrue(TutorialContinuationService.execute(context, acceptAction, f.run, f.worker).success(), "replayed acceptance reconciles");
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player), 5, "replay never charges twice");
            helper.assertFalse(HiredVillagerContractService.stopTutorialContract(helper.getLevel(), f.worker, f.player.getUUID(),
                    f.run.binding().assignment(), f.run.trial()), "stale tutorial Stop cannot cancel later paid work");
            helper.assertFalse(HireContractStore.load(f.worker).orElseThrow().autoPayment(), "normal continuation starts with automatic payment off");
            helper.succeed();
        }
    }

    private static VillagerActionDefinition action(String operation) {
        JsonObject wrapper = JsonParser.parseString("{\"actions\":[{\"type\":\"tutorial\",\"role\":\"farming\",\"operation\":\"" + operation
                + "\",\"quest\":\"villagerretaliation:clipboard_farming\"}]}").getAsJsonObject();
        return VillagerActionDefinition.readList(ResourceLocation.parse("villagerretaliation:test"), "test", wrapper,
                TutorialService.questId(HiredVillagerRole.FARMING)).getFirst();
    }

    @GameTest(template = "empty", timeoutTicks = 220, batch = "clipboard")
    public static void minerCutsOnlyThreeApprovedOresAndDeliversAttributedDrops(GameTestHelper helper) throws Exception {
        var f = fixture(helper);
        f.run.stop(f.run.binding());
        HiredVillagerContractService.stopTutorialContract(helper.getLevel(), f.worker, f.player.getUUID(), f.run.binding().assignment(), f.run.trial());
        f.worker.setVillagerData(f.worker.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        var level = helper.getLevel();
        var contract = HireContract.tutorial(f.player.getUUID(), level.getGameTime(), HiredVillagerRole.MINING, UUID.randomUUID());
        var mining = new TutorialRun(contract.tutorialId().orElseThrow(), TutorialService.questId(HiredVillagerRole.MINING).toString(),
                new TutorialRun.Binding(f.player.getUUID(), f.worker.getUUID(), f.worker.getUUID(), contract.id(), UUID.randomUUID(), UUID.randomUUID(),
                        0, "mining", level.dimension().location().toString()));
        TutorialSavedData.get(level).reserve(mining); HireContractLifecycle.begin(level, f.worker, contract);
        var ores = new java.util.ArrayList<BlockPos>();
        for (int x = 2; x <= 4; x++) {
            var ore = helper.absolutePos(new BlockPos(x, 2, 2)); ores.add(ore);
            level.setBlock(ore, Blocks.COAL_ORE.defaultBlockState(), 3);
            level.setBlock(ore.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ore.south().below(), Blocks.STONE.defaultBlockState(), 3);
        }
        var untouched = helper.absolutePos(new BlockPos(6, 2, 2)); level.setBlock(untouched, Blocks.COAL_ORE.defaultBlockState(), 3);
        HiredVillagerWorkService.setWorkArea(f.player, level, f.worker, ores.getFirst().below(), ores.getLast().south().above());
        var inventory = HiredJobInventory.getJobInventory(f.worker);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        HiredWorkSession.invalidate(f.worker);
        var plan = TutorialMiningPlan.inspect(level, f.worker);
        helper.assertTrue(plan.scope() != null, "mining fixture passes preflight: " + plan.blocker());
        mining.activate(mining.binding(), plan.scope());
        stepMining(helper, f, mining, ores, untouched, 0);
    }
    private static void stepMining(GameTestHelper helper, Fixture f, TutorialRun run, List<BlockPos> ores, BlockPos untouched, int attempt) {
        try {
            var level = helper.getLevel();
            var next = ores.stream().filter(pos -> run.snapshot().receipts().values().stream().noneMatch(r ->
                    r.permit().action().equals("mine") && r.permit().target().equals(TutorialWorkService.position(level, pos)))).findFirst();
            if (next.isPresent()) {
                var stand = next.get().south(); f.worker.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                var context = HiredWorkSession.create(level, f.worker, HiredVillagerRole.MINING).context();
                // Skip the breaking animation's wait; the real worker still validates, mines, consumes durability and stores real loot.
                context.setProgressTicks(10000);
                new com.jvn.villagerretaliation.interaction.work.mining.MiningWorker().tick(level, f.worker, f.player, context);
            } else {
                f.worker.setPos(f.outputPos.getX() - 0.5, f.outputPos.getY(), f.outputPos.getZ() + 0.5);
                HiredJobInventory.getJobInventory(f.worker).depositOutputToAssignedStorageAt(f.outputPos);
            }
            if (run.completed()) {
                helper.assertValueEqual(run.committedUnits("mine"), 3, "only three distinct successful approved cuts count");
                helper.assertValueEqual(run.deposited("minecraft:coal"), 3, "only the worker's three real drops are delivered");
                helper.assertTrue(level.getBlockState(untouched).is(Blocks.COAL_ORE), "unapproved ore remains untouched");
                helper.assertFalse(TutorialWorkService.canTick(f.worker, HiredVillagerRole.MINING), "completion freezes further free mining");
                f.close(); helper.succeed(); return;
            }
            if (attempt >= 180) helper.fail("mining did not finish: " + HiredWorkSession.create(level, f.worker, HiredVillagerRole.MINING).state());
            helper.runAfterDelay(1, () -> stepMining(helper, f, run, ores, untouched, attempt + 1));
        } catch (RuntimeException | Error failure) { f.close(); throw failure; }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "clipboard")
    public static void provisionsSurviveFullInventoryAndInterruptedLedgerAcknowledgement(GameTestHelper helper) throws Exception {
        try (var f = fixture(helper)) {
            var before = f.run.snapshot();
            for (int slot = 0; slot < f.player.getInventory().items.size(); slot++)
                f.player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            helper.assertValueEqual(TutorialProvisionService.deliver(f.player, f.run, "test_inputs", new ItemStack(Items.WHEAT_SEEDS, 2)),
                    TutorialProvisionService.Delivery.WAITING_FOR_SPACE, "full inventory retains a pending grant");
            f.player.getInventory().setItem(0, ItemStack.EMPTY);
            var pending = f.run.snapshot();
            helper.assertValueEqual(TutorialProvisionService.deliver(f.player, f.run, "test_inputs", new ItemStack(Items.WHEAT_SEEDS, 2)),
                    TutorialProvisionService.Delivery.DELIVERED, "space permits the original delivery");
            var restored = TutorialRun.restore(pending);
            helper.assertValueEqual(TutorialProvisionService.deliver(f.player, restored, "test_inputs", new ItemStack(Items.WHEAT_SEEDS, 2)),
                    TutorialProvisionService.Delivery.ALREADY_DELIVERED, "player's receipt reconciles an older overworld grant acknowledgement");
            helper.assertValueEqual(f.player.getInventory().getItem(0).getCount(), 2, "reconciliation duplicates no seeds");
            helper.assertTrue(f.player.getInventory().getItem(1).is(Items.COBBLESTONE), "recovery does not delete indistinguishable player property");
            f.player.getInventory().setItem(2, ItemStack.EMPTY);
            helper.assertValueEqual(TutorialProvisionService.introductoryClipboard(f.player, f.run), TutorialProvisionService.Delivery.DELIVERED,
                    "first lesson supplies the shared Clipboard once");
            helper.assertValueEqual(TutorialProvisionService.introductoryClipboard(f.player,
                    new TutorialRun(UUID.randomUUID(), "villagerretaliation:clipboard_nitwit", before.binding())),
                    TutorialProvisionService.Delivery.ALREADY_DELIVERED, "another job shares the same global Clipboard grant");
            helper.succeed();
        }
    }

    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void allFourteenLessonsLoadAndStayPersonal(GameTestHelper helper)throws Exception{
        for(var role:HiredVillagerRole.values()){
            var quest=com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(helper.getLevel().getServer(),TutorialService.questId(role)).orElse(null);
            helper.assertTrue(quest!=null,"runtime loads the authored "+role+" bundle");
            helper.assertFalse(com.jvn.villagerretaliation.quest.PartyQuestService.isShareable(quest),"personal tutorial cannot leak through party sharing");
        }
        try(var f=fixture(helper)){
            var plan=TutorialBuilderPlan.plan(helper.getLevel(),net.minecraft.world.level.block.Rotation.NONE).orElse(null);
            helper.assertTrue(plan!=null&&!plan.materials().isEmpty(),"exact Plains Accessory 1 resolves its actual runtime materials");
            helper.assertValueEqual(plan.entry().id(),TutorialBuilderPlan.TEMPLATE,"exact template is retained");
            helper.assertValueEqual(TutorialBuilderPlan.fingerprint(plan),TutorialBuilderPlan.fingerprint(TutorialBuilderPlan.plan(helper.getLevel(),net.minecraft.world.level.block.Rotation.NONE).orElseThrow()),"runtime plan fingerprint is stable");
        }
        helper.succeed();
    }
    private static TutorialRun rebind(Fixture f,HiredVillagerRole role,VillagerProfession profession){
        var level=f.helper.getLevel();f.run.stop(f.run.binding());
        HiredVillagerContractService.stopTutorialContract(level,f.worker,f.player.getUUID(),f.run.binding().assignment(),f.run.trial());
        f.worker.setVillagerData(f.worker.getVillagerData().setProfession(profession));
        var contract=HireContract.tutorial(f.player.getUUID(),level.getGameTime(),role,UUID.randomUUID());
        var run=new TutorialRun(contract.tutorialId().orElseThrow(),TutorialService.questId(role).toString(),new TutorialRun.Binding(f.player.getUUID(),f.worker.getUUID(),f.worker.getUUID(),contract.id(),UUID.randomUUID(),UUID.randomUUID(),0,role.serializedName(),level.dimension().location().toString()));
        TutorialSavedData.get(level).reserve(run);HireContractLifecycle.begin(level,f.worker,contract);HiredWorkSession.invalidate(f.worker);return run;
    }
    private static void filter(Villager worker,net.minecraft.world.item.Item result){
        var filter=new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get());
        com.jvn.villagerretaliation.item.VillagerItemFilterData.setMode(filter,com.jvn.villagerretaliation.item.VillagerItemFilterData.Mode.ALLOWLIST);
        com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(filter,0,new ItemStack(result));
        com.jvn.villagerretaliation.inventory.VillagerItemFilterService.replaceFilter(worker,filter);
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void craftsmanConsumesOneRealLogAndDeliversOnlyItsFourPlanks(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.CRAFTSMAN,VillagerProfession.TOOLSMITH);
            var level=helper.getLevel();var table=helper.absolutePos(new BlockPos(2,1,3));level.setBlock(table,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
            HiredVillagerWorkService.setWorkArea(f.player,level,f.worker,table.offset(-1,-1,-1),table.offset(1,1,1));
            var inventory=HiredJobInventory.getJobInventory(f.worker);inventory.setItem(HiredJobInventory.MAIN_GRID_START,new ItemStack(Items.OAK_LOG,3));inventory.markPlayerPlacedSupply(HiredJobInventory.MAIN_GRID_START);
            filter(f.worker,Items.OAK_PLANKS);HiredWorkSession.invalidate(f.worker);
            var plan=TutorialCraftingPlan.inspect(level,f.worker);helper.assertTrue(plan.scope()!=null,"real crafting fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());
            f.output.setItem(0,new ItemStack(Items.OAK_PLANKS,20));
            for(int i=0;i<20&&!run.completed();i++){
                f.worker.setPos(table.getX()+0.5,table.getY(),table.getZ()+1.5);
                var context=HiredWorkSession.create(level,f.worker,HiredVillagerRole.CRAFTSMAN).context();context.setProgressTicks(10000);
                new com.jvn.villagerretaliation.interaction.work.CraftsmanWorker().tick(level,f.worker,f.player,context);
                f.worker.setPos(f.outputPos.getX()-0.5,f.outputPos.getY(),f.outputPos.getZ()+0.5);inventory.depositOutputToAssignedStorageAt(f.outputPos);
            }
            helper.assertValueEqual(run.committedUnits("craft"),1,"one actual recipe operation, no repeat free crafts");
            helper.assertValueEqual(run.deposited("minecraft:oak_planks"),4,"only actual recipe result counts");
            helper.assertValueEqual(inventory.getItem(HiredJobInventory.MAIN_GRID_START).getCount(),2,"two unallocated logs remain untouched");
            helper.assertTrue(run.completed(),"real craft and delivery finish");
            // A learner may authorize the bounded craft through common dialogue before reaching these cards.
            // Finishing that work must not strand the later setup-only exercise or restart free production.
            var lessonFilter = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.RECIPE_FILTER.get());
            com.jvn.villagerretaliation.item.VillagerRecipeFilterData.setRecipe(lessonFilter, level, TutorialCraftingPlan.recipe(level).id());
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setListMode(lessonFilter,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.ListMode.DENY_MATCHING);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, lessonFilter);
            helper.assertTrue(com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignHeldFilter(f.player, f.worker).assigned(),
                    "completed bound trial still accepts instructional filter assignment");
            helper.assertTrue(TutorialFilterExercise.check(level, f.worker, run, "filter_excluded"),
                    "completed trial can demonstrate exclusion without a new allowance");
            var dialogue = VillagerInteractionService.createDialogueContext(level, f.player, f.worker);
            helper.assertTrue(TutorialLessonCheckpoints.acknowledge(dialogue, run, TutorialFilterExercise.OBSERVED).isEmpty(),
                    "completed trial can observe its installed filter");
            var corrected = com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignedFilter(f.worker).copyWithCount(1);
            com.jvn.villagerretaliation.item.VillagerFilterPolicy.setListMode(corrected,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.ListMode.ALLOW_MATCHING);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, corrected);
            com.jvn.villagerretaliation.inventory.VillagerItemFilterService.assignHeldFilter(f.player, f.worker);
            helper.assertTrue(TutorialFilterExercise.check(level, f.worker, run, "filter_corrected"),
                    "completed trial can finish correction and reach the closing choice");
            helper.assertTrue(run.completed(), "setup-only exercise retains earned completion");
            helper.assertValueEqual(run.committedUnits("craft"), 1, "setup does not replenish the spent craft budget");
            helper.assertFalse(TutorialWorkService.canTick(f.worker, HiredVillagerRole.CRAFTSMAN),
                    "completed work stays frozen until explicit continuation");
            helper.succeed();
        }
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void smelterRequiresRealVanillaTransitionsAndRejectsOldFurnaceOutput(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.SMELTER,VillagerProfession.ARMORER);var level=helper.getLevel();
            var pos=helper.absolutePos(new BlockPos(2,1,3));level.setBlock(pos,Blocks.FURNACE.defaultBlockState(),3);
            var furnace=(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(pos);
            HiredVillagerWorkService.setWorkArea(f.player,level,f.worker,pos.offset(-1,-1,-1),pos.offset(1,1,1));
            var inventory=HiredJobInventory.getJobInventory(f.worker);inventory.setItem(HiredJobInventory.MAIN_GRID_START,new ItemStack(Items.RAW_IRON,5));inventory.markPlayerPlacedSupply(HiredJobInventory.MAIN_GRID_START);
            inventory.setItem(HiredJobInventory.MAIN_GRID_START+1,new ItemStack(Items.COAL));inventory.markPlayerPlacedSupply(HiredJobInventory.MAIN_GRID_START+1);
            filter(f.worker,Items.IRON_INGOT);HiredWorkSession.invalidate(f.worker);
            furnace.setItem(2,new ItemStack(Items.IRON_INGOT));
            helper.assertTrue(TutorialFurnacePlan.inspect(level,f.worker,HiredVillagerRole.SMELTER).scope()==null,"old station output is not a sponsored batch");furnace.setItem(2,ItemStack.EMPTY);
            var plan=TutorialFurnacePlan.inspect(level,f.worker,HiredVillagerRole.SMELTER);helper.assertTrue(plan.scope()!=null,"actual furnace fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());
            for(int i=0;i<650&&!run.completed();i++){
                f.worker.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+1.5);
                var context=HiredWorkSession.create(level,f.worker,HiredVillagerRole.SMELTER).context();
                new com.jvn.villagerretaliation.interaction.work.SmeltingWorker().tick(level,f.worker,f.player,context);
                // Execute normal vanilla ticks, retaining the recipe's real processing duration.
                net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
                f.worker.setPos(f.outputPos.getX()-0.5,f.outputPos.getY(),f.outputPos.getZ()+0.5);inventory.depositOutputToAssignedStorageAt(f.outputPos);
            }
            helper.assertValueEqual(run.committedUnits("load"),2,"only two inputs enter the finite batch");
            helper.assertValueEqual(run.committedUnits("process"),2,"two actual vanilla recipe transitions");
            helper.assertValueEqual(run.deposited("minecraft:iron_ingot"),2,"two attributable ingots, not old output");
            helper.assertTrue(run.completed(),"bounded furnace processing and delivery finish");helper.succeed();
        }
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void nitwitReportsOnceWithoutFabricatingItems(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.NITWIT,VillagerProfession.NITWIT);
            run.activate(run.binding(),new TutorialRun.Scope(Map.of("report",java.util.Set.of(f.worker.getUUID().toString())),Map.of("report",1),Map.of("report",1),""));
            var context=HiredWorkSession.create(helper.getLevel(),f.worker,HiredVillagerRole.NITWIT).context();
            new com.jvn.villagerretaliation.interaction.work.NitwitWorker().tick(helper.getLevel(),f.worker,f.player,context);
            new com.jvn.villagerretaliation.interaction.work.NitwitWorker().tick(helper.getLevel(),f.worker,f.player,context);
            helper.assertValueEqual(run.committedUnits("report"),1,"only one real report is committed");helper.assertTrue(run.completed(),"report is the actual Nitwit outcome");
            helper.assertTrue(run.snapshot().receipts().values().stream().allMatch(receipt->receipt.products().isEmpty()),"no fabricated Nitwit loot");helper.succeed();
        }
    }

    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void brewerTransformsThreeActualBottlesThroughTheRuntimeRoute(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.BREWING,VillagerProfession.CLERIC);var level=helper.getLevel();
            var pos=helper.absolutePos(new BlockPos(2,1,3));level.setBlock(pos,Blocks.BREWING_STAND.defaultBlockState(),3);
            var stand=(net.minecraft.world.level.block.entity.BrewingStandBlockEntity)level.getBlockEntity(pos);
            HiredVillagerWorkService.setWorkArea(f.player,level,f.worker,pos.offset(-1,-1,-1),pos.offset(1,1,1));
            var route=com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog.find(level,ResourceLocation.parse("minecraft:potion"),ResourceLocation.parse("minecraft:swiftness")).orElseThrow();
            var state=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.worker);
            com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker.setOrder(state,route.itemId(),route.potionId(),3,false,run.binding().assignment());
            var inventory=HiredJobInventory.getJobInventory(f.worker);int slot=HiredJobInventory.MAIN_GRID_START;
            for(int i=0;i<3;i++){inventory.setItem(slot,net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,net.minecraft.world.item.alchemy.Potions.WATER));inventory.markPlayerPlacedSupply(slot++);}
            for(var ingredient:route.ingredients()){inventory.setItem(slot,new ItemStack(ingredient));inventory.markPlayerPlacedSupply(slot++);}
            inventory.setItem(slot,new ItemStack(Items.BLAZE_POWDER));inventory.markPlayerPlacedSupply(slot);
            HiredWorkSession.invalidate(f.worker);var plan=TutorialBrewingPlan.inspect(level,f.worker);helper.assertTrue(plan.scope()!=null,"actual brewing fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());
            for(int i=0;i<1800&&!run.completed();i++){
                f.worker.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+1.5);
                var context=HiredWorkSession.create(level,f.worker,HiredVillagerRole.BREWING).context();
                new com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker().tick(level,f.worker,f.player,context);
                net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(level,pos,level.getBlockState(pos),stand);
                if(run.committedUnits("brew")>0&&!inventory.collectOutputItems().isEmpty()){
                    var bottle=inventory.collectOutputItems().getFirst().stack().copy();
                    helper.assertTrue(TutorialOutputService.attributable(f.worker,bottle),"actual produced potion retains exact identity through job inventory");
                    var altered=bottle.copy();altered.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,net.minecraft.world.item.alchemy.Potions.LONG_SWIFTNESS).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS));
                    helper.assertFalse(TutorialOutputService.attributable(f.worker,altered),"a different potion component cannot borrow this recipe's receipt");
                }
                f.worker.setPos(f.outputPos.getX()-0.5,f.outputPos.getY(),f.outputPos.getZ()+0.5);inventory.depositOutputToAssignedStorageAt(f.outputPos);
            }
            helper.assertValueEqual(run.committedUnits("brew_stage"),route.ingredients().size(),"real vanilla potion transitions, one for each runtime ingredient");
            helper.assertValueEqual(run.committedUnits("brew"),3,"three genuinely collected route outputs");
            helper.assertValueEqual(run.deposited("minecraft:potion"),3,"all three actual bottles delivered");
            helper.assertTrue(run.completed(),"finite brewing finishes: "+state);
            for(int i=0;i<f.output.getContainerSize();i++)if(!f.output.getItem(i).isEmpty())helper.assertTrue(ItemStack.isSameItemSameComponents(f.output.getItem(i),route.output()),"delivered potion components match the runtime route");
            helper.succeed();
        }
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void hunterCannotDamageAnotherAnimalAndReconcilesRealLoot(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.HUNTING,VillagerProfession.FLETCHER);var level=helper.getLevel();
            var pig=helper.spawn(EntityType.PIG,new BlockPos(2,1,3));pig.setNoAi(true);
            var outside=helper.spawn(EntityType.COW,new BlockPos(3,1,3));outside.setNoAi(true);
            var min=helper.absolutePos(new BlockPos(1,1,1));var max=helper.absolutePos(new BlockPos(4,3,4));
            HiredVillagerWorkService.setWorkArea(f.player,level,f.worker,min,max);
            var state=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.worker);state.putBoolean("HuntAnimals",true);state.putBoolean("HuntHostiles",false);state.putBoolean("HuntPlayers",false);
            HiredJobInventory.getJobInventory(f.worker).setItem(HiredJobInventory.MAINHAND_SLOT,new ItemStack(Items.WOODEN_SWORD));
            var task=new com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask(UUID.randomUUID(),com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.HUNT,min,max,1,null,pig.getUUID());
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(f.player,f.worker,task,false),"","normal targeted Hunt task accepted");
            var plan=TutorialHuntingPlan.inspect(level,f.worker);helper.assertTrue(plan.scope()!=null,"hunting fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());
            float health=outside.getHealth();outside.hurt(f.worker.damageSources().mobAttack(f.worker),100);helper.assertValueEqual(outside.getHealth(),health,"bound trial cannot harm unapproved quarry");
            pig.hurt(f.worker.damageSources().mobAttack(f.worker),100);
            helper.assertValueEqual(run.committedUnits("kill"),1,"only actual approved death counts");helper.assertFalse(run.completed(),"death alone does not skip loot reconciliation");
            for(int i=0;i<20&&!run.completed();i++){
                f.worker.setPos(pig.getX(),pig.getY(),pig.getZ());
                new com.jvn.villagerretaliation.interaction.work.HuntingWorker().tick(level,f.worker,f.player,HiredWorkSession.create(level,f.worker,HiredVillagerRole.HUNTING).context());
                f.worker.setPos(f.outputPos.getX()-0.5,f.outputPos.getY(),f.outputPos.getZ()+0.5);HiredJobInventory.getJobInventory(f.worker).depositOutputToAssignedStorageAt(f.outputPos);
            }
            helper.assertTrue(run.completed(),"actual supported loot is reconciled and delivered, including honest zero-loot outcomes");helper.succeed();
        }
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void handlerShearsOnceAndWaitsForAnActuallySpawnedLamb(GameTestHelper helper)throws Exception{
        var f=fixture(helper);var run=rebind(f,HiredVillagerRole.ANIMAL_HANDLING,VillagerProfession.SHEPHERD);var level=helper.getLevel();
        var first=helper.spawn(EntityType.SHEEP,new BlockPos(2,1,3));var second=helper.spawn(EntityType.SHEEP,new BlockPos(3,1,3));first.setNoAi(true);second.setNoAi(true);
        var min=helper.absolutePos(new BlockPos(1,1,1));var max=helper.absolutePos(new BlockPos(4,3,4));HiredVillagerWorkService.setWorkArea(f.player,level,f.worker,min,max);
        var state=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.worker);
        com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets.toggleTarget(state,"minecraft:sheep");
        state.putBoolean("AnimalHandlingShearSheep",true);state.putBoolean("AnimalHandlingBreed",true);state.putBoolean("AnimalHandlingMilk",false);state.putBoolean("AnimalHandlingCollect",false);state.putBoolean("AnimalHandlingCull",false);
        var inventory=HiredJobInventory.getJobInventory(f.worker);inventory.setItem(HiredJobInventory.MAINHAND_SLOT,new ItemStack(Items.SHEARS));inventory.setItem(HiredJobInventory.MAIN_GRID_START,new ItemStack(Items.WHEAT,2));inventory.markPlayerPlacedSupply(HiredJobInventory.MAIN_GRID_START);
        var task=new com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask(UUID.randomUUID(),com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.SHEAR,min,max,1,null,first.getUUID());
        helper.assertValueEqual(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(f.player,f.worker,task,false),"","normal selected Shear task accepted");
        var plan=TutorialAnimalPlan.inspect(level,f.worker);helper.assertTrue(plan.scope()!=null,"sheep fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());
        for(int i=0;i<8&&run.committedUnits("shear")==0;i++){
            f.worker.setPos(first.getX(),first.getY(),first.getZ()-1);
            new com.jvn.villagerretaliation.interaction.work.AnimalBreedingWorker().tick(level,f.worker,f.player,HiredWorkSession.create(level,f.worker,HiredVillagerRole.ANIMAL_HANDLING).context());
        }
        helper.assertValueEqual(run.committedUnits("shear"),1,"one real shearing");helper.assertTrue(first.isSheared(),"actual sheep state changes");
        f.worker.setPos(f.outputPos.getX()-0.5,f.outputPos.getY(),f.outputPos.getZ()+0.5);inventory.depositOutputToAssignedStorageAt(f.outputPos);
        var breed=new com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask(UUID.randomUUID(),com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.BREED,min,max,1,null,first.getUUID());
        helper.assertValueEqual(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(f.player,f.worker,breed,false),"","normal selected Breed task accepted");
        for(int i=0;i<8&&!first.isInLove();i++){
            f.worker.setPos(first.getX(),first.getY(),first.getZ()-1);
            new com.jvn.villagerretaliation.interaction.work.AnimalBreedingWorker().tick(level,f.worker,f.player,HiredWorkSession.create(level,f.worker,HiredVillagerRole.ANIMAL_HANDLING).context());
        }
        helper.assertTrue(first.isInLove()&&second.isInLove(),"ordinary worker consumes food and prepares the real pair");helper.assertValueEqual(run.committedUnits("breed"),0,"love mode alone does not earn breeding credit");
        first.spawnChildFromBreeding(level,second);
        helper.runAfterDelay(3,()->{try{helper.assertValueEqual(run.committedUnits("breed"),1,"actual added child commits the bound pair operation");helper.assertTrue(run.completed(),"real wool delivery and genuine birth finish the lesson");helper.succeed();}finally{f.close();}});
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void loggerCutsOnlyApprovedTreeAndPlantsRealReplacement(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.LOGGING,VillagerProfession.NONE);var level=helper.getLevel();
            var root=helper.absolutePos(new BlockPos(2,1,3));level.setBlock(root.below(),Blocks.DIRT.defaultBlockState(),3);
            for(int y=0;y<4;y++)level.setBlock(root.above(y),Blocks.OAK_LOG.defaultBlockState(),3);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)level.setBlock(root.offset(x,3,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1),3);
            var untouched=root.offset(4,0,0);level.setBlock(untouched,Blocks.OAK_LOG.defaultBlockState(),3);
            HiredVillagerWorkService.setWorkArea(f.player,level,f.worker,root.offset(-2,-1,-2),root.offset(2,5,2));
            var state=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.worker);
            state.putBoolean("LoggingPlantSaplings",true);state.putBoolean("LoggingPickUpDecayDrops",true);state.putBoolean("LoggingStripLogs",false);state.putBoolean("LoggingHarvestLeaves",false);state.putBoolean("LoggingBonemealSaplings",false);
            var inventory=HiredJobInventory.getJobInventory(f.worker);inventory.setItem(HiredJobInventory.MAINHAND_SLOT,new ItemStack(Items.WOODEN_AXE));inventory.setItem(HiredJobInventory.MAIN_GRID_START,new ItemStack(Items.OAK_SAPLING));inventory.markPlayerPlacedSupply(HiredJobInventory.MAIN_GRID_START);
            HiredWorkSession.invalidate(f.worker);var plan=TutorialLoggingPlan.inspect(level,f.worker);helper.assertTrue(plan.scope()!=null,"logging fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());
            for(int i=0;i<100&&!run.completed();i++){
                f.worker.setPos(root.getX()+1.5,root.getY(),root.getZ()+0.5);var context=HiredWorkSession.create(level,f.worker,HiredVillagerRole.LOGGING).context();context.setProgressTicks(10000);
                new com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker().tick(level,f.worker,f.player,context);
                f.worker.setPos(f.outputPos.getX()-0.5,f.outputPos.getY(),f.outputPos.getZ()+0.5);inventory.depositOutputToAssignedStorageAt(f.outputPos);
            }
            helper.assertValueEqual(run.committedUnits("log"),4,"four actual approved log breaks");helper.assertValueEqual(run.committedUnits("replant"),1,"one actual sapling consumption and placement");
            helper.assertTrue(level.getBlockState(root).is(Blocks.OAK_SAPLING),"replacement really exists");helper.assertTrue(level.getBlockState(untouched).is(Blocks.OAK_LOG),"unapproved timber is untouched");
            helper.assertTrue(run.completed(),"bounded timber delivery finishes: "+state);helper.succeed();
        }
    }

    @GameTest(template="empty",timeoutTicks=460,batch="clipboard_routes")
    public static void guardActuallyWalksTheOrderedCircuitAndReturns(GameTestHelper helper)throws Exception{
        var f=fixture(helper);var run=rebind(f,HiredVillagerRole.COMBAT,VillagerProfession.WEAPONSMITH);var level=helper.getLevel();
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)level.setBlock(helper.absolutePos(new BlockPos(x,0,z)),Blocks.STONE.defaultBlockState(),3);
        var post=helper.absolutePos(new BlockPos(1,1,1));var east=helper.absolutePos(new BlockPos(7,1,1));var north=helper.absolutePos(new BlockPos(4,1,7));
        HiredVillagerWorkService.setWorkArea(f.player,level,f.worker,post.offset(-1,-1,-1),post.offset(1,2,1));
        var state=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.worker);state.putString(com.jvn.villagerretaliation.interaction.HiredCombatMode.STATE_TAG,"guard");state.putBoolean("Enabled",true);
        new com.jvn.villagerretaliation.interaction.HiredRoute(List.of(post,east,north),true).save(state);
        HiredJobInventory.getJobInventory(f.worker).setItem(HiredJobInventory.MAINHAND_SLOT,new ItemStack(Items.WOODEN_SWORD));
        f.worker.setPos(post.getX()+0.5,post.getY(),post.getZ()+0.5);f.worker.setOnGround(true);HiredWorkSession.invalidate(f.worker);
        var plan=TutorialGuardPlan.inspect(level,f.worker);helper.assertTrue(plan.scope()!=null,"real navigable guard fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());f.worker.setNoAi(false);
        stepRoute(helper,f,run,0);
    }
    @GameTest(template="empty",timeoutTicks=460,batch="clipboard_routes")
    public static void courierWalksAllocatedLogsFromDifferentSourceToDestination(GameTestHelper helper)throws Exception{
        var f=fixture(helper);var run=rebind(f,HiredVillagerRole.COURIER,VillagerProfession.NONE);var level=helper.getLevel();
        var source=helper.absolutePos(new BlockPos(0,1,2));var a=source.east();var b=f.outputPos.west();
        var input=(Container)level.getBlockEntity(source);input.setItem(0,new ItemStack(Items.COBBLESTONE,64));input.setItem(1,new ItemStack(Items.OAK_LOG,9));
        var state=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.worker);state.putBoolean("Enabled",true);
        new com.jvn.villagerretaliation.interaction.HiredRoute(List.of(a,b),false).save(state);
        f.worker.setPos(a.getX()+0.5,a.getY(),a.getZ()+0.5);f.worker.setOnGround(true);HiredWorkSession.invalidate(f.worker);
        var plan=TutorialCourierPlan.inspect(level,f.worker);helper.assertTrue(plan.scope()!=null,"real courier fixture: "+plan.blocker());run.activate(run.binding(),plan.scope());f.worker.setNoAi(false);
        stepRoute(helper,f,run,0);
    }
    private static void stepRoute(GameTestHelper helper,Fixture f,TutorialRun run,int tick){
        try{
            var role=HiredVillagerRole.bySerializedName(run.binding().role());var context=HiredWorkSession.create(helper.getLevel(),f.worker,role).context();
            if(role==HiredVillagerRole.COMBAT)new com.jvn.villagerretaliation.interaction.work.CombatWorker().tick(helper.getLevel(),f.worker,f.player,context);
            else new com.jvn.villagerretaliation.interaction.work.CourierWorker().tick(helper.getLevel(),f.worker,f.player,context);
            if(run.completed()){
                if(role==HiredVillagerRole.COMBAT){helper.assertValueEqual(run.committedUnits("patrol"),4,"post, both distinct checkpoints and return are ordered actual arrivals");}
                else{
                    helper.assertValueEqual(run.committedUnits("pickup"),4,"only four real items enter cargo");helper.assertValueEqual(run.deposited("minecraft:oak_log"),4,"all allocated cargo actually reaches B");
                    var input=(Container)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(0,1,2)));
                    helper.assertValueEqual(input.getItem(1).getCount(),5,"unallocated source logs remain untouched");helper.assertValueEqual(input.getItem(0).getCount(),64,"unrelated source items remain untouched");
                }
                helper.assertFalse(TutorialWorkService.canTick(f.worker,role),"finite route trial freezes");f.close();helper.succeed();return;
            }
            if(tick>=420){f.close();helper.fail("route did not finish: "+role+" "+context.state());return;}
            helper.runAfterDelay(1,()->stepRoute(helper,f,run,tick+1));
        }catch(RuntimeException|Error failure){f.close();throw failure;}
    }

    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void recoveryMovesOnlyReceiptMarkedCarriedOutput(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            f.work();var inventory=HiredJobInventory.getJobInventory(f.worker);
            var original=inventory.collectOutputItems().stream().filter(item->item.stack().is(Items.WHEAT)).findFirst().orElseThrow();
            var actual=inventory.removeItem(original.slot(),original.stack().getCount());
            int carried=HiredJobInventory.MAIN_GRID_START+5,own=carried+1;
            inventory.setItem(carried,actual);inventory.markPlayerPlacedSupply(carried);
            inventory.setItem(own,new ItemStack(Items.WHEAT,12));inventory.markPlayerPlacedSupply(own);
            helper.assertTrue(TutorialOutputService.recoverCarried(f.worker)>=1,"actual receipt-marked wheat and any real spare harvest drops move to output cargo");
            helper.assertValueEqual(inventory.getItem(own).getCount(),12,"matching player-owned wheat is never reclaimed or credited");
            f.deliver();helper.assertTrue(f.run.completed(),"the original recovered output completes its existing allowance");helper.succeed();
        }
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void builderGrantIsOnceOnlyAndFootprintNeedsAnActualCommittedPreview(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.BUILDER,VillagerProfession.MASON);
            helper.assertValueEqual(TutorialBuilderPlan.blueprint(f.player,run),TutorialProvisionService.Delivery.DELIVERED,"sponsored blueprint purchase has no charge");
            var blueprint=f.player.getInventory().items.stream().filter(stack->com.jvn.villagerretaliation.item.ConstructionBlueprintItem.previewData(stack).isPresent()).findFirst().orElseThrow();
            var check=new WorkStatePredicate(TutorialService.questId(HiredVillagerRole.BUILDER).toString(),HiredVillagerRole.BUILDER,"blueprint","",null,1,true);
            helper.assertFalse(check.evaluate(helper.getLevel(),f.player).matches(),"merely receiving the blueprint is not placing its preview");
            var plan=TutorialBuilderPlan.plan(helper.getLevel(),net.minecraft.world.level.block.Rotation.NONE).orElseThrow();
            com.jvn.villagerretaliation.item.ConstructionBlueprintItem.updatePlacement(blueprint,helper.getLevel(),plan,helper.absolutePos(new BlockPos(2,1,3)),net.minecraft.world.level.block.Rotation.NONE);
            helper.assertTrue(check.evaluate(helper.getLevel(),f.player).matches(),"real committed blueprint placement can acknowledge the footprint step");
            helper.assertValueEqual(TutorialBuilderPlan.blueprint(f.player,run),TutorialProvisionService.Delivery.ALREADY_DELIVERED,"repeated blueprint requests do not duplicate the once-only project");
            helper.assertValueEqual(VillagerCurrencyPayment.count(f.player),0,"blueprint and setup take no currency");
            helper.assertFalse(run.completed(),"preview and menu setup are not completed construction");helper.succeed();
        }
    }
    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void acknowledgementsReadCommittedSetupAndRejectAnotherTutor(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var context=VillagerInteractionService.createDialogueContext(helper.getLevel(),f.player,f.worker);
            helper.assertValueEqual(TutorialLessonCheckpoints.acknowledge(context,f.run,"step_2_sequence_1"),"","existing correct Supplies and accessible hoe count as setup");
            helper.assertTrue(f.run.snapshot().learned().contains("step_2_sequence_1"),"checkpoint persists its explicit acknowledgement");
            var other=helper.spawn(EntityType.VILLAGER,new BlockPos(1,1,1));
            var wrong=VillagerInteractionService.createDialogueContext(helper.getLevel(),f.player,other);
            helper.assertFalse(TutorialLessonCheckpoints.acknowledge(wrong,f.run,"step_3_sequence_1").isEmpty(),"another nearby tutor cannot acknowledge the bound worker's step");
            helper.assertFalse(f.run.snapshot().learned().contains("step_3_sequence_1"),"wrong tutor grants no progress");helper.succeed();
        }
    }

    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void ownerCheckpointRecoversAnOlderWorldLedgerWithoutNewAllowance(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var original=TutorialSavedData.get(helper.getLevel());
            var older=original.save(new net.minecraft.nbt.CompoundTag(),helper.getLevel().registryAccess());
            f.work();f.deliver();helper.assertTrue(f.run.completed(),"actual work is earned before simulating older SavedData");
            var restored=TutorialSavedData.load(older,helper.getLevel().registryAccess());restored.restoreOnlineOwnerCopies(helper.getLevel().getServer());
            var recovered=restored.find(f.player.getUUID(),f.run.snapshot().tutorial()).orElseThrow();
            helper.assertTrue(recovered.completed(),"owner's checkpoint retains newer committed completion");
            helper.assertValueEqual(recovered.trial(),f.run.trial(),"recovery keeps original trial identity");
            helper.assertValueEqual(recovered.committedUnits("harvest"),1,"recovery retains spent productive budget");
            helper.assertFalse(restored.reserve(new TutorialRun(UUID.randomUUID(),f.run.snapshot().tutorial(),f.run.binding())),"older world data cannot issue a second allowance");
            helper.assertTrue(f.run.stop(f.run.binding()),"earned Stop choice recorded");
            HiredVillagerContractService.stopTutorialContract(helper.getLevel(),f.worker,f.player.getUUID(),f.run.binding().assignment(),f.run.trial());
            helper.assertFalse(TutorialWorkService.isTrial(f.worker),"ended tutorial does not interfere with ordinary villager behavior");helper.succeed();
        }
    }

    @GameTest(template="empty",timeoutTicks=100,batch="clipboard")
    public static void replacementKeepsAllowanceAndRetiresTheOldAssignmentOnReturn(GameTestHelper helper)throws Exception{
        try(var f=fixture(helper)){
            var run=rebind(f,HiredVillagerRole.NITWIT,VillagerProfession.NITWIT);
            run.activate(run.binding(),new TutorialRun.Scope(Map.of("report",java.util.Set.of(f.worker.getUUID().toString())),Map.of("report",1),Map.of("report",1),""));run.pause();
            var replacement=helper.spawn(EntityType.VILLAGER,new BlockPos(1,1,1));replacement.setNoAi(true);replacement.setVillagerData(replacement.getVillagerData().setProfession(VillagerProfession.NITWIT));
            var contract=HireContract.tutorial(f.player.getUUID(),helper.getLevel().getGameTime(),HiredVillagerRole.NITWIT,run.trial());
            var old=run.binding();helper.assertTrue(run.replaceWorker(old,replacement.getUUID(),replacement.getUUID(),contract.id(),old.task(),old.dimension(),true),"explicit replacement keeps its existing trial and task");
            TutorialSavedData.get(helper.getLevel()).setDirty();HireContractLifecycle.begin(helper.getLevel(),replacement,contract);
            com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(replacement).putBoolean("Enabled",true);run.resume(run.binding(),true);
            TutorialWorkService.reconcileContinuation(f.worker);
            helper.assertFalse(TutorialWorkService.isTrial(f.worker),"a returned old worker's stale trial is retired without touching its replacement");
            new com.jvn.villagerretaliation.interaction.work.NitwitWorker().tick(helper.getLevel(),replacement,f.player,HiredWorkSession.create(helper.getLevel(),replacement,HiredVillagerRole.NITWIT).context());
            helper.assertValueEqual(run.committedUnits("report"),1,"replacement issues only the one remaining real report");helper.assertTrue(run.completed(),"replacement retains completion");replacement.discard();helper.succeed();
        }
    }

    private static Fixture fixture(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos soil = helper.absolutePos(new BlockPos(2, 1, 2)), crop = soil.offset(1, 1, 0);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 5; z++) level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
        BlockPos supplyPos = helper.absolutePos(new BlockPos(0, 1, 2)), outputPos = helper.absolutePos(new BlockPos(5, 1, 2));
        level.setBlock(supplyPos, Blocks.CHEST.defaultBlockState(), 3); level.setBlock(outputPos, Blocks.CHEST.defaultBlockState(), 3);
        Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.FARMER));
        worker.setNoAi(true);
        var contract = HireContract.tutorial(player.getUUID(), level.getGameTime(), HiredVillagerRole.FARMING, UUID.randomUUID());
        var run = new TutorialRun(contract.tutorialId().orElseThrow(), TutorialService.questId(HiredVillagerRole.FARMING).toString(),
                new TutorialRun.Binding(player.getUUID(), worker.getUUID(), worker.getUUID(), contract.id(), UUID.randomUUID(), UUID.randomUUID(), 0,
                        "farming", level.dimension().location().toString()));
        TutorialSavedData.get(level).reserve(run); HireContractLifecycle.begin(level, worker, contract);
        AssignedStorageService.assign(player, worker, List.of(new AssignedStorageService.StoragePosition(level.dimension(), supplyPos)), "supply");
        AssignedStorageService.assign(player, worker, List.of(new AssignedStorageService.StoragePosition(level.dimension(), outputPos)), "output");
        HiredVillagerWorkService.setWorkArea(player, level, worker, soil, crop);
        var inventory = HiredJobInventory.getJobInventory(worker);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.WOODEN_HOE));
        inventory.setItem(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.WHEAT_SEEDS, 2));
        inventory.markPlayerPlacedSupply(HiredJobInventory.MAIN_GRID_START);
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.FARMING);
        session.state().putString(HiredFarmingOptions.CROP_TAG, "WHEAT");
        session.state().putBoolean(HiredFarmingOptions.TILL_SOIL_TAG, true);
        session.state().putBoolean(HiredFarmingOptions.PLANT_CROPS_TAG, true);
        session.state().putBoolean(HiredFarmingOptions.HARVEST_CROPS_TAG, true);
        session.state().putBoolean("Enabled", true);
        HiredWorkSession.invalidate(worker);
        var plan = TutorialFarmingPlan.inspect(level, worker);
        helper.assertTrue(plan.scope() != null, "fixture passes real preflight: " + plan.blocker());
        run.activate(run.binding(), plan.scope());
        return new Fixture(helper, player, worker, run, (Container) level.getBlockEntity(outputPos), soil, crop, outputPos);
    }
    private record Fixture(GameTestHelper helper, ServerPlayer player, Villager worker, TutorialRun run, Container output,
            BlockPos soil, BlockPos crop, BlockPos outputPos) implements AutoCloseable {
        void tick(BlockPos at) {
            worker.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            var context = HiredWorkSession.create(helper.getLevel(), worker, HiredVillagerRole.FARMING).context();
            new FarmingWorker().tick(helper.getLevel(), worker, player, context);
        }
        void work() { tick(soil.above()); tick(soil.above()); tick(crop); }
        void deliver() { for (int i = 0; i < 8 && !run.completed(); i++) tick(outputPos.west()); }
        @Override public void close() {
            helper.getLevel().getServer().getPlayerList().remove(player);
            worker.discard();
        }
    }
}
