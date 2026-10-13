package com.jvn.villagerretaliation.settlement;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused compatibility checks for pre-escrow projects and existing housing permissions. */
@GameTestHolder("settlement_construction_regression")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionRegressionGameTests {
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void legacyCrewSaveLoad(GameTestHelper helper) {
        SettlementConstructionExecutionGameTests.backgroundCrewConsumesFiniteMaterialsAcrossReload(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void legacyNativeCrew(GameTestHelper helper) {
        SettlementConstructionExecutionGameTests.nativeCrewSharesProgressAndUsesProjectBounds(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void legacyDoorAccounting(GameTestHelper helper) {
        SettlementConstructionExecutionGameTests.crewPlacesDoorPairsWithOneItemAndReceiptPerDoor(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void blueprintAuthority(GameTestHelper helper) {
        SettlementBlueprintGameTests.placementRespectsHeldIdentityLocksAndConstructionAuthority(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void blueprintRotation(GameTestHelper helper) {
        SettlementBlueprintGameTests.remoteBlueprintPlacementRotatesWithoutLoadingOrChangingTheWorld(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void housingAuthority(GameTestHelper helper) {
        SettlementHousingGameTests.housingRequiresStaffPermissionAndCurrentMembership(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void housingPersonalContracts(GameTestHelper helper) {
        SettlementHousingGameTests.housingIncludesChildrenAndPreservesPersonalContracts(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void housingSavedClaims(GameTestHelper helper) {
        SettlementHousingGameTests.savedHomeAssignmentsRejectDuplicateBedsAndRetainLegacyCompatibility(helper);
    }
}
