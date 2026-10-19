package com.jvn.villagerretaliation.progress;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_boundary")
@PrefixGameTestTemplate(false)
public final class PlayerHistoryGameTests {
    private PlayerHistoryGameTests() { }

    @GameTest(template = "empty", batch = "player_history")
    public static void discoveryListsShareWorldHistoryWithoutSharingPersonalHistory(GameTestHelper helper) {
        var first = helper.makeMockServerPlayerInLevel();
        var second = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        var data = PlayerHistorySavedData.get(level);
        helper.assertTrue(data.dimensions(first.getUUID()).isEmpty(), "old travel is not inferred from login");
        PlayerHistoryService.dimensionChanged(new PlayerEvent.PlayerChangedDimensionEvent(first, Level.OVERWORLD, Level.NETHER));
        PlayerHistoryService.dimensionChanged(new PlayerEvent.PlayerChangedDimensionEvent(second, Level.OVERWORLD, Level.END));
        var both = condition("player_dimension_visited", "dimensions", "minecraft:the_nether", "minecraft:the_end");
        helper.assertFalse(DialogueCondition.matchesAll(DialogueContext.forPlayer(first), List.of(both)), "All needs every personal visit");
        var any = parse("{\"type\":\"player_dimension_visited\",\"dimensions\":[\"minecraft:the_nether\",\"minecraft:the_end\"],\"all\":false}");
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(first), List.of(any)), "Any accepts a partial personal history");
        var world = condition("world_dimension_unlocked", "dimensions", "minecraft:the_nether", "minecraft:the_end");
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(first), List.of(world)), "different players contribute to the world");
        var saved = VillagerConditionAccess.forSaved(level, first, null, null);
        helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, world), DialogueCondition.ConditionOutcome.MATCHED,
                "saved-provider quests use the same world history");
        PlayerHistoryService.tick(first);
        var biome = level.getBiome(first.blockPosition()).unwrapKey().orElseThrow().location();
        helper.assertTrue(data.biomes(first.getUUID()).contains(biome), "server observation records the current biome");
        var advancement = level.getServer().getAdvancements().get(id("minecraft:story/mine_stone"));
        PlayerHistoryService.advancementEarned(new AdvancementEvent.AdvancementEarnEvent(second, advancement));
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(first),
                List.of(condition("world_advancement", "advancements", advancement.id().toString()))), "world advancements are shared");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "player_history")
    public static void vanillaTotalsAndInclusiveRangesWorkWithoutChangingQuestCounters(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.awardStat(Stats.ENTITY_KILLED.get(EntityType.ENDER_DRAGON), 1);
        player.awardStat(Stats.ENTITY_KILLED_BY.get(EntityType.PLAYER), 1);
        player.awardStat(Stats.ITEM_CRAFTED.get(Items.CRAFTING_TABLE), 3);
        player.awardStat(Stats.ITEM_USED.get(Items.BOW), 2);
        player.awardStat(Stats.DEATHS, 4);
        player.awardStat(Stats.PLAYER_KILLS, 2);
        var checks = List.of(condition("player_killed_entity", "entities", "minecraft:ender_dragon"),
                condition("player_killed_by_entity", "entities", "minecraft:player"),
                condition("player_crafted_item", "items", "minecraft:crafting_table"),
                condition("player_used_item", "items", "minecraft:bow"),
                parse("{\"type\":\"player_deaths\",\"min\":4,\"max\":4}"),
                parse("{\"type\":\"player_kills\",\"min\":2,\"max\":2}"));
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), checks), "existing vanilla totals satisfy the shared checks");
        var saved = VillagerConditionAccess.forSaved(helper.getLevel(), player, null, null);
        for (var check : checks) helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, check), DialogueCondition.ConditionOutcome.MATCHED,
                "saved-provider quests agree with dialogue");
        var missing = condition("player_killed_entity", "entities", "absent_mod:mob");
        helper.assertValueEqual(ConditionEvaluator.evaluate(VillagerConditionAccess.forPlayer(player), null, new DialogueCondition.Not(missing)),
                DialogueCondition.ConditionOutcome.UNKNOWN, "missing statistic targets cannot pass through negation");
        helper.assertFalse(DialogueCondition.matchesAll(DialogueContext.forPlayer(player),
                List.of(parse("{\"type\":\"player_deaths\",\"min\":5}"))), "lower bounds are enforced");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "player_history")
    public static void registryDeathCausesPreserveKillerCreditAndLatestDeath(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var data = PlayerHistorySavedData.get(helper.getLevel());
        helper.assertTrue(PlayerHistoryService.replacements(player).values().stream().allMatch(String::isEmpty), "no death has empty replacements");
        var skeleton = helper.spawn(EntityType.SKELETON, 2, 1, 2);
        skeleton.setNoAi(true);
        player.setLastHurtByMob(skeleton);
        PlayerHistoryService.died(new LivingDeathEvent(player, player.damageSources().fall()));
        var fall = data.latestDeath(player.getUUID());
        helper.assertValueEqual(fall.damageType(), id("minecraft:fall"), "fatal damage stays a fall");
        helper.assertValueEqual(fall.killerType(), id("minecraft:skeleton"), "combat credit remains separate");
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), List.of(parse(
                "{\"type\":\"player_last_death\",\"damage_types\":[\"minecraft:fall\",\"minecraft:drown\"],\"entities\":[\"minecraft:skeleton\"]}"))),
                "latest-death filters match the same death with alternative causes");
        var custom = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, id("player_history:arcane")));
        PlayerHistoryService.died(new LivingDeathEvent(player, new DamageSource(custom)));
        helper.assertTrue(data.deathCauses(player.getUUID()).containsAll(Set.of(id("minecraft:fall"), id("player_history:arcane"))),
                "registry-defined mod damage accumulates alongside vanilla damage");
        helper.assertValueEqual(data.latestDeath(player.getUUID()).damageType(), id("player_history:arcane"), "latest death replaces the prior cause");
        var cancelled = new LivingDeathEvent(player, player.damageSources().drown());
        cancelled.setCanceled(true);
        PlayerHistoryService.died(cancelled);
        helper.assertFalse(data.deathCauses(player.getUUID()).contains(id("minecraft:drown")), "cancelled deaths do not count");
        var values = DialoguePlaceholders.base(DialogueContext.forPlayer(player));
        helper.assertValueEqual(values.get("last_death_killer"), skeleton.getName().getString(), "dialogue uses the saved killer");
        helper.assertValueEqual(values.get("last_death_dimension"), player.serverLevel().dimension().location().toString(), "dialogue uses the saved dimension");
        helper.assertTrue(data.hasPendingDeath(player.getUUID()), "dialogue does not consume the pending quest notification");
        skeleton.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "player_history")
    public static void latestDeathReadsDoNotConsumePendingRespawnDelivery(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var data = PlayerHistorySavedData.get(helper.getLevel());
        var death = new PlayerHistorySavedData.Death(id("minecraft:drown"), null, null, "",
                player.serverLevel().dimension().location(), BlockPos.ZERO, 42, "Drowned");
        data.recordDeath(player.getUUID(), death);
        var restored = PlayerHistorySavedData.load(data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.hasPendingDeath(player.getUUID()), "unrespawned disconnect survives reload");
        player.setHealth(0);
        PlayerHistoryService.tick(player);
        helper.assertTrue(data.hasPendingDeath(player.getUUID()), "dead players cannot trigger quest actions");
        player.setHealth(20);
        PlayerHistoryService.tick(player);
        helper.assertFalse(data.hasPendingDeath(player.getUUID()), "living respawn consumes the notification");
        PlayerHistoryService.tick(player);
        helper.assertValueEqual(data.latestDeath(player.getUUID()), death, "repeated ticks retain the dialogue snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "player_history")
    public static void nativeDeathsAndProjectilesReachTheRegisteredHistoryListener(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(player.connection.getConnection());
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.die(player.damageSources().drown());
        var data = PlayerHistorySavedData.get(helper.getLevel());
        helper.assertValueEqual(data.latestDeath(player.getUUID()).damageType(), id("minecraft:drown"), "native drowning invokes the registered death listener");
        var victim = helper.makeMockServerPlayerInLevel();
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(victim.connection.getConnection());
        victim.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var killer = helper.makeMockServerPlayerInLevel();
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(killer.connection.getConnection());
        victim.setLastHurtByPlayer(killer);
        victim.die(victim.damageSources().playerAttack(killer));
        var pvp = data.latestDeath(victim.getUUID());
        helper.assertValueEqual(pvp.killerType(), id("minecraft:player"), "native PvP captures player credit");
        helper.assertValueEqual(pvp.killerId(), killer.getUUID(), "PvP records the credited player identity");
        var target = helper.makeMockServerPlayerInLevel();
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(target.connection.getConnection());
        target.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var skeleton = helper.spawn(EntityType.SKELETON, 2, 1, 2);
        skeleton.setNoAi(true);
        var arrow = helper.spawn(EntityType.ARROW, 3, 1, 2);
        target.setLastHurtByMob(skeleton);
        target.die(target.damageSources().arrow(arrow, skeleton));
        var shot = data.latestDeath(target.getUUID());
        helper.assertValueEqual(shot.damageType(), id("minecraft:arrow"), "projectile damage remains an arrow");
        helper.assertValueEqual(shot.killerType(), id("minecraft:skeleton"), "projectile credit identifies the shooter");
        arrow.discard();
        skeleton.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "player_history_artifacts")
    public static void questSchemaArtifactIncludesTheProgressConditions(GameTestHelper helper) {
        com.jvn.villagerretaliation.gametest.VillagerQuestGameTests.questV2SchemaArtifactMatchesJavaExport(helper);
    }

    @GameTest(template = "empty", batch = "player_history_artifacts")
    public static void registryArtifactIncludesTheRegisteredProgressConditions(GameTestHelper helper) {
        com.jvn.villagerretaliation.gametest.VillagerQuestGameTests.questRegistryMetadataArtifactMatchesJavaExport(helper);
    }

    private static DialogueCondition condition(String type, String field, String... ids) {
        var value = new com.google.gson.JsonObject();
        value.addProperty("type", type);
        var entries = new com.google.gson.JsonArray();
        for (String id : ids) entries.add(id);
        value.add(field, entries);
        return DialogueCondition.readObjects(id("player_history:test"), "test", List.of(value), null).getFirst();
    }

    private static DialogueCondition parse(String json) {
        return DialogueCondition.readObjects(id("player_history:test"), "test", List.of(JsonParser.parseString(json)), null).getFirst();
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
