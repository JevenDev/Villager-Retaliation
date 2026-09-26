package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.VillageOverviewPayload;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Resident;
import com.jvn.villagerretaliation.network.VillageOverviewRequestPayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillageOverviewService {
    private VillageOverviewService() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()
                || !event.getEntity().isShiftKeyDown()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BellBlock)) {
            return;
        }
        open(player, event.getPos());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static void open(ServerPlayer player, BlockPos anchor) {
        send(player, anchor, true);
    }

    public static void refresh(ServerPlayer player, VillageOverviewRequestPayload request) {
        send(player, request.anchor(), false);
    }

    private static void send(ServerPlayer player, BlockPos anchor, boolean openScreen) {
        if (!canInspect(player, anchor)
                || !ServerboundRequestLimiter.tryAcquire(player, VillageOverviewRequestPayload.TYPE.id(), 20L)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(player, anchor, openScreen));
    }

    static boolean canInspect(ServerPlayer player, BlockPos anchor) {
        return player != null && anchor != null && player.isAlive()
                && player.distanceToSqr(anchor.getCenter()) <= 64.0D
                && player.serverLevel().hasChunkAt(anchor);
    }

    static VillageOverviewPayload snapshot(ServerPlayer player, BlockPos anchor, boolean openScreen) {
        ServerLevel level = player.serverLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.peekAt(level, anchor).or(() -> registry.discoverAt(level, anchor))
                .flatMap(registry::canonicalRecord).orElse(null);
        if (village == null) {
            return new VillageOverviewPayload(anchor, level.dimension().location(), openScreen, "",
                    VillageLifecycleState.ACTIVE, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
        }
        var reputation = VillagerReputationSavedData.get(level);
        var social = VillagerSocialGraphSavedData.get(level);
        List<Resident> residents = new ArrayList<>();
        int adults = 0;
        int present = 0;
        for (var resident : village.activeResidents(level.getGameTime())) {
            var entity = level.getEntity(resident.id());
            Villager villager = entity instanceof Villager loaded && loaded.isAlive() ? loaded : null;
            String name = villager == null ? social.knownName(resident.id()).orElse("")
                    : VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
            ResourceLocation profession = villager == null
                    ? social.knownProfession(resident.id()).flatMap(VillagerProfessionUtil::parse)
                            .map(VillagerProfessionUtil::id).orElse(ResourceLocation.withDefaultNamespace("none"))
                    : VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
            boolean baby = villager == null ? !resident.adult() : villager.isBaby();
            Presence presence = villager == null ? Presence.UNLOADED
                    : village.footprintSections().contains(SectionPos.asLong(villager.blockPosition()))
                            ? Presence.HERE : Presence.AWAY;
            var standing = reputation.get(resident.id(), player.getUUID());
            residents.add(new Resident(name, profession, baby, presence,
                    VillagerReputationLevel.fromReputation(standing == null ? 0 : standing.reputation()),
                    villager == null ? BlockPos.ZERO : villager.blockPosition()));
            if (!baby) {
                adults++;
            }
            if (presence == Presence.HERE) {
                present++;
            }
        }
        residents.sort(Comparator.comparing(Resident::presence)
                .thenComparing(Resident::name, String.CASE_INSENSITIVE_ORDER));
        int[] facilities = countFacilities(level, anchor, village);
        var trust = VillageNamingService.trustGate(level, player, village);
        return new VillageOverviewPayload(anchor, level.dimension().location(), openScreen,
                village.displayName(), village.lifecycleState(), adults, residents.size() - adults, present,
                facilities[0], facilities[1], facilities[2], trust.trustedResidents(), trust.requiredResidents(), residents);
    }

    private static int[] countFacilities(ServerLevel level, BlockPos anchor,
                                        VillageAllegianceRegistrySavedData.AllegianceRecord village) {
        int[] counts = new int[3];
        ChunkPos center = new ChunkPos(anchor);
        ChunkPos.rangeClosed(center, 6).filter(chunk -> level.hasChunk(chunk.x, chunk.z)).forEach(chunk ->
                level.getPoiManager().getInChunk(type -> type.is(PoiTypes.HOME)
                                || type.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), chunk, PoiManager.Occupancy.ANY)
                        .filter(poi -> poi.getPos().distSqr(anchor) <= 96.0D * 96.0D)
                        .filter(poi -> village.footprintSections().contains(SectionPos.asLong(poi.getPos())))
                        .forEach(poi -> {
                            if (poi.getPoiType().is(PoiTypes.HOME)) {
                                counts[0]++;
                                if (poi.hasSpace()) {
                                    counts[1]++;
                                }
                            } else {
                                counts[2]++;
                            }
                        }));
        return counts;
    }
}
