package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementStageRulesGameTests {
    private SettlementStageRulesGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void dataPackOverridesLoadAtomicallyAndRejectInvalidRules(GameTestHelper helper) {
        var previous = rules();
        var resources = helper.getLevel().getServer().getResourceManager();
        var location = ResourceLocation.parse("villagerretaliation:settlement_stages/city.json");
        var city = previous.get(SettlementStage.CITY);
        try {
            SettlementStageRules.reload(resources);
            helper.assertValueEqual(rules(), previous, "bundled data preserves the established stage defaults");
            for (var stage : SettlementStage.values()) {
                helper.assertValueEqual(SettlementStageRules.forStage(stage), SettlementStageRules.legacy(stage),
                        "bundled capacities and services match the pre-configuration defaults");
            }
            var custom = json(city);
            custom.addProperty("radius", 320);
            custom.addProperty("projects", 12);
            custom.addProperty("depots", 160);
            custom.addProperty("market_supply", 512);
            custom.addProperty("market_demand", 1_024);
            SettlementStageRules.reload(new OverrideResources(resources, location, custom.toString()));
            helper.assertValueEqual(SettlementStage.CITY.radius(), 320, "the highest-priority resource configures expansion");
            helper.assertValueEqual(SettlementStage.CITY.projects(), 12, "the resource configures project capacity");
            helper.assertValueEqual(SettlementStage.CITY.depots(), 160, "the resource configures depot capacity");
            helper.assertValueEqual(SettlementTradeService.dailySupply(SettlementStage.CITY), 512, "market supply comes from the resource");
            var valid = rules();
            custom.addProperty("radius", 80);
            expectInvalid(helper, () -> SettlementStageRules.reload(new OverrideResources(resources, location, custom.toString())));
            helper.assertValueEqual(rules(), valid, "a nonmonotonic override cannot partially replace live rules");
            custom.addProperty("radius", 512.5D);
            expectInvalid(helper, () -> SettlementStageRules.reload(new OverrideResources(resources, location, custom.toString())));
            helper.assertValueEqual(rules(), valid, "fractional capacities reject the entire reload");
            custom.addProperty("radius", 320);
            custom.add("services", JsonParser.parseString("[\"population\"]"));
            expectInvalid(helper, () -> SettlementStageRules.reload(new OverrideResources(resources, location, custom.toString())));
            helper.assertValueEqual(rules(), valid, "service overrides cannot remove base population, housing or food rules");
        } finally {
            SettlementStageRules.apply(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void customLimitsControlTerritoryDepotsAndConcurrentProjects(GameTestHelper helper) {
        var previous = rules();
        var fixture = fixture(helper);
        var custom = rules();
        custom.put(SettlementStage.CAMP, new SettlementStageRules.Limits(80, 0, 2, 0, 0, Set.of()));
        var hamlet = custom.get(SettlementStage.HAMLET);
        custom.put(SettlementStage.HAMLET, new SettlementStageRules.Limits(96, 2, hamlet.depots(), 0, 0, hamlet.services()));
        try {
            SettlementStageRules.apply(custom);
            helper.assertValueEqual(fixture.town().radius(), 80, "a live reload expands an existing town");
            var stores = area(fixture, SettlementAreaType.DEPOT, 76, 3);
            helper.assertTrue(fixture.town().putArea(fixture.leader().getUUID(), stores), "designation can use the expanded boundary");
            helper.assertFalse(fixture.town().putArea(fixture.leader().getUUID(), area(fixture, SettlementAreaType.FARM, 81, 3)),
                    "designation still rejects the configured boundary's exterior");
            for (int index = 0; index < 2; index++) {
                var depot = fixture.town().registerDepot(fixture.leader().getUUID(), stores.id(), stores.min().south(index),
                        SettlementDepotPurpose.GENERAL);
                helper.assertTrue(depot != null, "configured depot slots accept real inventories");
                depot.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
            }
            helper.assertTrue(fixture.town().registerDepot(fixture.leader().getUUID(), stores.id(), stores.min().south(2),
                    SettlementDepotPurpose.GENERAL) == null, "the next depot exceeds the configured limit");
            fixture.town().advance();
            var entry = BuilderStructureCatalog.byId(helper.getLevel().getServer(),
                    ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
            var plan = BuilderStructureScanner.scan(helper.getLevel(), entry, Rotation.NONE).orElseThrow();
            for (int index = 0; index < 3; index++) {
                var origin = fixture.town().center().offset(-40 + index * 24, 0, 20);
                var site = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Site " + index,
                        origin.offset(plan.localMin()), origin.offset(plan.localMax()), 5, true);
                helper.assertTrue(fixture.town().putArea(fixture.leader().getUUID(), site), "construction sites register");
                var builder = UUID.randomUUID();
                fixture.town().assignDuty(builder, site.id());
                var project = SettlementProject.create(fixture.town(), site.id(), builder, plan, origin,
                        UUID.randomUUID(), helper.getLevel().getGameTime());
                helper.assertValueEqual(fixture.town().startProject(fixture.leader().getUUID(), project), index < 2,
                        "the configured project count governs commissioning");
            }
            SettlementStageRules.apply(previous);
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.projects().size(), 2, "lower project capacity preserves both accepted projects");
            helper.assertValueEqual(restored.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(Items.IRON_INGOT))).sum(),
                    14, "rule changes preserve every stored item");
        } finally {
            SettlementStageRules.apply(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void earnedTerritoryAndExistingDepotsSurviveReducedRules(GameTestHelper helper) {
        var previous = rules();
        var fixture = fixture(helper);
        var expanded = rules();
        expanded.put(SettlementStage.CAMP, new SettlementStageRules.Limits(96, 0, 3, 0, 0, Set.of()));
        try {
            SettlementStageRules.apply(expanded);
            var post = area(fixture, SettlementAreaType.GUARD_POST, 90, 4);
            helper.assertTrue(fixture.town().putArea(fixture.leader().getUUID(), post), "the earned boundary contains the outlying post");
            var guard = UUID.randomUUID();
            fixture.town().assignDuty(guard, post.id());
            helper.assertTrue(fixture.town().setGuardOrder(fixture.leader().getUUID(), guard,
                    new SettlementGuardOrder(SettlementGuardOrder.Mode.DEFEND, List.of(post.min()))), "the distant guard order is valid");
            for (int index = 0; index < 3; index++) {
                var depot = fixture.town().registerDepot(fixture.leader().getUUID(), post.id(), post.min().south(index),
                        SettlementDepotPurpose.GENERAL);
                depot.setItem(0, new ItemStack(Items.BREAD, index + 1));
            }
            var smaller = rules();
            smaller.put(SettlementStage.CAMP, new SettlementStageRules.Limits(32, 0, 1, 0, 0, Set.of()));
            SettlementStageRules.apply(smaller);
            helper.assertValueEqual(fixture.town().radius(), 96, "lower limits never retract earned territory");
            helper.assertTrue(SettlementGuardOrder.within(fixture.town(), post.min()), "existing defense routes remain inside town authority");
            helper.assertTrue(fixture.town().registerDepot(fixture.leader().getUUID(), post.id(), post.min().south(3),
                    SettlementDepotPurpose.GENERAL) == null, "an over-capacity town cannot register additional depots");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.radius(), 96, "earned expansion survives a save under smaller rules");
            helper.assertValueEqual(restored.areas().get(post.id()), post, "the remote designation survives reload");
            helper.assertValueEqual(restored.guardOrders().get(guard), fixture.town().guardOrders().get(guard), "guard authority survives reload");
            helper.assertValueEqual(restored.depots().size(), 3, "lower capacity does not delete existing depots");
            helper.assertValueEqual(restored.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(Items.BREAD))).sum(),
                    6, "lower capacity does not delete inventory");
            helper.assertValueEqual(SettlementViewWindow.around(restored, post.min().getX(), post.min().getZ(), 32).radius(),
                    112, "RTS authority retains the earned boundary");
        } finally {
            SettlementStageRules.apply(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void oldTownSavesRetainTheirOriginalExpansion(GameTestHelper helper) {
        var previous = rules();
        var fixture = fixture(helper);
        var area = area(fixture, SettlementAreaType.FARM, 60, 3);
        fixture.town().putArea(fixture.leader().getUUID(), area);
        fixture.town().support().observe(0, true, false);
        fixture.town().support().observe(200, true, false);
        var saved = fixture.town().save(helper.getLevel().registryAccess());
        saved.remove("ExpansionRadius");
        saved.remove("SupportServices");
        var smaller = rules();
        smaller.put(SettlementStage.CAMP, new SettlementStageRules.Limits(16, 0, 1, 0, 0, Set.of()));
        try {
            SettlementStageRules.apply(smaller);
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.radius(), 64, "pre-configuration Camp saves keep their original radius");
            helper.assertTrue(restored.areas().containsKey(area.id()), "legacy outer buildings are retained");
            helper.assertValueEqual(restored.support().sustainedTicks(), 200L, "unchanged service rules preserve legacy support time");
            saved.putInt("ExpansionRadius", SettlementStageRules.MAX_RADIUS + 1);
            expectInvalid(helper, () -> SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}));
        } finally {
            SettlementStageRules.apply(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void serviceRulesChangeEligibilityAndRestartSustainedSupport(GameTestHelper helper) {
        var previous = rules();
        var fixture = fixture(helper);
        fixture.town().support().observe(0, true, false);
        fixture.town().support().observe(200, true, false);
        var saved = fixture.town().save(helper.getLevel().registryAccess());
        helper.assertTrue(SettlementProgressionService.assess(helper.getLevel(), fixture.town()).missing()
                .contains(SettlementProgressionService.Requirement.FOOD_PRODUCTION), "default Hamlet needs a food service");
        var custom = rules();
        var limits = custom.get(SettlementStage.HAMLET);
        custom.put(SettlementStage.HAMLET, new SettlementStageRules.Limits(limits.radius(), limits.projects(), limits.depots(),
                0, 0, Set.of(SettlementProgressionService.Requirement.GUARD_POST)));
        try {
            SettlementStageRules.apply(custom);
            var status = SettlementProgressionService.assess(helper.getLevel(), fixture.town());
            helper.assertFalse(status.missing().contains(SettlementProgressionService.Requirement.FOOD_PRODUCTION),
                    "removed service prerequisites no longer block advancement");
            helper.assertTrue(status.missing().contains(SettlementProgressionService.Requirement.GUARD_POST),
                    "the custom prerequisite blocks advancement");
            helper.assertValueEqual(fixture.town().support().sustainedTicks(), 0L, "support cannot carry over from different service rules");
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.support().sustainedTicks(), 0L, "restarting with different service rules also resets support");
            saved.remove("SupportServices");
            helper.assertValueEqual(SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}).support().sustainedTicks(),
                    0L, "legacy support is compared against the original service requirements");
            var post = area(fixture, SettlementAreaType.GUARD_POST, 8, 3);
            fixture.town().putArea(fixture.leader().getUUID(), post);
            var guard = UUID.randomUUID();
            VillageAllegianceRegistrySavedData.get(helper.getLevel()).addOrUpdateResident(
                    fixture.town().id(), guard, true, helper.getLevel().getGameTime());
            fixture.town().assignDuty(guard, post.id());
            helper.assertFalse(SettlementProgressionService.assess(helper.getLevel(), fixture.town()).missing()
                    .contains(SettlementProgressionService.Requirement.GUARD_POST), "an enabled staffed post satisfies the custom prerequisite");
            fixture.town().support().observe(400, true, false);
            var capacitiesOnly = rules();
            capacitiesOnly.put(SettlementStage.CITY, new SettlementStageRules.Limits(320, 8, 128, 256, 512,
                    previous.get(SettlementStage.CITY).services()));
            SettlementStageRules.apply(capacitiesOnly);
            helper.assertValueEqual(fixture.town().support().sustainedTicks(), 200L, "unrelated capacity changes preserve valid support");
        } finally {
            SettlementStageRules.apply(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void snapshotsAndViewPacketsCarryServerLimitsAcrossDifferentLocalRules(GameTestHelper helper) {
        var previous = rules();
        var fixture = fixture(helper);
        while (fixture.town().stage() != SettlementStage.CITY) fixture.town().advance();
        var custom = rules();
        custom.put(SettlementStage.CITY, new SettlementStageRules.Limits(320, 12, 160, 512, 1_024,
                previous.get(SettlementStage.CITY).services()));
        try {
            SettlementStageRules.apply(custom);
            var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                    fixture.town().id().value(), SettlementManagementRequestPayload.Page.SUMMARY, 0, "", 7, false));
            var window = SettlementViewWindow.around(fixture.town(), fixture.town().center().getX(), fixture.town().center().getZ(), 64);
            var view = new SettlementViewStatePayload(UUID.randomUUID(), fixture.town().id().value(), fixture.town().dimension(), 0, window, true);
            SettlementStageRules.apply(previous);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
                var decoded = SettlementManagementPayload.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(decoded, snapshot, "server limits survive management packet encoding");
                helper.assertValueEqual(decoded.header().radius(), 320, "the client receives earned server territory");
                helper.assertValueEqual(decoded.header().projectLimit(), 12, "the client receives the server project limit");
                helper.assertValueEqual(decoded.header().depotLimit(), 160, "the client receives the server depot limit");
                helper.assertValueEqual(decoded.header().marketSupply(), 512, "the client receives the server market supply");
                helper.assertValueEqual(decoded.header().marketDemand(), 1_024, "the client receives the server market demand");
                SettlementViewStatePayload.STREAM_CODEC.encode(buffer, view);
                helper.assertValueEqual(SettlementViewStatePayload.STREAM_CODEC.decode(buffer), view,
                        "camera decoding accepts server territory larger than local stage defaults");
                helper.assertValueEqual(buffer.readableBytes(), 0, "both packets are consumed exactly");
            } finally {
                buffer.release();
            }
        } finally {
            SettlementStageRules.apply(previous);
        }
        helper.succeed();
    }

    private static EnumMap<SettlementStage, SettlementStageRules.Limits> rules() {
        var result = new EnumMap<SettlementStage, SettlementStageRules.Limits>(SettlementStage.class);
        for (var stage : SettlementStage.values()) result.put(stage, SettlementStageRules.forStage(stage));
        return result;
    }

    private static JsonObject json(SettlementStageRules.Limits limits) {
        var root = new JsonObject();
        root.addProperty("radius", limits.radius());
        root.addProperty("projects", limits.projects());
        root.addProperty("depots", limits.depots());
        root.addProperty("market_supply", limits.marketSupply());
        root.addProperty("market_demand", limits.marketDemand());
        var services = new com.google.gson.JsonArray();
        limits.services().forEach(service -> services.add(service.name().toLowerCase(java.util.Locale.ROOT)));
        root.add("services", services);
        return root;
    }

    private static SettlementArea area(Fixture fixture, SettlementAreaType type, int offset, int size) {
        var min = fixture.town().center().offset(offset, 0, 0);
        return new SettlementArea(UUID.randomUUID(), type, "Configured area", min, min.offset(size, 3, size), 5, true);
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(8_000_000 + origin.getX() * 8, 80, 8_000_000 + origin.getZ() * 8);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "StageSteward"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Configured town");
        return new Fixture(leader, SettlementSavedData.get(level).establish(
                registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow());
    }

    private static void expectInvalid(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException expected) {
            return;
        }
        helper.fail("Invalid settlement rules were accepted");
    }

    private record Fixture(ServerPlayer leader, SettlementRecord town) {
    }

    private record OverrideResources(ResourceManager delegate, ResourceLocation location, String json) implements ResourceManager {
        @Override
        public Optional<Resource> getResource(ResourceLocation requested) {
            return requested.equals(this.location) ? Optional.of(new Resource(null,
                    () -> new ByteArrayInputStream(this.json.getBytes(StandardCharsets.UTF_8)))) : this.delegate.getResource(requested);
        }

        @Override
        public Set<String> getNamespaces() {
            return this.delegate.getNamespaces();
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation location) {
            return this.delegate.getResourceStack(location);
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> predicate) {
            return this.delegate.listResources(path, predicate);
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> predicate) {
            return this.delegate.listResourceStacks(path, predicate);
        }

        @Override
        public Stream<PackResources> listPacks() {
            return this.delegate.listPacks();
        }
    }
}
