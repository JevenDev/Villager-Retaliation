package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementCourierRecovery {
    static final String DROP = "VillagerRetaliationDiplomaticDrop";
    enum State { RESERVED, RELEASED, REMOVED }
    static final class Drop {
        final UUID id;
        final ItemStack item;
        State state = State.RESERVED;
        Drop(UUID id, ItemStack item) { this.id = id; this.item = item.copy(); }
        CompoundTag save(HolderLookup.Provider provider) {
            var tag = new CompoundTag(); tag.putUUID("Id", this.id); tag.put("Item", this.item.save(provider)); tag.putString("State", this.state.name()); return tag;
        }
    }
    private SettlementCourierRecovery() { }
    static ListTag save(List<Drop> drops, HolderLookup.Provider provider) {
        var tag = new ListTag(); drops.forEach(drop -> tag.add(drop.save(provider))); return tag;
    }
    static List<Drop> load(CompoundTag tag, HolderLookup.Provider provider) {
        var list = tag.getList("Recovery", Tag.TAG_COMPOUND);
        if (!list.equals(tag.get("Recovery")) || list.size() > 28) throw new IllegalArgumentException("Invalid courier recovery list");
        var result = new ArrayList<Drop>(); var ids = new java.util.HashSet<UUID>();
        for (var raw : list) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id") || !entry.contains("Item", Tag.TAG_COMPOUND) || !entry.contains("State", Tag.TAG_STRING)
                    || !ids.add(entry.getUUID("Id"))) throw new IllegalArgumentException("Invalid courier recovery identity");
            var item = ItemStack.parse(provider, entry.getCompound("Item")).orElseThrow(() -> new IllegalArgumentException("Unreadable courier item"));
            if (item.isEmpty() || item.getCount() > item.getMaxStackSize()) throw new IllegalArgumentException("Invalid recovered cargo");
            var drop = new Drop(entry.getUUID("Id"), item); drop.state = State.valueOf(entry.getString("State")); result.add(drop);
        }
        return result;
    }
    static void validate(SettlementFoodRequest request) {
        var journey = request.journey;
        if (journey.recovery.isEmpty()) return;
        if (journey.presence != SettlementCourierJourney.Presence.DEAD) throw new IllegalArgumentException("Living courier has death cargo");
        var available = request.offer.parcels().stream().map(parcel -> parcel.item()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        var consumed = new ArrayList<ItemStack>(); request.offer.remaining().forEach(parcel -> consumed.add(parcel.item()));
        int boats = 0; long pendingFood = 0;
        for (var drop : journey.recovery) {
            if (SettlementFoodService.isMeal(drop.item)) {
                if (drop.state == State.RESERVED) pendingFood += SettlementAidStock.nutrition(drop.item);
                else consumed.add(drop.item);
            } else if (!ItemStack.matches(drop.item, journey.boat) || ++boats > 1 || journey.boatCustody != SettlementCourierJourney.BoatCustody.LOST)
                throw new IllegalArgumentException("Recovered boat differs from its reservation");
        }
        long remaining = request.offer.remaining().stream().mapToLong(parcel -> SettlementAidStock.nutrition(parcel.item())).sum();
        if (request.offer.custody() == SettlementFoodOffer.Custody.RECOVERABLE && pendingFood != remaining)
            throw new IllegalArgumentException("Recoverable food does not match shipment custody");
        if (request.deliveredNutrition() + consumed.stream().mapToLong(SettlementAidStock::nutrition).sum() > request.offer.nutrition())
            throw new IllegalArgumentException("Recovered and delivered food exceed the original reservation");
        for (var item : consumed) {
            int count = item.getCount();
            for (var stock : available) if (ItemStack.isSameItemSameComponents(stock, item)) {
                int taken = Math.min(count, stock.getCount()); stock.shrink(taken); count -= taken;
            }
            if (count != 0) throw new IllegalArgumentException("Recovered food exceeds the original reservation");
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void died(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager courier) || !(courier.level() instanceof ServerLevel level) || !SettlementCourierService.owns(courier)) return;
        var request = SettlementCourierService.request(courier); var journey = request.journey;
        if (journey.presence == SettlementCourierJourney.Presence.DEAD) return;
        SettlementCourierService.capture(journey, courier); journey.position = courier.blockPosition().immutable();
        journey.presence = SettlementCourierJourney.Presence.DEAD; journey.obstructed = true;
        if (request.offer.custody() == SettlementFoodOffer.Custody.COURIER) {
            for (var parcel : request.offer.remaining()) journey.recovery.add(new Drop(UUID.randomUUID(), parcel.item()));
            request.offer.custody = SettlementFoodOffer.Custody.RECOVERABLE;
        }
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.PACKED
                || journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHING && SettlementCourierService.existing(level.getServer(), journey.boatId) == null) {
            journey.recovery.add(new Drop(UUID.randomUUID(), journey.boat));
        }
        if (!journey.boat.isEmpty()) journey.boatCustody = SettlementCourierJourney.BoatCustody.LOST;
        SettlementCourierService.changed(level, request);
        SettlementAidService.transition(SettlementSavedData.get(level), request, SettlementFoodRequest.State.BLOCKED, "courier_lost");
    }
    static void update(ServerLevel level, SettlementFoodRequest request, long now, SettlementCourierService.Runtime runtime) {
        var journey = request.journey; var data = SettlementSavedData.get(level);
        if (request.offer.custody() == SettlementFoodOffer.Custody.RESERVED) {
            var donor = data.get(request.donor()).orElse(null);
            if (donor == null || !SettlementAidStock.refund(level, donor, request)) SettlementCourierService.block(level, request, "refund_storage_full");
        }
        var drop = journey.recovery.stream().filter(value -> value.state == State.RESERVED).findFirst().orElse(null);
        if (drop == null) return;
        if (!SettlementAidCheckpoint.durable(level, journey.revision) || !level.isPositionEntityTicking(journey.position)
                || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(journey.position).toLong())) return;
        var found = SettlementCourierService.existing(level.getServer(), drop.id);
        if (found == null) {
            var item = new ItemEntity(level, journey.position.getX() + 0.5, journey.position.getY() + 0.1, journey.position.getZ() + 0.5, drop.item.copy());
            item.setUUID(drop.id); item.getPersistentData().putUUID(DROP, journey.id);
            item.setNeverPickUp(); item.setInvulnerable(true); item.setNoGravity(true); item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (!level.addFreshEntity(item)) SettlementCourierService.block(level, request, "cargo_recovery_blocked");
            return;
        }
        if (!(found instanceof ItemEntity item) || item.level() != level || !ItemStack.matches(drop.item, item.getItem())) {
            SettlementCourierService.block(level, request, "cargo_identity_mismatch"); return;
        }
        if (!SettlementCourierService.nativeDurable(level, journey, item, runtime, now)) return;
        if (SettlementFoodService.isMeal(drop.item)) {
            var remaining = new ArrayList<SettlementFoodOffer.Parcel>(); int count = drop.item.getCount();
            for (var parcel : request.offer.remaining()) {
                var itemStack = parcel.item();
                if (ItemStack.isSameItemSameComponents(itemStack, drop.item)) {
                    int taken = Math.min(count, itemStack.getCount()); count -= taken; itemStack.shrink(taken);
                }
                if (!itemStack.isEmpty()) remaining.add(new SettlementFoodOffer.Parcel(parcel.depot(), itemStack));
            }
            if (count != 0) { SettlementCourierService.block(level, request, "cargo_identity_mismatch"); return; }
            request.offer.replaceRemaining(remaining);
        }
        drop.state = State.RELEASED;
        SettlementCourierService.changed(level, request);
    }
    static void unlock(ServerLevel level, SettlementFoodRequest request) {
        if (!SettlementAidCheckpoint.durable(level, request.journey.revision)) return;
        for (var drop : request.journey.recovery) if (drop.state == State.RELEASED) {
            var found = SettlementCourierService.existing(level.getServer(), drop.id);
            if (found instanceof ItemEntity item) release(item);
        }
    }
    private static void release(ItemEntity item) { item.setNoPickUpDelay(); item.setInvulnerable(false); item.setNoGravity(false); }
    private static Drop drop(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level) || !item.getPersistentData().hasUUID(DROP)) return null;
        var request = SettlementSavedData.get(level).aid().request(item.getPersistentData().getUUID(DROP));
        if (request == null || request.journey == null) return null;
        return request.journey.recovery.stream().filter(value -> value.id.equals(item.getUUID())).findFirst().orElse(null);
    }
    public static boolean locked(ItemEntity item) {
        if (!item.getPersistentData().hasUUID(DROP)) return false;
        if (!(item.level() instanceof ServerLevel level)) return true;
        var data = SettlementSavedData.get(level); var drop = drop(item);
        var request = data.aid().request(item.getPersistentData().getUUID(DROP));
        return drop == null || request == null || data.readOnly() || drop.state != State.RELEASED || data.aid().durableRevision < request.journey.revision;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)
                || !item.getPersistentData().hasUUID(DROP)) return;
        var data = SettlementSavedData.get(level); if (data.readOnly()) { item.setNeverPickUp(); return; }
        var drop = drop(item);
        if (drop != null && drop.state == State.REMOVED) { event.setCanceled(true); item.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK); }
        else if (locked(item)) { item.setNeverPickUp(); item.setInvulnerable(true); item.setNoGravity(true); }
        else release(item);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (locked(event.getItemEntity())) event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)) return;
        var drop = drop(item);
        if (drop == null || drop.state != State.RELEASED || item.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) return;
        drop.state = State.REMOVED;
        var request = SettlementSavedData.get(level).aid().request(item.getPersistentData().getUUID(DROP));
        SettlementCourierService.changed(level, request);
    }
}
