package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerInteractionScreenOpener {
    private VillagerInteractionScreenOpener() {
    }

    public static void openNormal(ServerPlayer player, Villager villager, boolean forceCameraTowardsVillager) {
        ServerLevel level = player.serverLevel();
        DialogueContext dialogueContext = VillagerInteractionService.createDialogueContext(level, player, villager);
        DialogueDisposition mood = VillagerDialogueService.moodFor(dialogueContext);
        List<DialogueOptionDefinition> dialogueOptions = VillagerDialogueResources.dialogueOptions(dialogueContext, mood);
        String greetingText = VillagerDialogueService.selectOpeningGreeting(dialogueContext);
        OpenVillagerInteractionPayload payload = createPayload(
                level,
                player,
                villager,
                mood,
                dialogueContext.primaryMood(),
                false,
                forceCameraTowardsVillager,
                dialogueOptions
        );
        PacketDistributor.sendToPlayer(player, payload);
        VillagerAmbientIndicatorService.onConversationOpened(level, villager, player);
        VillagerInteractionService.broadcastVillagerChat(level, villager, greetingText);
    }

    public static void openForced(
            ServerPlayer player,
            Villager villager,
            List<DialogueOptionDefinition> dialogueOptions,
            boolean forceCameraTowardsVillager) {
        ServerLevel level = player.serverLevel();
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        OpenVillagerInteractionPayload payload = createPayload(
                level,
                player,
                villager,
                VillagerDialogueService.moodFor(context),
                context.primaryMood(),
                true,
                forceCameraTowardsVillager,
                dialogueOptions
        );
        PacketDistributor.sendToPlayer(player, payload);
        VillagerAmbientIndicatorService.onConversationOpened(level, villager, player);
    }

    private static OpenVillagerInteractionPayload createPayload(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            DialogueDisposition mood,
            VillagerMood primaryMood,
            boolean forcedConversation,
            boolean forceCameraTowardsVillager,
            List<DialogueOptionDefinition> dialogueOptions) {
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerGiftKnowledgeService.GiftKnowledgeSnapshot giftKnowledge =
                VillagerGiftKnowledgeService.knownGifts(level, player, villager.getVillagerData().getProfession());
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        VillagerReputationNetworking.sendProfile(player, villager, profile);
        return new OpenVillagerInteractionPayload(
                villager.getId(),
                "",
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                VillagerPresetNameRegistry.resolveGender(villager).serializedName(),
                villager.isBaby(),
                reputation.value(),
                reputation.level(),
                mood,
                primaryMood,
                VillagerRecruitmentService.isFollowing(villager, player),
                forcedConversation,
                forceCameraTowardsVillager,
                dialogueOptions,
                giftKnowledge.likedGiftNames(),
                giftKnowledge.dislikedGiftNames(),
                VillagerSocialGraphService.familySnapshot(level, villager),
                VillagerSocialGraphService.relationshipSnapshot(level, villager)
        );
    }

    private static ReputationSnapshot reputationSnapshot(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return new ReputationSnapshot(reputation.value(), reputation.level());
    }

    private static String professionTranslationKey(Villager villager) {
        if (villager.isBaby()) {
            return "villagerretaliation.gui.profession.child";
        }
        return VillagerProfessionUtil.translationKey(
                villager.getVillagerData().getProfession(),
                "villagerretaliation.gui.profession.unemployed"
        );
    }

    private record ReputationSnapshot(int value, VillagerReputationLevel level) {
    }
}
