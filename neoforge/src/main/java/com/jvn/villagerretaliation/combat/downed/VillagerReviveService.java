package com.jvn.villagerretaliation.combat.downed;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.party.CompanionLifecycleService;
import com.jvn.villagerretaliation.party.CompanionRelationshipService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class VillagerReviveService {
    private static final Map<UUID, Rescue> RESCUES = new HashMap<>();

    private VillagerReviveService() {
    }

    public static boolean interact(ServerPlayer player, Villager villager, InteractionHand hand, ItemStack held) {
        if (hand != InteractionHand.MAIN_HAND || !held.isEmpty() || !player.isShiftKeyDown() || !VillagerDownedService.isDowned(villager)) return false;
        begin(player, villager);
        return true;
    }

    public static boolean begin(ServerPlayer player, Villager villager) {
        if (!allowed(player, villager) || !player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("Move within three blocks and hold Sneak to help your downed companion"), true);
            return false;
        }
        if (RESCUES.containsKey(villager.getUUID())) return false;
        if (RESCUES.values().stream().anyMatch(rescue -> rescue.playerId().equals(player.getUUID()))) return false;
        int foodSlot = -1;
        if (VillagerRetaliationConfig.REVIVE_REQUIRES_BREAD.get()) {
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                if (player.getInventory().getItem(slot).is(Items.BREAD)) { foodSlot = slot; break; }
            }
            if (foodSlot < 0) {
                player.displayClientMessage(Component.literal("Helping up requires one bread in your inventory"), true);
                return false;
            }
        }
        ItemStack bread = foodSlot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(foodSlot);
        RESCUES.put(villager.getUUID(), new Rescue(player.getUUID(), villager.getUUID(), player.position(), villager.position(), foodSlot, bread,
                player.getServer().overworld().getGameTime(), VillagerRetaliationConfig.REVIVE_HELP_TICKS.get()));
        player.displayClientMessage(Component.literal("Helping up. Keep holding Sneak and stay still. Allies can cover you"), true);
        return true;
    }

    public static boolean allowed(ServerPlayer player, Villager villager) {
        return player != null && villager != null && player.isAlive() && !player.isSpectator()
                && !player.isRemoved() && !villager.isRemoved() && VillagerDownedService.isDowned(villager)
                && player.level() == villager.level() && player.distanceToSqr(villager) <= 9.0D && player.hasLineOfSight(villager)
                && (PartyService.areInSameOrAlliedParty(player, villager)
                || player.getUUID().equals(CompanionLifecycleService.owner(player.serverLevel(), villager.getUUID())));
    }

    public static void tick(MinecraftServer server) {
        for (Rescue rescue : java.util.List.copyOf(RESCUES.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(rescue.playerId());
            Villager villager = VillagerEntityResolver.loaded(server, rescue.villagerId());
            if (player == null || villager == null || !allowed(player, villager) || !player.isShiftKeyDown() || player.isUsingItem()
                    || player.containerMenu != player.inventoryMenu || !player.getMainHandItem().isEmpty()
                    || player.position().distanceToSqr(rescue.playerStart()) > 0.0625D
                    || villager.position().distanceToSqr(rescue.villagerStart()) > 1.0D
                    || rescue.foodSlot() >= 0 && (player.getInventory().getItem(rescue.foodSlot()) != rescue.bread()
                    || !rescue.bread().is(Items.BREAD) || rescue.bread().isEmpty())) {
                cancel(server, rescue, "Helping up interrupted. No supplies used");
                continue;
            }
            long elapsed = server.overworld().getGameTime() - rescue.started();
            if (elapsed < rescue.duration()) {
                if (elapsed % 20L == 0L) player.displayClientMessage(Component.literal("Helping up " + Math.min(99, elapsed * 100L / rescue.duration()) + "%"), true);
                continue;
            }
            if (rescue.foodSlot() >= 0) {
                rescue.bread().shrink(1);
                player.getInventory().setChanged();
            }
            RESCUES.remove(rescue.villagerId());
            VillagerDownedService.recover(villager);
            CompanionRelationshipService.rescued(player.serverLevel(), villager, player);
            player.displayClientMessage(Component.literal("Companion helped up"), true);
        }
    }

    public static boolean isBeingHelped(Villager villager) { return RESCUES.containsKey(villager.getUUID()); }

    public static void damaged(LivingEntity entity) {
        if (entity.getServer() == null) return;
        for (Rescue rescue : java.util.List.copyOf(RESCUES.values())) {
            if (rescue.playerId().equals(entity.getUUID()) || rescue.villagerId().equals(entity.getUUID()))
                cancel(entity.getServer(), rescue, "Helping up interrupted by damage. No supplies used");
        }
    }

    public static void externalRescue(ServerPlayer player, Villager villager) {
        if (player != null && player.level() == villager.level() && player.distanceToSqr(villager) <= 9.0D
                && (PartyService.areInSameOrAlliedParty(player, villager)
                || player.getUUID().equals(CompanionLifecycleService.owner(player.serverLevel(), villager.getUUID())))) {
            CompanionRelationshipService.rescued(player.serverLevel(), villager, player);
        }
        RESCUES.remove(villager.getUUID());
    }

    private static void cancel(MinecraftServer server, Rescue rescue, String reason) {
        RESCUES.remove(rescue.villagerId());
        var player = server.getPlayerList().getPlayer(rescue.playerId());
        if (player != null) player.displayClientMessage(Component.literal(reason), true);
    }

    public static void clearRuntimeState() { RESCUES.clear(); }

    private record Rescue(UUID playerId, UUID villagerId, Vec3 playerStart, Vec3 villagerStart,
            int foodSlot, ItemStack bread, long started, int duration) { }
}
