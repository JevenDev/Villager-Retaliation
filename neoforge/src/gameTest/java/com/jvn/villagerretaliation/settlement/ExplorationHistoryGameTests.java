package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.progress.EventHistorySavedData;
import com.jvn.villagerretaliation.progress.ExplorationHistorySavedData;
import com.jvn.villagerretaliation.progress.ExplorationHistoryService;
import com.jvn.villagerretaliation.progress.ProgressHistoryService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("exploration_history")
@PrefixGameTestTemplate(false)
public final class ExplorationHistoryGameTests {
    private ExplorationHistoryGameTests() { }

    @GameTest(template = "empty", batch = "exploration_history")
    public static void actualLoadedStructureBoundsTrackEntriesAndDistinctInstancesWithoutLoadingReferences(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var first = helper.absolutePos(new BlockPos(2048, 2, 2048));
        var second = first.offset(32, 0, 0);
        var type = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("minecraft:monument"));
        for (var position : List.of(first, second)) {
            var chunk = level.getChunk(position);
            var start = new StructureStart(type, chunk.getPos(), 0,
                    new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(position))));
            chunk.setStartForStructure(type, start);
            chunk.addReferenceForStructure(type, chunk.getPos().toLong());
        }
        player.setPos(first.east().getCenter());
        ExplorationHistoryService.tick(player);
        var data = ExplorationHistorySavedData.get(level);
        helper.assertTrue(data.structures(player.getUUID()).isEmpty(), "nearby structures do not count as visited");
        player.setPos(first.getCenter());
        ExplorationHistoryService.tick(player);
        ExplorationHistoryService.tick(player);
        player.setPos(first.east().getCenter());
        ExplorationHistoryService.tick(player);
        player.setPos(first.getCenter());
        ExplorationHistoryService.tick(player);
        player.setPos(second.getCenter());
        ExplorationHistoryService.tick(player);
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), List.of(parse("""
                {"type":"player_structure_visited","structures":["minecraft:monument"],"min":3,"max":3,"max_age_ticks":0}
                """), parse("""
                {"type":"player_destination_count","destination":"structures","min":2,"max":2}
                """), parse("""
                {"type":"player_destination_count","destination":"structure_types","min":1,"max":1}
                """))), "three entries into two monuments represent one structure type");
        var chunk = level.getChunk(second);
        var unloaded = new ChunkPos(chunk.getPos().x + 512, chunk.getPos().z + 512);
        chunk.addReferenceForStructure(type, unloaded.toLong());
        helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.x, unloaded.z) == null, "fixture reference is unloaded");
        int loaded = level.getChunkSource().getLoadedChunksCount();
        ExplorationHistoryService.clear(player.getUUID());
        ExplorationHistoryService.tick(player);
        helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), loaded, "visit detection never loads structure reference chunks");
        helper.assertValueEqual(EventHistorySavedData.get(level).query(level, "player", null, player.getUUID(),
                ProgressHistoryService.event("structure_visited"), "minecraft:monument").count(), 3L, "entry events share the persistent count and timing system");
        player.setPos(second.east().getCenter());
        ExplorationHistoryService.tick(player);
        player.setPos(second.getCenter());
        ExplorationHistoryService.tick(player);
        helper.assertValueEqual(EventHistorySavedData.get(level).query(level, "player", null, player.getUUID(),
                ProgressHistoryService.event("structure_visited"), "minecraft:monument").count(), 4L,
                "an unrelated unloaded reference cannot hide a known exit and re-entry");
        var restored = ExplorationHistorySavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(restored.structures(player.getUUID()).equals(data.structures(player.getUUID())), "physical destination identities and visits survive reload");
        var other = helper.makeMockServerPlayerInLevel();
        helper.assertFalse(DialogueCondition.matchesAll(DialogueContext.forPlayer(other), List.of(parse("""
                {"type":"player_structure_visited","structures":["minecraft:monument"]}
                """))), "exploration remains personal");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "exploration_history")
    public static void realSettlementTerritoryAndSavedIssuerContextUseDurableVisitHistory(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var center = helper.absolutePos(new BlockPos(4096, 2, 4096));
        var outside = center.offset(512, 0, 512);
        level.getChunk(center); level.getChunk(outside);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var settlement = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, settlement, player.getUUID(), center, level.getGameTime()).orElseThrow();
        player.setPos(center.getCenter());
        ExplorationHistoryService.tick(player);
        ExplorationHistoryService.clear(player.getUUID());
        ExplorationHistoryService.tick(player);
        player.setPos(outside.getCenter());
        ExplorationHistoryService.tick(player);
        player.setPos(center.getCenter());
        ExplorationHistoryService.tick(player);
        player.setPos(outside.getCenter());
        ExplorationHistoryService.tick(player);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.setIssuer(UUID.randomUUID(), "Resident", "minecraft:none", 1, level.dimension(), center, "");
        var saved = VillagerConditionAccess.forSaved(level, player, null, progress);
        helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, parse("""
                {"type":"player_settlement_visited","settlement":"current","min":2,"max":2}
                """)), ConditionOutcome.MATCHED, "an unloaded issuer uses the bound town rather than the player's current location");
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), List.of(parse("""
                {"type":"player_settlement_visited","settlement":"id","id":"%s","min":2,"max":2}
                """.formatted(town.id().value())), parse("""
                {"type":"player_destination_count","destination":"settlements","min":1,"max":1}
                """))), "returning twice does not add another distinct settlement");
        helper.assertValueEqual(ConditionEvaluator.evaluate(VillagerConditionAccess.forPlayer(player), null, new DialogueCondition.Not(parse("""
                {"type":"player_settlement_visited","settlement":"current"}
                """))), ConditionOutcome.UNKNOWN, "an unavailable current settlement cannot succeed through negation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "exploration_history")
    public static void villageAliasesCollapseDistinctCountsAndPreserveBothVisitTimelines(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var first = registry.create(0, level.dimension().location(), helper.absolutePos(new BlockPos(8192, 1, 8192)), "");
        var second = registry.create(0, level.dimension().location(), helper.absolutePos(new BlockPos(8208, 1, 8208)), "");
        var data = ExplorationHistorySavedData.get(level);
        var dimension = level.dimension().location();
        data.observeSettlement(level, player.getUUID(), new ExplorationHistorySavedData.SettlementDestination(dimension, first.value()), 1);
        data.observeSettlement(level, player.getUUID(), null, 2);
        data.observeSettlement(level, player.getUUID(), new ExplorationHistorySavedData.SettlementDestination(dimension, second.value()), 3);
        helper.assertTrue(registry.merge(first, second), "fixture village identities merge");
        helper.assertFalse(data.observeSettlement(level, player.getUUID(), new ExplorationHistorySavedData.SettlementDestination(dimension, first.value()), 4),
                "an alias change while inside is not a new visit");
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), List.of(parse("""
                {"type":"player_destination_count","destination":"settlements","min":1,"max":1}
                """), parse("""
                {"type":"player_settlement_visited","settlement":"id","id":"%s","min":2,"max":2}
                """.formatted(first.value())))), "canonical history combines visit counts without double-counting destinations");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "exploration_history")
    public static void inventoryAndCursorTransactionsRecordPossessionWithoutCreditingContainerOrRecipePreviews(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        player.initInventoryMenu();
        var external = new SimpleContainer(27);
        external.setItem(0, new ItemStack(Items.DIAMOND));
        external.setItem(2, new ItemStack(Items.NETHER_STAR));
        var menu = ChestMenu.threeRows(1, player.getInventory(), external);
        player.containerMenu = menu;
        var data = ExplorationHistorySavedData.get(level);
        ExplorationHistoryService.observeInventory(player);
        helper.assertFalse(data.possessed(player.getUUID()).contains(id("minecraft:nether_star")), "viewing a container does not establish ownership");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && player.getInventory().isEmpty(), "the item was acquired and returned between ticks");
        helper.assertTrue(data.possessed(player.getUUID()).contains(id("minecraft:diamond")), "completed cursor transfers are remembered immediately");
        player.containerMenu = player.inventoryMenu;
        player.inventoryMenu.getSlot(0).set(new ItemStack(Items.ELYTRA));
        player.inventoryMenu.broadcastChanges();
        ExplorationHistoryService.observeInventory(player);
        helper.assertFalse(data.possessed(player.getUUID()).contains(id("minecraft:elytra")), "crafting result previews are excluded");
        player.inventoryMenu.getSlot(0).set(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.inventoryMenu.broadcastChanges();
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
        helper.assertTrue(data.possessed(player.getUUID()).contains(id("minecraft:totem_of_undying")), "inventory slot notifications survive immediate removal");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        ExplorationHistoryService.observeInventory(player);
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), List.of(parse("""
                {"type":"player_item_possessed","items":["minecraft:diamond","minecraft:totem_of_undying","minecraft:iron_helmet","minecraft:shield"]}
                """))), "inventory, equipment and offhand discoveries share the condition system");
        var restored = ExplorationHistorySavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(restored.possessed(player.getUUID()).contains(id("minecraft:diamond")), "historical ownership survives save and reload after loss");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "exploration_history")
    public static void actualSuccessfulPickupsRecordDiscoveryAndMissingModTargetsStayUnknown(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var item = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.EMERALD, 3));
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);
        item.playerTouch(player);
        helper.assertTrue(ExplorationHistorySavedData.get(helper.getLevel()).possessed(player.getUUID()).contains(id("minecraft:emerald")),
                "successful vanilla pickup callbacks record possession before the next player tick");
        for (String json : List.of("""
                {"type":"player_item_possessed","items":["absent:artifact"]}
                """, """
                {"type":"player_structure_visited","structures":["absent:temple"]}
                """, """
                {"type":"player_destination_count","destination":"structures","structures":["absent:temple"],"min":0,"max":0}
                """)) helper.assertValueEqual(ConditionEvaluator.evaluate(VillagerConditionAccess.forPlayer(player), null, new DialogueCondition.Not(parse(json))),
                        ConditionOutcome.UNKNOWN, "missing mod resources stay unknown under negation");
        helper.succeed();
    }

    private static DialogueCondition parse(String json) {
        var condition = DialogueCondition.readObjects(id("test:exploration"), "test", List.of(JsonParser.parseString(json)), null).getFirst();
        if (condition instanceof DialogueCondition.Invalid invalid) throw new AssertionError(invalid.reason());
        return condition;
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
