package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;

/**
 * Keeps a villager's weapon visibly drawn for a data-driven warning without
 * assigning an attack target or changing retaliation state.
 */
public final class VillagerWeaponDrawService {
    public static final int DEFAULT_DRAW_TICKS = 20 * 10;
    private static final String DRAWN_UNTIL_TAG = "VillagerRetaliationWeaponDrawnUntil";

    private VillagerWeaponDrawService() {
    }

    public static boolean draw(Villager villager, int durationTicks) {
        if (villager == null
                || villager.level().isClientSide
                || !villager.isAlive()
                || villager.isBaby()
                || VillagerInventoryAccess.hasOpenInventory(villager)) {
            return false;
        }

        boolean equipped = VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || VillagerCombatLoadoutService.equipCombatWeapon(
                villager,
                VillagerRetaliationVillagerWeapons::isUsableWeapon);
        if (!equipped) {
            return false;
        }

        long drawnUntil = villager.level().getGameTime() + Math.max(1, durationTicks);
        CompoundTag data = villager.getPersistentData();
        data.putLong(DRAWN_UNTIL_TAG, Math.max(data.getLong(DRAWN_UNTIL_TAG), drawnUntil));
        return true;
    }

    public static boolean isDrawn(Villager villager) {
        if (villager == null || villager.level().isClientSide) {
            return false;
        }
        long drawnUntil = villager.getPersistentData().getLong(DRAWN_UNTIL_TAG);
        return drawnUntil > villager.level().getGameTime();
    }

    public static void tick(Villager villager) {
        if (villager == null || villager.level().isClientSide) {
            return;
        }
        CompoundTag data = villager.getPersistentData();
        long drawnUntil = data.getLong(DRAWN_UNTIL_TAG);
        if (drawnUntil <= 0L || drawnUntil > villager.level().getGameTime()) {
            if (drawnUntil > 0L && VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)) {
                VillagerInventoryAccess.maintainBorrowedCombatWeapon(villager);
            }
            return;
        }
        data.remove(DRAWN_UNTIL_TAG);
    }

    public static void sheathe(Villager villager) {
        if (villager == null) {
            return;
        }
        villager.getPersistentData().remove(DRAWN_UNTIL_TAG);
        if (villager.getTarget() == null) {
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        }
    }
}
