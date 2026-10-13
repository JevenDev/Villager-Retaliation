package com.jvn.villagerretaliation.party;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class CompanionRelationshipService {
    private CompanionRelationshipService() {
    }

    public static void unlock(ServerLevel level, UUID villagerId, UUID playerId, boolean free, int minimumReputation) {
        var data = CompanionSavedData.get(level);
        var state = data.getOrCreate(villagerId);
        var grants = state.getCompound("Eligibility");
        var grant = grants.getCompound(playerId.toString());
        if (grant.getBoolean("Free") && !grant.contains("FreeMinimumReputation"))
            grant.putInt("FreeMinimumReputation", grant.getInt("MinimumReputation"));
        if (free) grant.putInt("FreeMinimumReputation", grant.contains("FreeMinimumReputation")
                ? Math.min(grant.getInt("FreeMinimumReputation"), minimumReputation) : minimumReputation);
        grant.putBoolean("Free", free || grant.getBoolean("Free"));
        grant.putInt("MinimumReputation", grant.contains("MinimumReputation")
                ? Math.min(grant.getInt("MinimumReputation"), minimumReputation) : minimumReputation);
        grants.put(playerId.toString(), grant);
        state.put("Eligibility", grants);
        data.setDirty();
    }

    public static boolean eligible(ServerLevel level, Villager villager, ServerPlayer player) {
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        if (state == null) return false;
        if (state.getBoolean("TrustSuspended") && player.getUUID().equals(CompanionLifecycleService.owner(level, villager.getUUID()))) {
            return VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                    >= VillagerReputationLevel.REVERED.trustRank();
        }
        if (player.getUUID().equals(CompanionLifecycleService.owner(level, villager.getUUID())) && state.getBoolean("Recruited")) return true;
        var grants = state.getCompound("Eligibility");
        String key = player.getUUID().toString();
        return grants.contains(key) && VillagerReputationManager.getReputation(level, villager, player.getUUID())
                >= grants.getCompound(key).getInt("MinimumReputation");
    }

    public static int recruitmentCost(ServerLevel level, Villager villager, ServerPlayer player) {
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        if (state != null && player.getUUID().equals(CompanionLifecycleService.owner(level, villager.getUUID())) && state.getBoolean("Recruited")) return 0;
        if (state != null) {
            var grant = state.getCompound("Eligibility").getCompound(player.getUUID().toString());
            int required = grant.contains("FreeMinimumReputation") ? grant.getInt("FreeMinimumReputation") : grant.getInt("MinimumReputation");
            if (grant.getBoolean("Free") && VillagerReputationManager.getReputation(level, villager, player.getUUID()) >= required) return 0;
        }
        if (VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank() >= VillagerReputationLevel.REVERED.trustRank()) return 0;
        return com.jvn.villagerretaliation.config.VillagerRetaliationConfig.COMPANION_RECRUITMENT_COST.get();
    }

    public static boolean experience(ServerLevel level, UUID villagerId, UUID playerId, String experienceId, int amount) {
        if (experienceId == null || experienceId.isBlank() || experienceId.length() > 128 || amount < 1 || amount > 5) return false;
        var data = CompanionSavedData.get(level);
        var state = data.getOrCreate(villagerId);
        var experiences = state.getCompound("Experiences");
        String key = playerId + "/" + experienceId;
        if (experiences.contains(key)) return false;
        experiences.putBoolean(key, true);
        state.put("Experiences", experiences);
        var bonds = state.getCompound("Bonds");
        bonds.putInt(playerId.toString(), Math.min(100, bonds.getInt(playerId.toString()) + amount));
        state.put("Bonds", bonds);
        data.setDirty();
        publishBond(level, villagerId, playerId, bonds.getInt(playerId.toString()));
        CompanionGrowthService.rememberEvent(level, villagerId, playerId, experienceId);
        return true;
    }

    public static boolean trustSuspended(ServerLevel level, UUID villagerId) {
        var state = CompanionSavedData.get(level).find(villagerId);
        return state != null && state.getBoolean("TrustSuspended");
    }

    public static int bond(ServerLevel level, UUID villagerId, UUID playerId) {
        var state = CompanionSavedData.get(level).find(villagerId);
        return state == null ? 0 : state.getCompound("Bonds").getInt(playerId.toString());
    }

    public static boolean rescued(ServerLevel level, Villager villager, ServerPlayer player) {
        var data = CompanionSavedData.get(level);
        var state = data.find(villager.getUUID());
        if (state == null) return false;
        String key = "LastRescue/" + player.getUUID();
        long now = level.getServer().overworld().getGameTime();
        if (state.contains(key) && now - state.getLong(key) < 24000L) return false;
        if (state.contains("LastBetrayal") && now - state.getLong("LastBetrayal") < 24000L) return false;
        state.putLong(key, now);
        data.setDirty();
        boolean changed = experience(level, villager.getUUID(), player.getUUID(), "rescue/" + now, 3);
        if (changed) {
            CompanionGrowthService.rememberEvent(level, villager.getUUID(), player.getUUID(), "villagerretaliation:rescued");
            player.sendSystemMessage(Component.literal("Your companion remembers the rescue. ")
                    .append(Component.literal("[Talk about it]").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/vr companion stories " + villager.getUUID())))));
        }
        var facts = VillagerQuestFacts.get(level);
        facts.setTag(QuestScopeKey.player(player.getUUID()), ResourceLocation.parse("villagerretaliation:companion_rescued"));
        facts.setVariable(QuestScopeKey.player(player.getUUID()), "rescued_companion", villager.getUUID().toString());
        return changed;
    }

    public static void betrayed(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!player.getUUID().equals(CompanionLifecycleService.owner(level, villager.getUUID()))) return;
        var data = CompanionSavedData.get(level);
        var state = data.find(villager.getUUID());
        long now = level.getServer().overworld().getGameTime();
        if (state.contains("LastBetrayal") && now - state.getLong("LastBetrayal") < 200L) return;
        state.putLong("LastBetrayal", now);
        int strikes = state.getInt("BetrayalStrikes") + 1;
        state.putInt("BetrayalStrikes", strikes);
        var bonds = state.getCompound("Bonds");
        bonds.putInt(player.getUUID().toString(), Math.max(-100, bonds.getInt(player.getUUID().toString()) - 10));
        state.put("Bonds", bonds);
        player.sendSystemMessage(Component.literal(strikes < 3
                ? "Your companion's trust is hurt. Repeated attacks will make them refuse to follow."
                : "Your companion refuses to follow after repeated attacks. Rebuild their trust before asking again."));
        if (strikes >= 3) {
            state.putBoolean("TrustSuspended", true);
            var party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
            if (party != null) {
                var record = party.villager(villager.getUUID());
                record.setStaying(level.dimension().location(), villager.blockPosition());
                com.jvn.villagerretaliation.interaction.VillagerRecruitmentService.applyPartyStay(level, villager, record.commanderId(), villager.blockPosition());
                PartyService.markChanged(level);
                PartySyncService.syncParty(level.getServer(), party.id());
            }
        }
        data.setDirty();
        publishBond(level, villager.getUUID(), player.getUUID(), bonds.getInt(player.getUUID().toString()));
    }

    private static void publishBond(ServerLevel level, UUID villagerId, UUID playerId, int value) {
        VillagerQuestFacts.get(level).setVariable(QuestScopeKey.player(playerId), "companion_bond/" + villagerId, Integer.toString(value));
    }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || context.villager() == null || context.player() == null)
            return ActionResult.unavailable("A specific villager and player are required", capabilities, VillagerActionResult.EMPTY);
        if (action.kind() == VillagerActionDefinition.Kind.COMPANION_UNLOCK) {
            unlock(context.level(), context.villager().getUUID(), context.player().getUUID(), action.factValue().equals("free"), action.amount());
            return new ActionResult(ActionStatus.SUCCESS, "Companion eligible. Joining remains the player's choice", capabilities, VillagerActionResult.success());
        }
        boolean changed = experience(context.level(), context.villager().getUUID(), context.player().getUUID(), action.factKey(), action.amount());
        return changed ? new ActionResult(ActionStatus.SUCCESS, "Shared experience remembered", capabilities, VillagerActionResult.success())
                : ActionResult.alreadySatisfied("Shared experience already remembered", capabilities, VillagerActionResult.success());
    }

    public static JsonObject unlockSchema() {
        return com.google.gson.JsonParser.parseString("{\"properties\":{\"free\":{\"type\":\"boolean\"},\"minimum_reputation\":{\"type\":\"integer\",\"minimum\":-1000,\"maximum\":1000}}}").getAsJsonObject();
    }

    public static JsonObject bondSchema() {
        return com.google.gson.JsonParser.parseString("{\"required\":[\"experience_id\",\"amount\"],\"properties\":{\"experience_id\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":128},\"amount\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":5}}}").getAsJsonObject();
    }
}
