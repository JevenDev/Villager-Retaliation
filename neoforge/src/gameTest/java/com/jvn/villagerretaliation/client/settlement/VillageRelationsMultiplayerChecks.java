package com.jvn.villagerretaliation.client.settlement;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.network.SettlementAidRequestPayload;
import com.jvn.villagerretaliation.settlement.SettlementAidService;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class VillageRelationsMultiplayerChecks {
    private record Fixture(UUID home, UUID contact) { }
    private static Fixture fixture;
    private static String previous = "";
    private static int step;
    private VillageRelationsMultiplayerChecks() { }
    static boolean handles(String phase) { return phase.startsWith("relations_") || phase.startsWith("aid_policy_"); }
    static void prepare(ServerPlayer host, ServerPlayer peer, SettlementRecord town) {
        var level = host.serverLevel(); var registry = VillageAllegianceRegistrySavedData.get(level);
        var oak = registry.create(level.getGameTime(), level.dimension().location(), town.center().offset(256, 0, 0), "Oakvale TCP");
        var stone = registry.create(level.getGameTime(), level.dimension().location(), town.center().offset(512, 0, 0), "Stoneford TCP");
        var hidden = registry.create(level.getGameTime(), level.dimension().location(), town.center().offset(768, 0, 0), "Hidden TCP village");
        VillageDiplomacyService.receiveCorrespondence(level, oak, town.id());
        VillageDiplomacyService.receiveCorrespondence(level, stone, town.id());
        VillageDiplomacyService.receiveCorrespondence(level, town.id(), oak);
        VillageDiplomacyService.receiveCorrespondence(level, hidden, oak);
        require(town.setPermissions(host.getUUID(), peer.getUUID(), Set.of(SettlementPermission.VIEW)), "viewing authority is granted independently of diplomacy");
        fixture = new Fixture(town.id().value(), oak.value()); write("relations-fixture.json", new Gson().toJson(fixture));
    }
    static boolean client(Minecraft minecraft, String phase, int ticks) {
        if (fixture == null) fixture = new Gson().fromJson(read("relations-fixture.json"), Fixture.class);
        if (!phase.equals(previous)) { previous = phase; step = 0; }
        if (phase.equals("relations_read")) {
            if (step == 0) { minecraft.setScreen(new VillageRelationsScreen(null, fixture.home, fixture.home, null)); step = 1; }
            else if (step == 1 && minecraft.screen instanceof VillageRelationsScreen screen && screen.snapshot() != null) {
                var snapshot = screen.snapshot();
                require(snapshot.inspects() && !snapshot.represents() && snapshot.total() == 2 && snapshot.entries().size() == 2,
                        "view-only delegate receives exactly the home contacts over TCP");
                require(snapshot.entries().stream().noneMatch(entry -> entry.name().contains("Hidden")), "hidden contact name does not cross TCP");
                minecraft.setScreen(new VillageRelationsScreen(null, fixture.home, fixture.contact, null)); step = 2;
            } else if (step == 2 && minecraft.screen instanceof VillageRelationsScreen screen && screen.snapshot() != null) {
                require(screen.snapshot().total() == 1 && screen.snapshot().entries().size() == 1
                        && screen.snapshot().entries().getFirst().id().equals(fixture.home), "foreign relations reveal only mutually known villages over TCP");
                screen.onClose(); return true;
            }
        } else if (phase.equals("relations_revoked")) {
            if (step == 0) { minecraft.setScreen(new VillageRelationsScreen(null, fixture.home, fixture.home, null)); step = 1; }
            else if (minecraft.screen instanceof VillageRelationsScreen screen && screen.snapshot() != null) {
                require(!screen.snapshot().inspects() && screen.snapshot().total() == 0 && screen.snapshot().entries().isEmpty(),
                        "revoking viewing authority clears all contact names and counts over TCP");
                screen.onClose(); return true;
            }
        } else {
            if (ticks == 10) {
                int quantity = phase.equals("aid_policy_stale") ? 80 : phase.equals("aid_policy_apply") ? 96 : 128;
                PacketDistributor.sendToServer(new SettlementAidRequestPayload(fixture.home, null, null, SettlementAidRequestPayload.Action.POLICY,
                        0, Long.parseLong(read("aid-policy-revision.txt")), 0,
                        new SettlementAidRequestPayload.Policy(false, 3, quantity, 0, 72000), VillageRelationsClient.nextRequest()));
            }
            if (ticks >= 40) return true;
        }
        require(ticks < 300, "relations multiplayer phase completes: " + phase); return false;
    }
    static String server(ServerPlayer host, ServerPlayer peer, SettlementRecord town, String phase, String acknowledgement) {
        if (!acknowledgement.equals(phase + "_done")) return null;
        var aid = SettlementSavedData.get(host.serverLevel()).aid();
        if (phase.equals("relations_read")) {
            require(town.setPermissions(host.getUUID(), peer.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.DIPLOMACY)), "diplomatic authority is granted explicitly");
            write("aid-policy-revision.txt", Long.toString(aid.policyRevision(town)));
            require(SettlementAidService.policy(host, town.id(), aid.policyRevision(town), false, 3, 64, 0, 72000) == SettlementAidService.Result.APPLIED,
                    "host changes the aid policy before a stale peer edit");
            return "aid_policy_stale";
        }
        if (phase.equals("aid_policy_stale")) {
            require(aid.policy(town).maximumItems() == 64, "stale TCP policy action preserves the host update");
            write("aid-policy-revision.txt", Long.toString(aid.policyRevision(town))); return "aid_policy_apply";
        }
        if (phase.equals("aid_policy_apply")) {
            require(aid.policy(town).maximumItems() == 96, "authorized current-revision TCP policy edit is applied");
            write("aid-policy-revision.txt", Long.toString(aid.policyRevision(town)));
            town.setPermissions(host.getUUID(), peer.getUUID(), Set.of(SettlementPermission.VIEW)); return "aid_policy_revoked";
        }
        if (phase.equals("aid_policy_revoked")) {
            require(aid.policy(town).maximumItems() == 96, "revoked diplomatic authority rejects a pending TCP edit");
            town.setPermissions(host.getUUID(), peer.getUUID(), Set.of()); return "relations_revoked";
        }
        return "outsider";
    }
    private static Path path(String name) { return Path.of(System.getProperty("villagerretaliation.settlementMultiplayer"), name); }
    private static void write(String name, String value) {
        try { Files.writeString(path(name), value); } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static String read(String name) {
        try { return Files.readString(path(name)); } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
