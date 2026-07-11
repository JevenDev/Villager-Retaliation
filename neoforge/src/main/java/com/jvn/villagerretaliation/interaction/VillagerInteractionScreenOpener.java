package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
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
                false,
                forceCameraTowardsVillager,
                dialogueOptions
        );
        VillagerInteractionTracker.rememberConversationOpened(level, villager, player);
        trySendToPlayer(player, payload);
        VillagerAmbientIndicatorService.onConversationOpened(level, villager, player);
        VillagerInteractionService.sendPersonalVillagerChat(player, villager, greetingText);
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
                false,
                forceCameraTowardsVillager,
                dialogueOptions
        );
        VillagerInteractionTracker.rememberConversationOpened(level, villager, player);
        trySendToPlayer(player, payload);
        VillagerAmbientIndicatorService.onConversationOpened(level, villager, player);
    }

    public static void openClipboard(ServerPlayer player, Villager villager, boolean forceCameraTowardsVillager) {
        ServerLevel level = player.serverLevel();
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        OpenVillagerInteractionPayload payload = createPayload(
                level,
                player,
                villager,
                VillagerDialogueService.moodFor(context),
                context.primaryMood(),
                false,
                true,
                forceCameraTowardsVillager,
                List.of()
        );
        VillagerInteractionTracker.rememberConversationOpened(level, villager, player);
        trySendToPlayer(player, payload);
        VillagerAmbientIndicatorService.onConversationOpened(level, villager, player);
    }

    public static void refreshNormal(ServerPlayer player, Villager villager) {
        ServerLevel level = player.serverLevel();
        DialogueContext dialogueContext = VillagerInteractionService.createDialogueContext(level, player, villager);
        DialogueDisposition mood = VillagerDialogueService.moodFor(dialogueContext);
        List<DialogueOptionDefinition> dialogueOptions = VillagerDialogueResources.dialogueOptions(dialogueContext, mood);
        trySendToPlayer(player, createPayload(
                level,
                player,
                villager,
                mood,
                dialogueContext.primaryMood(),
                false,
                false,
                false,
                dialogueOptions
        ));
    }

    private static void trySendToPlayer(ServerPlayer player, OpenVillagerInteractionPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException ignored) {
            // Server-side test harnesses can use mock connections without negotiated custom payloads.
        }
    }

    private static OpenVillagerInteractionPayload createPayload(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            DialogueDisposition mood,
            VillagerMood primaryMood,
            boolean forcedConversation,
            boolean clipboardMenu,
            boolean forceCameraTowardsVillager,
            List<DialogueOptionDefinition> dialogueOptions) {
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerGiftKnowledgeService.GiftKnowledgeSnapshot giftKnowledge =
                VillagerGiftKnowledgeService.knownGifts(level, player, villager.getVillagerData().getProfession());
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        boolean hiredByPlayer = HiredVillagerContractService.isHiredBy(level, villager, player);
        boolean hiredAnyPlayer = HiredVillagerContractService.isHired(level, villager);
        PartyRecord villagerParty = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord partyVillager = villagerParty == null ? null : villagerParty.villager(villager.getUUID());
        PartyRecord playerParty = PartyService.getPartyForPlayer(level, player.getUUID()).orElse(null);
        boolean partyVillagerAuthorized = partyVillager != null
                && villagerParty.leaderId().equals(player.getUUID())
                && partyVillager.recruiterId().equals(player.getUUID());
        boolean partyRecruitAvailable = partyVillager == null
                && !hiredAnyPlayer
                && VillagerRecruitmentService.canRecruit(level, villager, player)
                && (playerParty == null
                || playerParty.leaderId().equals(player.getUUID())
                && playerParty.villagers().size() < PartyService.MAX_VILLAGERS);
        int partyRemainingDays = partyVillager == null
                ? 0
                : partyVillager.remainingDays(level.getServer().overworld().getGameTime());
        VillagerWalletService.WalletSnapshot wallet = VillagerWalletService.getWallet(villager);
        VillagerCurrencyResources.Text currencyText = VillagerCurrencyResources.text(level.getServer());
        VillagerReputationNetworking.sendProfile(player, villager, profile);
        net.minecraft.nbt.CompoundTag workState = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(workState, villager);
        HiredHuntingTargets.Selection huntingTargets = HiredHuntingTargets.fromState(workState);
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
                VillagerRecruitmentService.isStayingHere(villager, player),
                forcedConversation,
                clipboardMenu,
                hiredByPlayer,
                hiredAnyPlayer && !hiredByPlayer,
                HiredVillagerContractService.getRemainingHireDays(level, villager),
                partyVillager != null,
                partyVillagerAuthorized,
                partyRecruitAvailable,
                partyRemainingDays,
                VillagerWalletService.getVendorCurrencyAvailable(villager),
                VillagerWalletService.getVendorCurrencyCap(villager),
                wallet.lifetimeEarned(),
                wallet.lifetimeDeposited(),
                currencyText.name(),
                currencyText.pluralName(),
                currencyText.walletLabel(),
                currencyText.iconSprite(),
                currencyText.textColor(),
                forceCameraTowardsVillager,
                HiredVillagerRoles.availableRoles(level, villager),
                HiredVillagerContractService.activeRole(level, villager),
                BrewingWorker.hasOrder(workState),
                BuilderTaskState.hasTask(workState),
                HiredVillagerContractService.isOneOffBuilderJob(level, villager),
                HiredFarmingOptions.tillSoil(workState),
                huntingTargets.animals(),
                huntingTargets.hostiles(),
                huntingTargets.players(),
                HiredLoggingFilters.selectedFilterStrings(workState),
                HiredLoggingOptions.stripLogs(workState),
                HiredLoggingOptions.harvestLeaves(workState),
                HiredLoggingOptions.bonemealSaplings(workState),
                HiredLoggingOptions.plantSaplings(workState),
                HiredLoggingOptions.pickUpDecayDrops(workState),
                HiredAnimalBreedingTargets.selectedTargetStrings(workState),
                HiredAnimalCullSettings.cap(workState),
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
