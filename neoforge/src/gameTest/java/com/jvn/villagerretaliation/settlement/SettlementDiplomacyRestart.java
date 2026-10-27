package com.jvn.villagerretaliation.settlement;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData;
import com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class SettlementDiplomacyRestart {
    private static final String OUTPUT = "villagerretaliation.diplomacyRestart";
    private static final int PHASE = Integer.getInteger("villagerretaliation.diplomacyRestartPhase", 0);
    private static Manifest manifest;
    private static int ticks;
    private static boolean initialized, done, configured, advanced;
    private static long checkpoint = -1;
    private record Entry(UUID request, UUID resident, UUID boat) { }
    private record Manifest(List<Entry> entries, long savedAt) { }
    private record Town(SettlementRecord town, SettlementDepot depot) { }
    private SettlementDiplomacyRestart() { }

    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null) return;
        var minecraft = Minecraft.getInstance(); minecraft.options.pauseOnLostFocus = false;
        if (!configured && minecraft.player != null) {
            minecraft.options.renderDistance().set(3); minecraft.options.simulationDistance().set(5);
            minecraft.options.framerateLimit().set(30); minecraft.options.enableVsync().set(false);
            GLFW.glfwHideWindow(minecraft.getWindow().getWindow()); configured = true;
        }
        if (done) minecraft.stop();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void server(ServerTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || done || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        var level = event.getServer().overworld(); var player = event.getServer().getPlayerList().getPlayers().getFirst();
        if (!initialized) {
            initialized = true; player.setGameMode(GameType.CREATIVE);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer()); level.setDayTime(6000);
            if (PHASE == 1) prepare(level);
            else {
                manifest = new Gson().fromJson(read("fixture.json"), Manifest.class);
                require(level.getGameTime() >= manifest.savedAt && level.getGameTime() - manifest.savedAt < 200,
                        "server time resumes without advancing for process downtime");
                verify(level);
            }
            player.connection.teleport(1024.5, -60, 1028.5, 0, 0);
        }
        ticks++; require(ticks < 1200, "restart phase finishes: " + PHASE);
        var data = SettlementSavedData.get(level);
        var physical = data.aid().request(manifest.entries.get(2).request).journey;
        if (!(level.getEntity(physical.resident) instanceof Villager courier)) return;
        require(courier.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "native resident equipment survives restart");
        require(courier.getHealth() >= 12 && courier.getHealth() <= courier.getMaxHealth(), "native resident health is preserved");
        require(courier.blockPosition().distSqr(physical.approach.steps().getFirst().position()) <= 16, "blocked courier does not escape across a restart");
        if (ticks < 60) return;
        if (PHASE >= 2 && !advanced) {
            advanced = true;
            for (int index = 0; index < 2; index++) {
                var request = data.aid().request(manifest.entries.get(index).request); var journey = request.journey;
                if (PHASE == 2) endLeg(level, request, SettlementCourierJourney.Leg.OUTBOUND);
                else if (PHASE == 3) endLeg(level, request, SettlementCourierJourney.Leg.HOMEBOUND);
                else endLeg(level, request, SettlementCourierJourney.Leg.RETURN_APPROACH);
            }
        }
        if (PHASE == 2 && !bothAt(level, SettlementCourierJourney.Leg.HOMEBOUND)) return;
        if (PHASE == 3 && !bothAt(level, SettlementCourierJourney.Leg.RETURN_APPROACH)) return;
        if (PHASE == 4 && manifest.entries.subList(0, 2).stream().anyMatch(entry -> data.aid().request(entry.request).state() != SettlementFoodRequest.State.COMPLETED)) return;
        if (PHASE >= 2 && !data.aid().receipts.isEmpty()) return;
        if (checkpoint < 0) { verify(level); checkpoint = data.aid().revision(); }
        if (!SettlementAidCheckpoint.durable(level, checkpoint)) return;
        manifest = new Manifest(manifest.entries, level.getGameTime());
        write("fixture.json", new Gson().toJson(manifest));
        write("phase-" + PHASE + ".txt", "Diplomacy restart phase " + PHASE + " passed at game time " + level.getGameTime() + "\n");
        done = true;
    }

    private static void prepare(ServerLevel level) {
        var entries = new ArrayList<Entry>();
        entries.add(shipment(level, new BlockPos(22000000, 64, 22000000), false, false));
        entries.add(shipment(level, new BlockPos(22001000, 64, 22000000), true, false));
        entries.add(shipment(level, new BlockPos(1024, -60, 1024), false, true));
        manifest = new Manifest(List.copyOf(entries), level.getGameTime());
    }
    private static Entry shipment(ServerLevel level, BlockPos origin, boolean collection, boolean physical) {
        var donor = town(level, origin.offset(4, 0, 0)); var recipient = town(level, origin.offset(132, 0, 0));
        var home = collection ? recipient : donor;
        var resident = EntityType.VILLAGER.create(level); resident.setUUID(UUID.randomUUID());
        var start = home.town.center().offset(-4, 0, 0); resident.moveTo(start.getX() + .5, start.getY(), start.getZ() + .5);
        resident.setHealth(12); resident.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        VillageAllegianceApi.assignKnown(level, resident, home.town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        home.town.recordResident(SettlementResidentState.capture(resident, home.town, level.getGameTime()));
        donor.depot.setItem(0, new ItemStack(Items.BREAD, 20));
        var boat = new ItemStack(Items.BIRCH_BOAT); boat.set(DataComponents.CUSTOM_NAME, Component.literal("Restart ferry")); home.depot.setItem(1, boat.copy());
        long now = level.getGameTime();
        var request = new SettlementFoodRequest(UUID.randomUUID(), recipient.town.id(), donor.town.id(), 100, now, now + 20, now + 24000, recipient.town.leader());
        request.donorAuthority = donor.town.leader(); request.maximumItems = 20;
        request.offer = new SettlementFoodOffer(collection ? SettlementFoodOffer.Arrangement.REQUESTER_COLLECTION : SettlementFoodOffer.Arrangement.DONOR_DELIVERY,
                resident.getUUID(), List.of(new SettlementFoodOffer.Parcel(donor.depot.id(), donor.depot.withdraw(item -> item.is(Items.BREAD), 20))));
        request.preparation = new SettlementAidPreparation(donor.depot.id(), recipient.depot.id(), donor.town.center(), recipient.town.center(), true,
                collection ? null : new SettlementAidPreparation.Candidate(resident.getUUID(), start), collection ? new SettlementAidPreparation.Candidate(resident.getUUID(), start) : null);
        request.state = SettlementFoodRequest.State.TRAVELLING;
        var data = SettlementSavedData.get(level); data.aid().requests.put(request.id(), request);
        var approach = SettlementCourierGameTests.route(start, home.town.center());
        var crossing = SettlementCourierGameTests.route(home.town.center(), collection ? donor.town.center() : recipient.town.center());
        AidRouteFixture.flat(level, origin, recipient.town.center());
        resident.getPersistentData().putUUID(SettlementCourierService.JOURNEY, request.id()); resident.getPersistentData().putLong(SettlementCourierService.INCARNATION, 1);
        var journey = new SettlementCourierJourney(request.id(), resident.getUUID(), home.town.id(), level.dimension().location(), approach, crossing,
                resident.saveWithoutId(new CompoundTag()), 1, home.depot.withdraw(item -> item.is(Items.BIRCH_BOAT), 1), now);
        journey.presence = physical ? SettlementCourierJourney.Presence.PHYSICAL : SettlementCourierJourney.Presence.STORED;
        journey.obstructed = !physical;
        if (!physical) {
            journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = journey.route().steps().size() - 2;
            journey.position = journey.route().steps().get(journey.step).position();
            if (!collection) request.offer.custody = SettlementFoodOffer.Custody.COURIER;
        }
        home.town.residents().get(resident.getUUID()).courier(request.id(), 1, true); data.aid().journey(request, journey);
        SettlementCourierService.changed(level, request);
        if (physical) {
            for (var position : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(6, 2, 2)))
                level.setBlock(position, (position.getY() < origin.getY() || position.getX() == origin.getX() + 1 || position.getX() == origin.getX() - 2 || position.getZ() == origin.getZ() - 2 || position.getZ() == origin.getZ() + 2 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            require(level.addFreshEntity(resident), "native courier is spawned once");
        }
        return new Entry(request.id(), resident.getUUID(), journey.boatId);
    }
    private static Town town(ServerLevel level, BlockPos center) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Restart village");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Food store", center, center.offset(2, 2, 2), 5, true);
        require(town.putArea(town.leader(), area), "registered depot area persists");
        var depot = town.registerDepot(town.leader(), area.id(), center, SettlementDepotPurpose.GENERAL); depot.observeRules(List.of());
        return new Town(town, depot);
    }
    private static void endLeg(ServerLevel level, SettlementFoodRequest request, SettlementCourierJourney.Leg leg) {
        var journey = request.journey; require(journey.leg == leg, "expected persisted leg " + leg);
        journey.step = journey.route().steps().size() - 1; journey.position = journey.route().steps().getLast().position();
        SettlementCourierService.changed(level, request);
    }
    private static boolean bothAt(ServerLevel level, SettlementCourierJourney.Leg leg) {
        return manifest.entries.subList(0, 2).stream().allMatch(entry -> SettlementSavedData.get(level).aid().request(entry.request).journey.leg == leg);
    }
    private static void verify(ServerLevel level) {
        var data = SettlementSavedData.get(level); require(!data.readOnly() && data.aid().requests().size() == 3, "all shipment records survive process restart");
        for (int index = 0; index < 3; index++) {
            var entry = manifest.entries.get(index); var request = data.aid().request(entry.request); var journey = request.journey;
            require(journey.resident.equals(entry.resident) && journey.boatId.equals(entry.boat), "resident and boat identities remain stable");
            var home = data.get(journey.home).orElseThrow(); var recipient = data.get(request.requester()).orElseThrow(); var donor = data.get(request.donor()).orElseThrow();
            require(home.residents().containsKey(entry.resident), "the original resident remains in its home census");
            long stock = recipient.depots().values().stream().mapToLong(depot -> depot.count(item -> item.is(Items.BREAD))).sum()
                    + donor.depots().values().stream().mapToLong(depot -> depot.count(item -> item.is(Items.BREAD))).sum();
            long cargo = request.offer.remaining().stream().mapToLong(parcel -> parcel.item().getCount()).sum();
            require(stock + cargo == 20, "food is conserved across the depot and shipment boundary");
            int storedBoats = home.depots().values().stream().mapToInt(depot -> depot.count(item -> item.is(Items.BIRCH_BOAT))).sum();
            require(storedBoats + (journey.boatCustody == SettlementCourierJourney.BoatCustody.RETURNED ? 0 : 1) == 1, "one real boat survives custody and return");
            require(journey.boat.getHoverName().getString().equals("Restart ferry"), "boat components survive restart");
            if (index < 2 && request.offer.empty()) require(request.deliveredNutrition() == 100, "only the delivered food earns credit");
            if (index < 2 && data.aid().receipts.isEmpty()) {
                int expected = request.deliveredNutrition() == 100 ? 3 : 0;
                require(VillageDiplomacySavedData.get(level).standing(donor.id(), recipient.id()) == expected, "delivery goodwill is applied exactly once");
            }
            if (PHASE == 4 && advanced && index < 2) require(!home.residents().get(entry.resident).courierAway(), "completed journeys restore their existing residents");
        }
    }
    private static Path path(String name) { return Path.of(System.getProperty(OUTPUT), name); }
    private static String read(String name) { try { return Files.readString(path(name)); } catch (IOException failure) { throw new UncheckedIOException(failure); } }
    private static void write(String name, String value) { try { Files.writeString(path(name), value); } catch (IOException failure) { throw new UncheckedIOException(failure); } }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
