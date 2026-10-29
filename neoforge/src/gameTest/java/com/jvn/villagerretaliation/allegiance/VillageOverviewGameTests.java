package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillageOverviewPayload;
import com.jvn.villagerretaliation.network.VillageOverviewRequestPayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageOverviewGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillageOverviewGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void overviewKeepsResidentNamesAndJobsWhenUnloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2)).atY(96);
        BlockPos spare = home.offset(1, 0, 0);
        BlockPos job = home.offset(2, 0, 0);
        level.getChunkAt(home);
        var poiRegistry = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE);
        var manager = level.getPoiManager();
        manager.add(home, poiRegistry.getHolderOrThrow(PoiTypes.HOME));
        manager.add(spare, poiRegistry.getHolderOrThrow(PoiTypes.HOME));
        manager.add(job, poiRegistry.getHolderOrThrow(PoiTypes.FARMER));
        manager.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(home), home, 1).orElseThrow();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.discoverAt(level, home).orElseThrow();
        var player = helper.makeMockServerPlayerInLevel();
        player.moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
        Villager adult = EntityType.VILLAGER.create(level);
        Villager child = EntityType.VILLAGER.create(level);
        helper.assertTrue(adult != null && child != null, "test residents can be created");
        adult.setCustomName(Component.literal("Ada"));
        adult.setVillagerData(adult.getVillagerData().setProfession(VillagerProfession.FARMER));
        child.setCustomName(Component.literal("Bea"));
        child.setAge(-24_000);
        for (Villager resident : List.of(adult, child)) {
            resident.moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
            level.addFreshEntity(resident);
            VillageAllegianceApi.assignKnown(level, resident, village, AllegianceAssignmentSource.ADMIN);
            VillagerSocialGraphSavedData.get(level).ensureProfile(level, resident);
        }
        VillagerReputationManager.setReputation(level, adult.getUUID(), player.getUUID(),
                VillagerRetaliationConfig.REVERED_THRESHOLD.get(), home);
        try {
            var snapshot = VillageOverviewService.snapshot(player, home, true);
            helper.assertTrue(snapshot.found(), "occupied home creates an inspectable village");
            helper.assertValueEqual(snapshot.adults(), 1, "adult population is separate from children");
            helper.assertValueEqual(snapshot.children(), 1, "children appear in the roster");
            helper.assertValueEqual(snapshot.present(), 2, "loaded residents in the footprint are counted here");
            helper.assertValueEqual(snapshot.beds(), 2, "nearby beds are counted once");
            helper.assertValueEqual(snapshot.freeBeds(), 1, "claimed and unclaimed beds are distinguished");
            helper.assertValueEqual(snapshot.workstations(), 1, "nearby workstations are counted");
            helper.assertValueEqual(snapshot.trusted(), 1, "the player's own standing is shown");
            helper.assertValueEqual(snapshot.required(), 1, "children do not inflate naming requirements");
            adult.discard();
            var refreshed = VillageOverviewService.snapshot(player, home, false);
            var unloaded = refreshed.residents().stream().filter(row -> row.name().equals("Ada")).findFirst().orElseThrow();
            helper.assertValueEqual(unloaded.presence(), VillageOverviewPayload.Presence.UNLOADED,
                    "an unloaded resident is not presented as currently here");
            helper.assertValueEqual(unloaded.profession(), ResourceLocation.withDefaultNamespace("farmer"),
                    "unloaded residents retain their known job");
            helper.assertValueEqual(refreshed.adults(), 1, "unloading does not erase the resident");
            helper.assertValueEqual(refreshed.present(), 1, "refresh updates the number currently here");
            helper.assertFalse(refreshed.openScreen(), "refresh responses cannot reopen a dismissed overview");
        } finally {
            adult.discard();
            child.discard();
            player.discard();
            manager.release(home);
            manager.remove(home);
            manager.remove(spare);
            manager.remove(job);
            registry.archive(village);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void overviewRejectsRemoteRequestsWithoutLoadingChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos nearby = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(nearby.getX() + 0.5D, nearby.getY(), nearby.getZ() + 0.5D);
        BlockPos remote = nearby.offset(1_900_000, 0, 1_900_000);
        try {
            helper.assertTrue(VillageOverviewService.canInspect(player, nearby), "nearby players can inspect settlements");
            helper.assertFalse(level.hasChunkAt(remote), "remote test chunk starts unloaded");
            VillageOverviewService.refresh(player, new VillageOverviewRequestPayload(remote));
            helper.assertFalse(VillageOverviewService.canInspect(player, remote), "remote overview requests are rejected");
            helper.assertFalse(level.hasChunkAt(remote), "rejected requests do not load remote chunks");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void overviewPayloadBoundsAndUnicodeNamesRoundTrip(GameTestHelper helper) {
        String longName = "A".repeat(63) + new String(Character.toChars(0x1F333)) + "tail";
        var resident = new VillageOverviewPayload.Resident(longName, ResourceLocation.withDefaultNamespace("farmer"),
                false, VillageOverviewPayload.Presence.HERE, VillagerReputationLevel.REVERED, BlockPos.ZERO);
        helper.assertValueEqual(resident.name(), "A".repeat(63), "truncation cannot leave a split surrogate");
        List<VillageOverviewPayload.Resident> residents = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            residents.add(resident);
        }
        var payload = new VillageOverviewPayload(BlockPos.ZERO, helper.getLevel().dimension().location(), true,
                "Bounded", VillageLifecycleState.ACTIVE, 200, 0, 200, 200, 0, 200, 200, 100, residents, VillageOpinion.NEUTRAL);
        helper.assertValueEqual(payload.residents().size(), VillageOverviewPayload.MAX_RESIDENTS,
                "a large village cannot create an unbounded roster packet");
        helper.assertValueEqual(payload.adults(), 200, "summary population is not truncated with the rows");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            VillageOverviewPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(VillageOverviewPayload.STREAM_CODEC.decode(buffer), payload,
                    "overview names, positions, standing, and census round-trip through the codec");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villageCommandIsAvailableWithoutOperatorPermissions(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var commands = helper.getLevel().getServer().getCommands().getDispatcher();
        var node = commands.getRoot().getChild("vr").getChild("village");
        try {
            helper.assertTrue(node != null && node.getCommand() != null, "the settlement command is registered");
            helper.assertTrue(node.canUse(player.createCommandSourceStack().withPermission(0)),
                    "regular players can access the settlement overview");
        } finally {
            player.discard();
        }
        helper.succeed();
    }
}
