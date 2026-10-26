package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SettlementCourierClientFixture {
    private SettlementCourierClientFixture() { }
    public static void seed(ServerLevel level, VillageAllegianceId source, VillageAllegianceId target) {
        var data = SettlementSavedData.get(level); var registry = VillageAllegianceRegistrySavedData.get(level);
        var home = data.get(source).orElseThrow(); var destination = registry.canonicalRecord(target).orElseThrow();
        var recipient = data.establish(registry, target, UUID.randomUUID(), destination.center(), level.getGameTime()).orElseThrow();
        var origin = home.center(); var from = origin.offset(4, 0, 0);
        var courier = EntityType.VILLAGER.create(level); courier.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        VillageAllegianceApi.assignKnown(level, courier, home.id(), AllegianceAssignmentSource.EXPLICIT_API);
        home.recordResident(SettlementResidentState.capture(courier, home, level.getGameTime()));
        long now = level.getServer().overworld().getGameTime();
        var request = new SettlementFoodRequest(UUID.randomUUID(), target, source, 100, now, now + 20, now + 24000, recipient.leader());
        request.donorAuthority = home.leader(); request.maximumItems = 20;
        UUID depot = UUID.randomUUID(), receiving = UUID.randomUUID();
        request.preparation = new SettlementAidPreparation(depot, receiving, from, recipient.center(), false,
                new SettlementAidPreparation.Candidate(courier.getUUID(), origin), null);
        request.offer = new SettlementFoodOffer(SettlementFoodOffer.Arrangement.DONOR_DELIVERY, courier.getUUID(),
                List.of(new SettlementFoodOffer.Parcel(depot, new ItemStack(Items.BREAD, 20))));
        request.offer.custody = SettlementFoodOffer.Custody.COURIER; request.state = SettlementFoodRequest.State.TRAVELLING;
        var journey = new SettlementCourierJourney(request.id(), courier.getUUID(), source, level.dimension().location(),
                SettlementCourierGameTests.route(origin, from), SettlementCourierGameTests.route(from, recipient.center()),
                courier.saveWithoutId(new CompoundTag()), 1, ItemStack.EMPTY, now);
        journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.step = 56; journey.position = journey.route().steps().get(journey.step).position();
        journey.presence = SettlementCourierJourney.Presence.STRANDED; journey.obstructed = true; journey.blocker = "courier_path_obstructed";
        request.blocker = journey.blocker; data.aid().requests.put(request.id(), request); data.aid().journey(request, journey);
        home.residents().get(courier.getUUID()).courier(request.id(), 1, true); data.setDirty();
    }
}
