package com.jvn.villagerretaliation.client.settlement;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

final class SettlementViewEntities {
    private final Map<UUID, Entity> entities = new LinkedHashMap<>();
    private boolean ready;

    SettlementViewEntities prepare(Iterable<Entity> source) {
        if (!this.ready) {
            this.entities.clear();
            for (var entity : source) this.entities.put(entity.getUUID(), entity);
            this.ready = true;
        }
        return this;
    }

    Entity get(UUID id) { return this.entities.get(id); }
    Collection<Entity> all() { return this.entities.values(); }
    void invalidate() { this.ready = false; }
    void clear() { this.entities.clear(); this.ready = false; }
}
