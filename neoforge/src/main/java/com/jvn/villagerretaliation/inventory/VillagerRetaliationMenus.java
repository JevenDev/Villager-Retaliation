package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<VillagerInventoryMenu>> VILLAGER_INVENTORY =
            MENUS.register("villager_inventory", () -> IMenuTypeExtension.create(VillagerInventoryMenu::new));

    private VillagerRetaliationMenus() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
