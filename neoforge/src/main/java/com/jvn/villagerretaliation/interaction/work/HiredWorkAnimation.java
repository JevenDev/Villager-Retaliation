package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.network.VillagerWorkAnimationPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side entry point for readable, player-like hired work actions. */
public final class HiredWorkAnimation {
    public static final int SWING = 0;
    public static final int USE_ITEM = 1;
    private static final int SWING_DURATION_TICKS = 8;
    private static final int USE_ITEM_DURATION_TICKS = 12;

    private HiredWorkAnimation() {
    }

    public static void swing(ServerLevel level, Villager villager, ItemStack item) {
        villager.swing(InteractionHand.MAIN_HAND, true);
        send(level, villager, SWING, SWING_DURATION_TICKS, item);
    }

    public static void useItem(ServerLevel level, Villager villager, ItemStack item) {
        send(level, villager, USE_ITEM, USE_ITEM_DURATION_TICKS, item);
    }

    private static void send(ServerLevel level, Villager villager, int animation, int durationTicks, ItemStack item) {
        if (level == null || villager == null) {
            return;
        }
        ItemStack displayItem = item == null || item.isEmpty() ? villager.getMainHandItem() : item;
        try {
            PacketDistributor.sendToPlayersTrackingEntity(
                    villager,
                    new VillagerWorkAnimationPayload(
                            villager.getId(),
                            animation,
                            durationTicks,
                            displayItem.isEmpty() ? ItemStack.EMPTY : displayItem.copyWithCount(1)));
        } catch (UnsupportedOperationException ignored) {
            // Mock server connections used by game tests do not negotiate custom payloads.
        }
    }
}
