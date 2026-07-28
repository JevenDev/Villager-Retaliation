package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class HiredCombatSkillPracticeService {
    private HiredCombatSkillPracticeService() {
    }

    public static void onDamageDealt(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F
                || !(VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(target, event.getSource()).orElse(null)
                        instanceof Villager villager)
                || villager == target
                || !villager.canAttack(target)
                || target.isAlliedTo(villager)) {
            return;
        }

        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (role != HiredVillagerRole.COMBAT && role != HiredVillagerRole.HUNTING) {
            return;
        }
        if (HiredVillagerContractService.currentContractHirer(villager)
                .filter(target.getUUID()::equals)
                .isPresent()) {
            return;
        }

        boolean ranged = event.getSource().getDirectEntity() != null
                && event.getSource().getDirectEntity() != villager;
        double threatFactor = Math.clamp(target.getMaxHealth() / 20.0D, 0.75D, 2.0D);
        long repetitionKey = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).hashCode();
        ServerPlayer hirer = HiredVillagerContractService.currentContractHirer(villager)
                .map(level.getServer().getPlayerList()::getPlayer)
                .orElse(null);
        HiredWorkSkillGrowthService.onPractice(
                level,
                villager,
                hirer,
                role,
                HiredWorkStateStore.state(villager),
                HiredWorkPractice.combat(ranged, role == HiredVillagerRole.HUNTING,
                        event.getNewDamage() * threatFactor, repetitionKey));
    }

    public static void onKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || !(VillagerRetaliationVillagerCombatUtil.resolveDeathAttacker(target, event.getSource()).orElse(null)
                        instanceof Villager villager)
                || villager == target
                || !villager.canAttack(target)
                || target.isAlliedTo(villager)) {
            return;
        }
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (role != HiredVillagerRole.COMBAT && role != HiredVillagerRole.HUNTING) {
            return;
        }
        if (HiredVillagerContractService.currentContractHirer(villager)
                .filter(target.getUUID()::equals)
                .isPresent()) {
            return;
        }

        boolean ranged = event.getSource().getDirectEntity() != null
                && event.getSource().getDirectEntity() != villager;
        double threat = Math.clamp(target.getMaxHealth() / 20.0D, 0.5D, 2.0D);
        long repetitionKey = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).hashCode();
        ServerPlayer hirer = HiredVillagerContractService.currentContractHirer(villager)
                .map(level.getServer().getPlayerList()::getPlayer)
                .orElse(null);
        HiredWorkSkillGrowthService.onPractice(
                level,
                villager,
                hirer,
                role,
                HiredWorkStateStore.state(villager),
                HiredWorkPractice.combatKill(ranged, role == HiredVillagerRole.HUNTING, threat, repetitionKey));
    }
}
