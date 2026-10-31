package com.jvn.villagerretaliation.util;

import com.jvn.toucanlib.riding.ToucanPassengerApi;
import com.jvn.toucanlib.riding.ToucanPassengerLayout;
import com.jvn.toucanlib.riding.ToucanPassengerLayouts;
import com.jvn.toucanlib.riding.ToucanPassengerSeat;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("shared_utilities")
@PrefixGameTestTemplate(false)
public final class ToucanPassengerGameTests {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("shared_utilities", "seats");
    private static final ToucanPassengerLayout LAYOUT = new ToucanPassengerLayout() {
        public int seatCapacity(Entity vehicle) { return 2; }
        public Vec3 seatOffset(Entity vehicle, Entity passenger, ToucanPassengerSeat seat) { return Vec3.ZERO; }
    };

    @GameTest(template = "empty")
    public static void vetoedDriverSwapRestoresOccupants(GameTestHelper helper) {
        verifyVeto(helper, false);
    }

    @GameTest(template = "empty")
    public static void vetoedRollbackReportsIncompleteRecovery(GameTestHelper helper) {
        verifyVeto(helper, true);
    }

    private static void verifyVeto(GameTestHelper helper, boolean vetoRecovery) {
        ToucanPassengerLayouts.register(ID, LAYOUT);
        var horse = helper.spawn(EntityType.HORSE, 1, 1, 1);
        var driver = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        var taker = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        helper.assertTrue(ToucanPassengerApi.tryMount(ID, horse, driver, ToucanPassengerSeat.DRIVER), "driver mounted");
        Consumer<EntityMountEvent> veto = event -> {
            if (event.getEntityBeingMounted() == horse && event.isMounting()
                    && (event.getEntityMounting() == taker || vetoRecovery && event.getEntityMounting() == driver))
                event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            var result = ToucanPassengerApi.takeDriverSeat(ID, horse, taker);
            helper.assertValueEqual(result, vetoRecovery ? ToucanPassengerApi.SeatChangeResult.RECOVERY_FAILED
                    : ToucanPassengerApi.SeatChangeResult.REFUSED, "recovery result distinguishes event vetoes");
            helper.assertTrue(taker.getVehicle() == null, "refused taker remains unmounted");
            if (!vetoRecovery) helper.assertTrue(driver.getVehicle() == horse, "original driver restored");
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void passengerMetadataSurvivesSaveLoad(GameTestHelper helper) {
        ToucanPassengerLayouts.register(ID, LAYOUT);
        var horse = helper.spawn(EntityType.HORSE, 1, 1, 1);
        var rider = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        var offset = new Vec3(.1, .2, .3);
        helper.assertTrue(ToucanPassengerApi.tryMount(ID, horse, rider, ToucanPassengerSeat.DRIVER, offset), "rider mounted");
        var saved = new CompoundTag();
        rider.saveWithoutId(saved);
        var loaded = EntityType.VILLAGER.create(helper.getLevel());
        loaded.load(saved);
        helper.assertValueEqual(ToucanPassengerApi.layoutOf(loaded), ID, "layout persists");
        helper.assertValueEqual(ToucanPassengerApi.customOffsetOf(loaded), offset, "offset persists");
        helper.succeed();
    }
}
