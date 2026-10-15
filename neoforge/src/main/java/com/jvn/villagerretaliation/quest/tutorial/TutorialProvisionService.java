package com.jvn.villagerretaliation.quest.tutorial;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Permanent lesson inputs, never indistinguishable loans. Player inventory and delivery receipt save together. */
public final class TutorialProvisionService {
    private static final String RECEIPTS = "VillagerRetaliationTutorialProvisions";
    public enum Delivery { DELIVERED, WAITING_FOR_SPACE, ALREADY_DELIVERED, CONFLICT }
    private TutorialProvisionService() { }
    public static Delivery introductoryClipboard(ServerPlayer player, TutorialRun lesson) {
        var data = TutorialSavedData.get(player.serverLevel());
        String id = "villagerretaliation:clipboard/shared";
        var shared = data.find(player.getUUID(), id).orElse(null);
        if (shared == null) {
            var b = lesson.binding();
            shared = new TutorialRun(java.util.UUID.randomUUID(), id, new TutorialRun.Binding(b.player(), b.mentor(), b.worker(),
                    java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), b.run(), 0, "shared", b.dimension()));
            if (!data.reserve(shared)) return Delivery.CONFLICT;
        }
        if (shared.snapshot().grants().get("introductory_clipboard") == TutorialRun.GrantState.DELIVERED) return Delivery.ALREADY_DELIVERED;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (com.jvn.villagerretaliation.item.VillagerRetaliationItems.isClipboard(player.getInventory().getItem(slot))) {
                useExisting(player, shared, "introductory_clipboard"); return Delivery.ALREADY_DELIVERED;
            }
        }
        return deliver(player, shared, "introductory_clipboard", new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.CLIPBOARD.get()));
    }
    public static Delivery deliver(ServerPlayer player, TutorialRun run, String grant, ItemStack requested) {
        if (requested.isEmpty() || requested.getCount() > requested.getMaxStackSize() || run.completed()) return Delivery.CONFLICT;
        String key = run.trial() + "/" + grant;
        var receipts = player.getPersistentData().getCompound(RECEIPTS);
        var receipt = receipts.getCompound(key);
        String fingerprint = requested.save(player.registryAccess()).toString();
        if (receipt.getBoolean("Complete")) {
            run.deliveredGrant(grant); TutorialSavedData.get(player.serverLevel()).setDirty();
            return Delivery.ALREADY_DELIVERED;
        }
        if (receipt.contains("Fingerprint") && !receipt.getString("Fingerprint").equals(fingerprint)) return Delivery.CONFLICT;
        var state = run.snapshot().grants().get(grant);
        if (state == TutorialRun.GrantState.DELIVERED) return Delivery.ALREADY_DELIVERED;
        if (state == null && !run.reserveGrant(grant)) return Delivery.CONFLICT;
        TutorialSavedData.get(player.serverLevel()).setDirty();
        int already = receipt.getInt("Delivered");
        if (already < 0 || already > requested.getCount()) return Delivery.CONFLICT;
        ItemStack remaining = requested.copyWithCount(requested.getCount() - already);
        int capacity = 0;
        for (var stack : player.getInventory().items) {
            if (stack.isEmpty()) capacity += requested.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, requested)) capacity += Math.max(0, stack.getMaxStackSize() - stack.getCount());
        }
        int offered = Math.min(remaining.getCount(), capacity);
        ItemStack delivery = requested.copyWithCount(offered);
        if (offered > 0) player.getInventory().add(delivery);
        int moved = offered - delivery.getCount();
        if (moved == 0 && receipt.contains("Fingerprint")) return Delivery.WAITING_FOR_SPACE;
        receipt.putString("Fingerprint", fingerprint);
        receipt.putInt("Delivered", already + moved);
        boolean complete = already + moved == requested.getCount();
        receipt.putBoolean("Complete", complete); receipts.put(key, receipt);
        player.getPersistentData().put(RECEIPTS, receipts);
        // The receipt is in the same player NBT as these exact delivered inventory changes.
        player.serverLevel().getServer().getPlayerList().saveAll();
        if (complete) run.deliveredGrant(grant);
        TutorialSavedData.get(player.serverLevel()).setDirty();
        return complete ? Delivery.DELIVERED : Delivery.WAITING_FOR_SPACE;
    }
    public static void useExisting(ServerPlayer player, TutorialRun run, String grant) {
        if (run.snapshot().grants().get(grant) == TutorialRun.GrantState.DELIVERED) return;
        run.reserveGrant(grant);
        var receipts = player.getPersistentData().getCompound(RECEIPTS);
        var entry = new CompoundTag(); entry.putBoolean("Complete", true); entry.putBoolean("UsedExisting", true);
        receipts.put(run.trial() + "/" + grant, entry); player.getPersistentData().put(RECEIPTS, receipts);
        player.serverLevel().getServer().getPlayerList().saveAll();
        run.deliveredGrant(grant); TutorialSavedData.get(player.serverLevel()).setDirty();
    }
}
