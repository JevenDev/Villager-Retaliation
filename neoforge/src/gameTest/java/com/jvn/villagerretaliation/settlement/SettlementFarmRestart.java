package com.jvn.villagerretaliation.settlement;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class SettlementFarmRestart {
    private static final String OUTPUT = "villagerretaliation.farmRestart";
    private static final int PHASE = Integer.getInteger("villagerretaliation.farmRestartPhase", 0);
    private static final BlockPos BASE = new BlockPos(1024, -59, 1024);
    private record Fixture(UUID town, UUID farmer, UUID active, UUID queued, UUID cancelled, int water, int soil, int damage) {}
    private static Fixture fixture;
    private static boolean initialized, configured, done, resumed;
    private static int ticks;
    private SettlementFarmRestart() {}

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null) return;
        var minecraft = Minecraft.getInstance();
        if (!configured && minecraft.player != null) {
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.renderDistance().set(3);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.framerateLimit().set(30);
            minecraft.options.enableVsync().set(false);
            GLFW.glfwHideWindow(minecraft.getWindow().getWindow());
            configured = true;
        }
        if (done) minecraft.stop();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void server(ServerTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || done || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        var level = event.getServer().overworld();
        var owner = event.getServer().getPlayerList().getPlayers().getFirst();
        if (!initialized) {
            initialized = true;
            owner.setGameMode(GameType.CREATIVE);
            owner.connection.teleport(BASE.getX() - 3, BASE.getY() + 1, BASE.getZ() - 2, 0, 0);
            if (PHASE == 1) prepare(level, owner);
            else fixture = read();
        }
        require(++ticks < 3000, "farm restart phase " + PHASE + " finishes");
        var town = SettlementSavedData.get(level).get(new VillageAllegianceId(fixture.town())).orElseThrow();
        require(!SettlementSavedData.get(level).readOnly(), "farm save loads without recovery mode");
        var job = town.farmJobs().get(fixture.active());
        var queued = town.farmJobs().get(fixture.queued());
        require(queued.paused() && queued.status() == SettlementProject.Status.PENDING, "paused queued job survives every process restart");
        if (PHASE == 4) {
            require(town.farmJobs().get(fixture.cancelled()).status() == SettlementProject.Status.CANCELLED, "unloaded cancellation survives restarting");
            if (!resumed) {
                resumed = true;
                var farmer = EntityType.VILLAGER.create(level);
                try { farmer.load(NbtIo.readCompressed(path("farmer.nbt"), NbtAccounter.unlimitedHeap())); }
                catch (IOException exception) { throw new UncheckedIOException(exception); }
                level.addFreshEntity(farmer);
                SettlementBackgroundService.onActive(farmer);
            }
            var farmer = (Villager) level.getEntity(fixture.farmer());
            if (farmer == null || ticks < 60) return;
            require(HiredTemporaryWorkService.queue(HiredWorkStateStore.state(farmer)) == null, "stale entity cannot resume cancelled preparation");
            require(SettlementFarmAcceptanceGameTests.bucketCount(HiredJobInventory.getJobInventory(farmer)) == 1, "entity return conserves the bucket");
            finish();
            return;
        }
        var farmer = level.getEntity(fixture.farmer()) instanceof Villager loaded ? loaded : null;
        if (farmer == null) return;
        var inventory = HiredJobInventory.getJobInventory(farmer);
        require(SettlementFarmAcceptanceGameTests.bucketCount(inventory) == 1, "bucket count survives process restarts");
        if (PHASE == 1) {
            if (job.done(true) < 1) return;
            require(SettlementFarmJobService.configure(owner, town, job.id(), job.revision(), 7, true, SettlementFarmJob.AUTOMATIC), "partial preparation pauses");
            remember(job, inventory, null);
            finish();
        } else if (PHASE == 2) {
            if (!resumed) {
                require(job.paused() && job.done(true) == fixture.water() && job.done(false) == fixture.soil(), "partial progress survives a cold start");
                require(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue() == fixture.damage(), "restart does not replay tool damage");
                require(SettlementFarmJobService.configure(owner, town, job.id(), job.revision(), 7, false, SettlementFarmJob.AUTOMATIC), "saved preparation resumes");
                resumed = true;
            }
            if (job.unfinished()) return;
            require(job.status() == SettlementProject.Status.COMPLETED && job.done(true) == 2, "restored farmer finishes both irrigation trips");
            remember(job, inventory, null);
            finish();
        } else {
            require(job.status() == SettlementProject.Status.COMPLETED && job.done(false) == fixture.soil(), "completion survives the second restart");
            require(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue() == fixture.damage(), "completed work does not replay");
            if (ticks < 60) return;
            var farm = new SettlementFarm(BASE.south(5), BASE.offset(2, 0, 7));
            String error = SettlementFarmWork.start(owner, town, SettlementFarmTerrain.preview(level, farm, town::contains), farm);
            require(error.isEmpty(), "another plot starts after restart: " + error);
            var active = town.farmJobs().values().stream().filter(candidate -> candidate.status() == SettlementProject.Status.ACTIVE).findFirst().orElseThrow();
            try { NbtIo.writeCompressed(farmer.saveWithoutId(new CompoundTag()), path("farmer.nbt")); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            SettlementCareService.onUnloaded(farmer);
            farmer.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            require(SettlementFarmJobService.cancel(owner, town, active.id()), "unloaded job cancels before saving");
            remember(job, inventory, active.id());
            finish();
        }
    }

    private static void prepare(ServerLevel level, ServerPlayer owner) {
        var setup = SettlementFarmAcceptanceGameTests.fixture(level, owner, BASE, 10, 2, false);
        var inventory = HiredJobInventory.getJobInventory(setup.farmer());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.BUCKET));
        String error = SettlementFarmWork.start(owner, setup.town(), SettlementFarmTerrain.preview(level, setup.farm(), setup.town()::contains), setup.farm());
        require(error.isEmpty(), "restart fixture starts: " + error);
        var active = setup.town().farmJobs().values().iterator().next();
        var next = new SettlementFarm(BASE.east(12), BASE.offset(14, 0, 2));
        require(SettlementFarmWork.start(owner, setup.town(), SettlementFarmTerrain.preview(level, next, setup.town()::contains), next).isEmpty(), "second plot queues");
        var queued = setup.town().farmJobs().values().stream().filter(job -> !job.id().equals(active.id())).findFirst().orElseThrow();
        require(SettlementFarmJobService.configure(owner, setup.town(), queued.id(), queued.revision(), 3, true, SettlementFarmJob.AUTOMATIC), "queued job pauses");
        fixture = new Fixture(setup.town().id().value(), setup.farmer().getUUID(), active.id(), queued.id(), null, 0, 0, 0);
    }

    private static void remember(SettlementFarmJob job, HiredJobInventory inventory, UUID cancelled) {
        fixture = new Fixture(fixture.town(), fixture.farmer(), fixture.active(), fixture.queued(), cancelled,
                job.done(true), job.done(false), inventory.getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue());
    }
    private static void finish() {
        try {
            Files.writeString(path("fixture.json"), new Gson().toJson(fixture));
            Files.writeString(path("phase-" + PHASE + ".txt"), "Farm restart phase " + PHASE + " passed");
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        done = true;
    }
    private static Fixture read() {
        try { return new Gson().fromJson(Files.readString(path("fixture.json")), Fixture.class); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static Path path(String file) { return Path.of(System.getProperty(OUTPUT), file); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
