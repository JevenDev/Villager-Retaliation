package com.jvn.villagerretaliation.settlement;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Small isolated suite for construction storage access, also covered by settlement_storage. */
@GameTestHolder("settlement_storage_roles")
@PrefixGameTestTemplate(false)
public final class SettlementStorageRoleGameTests {
    private SettlementStorageRoleGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage_roles")
    public static void builderOnlyDepotsExcludeFoodAndBackgroundProduction(GameTestHelper helper) {
        SettlementStorageGameTests.builderOnlyDepotsExcludeFoodAndBackgroundProduction(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage_roles")
    public static void changingDepotPurposeRevokesLoadedWorkerAccess(GameTestHelper helper) {
        SettlementStorageGameTests.changingDepotPurposeRevokesLoadedWorkerAccess(helper);
    }
}
