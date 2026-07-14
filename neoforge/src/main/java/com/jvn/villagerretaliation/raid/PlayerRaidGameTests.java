package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class PlayerRaidGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private PlayerRaidGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void raidSnapshotRoundTrips(GameTestHelper helper) {
        PlayerRaidSavedData data = new PlayerRaidSavedData();
        VillageAllegianceId village = VillageAllegianceId.random();
        UUID player = UUID.randomUUID();
        UUID raiderVillager = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID defector = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                village, helper.getLevel().dimension().location(), new BlockPos(4, 2, 4),
                Set.of(SectionPos.asLong(new BlockPos(4, 2, 4))), "Test Village", player, UUID.randomUUID(),
                Set.of(player), Set.of(raiderVillager), Set.of(defender, defector), Set.of(defector), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 242L);
        raid.setGolemBudget(4);
        raid.addSpawnedGolems(1);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        PlayerRaidSavedData loaded = PlayerRaidSavedData.load(saved, helper.getLevel().registryAccess());
        PlayerRaidSavedData.RaidRecord restored = loaded.raid(raid.id());
        helper.assertTrue(restored != null, "raid identity should survive save/load");
        helper.assertValueEqual(restored.villageId(), village, "village id");
        helper.assertValueEqual(restored.phase(), PlayerRaidSavedData.Phase.ACTIVE, "active phase");
        helper.assertValueEqual(restored.raiderPlayers(), Set.of(player), "snapshotted players");
        helper.assertValueEqual(restored.defenders(), Set.of(defender, defector), "snapshotted defenders");
        helper.assertValueEqual(restored.defectors(), Set.of(defector), "defector set");
        helper.assertValueEqual(restored.initialDefenderCount(), 2, "initial objective count");
        helper.assertValueEqual(restored.golemBudget(), 4, "fixed golem budget");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void betrayalPenaltyAndGolemBudgetAreBounded(GameTestHelper helper) {
        helper.assertValueEqual(PlayerRaidService.betrayalReputation(100), -250, "positive reputation floors at -250");
        helper.assertValueEqual(PlayerRaidService.betrayalReputation(-250), -250, "exact floor is stable");
        helper.assertValueEqual(PlayerRaidService.betrayalReputation(-400), -650, "worse reputation loses another 250");
        helper.assertValueEqual(PlayerRaidService.calculateGolemBudget(17, 5, 1, 8, 2, 1, 6), 4,
                "ceil defenders plus raider bonus less existing golems");
        helper.assertValueEqual(PlayerRaidService.calculateGolemBudget(1, 1, 6, 8, 2, 1, 6), 0,
                "existing aligned golems can exhaust the fixed budget");
        helper.succeed();
    }
}
