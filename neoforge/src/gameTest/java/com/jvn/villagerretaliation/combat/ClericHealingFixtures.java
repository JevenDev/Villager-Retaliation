package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public final class ClericHealingFixtures {
    private ClericHealingFixtures() {}

    public static Fixture create(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(4, -1, 4))) {
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        }
        Villager cleric = EntityType.VILLAGER.create(helper.getLevel());
        cleric.setVillagerData(cleric.getVillagerData().setProfession(VillagerProfession.CLERIC));
        cleric.setNoAi(true);
        cleric.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
        helper.getLevel().addFreshEntity(cleric);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ClericPatient"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(pos.getX() + 2.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
        player.setHealth(10.0F);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, VillagerCurrencyResources.createStack(helper.getLevel().getServer(), 64));
        VillagerReputationManager.setReputation(helper.getLevel(), cleric, player.getUUID(), VillagerRetaliationConfig.RESPECTED_THRESHOLD.get());
        return new Fixture(player, cleric);
    }

    public static void recruit(GameTestHelper helper, Fixture fixture) {
        VillagerReputationManager.setReputation(helper.getLevel(), fixture.cleric(), fixture.player().getUUID(),
                VillagerRetaliationConfig.ROYALTY_THRESHOLD.get() + 50);
        helper.assertTrue(PartyVillagerContractService.recruit(fixture.player(), fixture.cleric()).success(), "party cleric recruited");
    }

    public static void expireThrowDelay(Villager cleric) {
        cleric.getPersistentData().getCompound("VillagerRetaliationClericHealing").putLong("NextSupportThrow", 0L);
    }

    public record Fixture(ServerPlayer player, Villager cleric) implements AutoCloseable {
        @Override
        public void close() {
            VillagerConversationService.endForPlayer(player, false);
            var level = player.serverLevel();
            var party = PartyService.getPartyForVillager(level, cleric.getUUID()).orElse(null);
            if (party != null) PartyVillagerContractService.disband(player);
            for (ThrownPotion potion : level.getEntitiesOfClass(ThrownPotion.class, cleric.getBoundingBox().inflate(8.0D))) {
                if (potion.getOwner() == cleric) potion.discard();
            }
            cleric.discard();
        }
    }
}
