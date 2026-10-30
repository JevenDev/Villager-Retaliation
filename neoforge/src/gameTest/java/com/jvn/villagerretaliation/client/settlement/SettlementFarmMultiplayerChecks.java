package com.jvn.villagerretaliation.client.settlement;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.settlement.SettlementFarm;
import com.jvn.villagerretaliation.settlement.SettlementFarmJob;
import com.jvn.villagerretaliation.settlement.SettlementFarmJobService;
import com.jvn.villagerretaliation.settlement.SettlementFarmTerrain;
import com.jvn.villagerretaliation.settlement.SettlementFarmWork;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementTestPlots;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

final class SettlementFarmMultiplayerChecks {
    private record Fixture(UUID town, UUID job, UUID original, UUID replacement, long revision, UUID request) {}
    private static Fixture fixture;
    private SettlementFarmMultiplayerChecks() {}

    static boolean handles(String phase) { return phase.startsWith("farm_"); }

    static void prepare(ServerPlayer owner, ServerPlayer peer, SettlementRecord town) {
        require(town.setPermissions(owner.getUUID(), peer.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.BUILD)),
                "peer receives farm management authority");
        var base = town.center().offset(16, 0, 16);
        var level = owner.serverLevel();
        SettlementTestPlots.claim(level, town, base.offset(-1, -1, -1), base.offset(6, 3, 6));
        for (var pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(6, 3, 6)))
            level.setBlock(pos, pos.getY() <= base.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        var original = farmer(owner, town, base.offset(5, 1, 0));
        var farm = new SettlementFarm(base, base.offset(2, 0, 2));
        String error = SettlementFarmWork.start(owner, town, SettlementFarmTerrain.preview(level, farm, town::contains), farm);
        require(error.isEmpty(), "TCP farm job starts: " + error);
        var job = town.farmJobs().values().stream().filter(SettlementFarmJob::unfinished).findFirst().orElseThrow();
        var replacement = farmer(owner, town, base.offset(5, 1, 5));
        fixture = new Fixture(town.id().value(), job.id(), original.getUUID(), replacement.getUUID(), job.revision(), UUID.randomUUID());
        write();
        require(SettlementFarmJobService.configure(owner, town, job.id(), job.revision(), 7, false, SettlementFarmJob.AUTOMATIC),
                "host changes farm job before peer's stale request");
    }

    private static Villager farmer(ServerPlayer owner, SettlementRecord town, BlockPos pos) {
        var farmer = EntityType.VILLAGER.create(owner.serverLevel());
        farmer.setPos(pos.getBottomCenter());
        farmer.setNoAi(true);
        farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(owner.serverLevel(), farmer, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        require(owner.serverLevel().addFreshEntity(farmer), "TCP farmer is loaded");
        var inventory = HiredJobInventory.getJobInventory(farmer);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        return farmer;
    }

    static boolean client(String phase, int ticks) {
        if (phase.equals("farm_setup")) return ticks == 50;
        if (ticks == 10) {
            var observed = read();
            SettlementManagementActionPayload.Command command = phase.equals("farm_revoked") ? new SettlementManagementActionPayload.CancelProject(observed.job())
                    : new SettlementManagementActionPayload.ConfigureFarmJob(observed.job(), observed.revision(), 4, true, observed.replacement());
            PacketDistributor.sendToServer(new SettlementManagementActionPayload(observed.town(), command, observed.request()));
        }
        return ticks == 50;
    }

    static String server(ServerPlayer owner, ServerPlayer peer, SettlementRecord town, String phase, String ack) {
        if (!ack.equals(phase + "_done")) return null;
        if (phase.equals("farm_setup")) {
            prepare(owner, peer, town);
            return "farm_stale";
        }
        var job = town.farmJobs().get(fixture.job());
        if (phase.equals("farm_stale")) {
            require(job.priority() == 7 && !job.paused() && job.revision() == 1, "stale TCP edit cannot overwrite host farm settings");
            fixture = new Fixture(fixture.town(), fixture.job(), fixture.original(), fixture.replacement(), job.revision(), UUID.randomUUID());
            write();
            return "farm_apply";
        }
        if (phase.equals("farm_apply")) {
            require(job.priority() == 4 && job.paused() && job.revision() == 2 && job.requested().equals(fixture.replacement()),
                    "current TCP edit pauses and requests the replacement farmer");
            require(!SettlementFarmJobService.configure(owner, town, job.id(), 1, 9, false, fixture.original()),
                    "host cannot overwrite the peer's newer reassignment");
            return "farm_replay";
        }
        if (phase.equals("farm_replay")) {
            require(job.revision() == 2 && job.paused(), "replayed TCP request does not apply farm controls twice");
            require(SettlementFarmJobService.configure(owner, town, job.id(), job.revision(), 4, false, fixture.replacement()), "host resumes the requested replacement");
            require(job.worker().equals(fixture.replacement()), "only the replacement farmer owns the resumed task");
            require(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.queue(HiredWorkStateStore.state(
                    (Villager) owner.serverLevel().getEntity(fixture.original()))) == null, "former farmer has no stale preparation queue");
            fixture = new Fixture(fixture.town(), fixture.job(), fixture.original(), fixture.replacement(), job.revision(), UUID.randomUUID());
            write();
            require(town.setPermissions(owner.getUUID(), peer.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF)),
                    "host revokes Build before the peer cancels");
            return "farm_revoked";
        }
        require(job.unfinished() && job.worker().equals(fixture.replacement()), "revoked Build rejects farm cancellation over TCP");
        require(SettlementFarmJobService.cancel(owner, town, job.id()), "host can cancel after the peer loses authority");
        return "disconnect";
    }

    private static Path path() { return Path.of(System.getProperty("villagerretaliation.settlementMultiplayer"), "farm-fixture.json"); }
    private static void write() {
        try { Files.writeString(path(), new Gson().toJson(fixture)); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static Fixture read() {
        try { return new Gson().fromJson(Files.readString(path()), Fixture.class); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
