package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class VillageDiplomacyService {
    private static final Map<MinecraftServer, Schedule> SCHEDULES = new WeakHashMap<>();

    private VillageDiplomacyService() { }

    public static void discover(ServerPlayer player, VillageAllegianceId id) {
        if (!usable(player)) return;
        var registry = VillageAllegianceRegistrySavedData.get(player.serverLevel());
        var village = registry.canonicalRecord(id).filter(record -> !record.archived()).orElse(null);
        if (village == null || !player.level().dimension().location().equals(village.originDimension())
                || !registry.contains(village, player.blockPosition())) return;
        VillageDiplomacySavedData.get(player.serverLevel()).learn(player.getUUID(),
                contact(village, VillageContact.Origin.VISIT, now(player.serverLevel()), false));
    }

    public static Map<VillageAllegianceId, VillageContact> visible(ServerPlayer player, VillageAllegianceId perspective) {
        if (!usable(player)) return Map.of();
        var data = VillageDiplomacySavedData.get(player.serverLevel());
        var result = new LinkedHashMap<>(data.personalContacts(player.getUUID()));
        var town = perspective == null ? null : SettlementSavedData.get(player.serverLevel()).get(perspective).orElse(null);
        if (town != null && town.permits(player.getUUID(), SettlementPermission.VIEW)) {
            data.contacts(town.id()).forEach(result::putIfAbsent);
            var own = VillageAllegianceRegistrySavedData.get(player.serverLevel()).canonicalRecord(town.id()).orElse(null);
            if (own != null && !own.archived() && own.originDimension() != null) {
                result.putIfAbsent(own.id(), contact(own, VillageContact.Origin.VISIT, now(player.serverLevel()), true));
            }
        }
        return Map.copyOf(result);
    }

    public static Map<VillageAllegianceId, VillageContact> visibleRelations(ServerPlayer player, VillageAllegianceId perspective,
                                                                         VillageAllegianceId subject) {
        var visible = visible(player, perspective);
        if (!visible.containsKey(subject)) return Map.of();
        var result = new LinkedHashMap<VillageAllegianceId, VillageContact>();
        VillageDiplomacySavedData.get(player.serverLevel()).contacts(subject).forEach((id, contact) -> {
            if (visible.containsKey(id)) result.put(id, visible.get(id));
        });
        return Map.copyOf(result);
    }

    public static boolean represents(ServerPlayer player, SettlementRecord town) {
        return usable(player) && town != null && town.autonomous() == null
                && town.permits(player.getUUID(), SettlementPermission.DIPLOMACY)
                && town.permits(player.getUUID(), SettlementPermission.VIEW);
    }

    public static Result introduce(ServerPlayer player, UUID ruler, VillageAllegianceId subject, long expected) {
        if (!usable(player)) return Result.FORBIDDEN;
        var data = VillageDiplomacySavedData.get(player.serverLevel());
        if (data.readOnly()) return Result.READ_ONLY;
        if (expected != data.revision()) return Result.STALE;
        var entity = player.serverLevel().getEntity(ruler);
        if (!(entity instanceof Villager villager) || !villager.isAlive() || player.distanceToSqr(villager) > 64) return Result.RULER_UNAVAILABLE;
        var town = SettlementSavedData.get(player.serverLevel()).settlements().stream()
                .filter(value -> value.autonomous() != null && ruler.equals(value.autonomous().ruler())).findFirst().orElse(null);
        if (town == null || !town.dimension().equals(player.level().dimension().location())) return Result.RULER_UNAVAILABLE;
        var known = data.personalContacts(player.getUUID()).get(subject);
        if (known == null || town.id().equals(subject)) return Result.UNKNOWN_VILLAGE;
        if (data.contacts(town.id()).containsKey(subject)) return Result.ALREADY_KNOWN;
        return data.learn(town.id(), known.introduced(now(player.serverLevel()))) ? Result.APPLIED : Result.LIMIT_REACHED;
    }

    public static Result share(ServerPlayer player, VillageAllegianceId perspective, VillageAllegianceId subject, long expected) {
        var town = usable(player) ? SettlementSavedData.get(player.serverLevel()).get(perspective).orElse(null) : null;
        if (!represents(player, town)) return Result.FORBIDDEN;
        var data = VillageDiplomacySavedData.get(player.serverLevel());
        if (data.readOnly()) return Result.READ_ONLY;
        if (expected != data.revision()) return Result.STALE;
        var known = data.personalContacts(player.getUUID()).get(subject);
        if (known == null || town.id().equals(subject)) return Result.UNKNOWN_VILLAGE;
        if (data.contacts(town.id()).containsKey(subject)) return Result.ALREADY_KNOWN;
        return data.learn(town.id(), known.introduced(now(player.serverLevel()))) ? Result.APPLIED : Result.LIMIT_REACHED;
    }

    public static Result send(ServerPlayer player, VillageAllegianceId perspective, VillageAllegianceId recipient,
                              VillageAllegianceId subject, VillageLetter.Kind kind, long expected) {
        var town = usable(player) ? SettlementSavedData.get(player.serverLevel()).get(perspective).orElse(null) : null;
        if (!represents(player, town)) return Result.FORBIDDEN;
        var level = player.serverLevel();
        var data = VillageDiplomacySavedData.get(level);
        if (data.readOnly()) return Result.READ_ONLY;
        if (expected != data.revision()) return Result.STALE;
        var rules = VillageDiplomacyRules.get(player.server);
        if (!rules.enabled() || !VillageDiplomacyRules.problem().isEmpty()) return Result.SUSPENDED;
        var visible = visible(player, town.id());
        var target = visible.get(recipient);
        var known = visible.get(subject);
        if (target == null || known == null || recipient.equals(town.id()) || recipient.equals(subject)) return Result.UNKNOWN_VILLAGE;
        if (!target.dimension().equals(town.dimension())) return Result.DIFFERENT_DIMENSION;
        if (kind == VillageLetter.Kind.INTRODUCTION && data.contacts(recipient).containsKey(subject)) return Result.ALREADY_KNOWN;
        long now = now(level);
        long delay = Math.max(rules.letterMinimum(), (long) Math.ceil(Math.sqrt(town.center().distSqr(target.position()))) * rules.letterTicksPerBlock());
        if (delay > Long.MAX_VALUE - now) return Result.SUSPENDED;
        var letter = new VillageLetter(UUID.randomUUID(), town.id(), recipient, kind, known,
                data.standing(town.id(), subject), now, now + delay);
        if (!data.send(letter, now)) return Result.LIMIT_REACHED;
        data.learn(town.id(), target.introduced(now));
        return Result.APPLIED;
    }

    public static boolean standingEvent(ServerLevel level, VillageAllegianceId first, VillageAllegianceId second,
                                        UUID source, long sequence, int delta, String reason) {
        if (level == null || !level.getServer().isSameThread()) return false;
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var a = VillageDiplomacyLedger.active(registry, first);
        var b = VillageDiplomacyLedger.active(registry, second);
        var rules = VillageDiplomacyRules.get(level.getServer());
        return rules.enabled() && a != null && b != null && !a.equals(b) && VillageDiplomacyRules.problem().isEmpty()
                && VillageDiplomacySavedData.get(level).adjust(VillageDiplomacySavedData.Pair.of(a, b), source, sequence, delta, reason, now(level), rules);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        var data = VillageDiplomacySavedData.get(server.overworld());
        var rules = VillageDiplomacyRules.get(server);
        if (data.readOnly() || !rules.enabled() || !VillageDiplomacyRules.problem().isEmpty()) return;
        var registry = VillageAllegianceRegistrySavedData.get(server.overworld());
        deliver(data, registry, now);
        var schedule = SCHEDULES.computeIfAbsent(server, ignored -> new Schedule());
        schedule.contact(server, registry, data, rules, now);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { SCHEDULES.remove(event.getServer()); }

    static void deliver(VillageDiplomacySavedData data, VillageAllegianceRegistrySavedData registry, long now) {
        var letter = data.due(now);
        if (letter == null) return;
        var sender = registry.canonicalRecord(letter.sender()).filter(record -> !record.archived()).orElse(null);
        var recipient = registry.canonicalRecord(letter.recipient()).filter(record -> !record.archived()).orElse(null);
        var subject = registry.canonicalRecord(letter.subject().village()).filter(record -> !record.archived()).orElse(null);
        if (sender != null && recipient != null && subject != null && sender.originDimension() != null
                && !sender.id().equals(recipient.id()) && !subject.id().equals(recipient.id())) {
            if (!data.canLearn(recipient.id(), sender.id(), subject.id())
                    || !data.canLearn(sender.id(), recipient.id())) return;
            data.learn(recipient.id(), contact(sender, VillageContact.Origin.CORRESPONDENCE, now, true));
            if (recipient.originDimension() != null) data.learn(sender.id(), contact(recipient, VillageContact.Origin.CORRESPONDENCE, now, true));
            data.learn(recipient.id(), letter.subject().identifiedAs(subject.id()).introduced(now));
            data.delivered(letter, true);
        } else data.delivered(letter, false);
    }

    static VillageContact contact(VillageAllegianceRegistrySavedData.AllegianceRecord village,
                                  VillageContact.Origin origin, long now, boolean established) {
        return new VillageContact(village.id(), village.originDimension(), village.center(), origin, now, established);
    }

    private static boolean usable(ServerPlayer player) {
        return player != null && player.server.isSameThread() && player.isAlive() && !player.isSpectator();
    }

    private static long now(ServerLevel level) { return level.getServer().overworld().getGameTime(); }

    public enum Result { APPLIED, FORBIDDEN, READ_ONLY, STALE, UNKNOWN_VILLAGE, RULER_UNAVAILABLE, ALREADY_KNOWN,
        LIMIT_REACHED, DIFFERENT_DIMENSION, SUSPENDED }

    private record Cell(net.minecraft.resources.ResourceLocation dimension, int x, int z) { }

    private static final class Schedule {
        private List<VillageAllegianceId> villages = List.of();
        private final Map<Cell, List<VillageAllegianceId>> cells = new HashMap<>();
        private final Map<VillageAllegianceId, Long> next = new HashMap<>();
        private final Map<VillageAllegianceId, Integer> candidates = new HashMap<>();
        private long refresh;
        private int cursor;
        private int cellSize;

        void contact(MinecraftServer server, VillageAllegianceRegistrySavedData registry, VillageDiplomacySavedData data,
                     VillageDiplomacyRules.Rules rules, long now) {
            if (now >= refresh || cellSize != rules.contactRange()) {
                cellSize = rules.contactRange();
                cells.clear();
                var ids = new ArrayList<VillageAllegianceId>();
                for (var town : SettlementSavedData.get(server.overworld()).settlements()) {
                    if (town.autonomous() == null) continue;
                    var village = registry.canonicalRecord(town.id()).filter(record -> !record.archived()).orElse(null);
                    if (village == null || village.originDimension() == null) continue;
                    ids.add(town.id());
                }
                for (var level : server.getAllLevels()) {
                    for (var village : registry.activeRecords(level.dimension().location())) {
                        cells.computeIfAbsent(cell(village), ignored -> new ArrayList<>()).add(village.id());
                    }
                }
                villages = List.copyOf(ids);
                next.keySet().retainAll(ids);
                candidates.keySet().retainAll(ids);
                refresh = now + 1200;
            }
            if (villages.isEmpty()) return;
            var id = villages.get(Math.floorMod(cursor++, villages.size()));
            if (now < next.getOrDefault(id, 0L)) return;
            next.put(id, now + rules.cadence());
            var village = registry.canonicalRecord(id).orElse(null);
            if (village == null || village.archived() || village.originDimension() == null) return;
            var home = cell(village);
            int offset = candidates.getOrDefault(id, 0);
            var bucket = cells.getOrDefault(new Cell(home.dimension(), home.x() + offset % 3 - 1,
                    home.z() + offset / 3 % 3 - 1), List.of());
            candidates.put(id, offset == Integer.MAX_VALUE ? 0 : offset + 1);
            if (bucket.isEmpty()) return;
            var other = registry.canonicalRecord(bucket.get(Math.floorMod(offset / 9, bucket.size()))).orElse(null);
            if (other == null || other.archived() || other.id().equals(id)
                    || village.center().distSqr(other.center()) > (double) cellSize * cellSize) return;
            data.learn(id, VillageDiplomacyService.contact(other, VillageContact.Origin.NEIGHBOR, now, true));
            data.learn(other.id(), VillageDiplomacyService.contact(village, VillageContact.Origin.NEIGHBOR, now, true));
        }

        private Cell cell(VillageAllegianceRegistrySavedData.AllegianceRecord village) {
            return new Cell(village.originDimension(), Math.floorDiv(village.center().getX(), cellSize), Math.floorDiv(village.center().getZ(), cellSize));
        }
    }
}
