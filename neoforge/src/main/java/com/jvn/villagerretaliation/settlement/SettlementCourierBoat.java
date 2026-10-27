package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import com.jvn.villagerretaliation.mixin.DiplomaticBoatItemAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementCourierBoat {
    private SettlementCourierBoat() { }
    static Boat create(ServerLevel level, SettlementCourierJourney journey, Vec3 position) {
        var item = (DiplomaticBoatItemAccessor) journey.boat.getItem();
        Boat boat = item.villagerretaliation$hasChest() ? new ChestBoat(level, position.x, position.y, position.z) : new Boat(level, position.x, position.y, position.z);
        EntityType.<Boat>createDefaultStackConfig(level, journey.boat, null).accept(boat);
        boat.setVariant(item.villagerretaliation$type());
        if (!journey.boatContext.isEmpty()) boat.load(journey.boatContext.copy());
        boat.setUUID(journey.boatId); boat.moveTo(position.x, position.y, position.z);
        boat.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id);
        boat.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.boatIncarnation);
        return boat;
    }
    static boolean materialize(ServerLevel level, SettlementCourierJourney journey, Villager courier) {
        if (journey.boatCustody != SettlementCourierJourney.BoatCustody.LAUNCHED) return true;
        var existing = SettlementCourierService.existing(level.getServer(), journey.boatId);
        var boat = existing instanceof Boat value ? value : create(level, journey, Vec3.atBottomCenterOf(journey.position));
        if (existing != null && (!(existing instanceof Boat) || existing.level() != level) || !boat.getPassengers().isEmpty()) return false;
        if (existing == null && (!level.noCollision(boat) || !level.addFreshEntity(boat))) return false;
        return courier.startRiding(boat, true);
    }
    static void navigate(ServerLevel level, SettlementFoodRequest request, Villager courier, DiplomaticRoute.Step target, long now) {
        var journey = request.journey;
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LOST) {
            var recovered = SettlementCourierService.existing(level.getServer(), journey.boatId);
            if (!(recovered instanceof Boat boat) || boat.level() != level || !boat.isAlive() || boat.distanceToSqr(courier) > 16
                    || boat.getPersistentData().getLong(SettlementCourierService.INCARNATION) != journey.boatIncarnation
                    || boat.getPassengers().stream().anyMatch(passenger -> passenger != courier)
                    || courier.getVehicle() != boat && !courier.startRiding(boat, true)) {
                SettlementCourierService.block(level, request, "boat_lost"); return;
            }
            journey.boatCustody = SettlementCourierJourney.BoatCustody.LAUNCHED; journey.obstructed = false;
            journey.lastProgress = now; SettlementCourierService.capture(journey, courier);
            SettlementCourierService.block(level, request, ""); SettlementCourierService.changed(level, request);
        }
        if (target.mode() == DiplomaticRoute.Mode.LAUNCH && journey.boatCustody == SettlementCourierJourney.BoatCustody.PACKED) {
            if (journey.boatIncarnation == Long.MAX_VALUE) { SettlementCourierService.block(level, request, "boat_identity_exhausted"); return; }
            journey.boatIncarnation++; journey.boatCustody = SettlementCourierJourney.BoatCustody.LAUNCHING;
            SettlementCourierService.changed(level, request); return;
        }
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHING) {
            if (!SettlementAidCheckpoint.urgent(level, journey.revision)) return;
            if (!level.getFluidState(target.position().below()).is(FluidTags.WATER)) {
                journey.obstructed = true; SettlementCourierService.block(level, request, "courier_launch_obstructed"); return;
            }
            var found = SettlementCourierService.existing(level.getServer(), journey.boatId);
            var boat = found instanceof Boat value ? value : create(level, journey, Vec3.atBottomCenterOf(target.position()));
            if (found != null && (!(found instanceof Boat) || found.level() != level) || !boat.getPassengers().isEmpty()
                    || !level.noCollision(boat) || found == null && !level.addFreshEntity(boat) || !courier.startRiding(boat, true)) {
                journey.obstructed = true; SettlementCourierService.block(level, request, "courier_launch_obstructed"); return;
            }
            journey.boatCustody = SettlementCourierJourney.BoatCustody.LAUNCHED; journey.step++; journey.position = target.position();
            journey.lastProgress = now; journey.obstructed = false; SettlementCourierService.capture(journey, courier);
            SettlementCourierService.block(level, request, ""); SettlementCourierService.travelled(level, request); SettlementCourierService.changed(level, request); return;
        }
        var found = SettlementCourierService.existing(level.getServer(), journey.boatId);
        if (!(found instanceof Boat boat) || found.level() != level || !boat.isAlive()) {
            journey.boatCustody = SettlementCourierJourney.BoatCustody.LOST;
            journey.obstructed = true; SettlementCourierService.block(level, request, "boat_lost"); return;
        }
        if (boat.getPassengers().stream().anyMatch(passenger -> passenger != courier)
                || courier.getVehicle() != boat && journey.boatCustody != SettlementCourierJourney.BoatCustody.PACKING) {
            for (var passenger : boat.getPassengers()) if (passenger instanceof net.minecraft.server.level.ServerPlayer player)
                SettlementCourierInterference.boatTaken(player, boat);
            journey.boatCustody = SettlementCourierJourney.BoatCustody.LOST;
            journey.obstructed = true; SettlementCourierService.block(level, request, "boat_taken"); return;
        }
        if (target.mode() == DiplomaticRoute.Mode.LAND) {
            if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED) {
                var landing = Vec3.atBottomCenterOf(target.position());
                if (!level.noCollision(courier, courier.getBoundingBox().move(landing.subtract(courier.position())))
                        || level.getBlockState(target.position().below()).getCollisionShape(level, target.position().below()).isEmpty()) {
                    journey.obstructed = true; SettlementCourierService.block(level, request, "courier_landing_obstructed"); return;
                }
                journey.boatContext = boat.saveWithoutId(new CompoundTag()); journey.boatCustody = SettlementCourierJourney.BoatCustody.PACKING;
                SettlementCourierService.changed(level, request); return;
            }
            if (!SettlementAidCheckpoint.urgent(level, journey.revision)) return;
            if (courier.isPassenger()) courier.stopRiding();
            if (courier.position().distanceToSqr(Vec3.atBottomCenterOf(target.position())) > 0.64) {
                if (SettlementCourierService.navigationDue(level, journey, now) && !courier.getNavigation().moveTo(courier.getNavigation().createPath(target.position(), 0), 0.5)) {
                    journey.obstructed = true; SettlementCourierService.block(level, request, "courier_landing_obstructed");
                }
                return;
            }
            if (!boat.getPassengers().isEmpty() || boat.distanceToSqr(courier) > 16 || boat instanceof ChestBoat chest && !chest.isEmpty()) {
                journey.obstructed = true; SettlementCourierService.block(level, request, "boat_recovery_blocked"); return;
            }
            journey.boatCustody = SettlementCourierJourney.BoatCustody.PACKED;
            boat.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK); journey.step++; journey.position = target.position();
            journey.lastProgress = now; journey.obstructed = false; SettlementCourierService.capture(journey, courier);
            SettlementCourierService.block(level, request, ""); SettlementCourierService.travelled(level, request); SettlementCourierService.changed(level, request); return;
        }
        var destination = Vec3.atBottomCenterOf(target.position());
        var delta = destination.subtract(boat.position());
        if (delta.horizontalDistanceSqr() <= 0.36 && Math.abs(delta.y) <= 1.25) {
            journey.step++; journey.position = target.position(); journey.lastProgress = now; journey.obstructed = false;
            SettlementCourierService.capture(journey, courier); SettlementCourierService.block(level, request, ""); SettlementCourierService.travelled(level, request); SettlementCourierService.changed(level, request);
            if (!SettlementCourierService.nearby(level, courier.blockPosition(), 96)) SettlementCourierService.beginRetirement(level, request, courier, now);
            return;
        }
        if (now - journey.lastProgress > 200) { journey.obstructed = true; SettlementCourierService.block(level, request, "courier_sailing_obstructed"); }
        var velocity = new Vec3(delta.x, 0, delta.z).normalize().scale(0.18);
        boat.setDeltaMovement(velocity.x, boat.getDeltaMovement().y, velocity.z);
        boat.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Boat boat)
                || !boat.getPersistentData().hasUUID(SettlementCourierService.JOURNEY)) return;
        var data = SettlementSavedData.get(level); if (data.readOnly()) return;
        var request = data.aid().request(boat.getPersistentData().getUUID(SettlementCourierService.JOURNEY));
        if (request == null || request.journey == null) {
            event.setCanceled(true); boat.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK); return;
        }
        var journey = request.journey;
        boolean physical = journey.presence == SettlementCourierJourney.Presence.PHYSICAL || journey.presence == SettlementCourierJourney.Presence.MATERIALIZING
                || journey.presence == SettlementCourierJourney.Presence.STRANDED;
        boolean afloat = journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED || journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHING
                || journey.boatCustody == SettlementCourierJourney.BoatCustody.PACKING;
        if (!boat.getUUID().equals(journey.boatId) || boat.getPersistentData().getLong(SettlementCourierService.INCARNATION) != journey.boatIncarnation
                || journey.boatCustody != SettlementCourierJourney.BoatCustody.LOST && (!physical || !afloat)) {
            event.setCanceled(true); boat.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Boat boat)
                || !boat.getPersistentData().hasUUID(SettlementCourierService.JOURNEY)) return;
        var request = SettlementSavedData.get(level).aid().request(boat.getPersistentData().getUUID(SettlementCourierService.JOURNEY));
        if (request == null || request.journey == null || !request.journey.boatId.equals(boat.getUUID())) return;
        if (boat.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            if (request.journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED
                    && boat.getPersistentData().getLong(SettlementCourierService.INCARNATION) == request.journey.boatIncarnation) {
                request.journey.boatContext = boat.saveWithoutId(new CompoundTag()); SettlementCourierService.changed(level, request);
            }
            return;
        }
        if (boat.getRemovalReason() != Entity.RemovalReason.KILLED && boat.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
        request.journey.boatCustody = SettlementCourierJourney.BoatCustody.LOST; request.journey.obstructed = true;
        SettlementCourierService.block(level, request, "boat_lost"); SettlementCourierService.changed(level, request);
    }
}
