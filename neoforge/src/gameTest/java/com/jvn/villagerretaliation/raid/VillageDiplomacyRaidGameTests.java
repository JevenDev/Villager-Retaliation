package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.*;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyApi;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("village_diplomacy")
@PrefixGameTestTemplate(false)
public final class VillageDiplomacyRaidGameTests {
    @GameTest(template = "empty")
    public static void explicitPlayerRaidRetainsPriorityOverTruce(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(4, 2, 4));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var home = registry.create(0, level.dimension().location(), position, "");
        var foreign = registry.create(0, level.dimension().location(), position, "");
        var actor = EntityType.VILLAGER.create(level);
        var target = EntityType.VILLAGER.create(level);
        VillageAllegianceApi.assignKnown(level, actor, home, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, target, foreign, AllegianceAssignmentSource.ADMIN);
        VillageDiplomacyApi.truce(level, home, foreign, 0);
        var raids = PlayerRaidSavedData.get(level);
        var player = UUID.randomUUID();
        var raid = raids.create(foreign, level.dimension().location(), position, Set.of(SectionPos.asLong(position)),
                "Diplomacy raid", player, null, Set.of(player), Set.of(actor.getUUID()), Set.of(target.getUUID()), Set.of(), 0);
        try {
            raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 0);
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(level, actor, target,
                    AllegianceCombatContext.DAMAGE, false).reason(),
                    AllegianceCombatDecision.Reason.PLAYER_RAID_OPPONENT, "explicit raid exception retained");
            raid.setPhase(PlayerRaidSavedData.Phase.RAIDER_VICTORY, 1);
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(level, actor, target,
                    AllegianceCombatContext.DAMAGE, false).reason(),
                    AllegianceCombatDecision.Reason.VILLAGE_TRUCE, "truce applies after the raid ends");
        } finally {
            raids.remove(raid.id());
            actor.discard();
            target.discard();
        }
        helper.succeed();
    }
}
