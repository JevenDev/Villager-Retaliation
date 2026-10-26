package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyCheckpoint;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementCourierInterference {
    static final class Incident {
        final long revision;
        boolean applied;
        Incident(long revision) { this.revision = revision; }
    }
    private SettlementCourierInterference() { }
    static ListTag save(Map<VillageAllegianceId, Incident> incidents) {
        var list = new ListTag(); incidents.forEach((id, incident) -> {
            var tag = new CompoundTag(); tag.putUUID("Village", id.value()); tag.putLong("Revision", incident.revision);
            tag.putBoolean("Applied", incident.applied); list.add(tag);
        }); return list;
    }
    static Map<VillageAllegianceId, Incident> load(CompoundTag tag, long revision) {
        var list = tag.getList("Interference", Tag.TAG_COMPOUND);
        if (!list.equals(tag.get("Interference")) || list.size() > 64) throw new IllegalArgumentException("Invalid courier incidents");
        var result = new LinkedHashMap<VillageAllegianceId, Incident>();
        for (var raw : list) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Village") || !entry.contains("Revision", Tag.TAG_LONG) || entry.getLong("Revision") < 1
                    || entry.getLong("Revision") > revision || !entry.contains("Applied", Tag.TAG_BYTE)) throw new IllegalArgumentException("Invalid courier incident");
            var incident = new Incident(entry.getLong("Revision")); incident.applied = entry.getBoolean("Applied");
            if (result.put(new VillageAllegianceId(entry.getUUID("Village")), incident) != null) throw new IllegalArgumentException("Duplicate courier incident");
        }
        return result;
    }
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof Villager courier)
                || !(event.getSource().getEntity() instanceof ServerPlayer player) || !SettlementCourierService.owns(courier)) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct != player && !(direct instanceof Projectile projectile && projectile.getOwner() == player)) return;
        record(player, SettlementCourierService.request(courier));
    }
    public static void boatHurt(net.minecraft.world.entity.vehicle.Boat boat, net.minecraft.world.damagesource.DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        Entity direct = source.getDirectEntity();
        if (direct == player || direct instanceof Projectile projectile && projectile.getOwner() == player) boatTaken(player, boat);
    }
    static void boatTaken(ServerPlayer player, net.minecraft.world.entity.vehicle.Boat boat) {
        if (!boat.getPersistentData().hasUUID(SettlementCourierService.JOURNEY)) return;
        var level = player.serverLevel(); var data = SettlementSavedData.get(level);
        var request = data.aid().request(boat.getPersistentData().getUUID(SettlementCourierService.JOURNEY));
        if (request == null || request.journey == null || !request.journey.boatId.equals(boat.getUUID()) || !request.journey.ownsResident()) return;
        var actors = boat.getPersistentData().getList("VillagerRetaliationBoatInterference", Tag.TAG_INT_ARRAY);
        var actor = net.minecraft.nbt.NbtUtils.createUUID(player.getUUID());
        if (actors.size() >= 64 || actors.contains(actor)) return;
        actors.add(actor); boat.getPersistentData().put("VillagerRetaliationBoatInterference", actors);
        if (!record(player, request)) {
            var entity = SettlementCourierService.existing(level.getServer(), request.journey.resident);
            if (entity instanceof Villager courier)
                com.jvn.villagerretaliation.reputation.VillagerReputationManager.addDirectReputation(level, courier, player.getUUID(), -3);
        }
    }
    static boolean record(ServerPlayer player, SettlementFoodRequest request) {
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var journey = request.journey;
        var home = data.get(journey.home).orElse(null);
        var represented = data.settlements().stream().filter(town -> town.territory().active()
                && town.permits(player.getUUID(), SettlementPermission.DIPLOMACY)).limit(2).toList();
        if (home == null || represented.size() != 1 || represented.getFirst() == home) return false;
        if (journey.interference.containsKey(represented.getFirst().id()) || journey.interference.size() >= 64) return true;
        SettlementCourierService.changed(level, request);
        journey.interference.put(represented.getFirst().id(), new Incident(journey.revision));
        return true;
    }
    static void update(ServerLevel level, SettlementFoodRequest request) {
        var journey = request.journey; var data = SettlementSavedData.get(level);
        for (var entry : journey.interference.entrySet()) {
            var incident = entry.getValue(); if (incident.applied) continue;
            if (!SettlementAidCheckpoint.durable(level, incident.revision)) return;
            var home = data.get(journey.home).orElse(null); var attacker = data.get(entry.getKey()).orElse(null);
            if (home == null || attacker == null || home == attacker) continue;
            var diplomacy = VillageDiplomacySavedData.get(level);
            if (!diplomacy.received(home.id(), attacker.id(), journey.id, 1)
                    && !VillageDiplomacyService.standingEvent(level, home.id(), attacker.id(), journey.id, 1, -3, "courier_interference")) continue;
            if (!VillageDiplomacyCheckpoint.durable(level, diplomacy.revision())) return;
            incident.applied = true; SettlementCourierService.changed(level, request); return;
        }
    }
}
