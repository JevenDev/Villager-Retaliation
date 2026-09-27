package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public final class SettlementProjectileCapture implements AutoCloseable {
    private static final ThreadLocal<SettlementProjectileCapture> ACTIVE = new ThreadLocal<>();
    private final SettlementProjectileCapture previous;
    private final Entity owner;
    private final List<Projectile> projectiles = new ArrayList<>();

    SettlementProjectileCapture(Entity owner) {
        this.previous = ACTIVE.get();
        this.owner = owner;
        ACTIVE.set(this);
    }

    static boolean capturing(Projectile projectile) {
        for (var capture = ACTIVE.get(); capture != null; capture = capture.previous) {
            if (projectile.getOwner() == capture.owner) return true;
        }
        return false;
    }

    public static boolean capture(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item
                && SettlementProjectileSimulation.captureDrop(item)) return true;
        if (!(entity instanceof Projectile projectile)) return false;
        for (var capture = ACTIVE.get(); capture != null; capture = capture.previous) {
            if (projectile.getOwner() == capture.owner) {
                capture.projectiles.add(projectile);
                return true;
            }
        }
        return false;
    }

    List<Projectile> projectiles() {
        return List.copyOf(this.projectiles);
    }

    @Override
    public void close() {
        if (ACTIVE.get() != this) throw new IllegalStateException("Projectile capture scopes closed out of order");
        if (this.previous == null) ACTIVE.remove();
        else ACTIVE.set(this.previous);
    }
}
