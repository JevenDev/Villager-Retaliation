package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class VillagerHostileTierHarass {
    private static final double THROW_MAX_DISTANCE_SQR = 144.0D;
    private static final Map<UUID, Long> NEXT_THROW_TICKS = new HashMap<>();

    private VillagerHostileTierHarass() {
    }

    static boolean tryThrow(
            Villager villager,
            LivingEntity target,
            ServerLevel level,
            long gameTime,
            double distanceSqr
    ) {
        if (!VillagerRetaliationConfig.HOSTILE_TIER_HARASS_THROW_ENABLED.get()
                || !(target instanceof Player player)
                || !player.isAlive()
                || player.isCreative()
                || player.isSpectator()
                || distanceSqr > THROW_MAX_DISTANCE_SQR
                || !villager.hasLineOfSight(player)
                || !isHostileTierAgainstPlayer(villager, player)) {
            return false;
        }

        if (gameTime < NEXT_THROW_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return false;
        }

        NEXT_THROW_TICKS.put(villager.getUUID(), gameTime + nextThrowDelayTicks(villager));
        if (villager.getRandom().nextBoolean()) {
            ThrownEgg egg = new ThrownEgg(level, villager);
            egg.setItem(new ItemStack(Items.EGG));
            shoot(villager, egg, player, level);
        } else {
            Snowball poisonousPotato = new Snowball(level, villager);
            poisonousPotato.setItem(new ItemStack(Items.POISONOUS_POTATO));
            shoot(villager, poisonousPotato, player, level);
        }
        return true;
    }

    static void clearState(Villager villager) {
        NEXT_THROW_TICKS.remove(villager.getUUID());
    }

    static void clearRuntimeState() {
        NEXT_THROW_TICKS.clear();
    }

    private static void shoot(
            Villager villager,
            ThrowableItemProjectile projectile,
            Player target,
            ServerLevel level
    ) {
        double dx = target.getX() + target.getDeltaMovement().x - villager.getX();
        double dy = target.getY(0.3333333333333333D) - projectile.getY();
        double dz = target.getZ() + target.getDeltaMovement().z - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        projectile.shoot(dx, dy + horizontal * 0.2D, dz, 1.1F, (float) (16 - level.getDifficulty().getId() * 4));
        level.addFreshEntity(projectile);
        villager.swing(InteractionHand.MAIN_HAND, true);
        villager.playSound(SoundEvents.EGG_THROW, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
    }

    private static boolean isHostileTierAgainstPlayer(Villager villager, Player player) {
        if (!(villager.level() instanceof ServerLevel level) || !VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return false;
        }
        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        return reputationLevel == VillagerReputationLevel.HOSTILE
                || reputationLevel == VillagerReputationLevel.DESPISED
                || reputationLevel == VillagerReputationLevel.FEARED;
    }

    private static int nextThrowDelayTicks(Villager villager) {
        int minDelay = Math.max(1, VillagerRetaliationConfig.HOSTILE_TIER_HARASS_THROW_MIN_INTERVAL_TICKS.get());
        int maxDelay = Math.max(1, VillagerRetaliationConfig.HOSTILE_TIER_HARASS_THROW_MAX_INTERVAL_TICKS.get());
        if (maxDelay < minDelay) {
            int swap = minDelay;
            minDelay = maxDelay;
            maxDelay = swap;
        }
        return minDelay + villager.getRandom().nextInt(maxDelay - minDelay + 1);
    }
}
