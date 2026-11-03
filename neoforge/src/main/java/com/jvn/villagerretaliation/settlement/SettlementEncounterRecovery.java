package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.quest.item.QuestItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementEncounterRecovery {
    static final String BODY = "JourneyRecoveryRequest", OVERFLOW = "JourneyRecoveryOverflow", RECEIPTS = "JourneyRecoveryReceipts";
    static final TagKey<Item> IMPORTANT = TagKey.create(Registries.ITEM, ResourceLocation.parse("villagerretaliation:encounter_recoverable"));
    private SettlementEncounterRecovery() { }
    static boolean protectedItem(ItemStack stack) { return !QuestItems.identity(stack).isEmpty() || stack.is(IMPORTANT); }
    static boolean recover(ItemStack stack, double chance, java.util.Random random) { return protectedItem(stack) || random.nextDouble() < chance; }
    static SettlementEncounterRemains remains(SettlementFoodRequest request, SettlementEncounterState state) {
        return request.journey.remains.stream().filter(value -> value.encounter.equals(state.id)).findFirst().orElseGet(() -> {
            var position = state.center;
            var center = new BlockPos(Math.floorDiv(position.getX(), 32) * 32 + 16, position.getY(), Math.floorDiv(position.getZ(), 32) * 32 + 16);
            var record = new SettlementEncounterRemains(UUID.randomUUID(), state.id, request.journey.resident, center, request.journey.residentContext);
            request.journey.remains.add(record); return record;
        });
    }
    static boolean died(ServerLevel level, SettlementFoodRequest request, Villager courier) {
        var state = SettlementJourneyEncounters.active(level, request); if (state == null) return false;
        var journey = request.journey;
        SettlementCourierService.capture(journey, courier); journey.position = courier.blockPosition().immutable();
        var record = remains(request, state);
        journey.presence = SettlementCourierJourney.Presence.DEAD; journey.obstructed = true;
        var home = SettlementSavedData.get(level).get(journey.home).orElse(null);
        if (home != null && level.getEntity(courier.getUUID()) != courier) {
            home.removeWorker(courier.getUUID()); home.removeResident(courier.getUUID()); home.removeDuty(courier.getUUID());
        }
        loseCargo(level, request, state, true);
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.PACKED && !journey.boat.isEmpty()) record.items.add(journey.boat.copy());
        if (!journey.boat.isEmpty()) journey.boatCustody = SettlementCourierJourney.BoatCustody.LOST;
        SettlementAidService.transition(SettlementSavedData.get(level), request, SettlementFoodRequest.State.BLOCKED, "encounter_fallen");
        SettlementCourierService.changed(level, request);
        SettlementJourneyEncounters.report(level, request, net.minecraft.network.chat.Component.translatable("villagerretaliation.encounter.fallen", courier.getDisplayName()));
        return true;
    }
    static void loseCargo(ServerLevel level, SettlementFoodRequest request, SettlementEncounterState state, boolean death) {
        if (request.offer == null || request.offer.custody() != SettlementFoodOffer.Custody.COURIER) return;
        var remaining = new ArrayList<SettlementFoodOffer.Parcel>();
        var random = new java.util.Random(state.id.getLeastSignificantBits() ^ request.id().getMostSignificantBits());
        for (var parcel : request.offer.remaining()) {
            var item = parcel.item();
            if (death) {
                request.lostNutrition += SettlementAidStock.nutrition(item);
                if (recover(item, state.definition.recovery(), random)) remains(request, state).items.add(item.copy());
            } else {
                int loss = protectedItem(item) ? 0 : (int) Math.floor(item.getCount() * state.definition.cargoLoss());
                request.lostNutrition += SettlementAidStock.nutrition(item.copyWithCount(loss));
                item.shrink(loss);
                if (!item.isEmpty()) remaining.add(new SettlementFoodOffer.Parcel(parcel.depot(), item));
            }
        }
        request.offer.replaceRemaining(remaining);
        if (death) request.offer.custody = SettlementFoodOffer.Custody.RECOVERABLE;
        SettlementCourierService.changed(level, request);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager courier) || !(courier.level() instanceof ServerLevel level)) return;
        var request = SettlementCourierService.request(courier);
        if (request == null || request.journey == null || request.journey.presence != SettlementCourierJourney.Presence.DEAD) return;
        var state = SettlementJourneyEncounters.active(level, request); if (state == null) return;
        var record = remains(request, state);
        var random = new java.util.Random(record.id.getLeastSignificantBits());
        for (var drop : event.getDrops()) if (recover(drop.getItem(), state.definition.recovery(), random)) record.items.add(drop.getItem().copy());
        event.getDrops().clear(); event.setCanceled(true);
        SettlementCourierService.capture(request.journey, courier);
        SettlementCourierService.changed(level, request);
    }
    static BlockPos safePosition(ServerLevel level, BlockPos center, Entity entity, int seed, int radius) {
        for (int attempt = 0; attempt < 96; attempt++) {
            var random = new java.util.Random((long) seed * 31 + attempt * 7919L);
            int dx = random.nextInt(radius * 2 + 1) - radius, dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;
            var column = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(column) || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(column).toLong())) continue;
            var position = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
            if (safe(level, position, entity)) return position;
        }
        return null;
    }
    static boolean safe(ServerLevel level, BlockPos position, Entity entity) {
        if (!level.hasChunkAt(position) || !level.isPositionEntityTicking(position)
                || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(position).toLong())
                || !level.getWorldBorder().isWithinBounds(position) || !level.getFluidState(position).isEmpty()
                || !level.getBlockState(position.below()).isFaceSturdy(level, position.below(), net.minecraft.core.Direction.UP)
                || !level.getBlockState(position).isAir() || !level.getBlockState(position.above()).isAir()
                || level.getHeight(Heightmap.Types.MOTION_BLOCKING, position.getX(), position.getZ()) > position.getY()) return false;
        entity.moveTo(Vec3.atBottomCenterOf(position));
        return level.noCollision(entity);
    }
    static void tick(ServerLevel level, SettlementFoodRequest request, long now) {
        if (request.journey == null || now % 20 != 0) return;
        for (var record : request.journey.remains) {
            if (record.claimant != null && !record.claimed) {
                var player = level.getServer().getPlayerList().getPlayer(record.claimant);
                if (player != null && !reconcile(level, request, record, player)
                        && level.getEntity(record.id) instanceof SettlementFallenVillager body) claim(player, body);
            }
            overflow(level, request, record, now);
            var existing = level.getEntity(record.id);
            if (!record.pending()) { if (existing instanceof SettlementFallenVillager) existing.discard(); continue; }
            if (!SettlementCourierService.nearby(level, record.center, 96)) { if (existing instanceof SettlementFallenVillager) existing.discard(); continue; }
            var body = existing instanceof SettlementFallenVillager fallen ? fallen
                    : com.jvn.villagerretaliation.entity.VillagerRetaliationEntityTypes.FALLEN_VILLAGER.get().create(level);
            if (body == null) continue;
            if (record.position == null) {
                var position = safePosition(level, record.center, body, record.id.hashCode(), SettlementEncounterRemains.RADIUS);
                if (position == null) continue;
                record.position = position; SettlementCourierService.changed(level, request); continue;
            }
            if (!safe(level, record.position, body)) { if (existing instanceof SettlementFallenVillager) existing.discard(); continue; }
            if (existing != null || !SettlementAidCheckpoint.urgent(level, request.journey.revision)) continue;
            body.setUUID(record.id); body.appearance(record.appearance); body.getPersistentData().putUUID(BODY, request.id());
            level.addFreshEntity(body);
        }
    }
    static SettlementFoodRequest request(ServerLevel level, Entity entity) {
        return entity.getPersistentData().hasUUID(BODY) ? SettlementSavedData.get(level).aid().request(entity.getPersistentData().getUUID(BODY)) : null;
    }
    static SettlementEncounterRemains record(SettlementFoodRequest request, UUID id) {
        return request == null || request.journey == null ? null : request.journey.remains.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }
    public static void claim(ServerPlayer player, SettlementFallenVillager body) {
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var request = request(level, body);
        var record = record(request, body.getUUID());
        if (record == null || data.readOnly() || !player.isAlive() || player.isSpectator() || player.distanceToSqr(body) > 36
                || !record.pending() || record.claimant != null && !record.claimant.equals(player.getUUID())) return;
        if (record.claimant == null) {
            record.claimant = player.getUUID(); SettlementCourierService.changed(level, request);
        }
        if (!SettlementAidCheckpoint.urgent(level, request.journey.revision)) return;
        if (reconcile(level, request, record, player)) return;
        var leftovers = new ListTag();
        for (var original : record.items) {
            int capacity = 0;
            for (var slot : player.getInventory().items) {
                if (slot.isEmpty()) capacity += original.getMaxStackSize();
                else if (ItemStack.isSameItemSameComponents(slot, original)) capacity += Math.max(0, original.getMaxStackSize() - slot.getCount());
            }
            for (var slot : player.getInventory().offhand) if (ItemStack.isSameItemSameComponents(slot, original))
                capacity += Math.max(0, original.getMaxStackSize() - slot.getCount());
            int offered = Math.min(original.getCount(), capacity);
            var delivery = original.copyWithCount(offered);
            if (!delivery.isEmpty()) player.getInventory().add(delivery);
            var leftover = original.copyWithCount(original.getCount() - offered + delivery.getCount());
            if (!leftover.isEmpty()) leftovers.add(leftover.save(level.registryAccess()));
        }
        var receipts = player.getPersistentData().getCompound(RECEIPTS);
        receipts.put(record.id.toString(), leftovers); player.getPersistentData().put(RECEIPTS, receipts);
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        player.server.getPlayerList().saveAll();
        reconcile(level, request, record, player);
    }
    static boolean reconcile(ServerLevel level, SettlementFoodRequest request, SettlementEncounterRemains record, ServerPlayer player) {
        var receipts = player.getPersistentData().getCompound(RECEIPTS);
        if (!receipts.contains(record.id.toString(), Tag.TAG_LIST)) return false;
        if (record.claimed) return true;
        var leftovers = receipts.getList(record.id.toString(), Tag.TAG_COMPOUND);
        for (int index = 0; index < leftovers.size(); index++) {
            var item = ItemStack.parse(level.registryAccess(), leftovers.getCompound(index)).orElseThrow();
            var id = UUID.nameUUIDFromBytes((record.id + ":overflow:" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            record.overflow.add(new SettlementCourierRecovery.Drop(id, item));
        }
        record.items.clear(); record.claimed = true;
        SettlementCourierService.changed(level, request); return true;
    }
    private static void overflow(ServerLevel level, SettlementFoodRequest request, SettlementEncounterRemains record, long now) {
        if (record.position == null || !SettlementAidCheckpoint.urgent(level, request.journey.revision)
                || !level.isPositionEntityTicking(record.position) || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(record.position).toLong())) return;
        for (var drop : record.overflow) {
            var found = level.getEntity(drop.id);
            if (drop.state == SettlementCourierRecovery.State.REMOVED) continue;
            if (drop.state == SettlementCourierRecovery.State.RELEASED) {
                if (found instanceof ItemEntity item) unlock(item); continue;
            }
            if (found == null) {
                var item = new ItemEntity(level, record.position.getX() + .5, record.position.getY() + .2, record.position.getZ() + .5, drop.item.copy());
                if (!level.noCollision(item)) continue;
                item.setUUID(drop.id); item.getPersistentData().putUUID(BODY, request.id()); item.getPersistentData().putUUID(OVERFLOW, record.id);
                item.setNeverPickUp(); item.setInvulnerable(true); item.setNoGravity(true); item.setUnlimitedLifetime(); item.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(item); return;
            }
            if (!(found instanceof ItemEntity item) || !ItemStack.matches(item.getItem(), drop.item)) return;
            if (!SettlementCourierService.nativeDurable(level, request.journey, item, SettlementCourierService.runtime(level.getServer()), now)) return;
            drop.state = SettlementCourierRecovery.State.RELEASED; SettlementCourierService.changed(level, request); return;
        }
    }
    private static void unlock(ItemEntity item) { item.setNoPickUpDelay(); item.setNoGravity(false); item.setInvulnerable(false); }
    private static SettlementCourierRecovery.Drop overflow(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level) || !item.getPersistentData().hasUUID(OVERFLOW)) return null;
        var record = record(request(level, item), item.getPersistentData().getUUID(OVERFLOW));
        return record == null ? null : record.overflow.stream().filter(drop -> drop.id.equals(item.getUUID())).findFirst().orElse(null);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var entity = event.getEntity();
        if (entity instanceof SettlementFallenVillager body) {
            var record = record(request(level, body), body.getUUID());
            if (record == null || !record.pending() || record.position == null || !record.position.equals(body.blockPosition())
                    || !safe(level, record.position, body)) { event.setCanceled(true); body.discard(); }
        } else if (entity instanceof ItemEntity item && item.getPersistentData().hasUUID(OVERFLOW)) {
            var drop = overflow(item);
            if (drop == null || drop.state == SettlementCourierRecovery.State.REMOVED) { event.setCanceled(true); item.discard(); }
            else { item.setNeverPickUp(); item.setInvulnerable(true); item.setNoGravity(true); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void pickup(ItemEntityPickupEvent.Pre event) {
        var item = event.getItemEntity(); if (!item.getPersistentData().hasUUID(OVERFLOW)) return;
        var drop = overflow(item); var request = request((ServerLevel) item.level(), item);
        if (drop == null || request == null || drop.state != SettlementCourierRecovery.State.RELEASED
                || !SettlementAidCheckpoint.urgent((ServerLevel) item.level(), request.journey.revision))
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)
                || item.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) return;
        var drop = overflow(item);
        if (drop != null && drop.state == SettlementCourierRecovery.State.RELEASED) {
            drop.state = SettlementCourierRecovery.State.REMOVED; SettlementCourierService.changed(level, request(level, item));
        }
    }
}
