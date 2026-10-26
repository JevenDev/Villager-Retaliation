package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("village_diplomacy")
@PrefixGameTestTemplate(false)
public final class VillageIntroductionGameTests {
    @GameTest(template = "empty")
    public static void introductionsRequireTheNearbyCurrentRulerAndPersonalDiscovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var settlements = SettlementSavedData.get(level);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "VillageVisitor"));
        var center = helper.absolutePos(new BlockPos(1, 2, 1));
        var a = registry.create(0, level.dimension().location(), center, "");
        var town = settlements.establish(registry, a, UUID.randomUUID(), center, 0).orElseThrow();
        var b = registry.create(0, level.dimension().location(), center.offset(256, 0, 0), "");
        settlements.establish(registry, b, UUID.randomUUID(), center.offset(256, 0, 0), 0).orElseThrow();
        level.getChunkAt(center);
        var ruler = EntityType.VILLAGER.create(level);
        ruler.setNoAi(true); ruler.setPos(center.getBottomCenter());
        helper.assertTrue(level.addFreshEntity(ruler), "ruler exists physically");
        helper.assertTrue(level.getEntity(ruler.getUUID()) == ruler, "ruler is in a visible chunk");
        var state = new AutonomousVillageState(); state.ruler = ruler.getUUID(); state.sections.add(SectionPos.asLong(center));
        town.autonomous(state);
        player.setPos(center.getBottomCenter());
        var data = VillageDiplomacySavedData.get(level);
        helper.assertValueEqual(VillageDiplomacyService.introduce(player, ruler.getUUID(), b, data.revision()),
                VillageDiplomacyService.Result.UNKNOWN_VILLAGE, "cannot introduce an undiscovered identity");
        player.setPos(center.offset(256, 0, 0).getBottomCenter());
        VillageDiplomacyService.discover(player, b);
        helper.assertValueEqual(VillageDiplomacyService.introduce(player, ruler.getUUID(), b, data.revision()),
                VillageDiplomacyService.Result.RULER_UNAVAILABLE, "remote conversation is rejected");
        player.setPos(center.getBottomCenter());
        helper.assertValueEqual(VillageDiplomacyService.introduce(player, ruler.getUUID(), b, data.revision()),
                VillageDiplomacyService.Result.APPLIED, "nearby ruler accepts personal discovery");
        helper.assertTrue(data.contacts(a).containsKey(b) && data.contacts(b).isEmpty(), "knowledge remains directional");
        helper.assertValueEqual(data.standing(a, b), 0, "introductions award no standing");
        state.ruler = UUID.randomUUID();
        helper.assertValueEqual(VillageDiplomacyService.introduce(player, ruler.getUUID(), b, data.revision()),
                VillageDiplomacyService.Result.RULER_UNAVAILABLE, "previous ruler no longer has authority");
        helper.assertTrue(data.contacts(a).containsKey(b), "succession retains the village's contacts");
        ruler.discard(); registry.removeVillage(a); registry.removeVillage(b);
        helper.succeed();
    }
}
