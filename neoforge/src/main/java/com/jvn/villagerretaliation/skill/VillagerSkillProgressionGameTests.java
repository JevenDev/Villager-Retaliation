package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerSkillProgressionGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerSkillProgressionGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void progressionCurveCarriesXpAndClampsAtMaximum(GameTestHelper helper) {
        if (!(VillagerSkillProgressionService.requiredXp(80) > VillagerSkillProgressionService.requiredXp(20))) {
            helper.fail("High skills should require more XP");
            return;
        }

        VillagerProfile profile = profileWithSkill(1);
        VillagerSkillProgressionService.apply(profile, List.of(
                practice(100.0D, 1L)), 0L, 1L, 1.0D);
        if (profile.skills().get(VillagerSkill.MINING) <= 2) {
            helper.fail("Excess XP did not carry across thresholds");
            return;
        }

        VillagerProfile maximum = profileWithSkill(99);
        VillagerSkillProgressionService.apply(maximum, List.of(
                practice(1_000.0D, 2L)), 0L, 1L, 1.0D);
        if (maximum.skills().get(VillagerSkill.MINING) != 100 || maximum.skillPracticeXp(VillagerSkill.MINING) != 0.0D) {
            helper.fail("Maximum skill was not clamped and cleared");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void dailyAndRepetitionReductionsResetAndStayBounded(GameTestHelper helper) {
        VillagerProfile daily = profileWithSkill(10);
        double beforeCap = VillagerSkillProgressionService.apply(
                daily, List.of(practice(6.0D, 1L)), 4L, 1L, 1.0D).grantedXp();
        double afterCap = VillagerSkillProgressionService.apply(
                daily, List.of(practice(1.0D, 2L)), 4L, 2L, 1.0D).grantedXp();
        double nextDay = VillagerSkillProgressionService.apply(
                daily, List.of(practice(1.0D, 2L)), 5L, 3L, 1.0D).grantedXp();
        if (Math.abs(beforeCap - 6.0D) > 0.0001D || Math.abs(afterCap - 0.2D) > 0.0001D || Math.abs(nextDay - 1.0D) > 0.0001D) {
            helper.fail("Daily soft cap did not reduce and reset deterministically");
            return;
        }

        VillagerProfile repeated = profileWithSkill(10);
        for (int i = 0; i < 8; i++) {
            VillagerSkillProgressionService.apply(repeated, List.of(practice(0.1D, 77L)), 8L, i, 1.0D);
        }
        double reduced = VillagerSkillProgressionService.apply(
                repeated, List.of(practice(0.1D, 77L)), 8L, 9L, 1.0D).grantedXp();
        if (Math.abs(reduced - 0.035D) > 0.0001D) {
            helper.fail("Repeated equivalent work was not reduced");
            return;
        }
        for (int i = 0; i < 100; i++) {
            VillagerSkillProgressionService.apply(repeated, List.of(practice(0.001D, 1_000L + i)), 9L, 20L + i, 1.0D);
        }
        if (repeated.repetitionKeyCount(VillagerSkill.MINING, 9L) > VillagerSkillProgressionService.MAX_REPETITION_KEYS_PER_SKILL) {
            helper.fail("Repetition keys exceeded their bound");
            return;
        }

        VillagerProfile loaded = VillagerProfile.load(repeated.save());
        if (loaded == null
                || Math.abs(loaded.skillPracticeXp(VillagerSkill.MINING) - repeated.skillPracticeXp(VillagerSkill.MINING)) > 0.0001D
                || loaded.repetitionKeyCount(VillagerSkill.MINING, 9L) != repeated.repetitionKeyCount(VillagerSkill.MINING, 9L)
                || !VillagerProfileManager.exportProfile(loaded).contains("skillPracticeDailyState")) {
            helper.fail("Practice XP or daily state did not survive save/load");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void workResultsAndNormalizationRequireExplicitSuccessfulPractice(GameTestHelper helper) {
        if (!WorkResult.idle("idle").practice().isEmpty()
                || !WorkResult.progressed("moving").practice().isEmpty()
                || !WorkResult.completed("done_without_work").practice().isEmpty()) {
            helper.fail("Idle, movement, and no-practice completion results should be empty");
            return;
        }
        WorkResult practiced = WorkResult.progressedWithPractice("worked", HiredWorkPractice.farming("plant"));
        if (practiced.practice().isEmpty()) {
            helper.fail("Explicit practice was discarded");
            return;
        }

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        double oneBlock = HiredWorkPractice.mining(helper.getLevel(), pos, Blocks.STONE.defaultBlockState())
                .stream().mapToDouble(VillagerSkillPractice::units).sum();
        double twoBlocks = oneBlock * 2.0D;
        double smallTree = HiredWorkPractice.logging(2).getFirst().units();
        double largeTree = HiredWorkPractice.logging(12).getFirst().units();
        double smallBuild = HiredWorkPractice.builderPlacement(Blocks.COBBLESTONE.defaultBlockState())
                .stream().mapToDouble(VillagerSkillPractice::units).sum();
        double largeBuild = smallBuild * 20.0D;
        double smallDelivery = HiredWorkPractice.courier(1, 4.0D).stream().mapToDouble(VillagerSkillPractice::units).sum();
        double largeDelivery = HiredWorkPractice.courier(32, 64.0D).stream().mapToDouble(VillagerSkillPractice::units).sum();
        if (!(twoBlocks > oneBlock && largeTree > smallTree && largeBuild > smallBuild && largeDelivery > smallDelivery)) {
            helper.fail("Successful larger work did not normalize above smaller work");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void hiredRolePracticeSplitsAcrossBothSkillsWithoutDuplicatingUnits(GameTestHelper helper) {
        List<VillagerSkillPractice> split = HiredWorkSkillGrowthService.normalizeRolePractice(
                HiredVillagerRole.MINING,
                List.of(
                        new VillagerSkillPractice(VillagerSkill.MINING, 0.4D, "test:mining", 42L),
                        new VillagerSkillPractice(VillagerSkill.MASONRY, 0.6D, "test:mining", 42L)));
        if (split.size() != 2
                || split.get(0).skill() != VillagerSkill.MINING
                || split.get(1).skill() != VillagerSkill.MASONRY
                || Math.abs(split.get(0).units() - 0.7D) > 0.0001D
                || Math.abs(split.get(1).units() - 0.3D) > 0.0001D
                || split.get(0).repetitionKey() != 42L
                || split.get(1).repetitionKey() != 42L
                || !split.get(0).source().equals("test:mining")
                || Math.abs(split.stream().mapToDouble(VillagerSkillPractice::units).sum() - 1.0D) > 0.0001D) {
            helper.fail("Hired work did not preserve its practice budget while splitting it 70/30");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void legacyHiredProgressMigratesExactlyOnce(GameTestHelper helper) {
        VillagerProfile profile = profileWithSkill(40);
        CompoundTag workState = new CompoundTag();
        CompoundTag legacy = new CompoundTag();
        legacy.putDouble(VillagerSkill.MINING.serializedName(), 0.5D);
        legacy.putDouble(VillagerSkill.FARMING.serializedName(), Double.NaN);
        workState.put(HiredWorkSkillGrowthService.LEGACY_PROGRESS_TAG, legacy);

        int originalSkill = profile.skills().get(VillagerSkill.MINING);
        if (!HiredWorkSkillGrowthService.migrateLegacyProgress(profile, workState, 1L)
                || workState.contains(HiredWorkSkillGrowthService.LEGACY_PROGRESS_TAG)
                || profile.skills().get(VillagerSkill.MINING) != originalSkill
                || profile.skillPracticeXp(VillagerSkill.MINING) <= 0.0D) {
            helper.fail("Legacy fractional work progress was not migrated safely");
            return;
        }
        double migratedXp = profile.skillPracticeXp(VillagerSkill.MINING);
        if (HiredWorkSkillGrowthService.migrateLegacyProgress(profile, workState, 2L)
                || profile.skillPracticeXp(VillagerSkill.MINING) != migratedXp) {
            helper.fail("Legacy migration was not idempotent");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void tradeProgressMigratesAndOfferKeysAreStable(GameTestHelper helper) {
        VillagerProfile profile = profileWithSkill(40);
        profile.setRegularTradeSkillGrowthProgress(VillagerSkill.TRADING, 0.5D, 1L);
        int original = profile.skills().get(VillagerSkill.TRADING);
        if (!VillagerSkillGrowthService.migrateRegularTradeProgress(profile, 2L)
                || profile.regularTradeSkillGrowthProgress(VillagerSkill.TRADING) != 0.0D
                || profile.skillPracticeXp(VillagerSkill.TRADING) <= 0.0D
                || profile.skills().get(VillagerSkill.TRADING) != original) {
            helper.fail("Regular trade progress did not migrate into centralized XP");
            return;
        }

        MerchantOffer breadA = offer(Items.BREAD);
        MerchantOffer breadB = offer(Items.BREAD);
        MerchantOffer apples = offer(Items.APPLE);
        if (VillagerSkillGrowthService.offerRepetitionKey(breadA) != VillagerSkillGrowthService.offerRepetitionKey(breadB)
                || VillagerSkillGrowthService.offerRepetitionKey(breadA) == VillagerSkillGrowthService.offerRepetitionKey(apples)) {
            helper.fail("Trade offer repetition keys were not stable and offer-specific");
            return;
        }
        helper.succeed();
    }

    private static VillagerProfile profileWithSkill(int value) {
        return VillagerProfile.create(
                UUID.randomUUID(), 1, 1L, VillagerSocialAttributes.DEFAULT, 1,
                VillagerSkillSet.filled(value), "minecraft:toolsmith", 0L);
    }

    private static VillagerSkillPractice practice(double units, long key) {
        return new VillagerSkillPractice(VillagerSkill.MINING, units, "test:mining", key);
    }

    private static MerchantOffer offer(net.minecraft.world.item.Item result) {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 2),
                new ItemStack(result),
                12,
                2,
                0.05F);
    }
}
