package com.jvn.villagerretaliation.storage;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid=VillagerRetaliation.MOD_ID)
public final class StorageEvents {
    private StorageEvents() { }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if(!(event.getLevel().getBlockEntity(event.getPos()) instanceof StorageBlockEntity bank)) return;
        // A missing/unloaded authoritative member cannot be mined into a second independent copy.
        if(bank.group().isEmpty() || bank.inventory().getContainerSize()!=(bank.safe()?54:27)) { event.setCanceled(true); return; }
        if(!event.getPlayer().isCreative() && !event.getPlayer().hasCorrectToolForDrops(event.getState())) event.setCanceled(true);
    }
}
