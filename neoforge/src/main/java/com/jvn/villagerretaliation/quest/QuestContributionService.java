package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.party.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class QuestContributionService {
    private static final Map<Key, Offer> OFFERS = new HashMap<>();

    private QuestContributionService() {
    }

    public static boolean offer(ServerPlayer player, Villager villager, ResourceLocation quest, int slot, int count) {
        var party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        if (party == null || sharedForPlayer(player.serverLevel(), player.getUUID(), quest).isEmpty()) return false;
        Container inventory;
        UUID sourceId;
        if (villager == null) {
            inventory = player.getInventory();
            sourceId = player.getUUID();
            if (slot < 0 || slot >= player.getInventory().items.size()) return false;
        } else {
            var record = party.villager(villager.getUUID());
            if (!CompanionAuthority.canEquip(player.getUUID(), party, record) || player.level() != villager.level()
                    || player.distanceToSqr(villager) > 64.0D || slot < HiredJobInventory.MAIN_GRID_START
                    || slot >= HiredJobInventory.FILTER_SLOT) return false;
            inventory = HiredJobInventory.getJobInventory(villager);
            sourceId = villager.getUUID();
        }
        var key = new Key(player.getUUID(), sourceId, slot, quest);
        if (count == 0) {
            OFFERS.remove(key);
            player.sendSystemMessage(Component.literal("Quest contribution withdrawn"));
            return true;
        }
        ItemStack stack = inventory.getItem(slot);
        if (count < 1 || count > stack.getCount() || stack.isEmpty()) return false;
        OFFERS.entrySet().removeIf(entry -> entry.getKey().source().equals(sourceId) && entry.getKey().slot() == slot);
        OFFERS.put(key, new Offer(party.id(), inventory, stack, stack.copy(), count, player.serverLevel().getServer().overworld().getGameTime() + 12000L));
        player.sendSystemMessage(Component.literal("Offered " + count + " x " + stack.getHoverName().getString() + " from slot " + slot
                + " for " + quest + ". Only this stack may be consumed, within 16 blocks, for 10 minutes. Use count 0 to withdraw."));
        return true;
    }

    private static java.util.Optional<PartySharedQuestRecord> sharedForPlayer(net.minecraft.server.level.ServerLevel level, UUID player, ResourceLocation quest) {
        return PartyQuestService.sharedForPlayer(level, player, quest);
    }

    static List<Contribution> contributions(ServerPlayer submitter, PartyRecord party, PartySharedQuestRecord shared) {
        long now = submitter.getServer().overworld().getGameTime();
        OFFERS.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
        var result = new ArrayList<Contribution>();
        for (var entry : List.copyOf(OFFERS.entrySet())) {
            Key key = entry.getKey();
            Offer offer = entry.getValue();
            if (!key.quest().equals(shared.questId()) || !offer.party().equals(party.id())
                    || key.source().equals(submitter.getUUID()) || !shared.linked(key.owner())) continue;
            BooleanSupplier valid = () -> valid(submitter, party, shared, key, offer);
            if (valid.getAsBoolean()) result.add(new Contribution(offer.stack(), offer.inventory(), key.slot(), offer.count(), valid,
                    () -> OFFERS.remove(key, offer), key.source() + " slot " + key.slot()));
        }
        return result;
    }

    private static boolean valid(ServerPlayer submitter, PartyRecord party, PartySharedQuestRecord shared, Key key, Offer offer) {
        if (OFFERS.get(key) != offer || submitter.getServer().overworld().getGameTime() >= offer.expires()
                || !party.playerIds().contains(key.owner()) || !shared.linked(key.owner())
                || !PartyService.getPartyForPlayer(submitter.serverLevel(), submitter.getUUID()).map(p -> p.id().equals(party.id())).orElse(false)
                || offer.inventory().getItem(key.slot()) != offer.stack()
                || !ItemStack.isSameItemSameComponents(offer.snapshot(), offer.stack())) return false;
        ServerPlayer owner = submitter.getServer().getPlayerList().getPlayer(key.owner());
        if (owner == null || !owner.isAlive() || owner.serverLevel() != submitter.serverLevel() || owner.distanceToSqr(submitter) > 256.0D) return false;
        if (key.source().equals(key.owner())) return true;
        Villager villager = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(submitter.getServer(), key.source());
        var record = party.villager(key.source());
        return villager != null && villager.isAlive() && villager.level() == submitter.level() && villager.distanceToSqr(submitter) <= 256.0D
                && CompanionAuthority.canEquip(owner.getUUID(), party, record);
    }

    public static void clearRuntimeState() { OFFERS.clear(); }

    record Contribution(ItemStack stack, Container inventory, int slot, int count, BooleanSupplier valid, Runnable consumed, String description) { }
    private record Key(UUID owner, UUID source, int slot, ResourceLocation quest) { }
    private record Offer(UUID party, Container inventory, ItemStack stack, ItemStack snapshot, int count, long expires) { }
}
