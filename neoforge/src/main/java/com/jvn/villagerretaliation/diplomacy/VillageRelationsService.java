package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.VillageRelationsPayload;
import com.jvn.villagerretaliation.network.VillageRelationsPayload.Entry;
import com.jvn.villagerretaliation.network.VillageRelationsRequestPayload;
import com.jvn.villagerretaliation.network.VillageRelationsRequestPayload.Action;
import com.jvn.villagerretaliation.network.VillageRelationsRequestPayload.View;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillageRelationsService {
    private VillageRelationsService() { }

    public static void handle(ServerPlayer player, VillageRelationsRequestPayload request) {
        if (!player.server.isSameThread() || !player.isAlive() || player.isSpectator()
                || !ServerboundRequestLimiter.tryAcquire(player, request.action() == Action.VIEW ? VillageRelationsRequestPayload.TYPE.id()
                        : com.jvn.villagerretaliation.VillagerRetaliation.id("village_relations_action"), 5L)) return;
        var result = switch (request.action()) {
            case VIEW -> VillageDiplomacyService.Result.APPLIED;
            case SHARE -> VillageDiplomacyService.share(player, id(request.perspective()), id(request.target()), request.revision());
            case INTRODUCE -> VillageDiplomacyService.introduce(player, request.ruler(), id(request.target()), request.revision());
            case SEND_INTRODUCTION, SEND_REPORT -> VillageDiplomacyService.send(player, id(request.perspective()),
                    id(request.village()), id(request.target()), request.action() == Action.SEND_REPORT
                            ? VillageLetter.Kind.STANDING_REPORT : VillageLetter.Kind.INTRODUCTION, request.revision());
        };
        PacketDistributor.sendToPlayer(player, snapshot(player, request, result));
    }

    public static SettlementRecord ruler(ServerPlayer player, UUID ruler) {
        if (ruler == null || !(player.serverLevel().getEntity(ruler) instanceof Villager villager)
                || !villager.isAlive() || player.distanceToSqr(villager) > 64) return null;
        return SettlementSavedData.get(player.serverLevel()).settlements().stream().filter(town -> town.autonomous() != null
                && ruler.equals(town.autonomous().ruler()) && town.dimension().equals(player.level().dimension().location()))
                .findFirst().orElse(null);
    }

    public static VillageRelationsPayload snapshot(ServerPlayer player, VillageRelationsRequestPayload request,
                                                   VillageDiplomacyService.Result result) {
        var level = player.serverLevel();
        var data = VillageDiplomacySavedData.get(level);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var settlements = SettlementSavedData.get(level);
        var perspective = id(request.perspective());
        var town = perspective == null ? null : settlements.get(perspective).orElse(null);
        boolean represents = VillageDiplomacyService.represents(player, town);
        var subject = id(request.village());
        var ruler = ruler(player, request.ruler());
        if (request.ruler() != null) {
            subject = ruler == null ? null : ruler.id();
            if (ruler == null) result = VillageDiplomacyService.Result.RULER_UNAVAILABLE;
            else {
                var record = registry.canonicalRecord(subject).filter(value -> !value.archived()).orElse(null);
                if (record != null) data.learn(player.getUUID(), VillageDiplomacyService.contact(record,
                        VillageContact.Origin.VISIT, player.server.overworld().getGameTime(), false));
            }
        }
        var visible = VillageDiplomacyService.visible(player, perspective);
        boolean known = subject != null && visible.containsKey(subject);
        var inspected = known ? settlements.get(subject).orElse(null) : null;
        boolean inspects = inspected != null && inspected.permits(player.getUUID(), SettlementPermission.VIEW);
        var origin = known ? entry(player, subject, visible.get(subject), false, data, registry) : null;
        var rows = new ArrayList<Entry>();
        if (request.view() == View.PERSPECTIVES) {
            for (var candidate : settlements.settlements()) {
                if (!candidate.permits(player.getUUID(), SettlementPermission.VIEW)) continue;
                var record = registry.canonicalRecord(candidate.id()).filter(value -> !value.archived()).orElse(null);
                if (record != null) rows.add(entry(player, null, VillageDiplomacyService.contact(record,
                        VillageContact.Origin.VISIT, 0, true), false, data, registry));
            }
        } else if (request.view() == View.KNOWLEDGE || request.view() == View.MAP) {
            var relations = known ? data.contacts(subject) : Map.<VillageAllegianceId, VillageContact>of();
            for (var contact : visible.values()) {
                rows.add(entry(player, subject, contact, known && relations.containsKey(contact.village()), data, registry));
            }
        } else if (known) {
            if (request.view() == View.CONTACTS) {
                for (var contact : VillageDiplomacyService.visibleRelations(player, perspective, subject).values()) {
                    rows.add(entry(player, subject, contact, true, data, registry));
                }
            } else if (request.view() == View.HISTORY) {
                var target = id(request.target());
                if (target != null && visible.containsKey(target) && data.contacts(subject).containsKey(target)) {
                    for (var event : data.history(subject, target).reversed()) {
                        var contact = visible.get(target);
                        rows.add(new Entry(target.value(), name(registry, target), contact.dimension(), contact.position(), false,
                                event.change(), VillageStanding.Band.NEUTRAL, VillageRelation.NEUTRAL, false, false, false,
                                "", event.reason(), event.time(), 0));
                    }
                }
            } else if (inspects) {
                var letters = request.view() == View.OUTBOX ? data.outgoing(subject) : data.reports(subject);
                for (var letter : letters) {
                    if (!visible.containsKey(letter.sender()) || !visible.containsKey(letter.recipient())
                            || !visible.containsKey(letter.subject().village())) continue;
                    var contact = letter.subject();
                    rows.add(new Entry(contact.village().value(), name(registry, contact.village()), contact.dimension(), contact.position(),
                            false, letter.standing(), VillageStanding.Band.NEUTRAL, VillageRelation.NEUTRAL, false, false, false,
                            name(registry, request.view() == View.OUTBOX ? letter.recipient() : letter.sender()),
                            letter.kind().name().toLowerCase(Locale.ROOT), letter.sentAt(), letter.arrivesAt()));
                }
            }
        }
        String query = request.query().strip().toLowerCase(Locale.ROOT);
        var filtered = rows.stream().filter(row -> row.name().toLowerCase(Locale.ROOT).contains(query));
        if (request.view() != View.HISTORY && request.view() != View.REPORTS && request.view() != View.OUTBOX)
            filtered = filtered.sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id));
        var entries = filtered.toList();
        int size = VillageRelationsRequestPayload.PAGE_SIZE;
        int index = Math.min(request.index(), Math.max(0, (entries.size() - 1) / size));
        var rules = VillageDiplomacyRules.get(player.server);
        String blocker = data.readOnly() ? "read_only" : !VillageDiplomacyRules.problem().isEmpty() ? "invalid_rules"
                : !rules.enabled() ? "disabled" : "";
        return new VillageRelationsPayload(request.requestId(), request.perspective(), origin, request.view(), index, entries.size(),
                data.revision(), player.server.overworld().getGameTime(), represents, inspects, result, blocker,
                entries.subList(index * size, Math.min(entries.size(), (index + 1) * size)));
    }

    private static Entry entry(ServerPlayer player, VillageAllegianceId subject, VillageContact contact, boolean relationship,
                               VillageDiplomacySavedData data, VillageAllegianceRegistrySavedData registry) {
        var id = contact.village();
        var shared = subject == null ? null : data.contact(subject, id);
        return new Entry(id.value(), name(registry, id), contact.dimension(), contact.position(), relationship,
                relationship ? data.standing(subject, id) : 0,
                relationship ? data.band(subject, id) : VillageStanding.Band.NEUTRAL,
                relationship ? VillageDiplomacyApi.query(player.serverLevel(), subject, id).relation() : VillageRelation.NEUTRAL,
                shared != null && shared.established(), data.personallyKnows(player.getUUID(), id), shared != null,
                "", "", contact.learnedAt(), 0);
    }

    private static String name(VillageAllegianceRegistrySavedData registry, VillageAllegianceId id) {
        String name = registry.canonicalRecord(id).map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName).orElse("");
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    private static VillageAllegianceId id(UUID id) { return id == null ? null : new VillageAllegianceId(id); }
}
