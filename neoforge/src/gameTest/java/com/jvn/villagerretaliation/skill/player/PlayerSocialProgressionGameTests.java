package com.jvn.villagerretaliation.skill.player;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class PlayerSocialProgressionGameTests {
    private PlayerSocialProgressionGameTests() { }

    @GameTest(template = "empty", batch = "social_progression")
    public static void awardsPersistOnceForTheOwningPlayerAndSkill(GameTestHelper helper) throws IOException {
        try (var disk = new SocialProgressionTestStorage(helper.getLevel())) {
            UUID player = UUID.randomUUID(), other = UUID.randomUUID();
            var award = award(player, PlayerSocialSkill.PERSUASION, 0, 10);
            var receipt = disk.data().grant(award);
            helper.assertValueEqual(receipt.after(), 10L, "authored experience is granted");
            helper.assertValueEqual(disk.data().grant(award), receipt, "duplicate award returns the same receipt");
            helper.assertValueEqual(disk.data().experience(other, PlayerSocialSkill.PERSUASION), 0L, "another player earns no experience");
            helper.assertValueEqual(disk.data().experience(player, PlayerSocialSkill.INSIGHT), 0L, "another skill earns no experience");
            disk.reload();
            helper.assertValueEqual(disk.data().grant(award), receipt, "reloaded duplicate returns the same receipt");
            for (var conflict : List.of(new PlayerSocialAward(award.session(), award.check(), UUID.randomUUID(), player, award.breakdown()),
                    new PlayerSocialAward(award.session(), award.check(), award.request(), other, award.breakdown()),
                    new PlayerSocialAward(award.session(), award.check(), award.request(), player,
                            SocialCheckBreakdown.capture(SocialProgressionTestData.definition(), PlayerSocialSkill.PERSUASION, 0, 30)))) {
                try { disk.data().grant(conflict); helper.fail("conflicting check identity must not grant experience"); }
                catch (IllegalArgumentException expected) { }
            }
            disk.data().grant(award(player, PlayerSocialSkill.PERSUASION, 10, 20));
            disk.data().grant(award(player, PlayerSocialSkill.INSIGHT, 0, 0));
            disk.data().grant(award(other, PlayerSocialSkill.DECEPTION, 0, 5));
            disk.reload();
            helper.assertValueEqual(disk.data().experience(player, PlayerSocialSkill.PERSUASION), 30L, "later checks accumulate experience");
            helper.assertValueEqual(disk.data().receipts().size(), 4, "zero awards also have durable receipts");
            helper.assertValueEqual(award.breakdown().bonus(), 0, "later levels do not alter the earlier check");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "social_progression")
    public static void failedWritesPublishNeitherExperienceNorReceipts(GameTestHelper helper) throws IOException {
        try (var disk = new SocialProgressionTestStorage(helper.getLevel())) {
            var award = award(UUID.randomUUID(), PlayerSocialSkill.INTIMIDATION, 0, 10);
            disk.blockWrites();
            try { disk.data().grant(award); helper.fail("blocked storage must reject the award"); }
            catch (IOException expected) { }
            helper.assertTrue(disk.data().progress().isEmpty() && disk.data().receipts().isEmpty(), "failed write changes no acknowledged state");
            disk.restoreWrites();
            disk.data().grant(award);
            disk.reload();
            helper.assertValueEqual(disk.data().experience(award.player(), PlayerSocialSkill.INTIMIDATION), 10L, "retry grants the original award once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "social_progression")
    public static void invalidSavedTotalsAndBreakdownsRemainDormantAndRecoverable(GameTestHelper helper) throws IOException {
        try (var disk = new SocialProgressionTestStorage(helper.getLevel())) {
            var award = award(UUID.randomUUID(), PlayerSocialSkill.PERSUASION, 0, 10);
            disk.data().grant(award);
            var original = disk.read();
            for (Consumer<CompoundTag> corrupt : List.<Consumer<CompoundTag>>of(
                    tag -> tag.putInt("DataVersion", 2),
                    tag -> tag.remove("Progress"),
                    tag -> tag.putString("Receipts", "unreadable"),
                    tag -> tag.getList("Progress", 10).getCompound(0).putLong("Experience", 20),
                    tag -> tag.getList("Progress", 10).add(tag.getList("Progress", 10).getCompound(0).copy()),
                    tag -> tag.getList("Receipts", 10).add(tag.getList("Receipts", 10).getCompound(0).copy()),
                    tag -> tag.getList("Receipts", 10).getCompound(0).putLong("Before", 5),
                    tag -> tag.getList("Receipts", 10).getCompound(0).putLong("After", 11),
                    tag -> breakdown(tag).putString("Progression", "INVALID RESOURCE"),
                    tag -> breakdown(tag).putString("Fingerprint", "changed"),
                    tag -> breakdown(tag).putString("Skill", "unknown"),
                    tag -> breakdown(tag).putInt("Version", 2),
                    tag -> breakdown(tag).putInt("Experience", 0),
                    tag -> breakdown(tag).putInt("Level", 1024),
                    tag -> breakdown(tag).putInt("Threshold", 1),
                    tag -> breakdown(tag).putInt("Bonus", 21),
                    tag -> breakdown(tag).putInt("RewardExperience", -1))) {
                var broken = original.copy();
                corrupt.accept(broken);
                var dormant = PlayerSocialProgressionSavedData.load(broken, disk.directory());
                helper.assertFalse(dormant.diagnostic().isBlank(), "invalid social history has a repair diagnostic");
                helper.assertTrue(dormant.progress().isEmpty() && dormant.receipts().isEmpty(), "unproven history is not published");
                helper.assertValueEqual(dormant.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "raw history remains repairable");
                try { dormant.grant(award); helper.fail("dormant history must not grant more experience"); }
                catch (IOException expected) { }
                helper.assertValueEqual(disk.read(), original, "invalid load never overwrites the existing file");
            }
        }
        helper.succeed();
    }

    private static CompoundTag breakdown(CompoundTag tag) {
        return tag.getList("Receipts", 10).getCompound(0).getCompound("Award").getCompound("Breakdown");
    }

    private static PlayerSocialAward award(UUID player, PlayerSocialSkill skill, long experience, int reward) throws IOException {
        return new PlayerSocialAward(UUID.randomUUID(), "convince", UUID.randomUUID(), player,
                SocialCheckBreakdown.capture(SocialProgressionTestData.definition(), skill, experience, reward));
    }
}
