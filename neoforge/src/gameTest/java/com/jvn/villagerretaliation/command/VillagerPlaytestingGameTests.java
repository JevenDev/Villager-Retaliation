package com.jvn.villagerretaliation.command;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.settlement.SettlementMembershipService;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_playtesting")
@PrefixGameTestTemplate(false)
public final class VillagerPlaytestingGameTests {
    private VillagerPlaytestingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void explicitSpawnsJoinManagedVillageAndKeepExistingAllegiance(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2)).offset(1_200_000, 0, 1_200_000);
        level.getChunkAt(center);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Spawn Camp");
        SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        for (MobSpawnType type : List.of(MobSpawnType.COMMAND, MobSpawnType.SPAWN_EGG, MobSpawnType.DISPENSER)) {
            Villager villager = EntityType.VILLAGER.spawn(level, center, type);
            helper.assertTrue(villager != null, "the explicit spawn enters the level");
            helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(level, villager).orElseThrow(), id,
                    "explicit spawns inside a managed village become residents");
            helper.assertValueEqual(VillageAllegianceApi.get(villager).orElseThrow().assignmentSource(),
                    AllegianceAssignmentSource.COMMAND, "explicit spawn provenance survives assignment");
            helper.assertTrue(registry.residentRoster(id, level.getGameTime()).stream()
                    .anyMatch(resident -> resident.id().equals(villager.getUUID())), "the census includes the spawned villager");
            com.jvn.villagerretaliation.allegiance.VillageAllegianceService.onEntityJoinLevel(
                    new EntityJoinLevelEvent(villager, level, true));
            helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(level, villager).orElseThrow(), id,
                    "reloading retains the assigned village");
            villager.discard();
        }
        var source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atBottomCenterOf(center)).withPermission(4);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("NoAI", true);
        Villager summoned = (Villager) net.minecraft.server.commands.SummonCommand.createEntity(
                source, EntityType.VILLAGER.builtInRegistryHolder(), Vec3.atBottomCenterOf(center), tag, false);
        helper.assertTrue(VillageAllegianceApi.canonicalPrimary(level, summoned).filter(id::equals).isPresent(),
                "NBT summons join the camp even without finalizeSpawn: " + VillageAllegianceApi.get(summoned)
                        + " at " + summoned.blockPosition() + " expected " + center);
        summoned.discard();
        Villager outsider = EntityType.VILLAGER.create(level);
        helper.assertFalse(SettlementMembershipService.permitsAutomaticMembership(
                level, outsider, id, AllegianceAssignmentSource.MIGRATION, List.of()),
                "migration cannot automatically enroll an outsider");
        var other = registry.create(level.getGameTime(), level.dimension().location(), center.east(256), "Existing Home");
        outsider.moveTo(Vec3.atBottomCenterOf(center));
        outsider.finalizeSpawn(level, level.getCurrentDifficultyAt(center), MobSpawnType.COMMAND, null);
        VillageAllegianceApi.assignKnown(level, outsider, other, AllegianceAssignmentSource.ADMIN);
        helper.assertTrue(level.addFreshEntity(outsider), "a preassigned villager enters the camp");
        helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(level, outsider).orElseThrow(), other,
                "spawning never replaces an existing allegiance");
        outsider.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reputationAcceptsNamesUuidsAndMultipleTargetSelectors(GameTestHelper helper) throws Exception {
        Villager first = villager(helper, "Playtest Alice", 1);
        Villager second = villager(helper, "Playtest Bob", 3);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                helper.getLevel(), new GameProfile(UUID.randomUUID(), "PlaytestRep"));
        player.setPos(first.position());
        var source = source(helper, first).withEntity(player);
        var dispatcher = dispatcher(helper);
        helper.assertValueEqual(dispatcher.execute(
                "vr admin villager reputation set \"playtest alice\" @s[type=minecraft:player] 17", source), 1,
                "quoted nearby names are case insensitive");
        helper.assertValueEqual(VillagerReputationManager.getReputation(helper.getLevel(), first, player.getUUID()), 17,
                "name targeting changes the selected villager");
        helper.assertValueEqual(dispatcher.execute(
                "vr admin villager reputation set " + second.getUUID() + " @s[type=minecraft:player] 18", source), 1,
                "UUID targeting remains available");
        first.addTag("vr_playtest_reputation");
        second.addTag("vr_playtest_reputation");
        helper.assertValueEqual(dispatcher.execute(
                "vr admin villager reputation set @e[tag=vr_playtest_reputation] @s[type=minecraft:player] 19", source), 2,
                "reputation retains multiple-target selectors");
        helper.assertValueEqual(VillagerReputationManager.getReputation(helper.getLevel(), first, player.getUUID()), 19,
                "the first selector match changes");
        helper.assertValueEqual(VillagerReputationManager.getReputation(helper.getLevel(), second, player.getUUID()), 19,
                "the second selector match changes");
        var pig = helper.spawn(EntityType.PIG, 4, 2, 4);
        pig.addTag("vr_playtest_reputation");
        helper.assertValueEqual(dispatcher.execute(
                "vr admin villager reputation set @e[tag=vr_playtest_reputation] @s[type=minecraft:player] 20", source), 0,
                "mixed entity selections remain invalid");
        helper.assertValueEqual(VillagerReputationManager.getReputation(helper.getLevel(), first, player.getUUID()), 19,
                "invalid selections do not partially change reputation");
        first.discard();
        second.discard();
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void namesAndSelectorsWorkAcrossVillagerCommands(GameTestHelper helper) throws Exception {
        Villager first = villager(helper, "Playtest Clara", 1);
        Villager second = villager(helper, "Playtest Daniel", 3);
        var source = source(helper, first);
        var dispatcher = dispatcher(helper);
        for (String command : List.of(
                "vr admin villager profile get \"Playtest Clara\"",
                "vr admin villager skill get \"Playtest Clara\"",
                "vr admin villager allegiance inspect \"Playtest Clara\"",
                "vr admin villager relationship set \"Playtest Clara\" \"Playtest Daniel\" crush")) {
            helper.assertValueEqual(dispatcher.execute(command, source), 1, "nearby names resolve across villager commands");
        }
        first.addTag("vr_playtest_single");
        helper.assertValueEqual(dispatcher.execute(
                "vr admin villager profile get @e[tag=vr_playtest_single,limit=1]", source), 1,
                "unquoted selectors work in commands that previously accepted name strings");
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allTargetArgumentsCompleteNamesAndSerialize(GameTestHelper helper) throws Exception {
        Villager villager = villager(helper, "Playtest Evelyn", 1);
        var source = source(helper, villager);
        var dispatcher = dispatcher(helper);
        for (String prefix : List.of(
                "vr admin villager reputation set ",
                "vr admin villager relationship set ",
                "vr admin villager relationship set \"Playtest Evelyn\" ",
                "vr admin villager allegiance inspect ",
                "vr admin villager profile get ",
                "vr admin villager skill get ",
                "vr admin quest start test ",
                "vr admin scene rebind test actor ",
                "vr admin debug hired inspect ",
                "vr companion stories ",
                "vr companion entrust ",
                "vr companion dimensionreturn ",
                "vr escort follow ")) {
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(prefix, source)).get();
            helper.assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("\"Playtest Evelyn\"")),
                    "all villager target arguments suggest nearby names");
        }
        for (String command : List.of("vr companion stories ", "vr companion equipment ")) {
            String suffix = command.endsWith("equipment ") ? " true" : "";
            var parsed = dispatcher.parse(command + "\"Playtest Evelyn\"" + suffix, source).getContext().build(command);
            helper.assertValueEqual(VillagerRetaliationCommands.namedEntityId(parsed, "villager"), villager.getUUID(),
                    "identity commands resolve nearby names");
            UUID unloaded = UUID.randomUUID();
            parsed = dispatcher.parse(command + unloaded + suffix, source).getContext().build(command);
            helper.assertValueEqual(VillagerRetaliationCommands.namedEntityId(parsed, "villager"), unloaded,
                    "identity commands retain UUID support for unloaded villagers");
        }
        var argument = new VillagerTargetArgument();
        helper.assertTrue(ArgumentTypeInfos.unpack(argument).instantiate(null) instanceof VillagerTargetArgument,
                "the argument is registered for command tree synchronization");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nameLookupStaysLocalAndSingleSelectorsStaySingle(GameTestHelper helper) throws Exception {
        Villager near = villager(helper, "Playtest Farah", 1);
        Villager far = villager(helper, "Playtest Farah", 3);
        far.moveTo(near.position().add(80.0D, 0.0D, 0.0D));
        var source = source(helper, near);
        var dispatcher = dispatcher(helper);
        near.discard();
        helper.assertValueEqual(dispatcher.execute("vr admin villager profile get \"Playtest Farah\"", source), 0,
                "name lookup does not reach distant villagers");
        Villager first = villager(helper, "Playtest Greta", 1);
        Villager second = villager(helper, "Playtest Helen", 3);
        first.addTag("vr_playtest_multiple");
        second.addTag("vr_playtest_multiple");
        boolean rejected = false;
        try {
            dispatcher.execute("vr admin villager profile get @e[tag=vr_playtest_multiple]", source);
        } catch (CommandSyntaxException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "single-villager commands reject multiple-target selectors");
        first.discard();
        second.discard();
        far.discard();
        helper.succeed();
    }

    private static Villager villager(GameTestHelper helper, String name, int x) {
        Villager villager = helper.spawn(EntityType.VILLAGER, x, 2, 2);
        villager.setNoAi(true);
        villager.setCustomName(Component.literal(name));
        return villager;
    }

    private static CommandSourceStack source(GameTestHelper helper, Villager villager) {
        return helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPosition(villager.position()).withPermission(4);
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }
}
