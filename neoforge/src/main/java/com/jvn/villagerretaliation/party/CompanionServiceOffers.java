package com.jvn.villagerretaliation.party;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.*;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** A one-use, chosen service term. Its clock starts on acceptance, never on an unlock. */
public final class CompanionServiceOffers {
    private CompanionServiceOffers() { }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || context.villager() == null || context.player() == null)
            return ActionResult.unavailable("A specific villager and player are required", capabilities, VillagerActionResult.EMPTY);
        var data = CompanionSavedData.get(context.level());
        var state = data.getOrCreate(context.villager().getUUID());
        var offers = state.getCompound("ServiceOffers");
        String key = context.player().getUUID() + "/" + action.factKey();
        if (offers.contains(key)) return ActionResult.alreadySatisfied("Service offer already remembered", capabilities, VillagerActionResult.success());
        var offer = new CompoundTag();
        offer.putString("Id", action.factKey());
        offer.putString("Name", action.text().isBlank() ? "Temporary companionship" : action.text());
        offer.putInt("Duration", action.amount());
        offer.putInt("MinimumReputation", Integer.parseInt(action.factValue()));
        offers.put(key, offer);
        state.put("ServiceOffers", offers);
        data.setDirty();
        context.player().sendSystemMessage(Component.literal(context.villager().getName().getString()
                + " offers " + action.amount() / 20 + " seconds of free companion service. Joining uses a party slot. ")
                .append(Component.literal("[Review offer]").withStyle(style -> style.withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/vr companion stories " + context.villager().getUUID())))));
        return new ActionResult(ActionStatus.SUCCESS, "Service offered; player acceptance is required", capabilities, VillagerActionResult.success());
    }

    public static boolean accept(ServerPlayer player, Villager villager, String id) {
        var state = CompanionSavedData.get(player.serverLevel()).find(villager.getUUID());
        if (state == null || player.distanceToSqr(villager) > 64 || player.serverLevel() != villager.level()) return false;
        var offer = state.getCompound("ServiceOffers").getCompound(player.getUUID() + "/" + id);
        if (offer.isEmpty() || offer.getBoolean("Used") || state.contains("ActiveService")
                || VillagerReputationManager.getReputation(player.serverLevel(), villager, player.getUUID()) < offer.getInt("MinimumReputation")) return false;
        var result = PartyVillagerContractService.recruitTemporary(player, villager, offer);
        if (!result.success()) {
            player.sendSystemMessage(Component.translatable(result.messageKey()));
            return false;
        }
        offer.putBoolean("Used", true);
        CompanionSavedData.get(player.serverLevel()).setDirty();
        player.sendSystemMessage(Component.literal("Service accepted for " + offer.getInt("Duration") / 20
                + " seconds. It ends with a safe goodbye; permanent recruitment is a separate choice."));
        return true;
    }

    static void maintain(ServerLevel level, PartyRecord party, PartyVillagerRecord record) {
        var state = CompanionSavedData.get(level).find(record.villagerId());
        if (state == null || !state.contains("ActiveService")) return;
        if (level.getServer().overworld().getGameTime() < state.getCompound("ActiveService").getLong("End")) return;
        var villager = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(level.getServer(), record.villagerId());
        // Keep the slot while unloaded, downed or in combat: expiry must not become an escape mechanism.
        if (villager == null || !CompanionLifecycleService.canPart(villager)) {
            state.putString("Reason", "Service ended; waiting for a safe goodbye (still occupies a slot)");
            CompanionSavedData.get(level).setDirty();
            return;
        }
        PartyVillagerContractService.endTemporaryService((ServerLevel) villager.level(), party, record, villager);
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"required":["service_id","duration_ticks"],"properties":{
                  "service_id":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":128},
                  "duration_ticks":{"type":"integer","minimum":200,"maximum":168000},
                  "text":{"type":"string","minLength":1,"maxLength":120},
                  "minimum_reputation":{"type":"integer","minimum":0,"maximum":1000}}}
                """).getAsJsonObject();
    }
}
