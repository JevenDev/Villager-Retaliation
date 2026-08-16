package com.jvn.villagerretaliation.mount;

import java.util.List;
import net.minecraft.world.entity.Entity;

final class VillagerMountAdapters {
    private static final List<VillagerMountAdapter> ADAPTERS = List.of(new AbstractHorseMountAdapter());

    private VillagerMountAdapters() {
    }

    static VillagerMountAdapter find(Entity entity) {
        return ADAPTERS.stream().filter(adapter -> adapter.supports(entity)).findFirst().orElse(null);
    }
}
