package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_authoring")
@PrefixGameTestTemplate(false)
public final class CompanionDialogueContextGameTests {
    private CompanionDialogueContextGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_contexts")
    public static void companionConditionsRespectOwnershipAndCommandState(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var other = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var level = helper.getLevel();
        var data = PartySavedData.get(level);
        long now = level.getGameTime();
        var party = data.createParty(player.getUUID(), now);
        var record = new PartyVillagerRecord(villager.getUUID(), player.getUUID(), UUID.randomUUID(), 0,
                PartyCommandMode.FOLLOW, null, null, now, now + 24000, 1, 32, "Companion",
                "minecraft:farmer", level.dimension().location(), villager.blockPosition());
        data.addVillager(party, record);
        var context = VillagerInteractionService.createDialogueContext(level, player, villager);
        var following = new DialogueCondition.CompanionState("following");
        helper.assertTrue(following.matches(context), "active companion follows");
        helper.assertFalse(following.matches(VillagerInteractionService.createDialogueContext(level, other, villager)),
                "another player's companion is not the player's companion");
        record.setStaying(level.dimension().location(), villager.blockPosition());
        helper.assertFalse(following.matches(context), "waiting is not following");
        helper.assertTrue(new DialogueCondition.CompanionState("waiting").matches(context), "waiting reads the party command");
        helper.succeed();
    }
}
