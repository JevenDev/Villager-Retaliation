package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.OptionalInt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerFilterPolicyGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String POLICY_ROOT = VillagerRetaliation.MOD_ID + ":filter_policy";

    private VillagerFilterPolicyGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void legacyPolicyMigratesWithoutReinterpretingEntryAmounts(GameTestHelper helper) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.EMERALD));
        VillagerItemFilterData.setAmount(filter, 0, 32);
        VillagerFilterPolicy.Policy legacy = VillagerFilterPolicy.read(filter);
        helper.assertTrue(legacy.state() == VillagerFilterPolicy.PolicyState.LEGACY,
                "filters without a policy tag must use legacy behavior");
        helper.assertTrue(legacy.direction() == VillagerFilterPolicy.TransferDirection.RECEIVE,
                "legacy item-frame filters must remain receive-side filters");
        helper.assertTrue(legacy.stockTarget().isEmpty(),
                "legacy entry amounts must not become a shared stock target");
        helper.assertTrue(VillagerItemFilterData.amountLimit(filter, new ItemStack(Items.EMERALD)) == 32,
                "legacy entry quantities must remain intact");

        helper.assertTrue(VillagerFilterPolicy.setDirection(
                        filter, VillagerFilterPolicy.TransferDirection.BOTH),
                "editing a legacy filter should author the current schema");
        helper.assertTrue(VillagerFilterPolicy.setStockTarget(filter, 512),
                "a valid shared stock target should be accepted");
        VillagerFilterPolicy.Policy explicit = VillagerFilterPolicy.read(filter);
        helper.assertTrue(explicit.state() == VillagerFilterPolicy.PolicyState.EXPLICIT,
                "edited filters must use the current schema");
        helper.assertTrue(explicit.stockTarget().orElse(-1) == 512,
                "the authored stock target should round-trip");

        ItemStack copied = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.copyConfiguration(filter, copied);
        helper.assertTrue(VillagerFilterPolicy.read(copied).equals(explicit),
                "configuration copies must preserve the policy schema");
        helper.assertTrue(VillagerItemFilterData.amountLimit(copied, new ItemStack(Items.EMERALD)) == 32,
                "configuration copies must preserve legacy matcher quantities");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void malformedPoliciesFailClosedAndAuthoritativeChangesClamp(GameTestHelper helper) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        writeRawPolicy(filter, "sideways", "allow_matching", "match_any", 64);
        VillagerFilterPolicy.Policy malformed = VillagerFilterPolicy.read(filter);
        helper.assertFalse(malformed.valid(), "unknown directions must be rejected");
        helper.assertTrue(VillagerFilterPolicy.receiveAllowance(malformed, 0, 0) == 0,
                "malformed policies must fail closed");

        writeRawPolicy(filter, "both", "allow_matching", "match_any", 1001);
        helper.assertFalse(VillagerFilterPolicy.read(filter).valid(),
                "out-of-range stored targets must be rejected");
        helper.assertTrue(VillagerFilterPolicy.TransferDirection.fromNetworkId(99) == null,
                "invalid direction packet values must be rejected");
        helper.assertTrue(VillagerFilterPolicy.ListMode.fromNetworkId(99) == null,
                "invalid list-mode packet values must be rejected");
        helper.assertTrue(VillagerFilterPolicy.CombinationMode.fromNetworkId(99) == null,
                "invalid combination packet values must be rejected");
        helper.assertTrue(VillagerFilterPolicy.setStockTarget(filter, 5000),
                "server-side edits should repair malformed data");
        helper.assertTrue(VillagerFilterPolicy.read(filter).stockTarget().orElse(-1)
                        == VillagerFilterPolicy.MAX_STOCK_TARGET,
                "server-side targets must clamp to the supported maximum");
        helper.assertTrue(VillagerFilterPolicy.setStockTarget(filter, 0),
                "zero should author an unlimited target");
        helper.assertTrue(VillagerFilterPolicy.read(filter).stockTarget().isEmpty(),
                "an unlimited target should omit the stock value");
        helper.assertFalse(VillagerFilterPolicy.setDirection(filter, null),
                "null direction changes must be rejected");
        helper.assertFalse(VillagerFilterPolicy.setCombinationMode(
                        filter, VillagerFilterPolicy.CombinationMode.LEGACY),
                "legacy composition must never be authorable");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void stockAllowancesRespectDirectionReservationsAndClaims(GameTestHelper helper) {
        VillagerFilterPolicy.Policy both = explicitPolicy(
                VillagerFilterPolicy.TransferDirection.BOTH,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING, 128);
        helper.assertTrue(VillagerFilterPolicy.receiveAllowance(both, 100, 10) == 18,
                "receive allowance must subtract stored stock and inbound reservations");
        helper.assertTrue(VillagerFilterPolicy.provideAllowance(both, 150, 5) == 17,
                "provide allowance must preserve the target and subtract outbound claims");
        helper.assertTrue(VillagerFilterPolicy.receiveAllowance(both, 128, 0) == 0,
                "reached targets must block more receiving");

        VillagerFilterPolicy.Policy receiveOnly = explicitPolicy(
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING, 64);
        helper.assertTrue(VillagerFilterPolicy.provideAllowance(receiveOnly, 100, 0) == 0,
                "receive-only filters must not provide items");
        VillagerFilterPolicy.Policy deny = explicitPolicy(
                VillagerFilterPolicy.TransferDirection.BOTH,
                VillagerFilterPolicy.ListMode.DENY_MATCHING, 64);
        helper.assertTrue(VillagerFilterPolicy.receiveAllowance(deny, 0, 0)
                        == VillagerFilterPolicy.UNLIMITED_ALLOWANCE,
                "deny rules must not apply quantitative targets");
        helper.succeed();
    }

    private static VillagerFilterPolicy.Policy explicitPolicy(
            VillagerFilterPolicy.TransferDirection direction,
            VillagerFilterPolicy.ListMode mode,
            int target) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerFilterPolicy.setPolicy(filter, direction, mode,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY, OptionalInt.of(target));
        return VillagerFilterPolicy.read(filter);
    }

    private static void writeRawPolicy(
            ItemStack filter, String direction, String listMode, String combination, int target) {
        CompoundTag policy = new CompoundTag();
        policy.putInt("Version", VillagerFilterPolicy.CURRENT_SCHEMA_VERSION);
        policy.putString("Direction", direction);
        policy.putString("ListMode", listMode);
        policy.putString("Combination", combination);
        policy.putInt("StockTarget", target);
        CompoundTag customData = new CompoundTag();
        customData.put(POLICY_ROOT, policy);
        filter.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
    }
}
