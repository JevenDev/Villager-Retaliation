package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import java.io.IOException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterRewardPersistenceGameTests {
    private CampaignEncounterRewardPersistenceGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_rewards")
    public static void failedSceneSavePreventsParticipantRewardDelivery(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, """
                {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
                 "spawn_mode":"fixed","spawn_radius":0,"members":[{"entity":"minecraft:cow"}],
                 "rewards":{"completion":[{"id":"payment","item":"minecraft:emerald","count":5}]}}
                """)) {
            fixture.run();
            var player = fixture.base.base.server.getPlayerList().getPlayer(fixture.encounter().participants().iterator().next());
            int before = player.getInventory().countItem(Items.EMERALD);
            fixture.block();
            var enemy = (LivingEntity) helper.getLevel().getEntity(fixture.encounter().spawned().iterator().next());
            EncounterService.onDeath(enemy, fixture.data());
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), before, "reward identity must be durable before inventory changes");
            fixture.restore(); fixture.run();
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), before + 5, "storage recovery delivers the original reward once");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), before + 5, "reload cannot duplicate the reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_rewards")
    public static void failedSceneSavePreventsVillagerProfileRewardDelivery(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, """
                {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
                 "spawn_mode":"fixed","spawn_radius":0,"members":[{"entity":"minecraft:cow","health":30}],
                 "allies":[{"id":"guard","entity":"minecraft:villager","cleanup_policy":"preserve"}]}
                """)) {
            fixture.run();
            var ally = fixture.encounter().allies().get("guard").entityId();
            var profile = VillagerProfileManager.getOrCreateProfile(helper.getLevel(),
                    (net.minecraft.world.entity.npc.Villager) helper.getLevel().getEntity(ally));
            profile.setSocialAttribute(VillagerSocialAttribute.GUTS, 10, helper.getLevel().getGameTime());
            fixture.block();
            var enemy = (LivingEntity) helper.getLevel().getEntity(fixture.encounter().spawned().iterator().next());
            EncounterService.onDeath(enemy, fixture.data());
            helper.assertValueEqual(VillagerProfileManager.getProfile(helper.getLevel(), ally).orElseThrow().socialAttributes().get(VillagerSocialAttribute.GUTS),
                    10, "profile reward identity must be durable before changing an ally");
            fixture.restore(); fixture.run(); fixture.reload(); fixture.run();
            helper.assertValueEqual(VillagerProfileManager.getProfile(helper.getLevel(), ally).orElseThrow().socialAttributes().get(VillagerSocialAttribute.GUTS),
                    13, "saved profile reward applies once after storage recovers");
        }
        helper.succeed();
    }
}
