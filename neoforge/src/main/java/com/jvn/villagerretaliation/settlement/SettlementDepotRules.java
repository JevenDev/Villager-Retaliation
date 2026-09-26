package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.inventory.ContainerFilterResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class SettlementDepotRules {
    private SettlementDepotRules() {
    }

    public static void observe(ServerLevel level, SettlementDepot depot) {
        BlockPos position = depot.position();
        if (!observable(level, position)) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (!observable(level, position.relative(direction))) {
                return;
            }
        }
        Map<UUID, SettlementDepot.FrameRule> frames = new LinkedHashMap<>();
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, new AABB(position).inflate(1.0D),
                frame -> frame.isAlive() && attached(frame).equals(position) && !frame.getItem().isEmpty())) {
            frames.put(frame.getUUID(), new SettlementDepot.FrameRule(frame.getDirection(), frame.getItem()));
        }
        depot.observeFrames(frames);
    }

    public static void changed(ItemFrame frame) {
        if (!(frame.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return;
        }
        BlockPos attached = attached(frame);
        Entity.RemovalReason reason = frame.getRemovalReason();
        boolean removed = reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED
                || reason == Entity.RemovalReason.CHANGED_DIMENSION;
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) {
                continue;
            }
            for (var depot : town.depots().values()) {
                if (depot.position().equals(attached)) {
                    if (reason == null) {
                        observe(level, depot);
                    }
                    depot.observeFrame(frame.getUUID(), frame.getDirection(), removed ? ItemStack.EMPTY : frame.getItem());
                } else if (depot.knowsFrame(frame.getUUID())) {
                    depot.observeFrame(frame.getUUID(), frame.getDirection(), ItemStack.EMPTY);
                }
            }
        }
    }

    private static boolean observable(ServerLevel level, BlockPos position) {
        return ContainerFilterResolver.canObserveEntities(level, position);
    }

    private static BlockPos attached(ItemFrame frame) {
        return frame.getPos().relative(frame.getDirection().getOpposite());
    }
}
