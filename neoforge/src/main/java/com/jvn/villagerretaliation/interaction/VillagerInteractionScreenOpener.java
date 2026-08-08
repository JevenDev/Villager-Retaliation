package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.allegiance.AllegianceState;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mount.VillagerMountOwnershipDialogue;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillageAllegianceView;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerInteractionScreenOpener {
    private VillagerInteractionScreenOpener() {
    }

    public static void openNormal(ServerPlayer player, Villager villager, boolean forceCameraTowardsVillager) {
        ServerLevel level = player.serverLevel();
        DialogueContext dialogueContext = VillagerInteractionService.createDialogueContext(level, player, villager);
        DialogueDisposition mood = VillagerDialogueService.moodFor(dialogueContext);
        List<DialogueOptionDefinition> dialogueOptions = VillagerMountOwnershipDialogue.addAvailableOption(
                level, player, villager, VillagerDialogueResources.dialogueOptions(dialogueContext, mood));
        dialogueOptions = com.jvn.villagerretaliation.duel.DuelDialogueService.addAvailableOptions(
                level, player, villager, dialogueOptions);
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
        VillagerInteractionService.sendPersonalRoutineVillagerChat(player, villager, greetingText);
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
        String openingText = VillagerDialogueResources
                .message(context, "interaction.clipboard.assignment.opening")
                .orElse("");
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
        VillagerInteractionService.sendPersonalRoutineVillagerChat(player, villager, openingText);
    }

    public static void refreshNormal(ServerPlayer player, Villager villager) {
        ServerLevel level = player.serverLevel();
        DialogueContext dialogueContext = VillagerInteractionService.createDialogueContext(level, player, villager);
        DialogueDisposition mood = VillagerDialogueService.moodFor(dialogueContext);
        List<DialogueOptionDefinition> dialogueOptions = VillagerMountOwnershipDialogue.addAvailableOption(
                level, player, villager, VillagerDialogueResources.dialogueOptions(dialogueContext, mood));
        dialogueOptions = com.jvn.villagerretaliation.duel.DuelDialogueService.addAvailableOptions(
                level, player, villager, dialogueOptions);
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
        DialogueContext dialogueContext = VillagerInteractionService.createDialogueContext(level, player, villager);
        VillagerAssignmentSnapshot assignment =
                HiredVillagerContractService.synchronizeAssignment(level, villager);
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerGiftKnowledgeService.GiftKnowledgeSnapshot giftKnowledge =
                VillagerGiftKnowledgeService.knownGifts(level, player, villager.getVillagerData().getProfession());
        ReputationSnapshot reputation = reputationSnapshot(level, villager, player);
        boolean hiredByPlayer = HiredVillagerContractService.isHiredBy(level, villager, player);
        boolean hiredAnyPlayer = HiredVillagerContractService.isHired(level, villager);
        String hirerName = hiredAnyPlayer ? hirerName(level, villager) : "";
        PartyRecord villagerParty = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord partyVillager = villagerParty == null ? null : villagerParty.villager(villager.getUUID());
        PartyRecord playerParty = PartyService.getPartyForPlayer(level, player.getUUID()).orElse(null);
        boolean partyVillagerAuthorized = partyVillager != null
                && playerParty != null
                && villagerParty.id().equals(playerParty.id())
                && villagerParty.hasAdminPrivileges(player.getUUID());
        boolean partyVillagerPartyMember = villagerParty != null
                && playerParty != null
                && villagerParty.id().equals(playerParty.id());
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
        net.minecraft.nbt.CompoundTag workState = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(workState, villager);
        HiredHuntingTargets.Selection huntingTargets = HiredHuntingTargets.fromState(workState);
        return new OpenVillagerInteractionPayload(
                villager.getId(),
                "",
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                VillagerPresetNameRegistry.resolveGender(villager).serializedName(),
                villager.isBaby(),
                hasTradingProfession(villager)
                        && !VillagerBehaviorSuppressionPolicy.suppresses(
                                villager, VillagerBehaviorSuppressionPolicy.Behavior.TRADING),
                com.jvn.villagerretaliation.duel.DuelService.availability(level, player, villager).visible(),
                reputation.value(),
                reputation.level(),
                mood,
                primaryMood,
                VillagerRecruitmentService.isFollowing(villager, player),
                VillagerRecruitmentService.isStayingHere(villager, player),
                VillagerRecruitmentService.canFollow(level, villager, player),
                VillagerRecruitmentService.canCommandStayHere(level, villager, player),
                assignment.revision(),
                VillagerInteractionTracker.isRoutineChatMuted(level, villager, player),
                forcedConversation,
                clipboardMenu,
                clipboardMenu && VillagerInteractionService.clipboardSelectionHasAssignment(player, level, villager),
                hiredByPlayer,
                hiredAnyPlayer && !hiredByPlayer,
                hirerName,
                HiredVillagerContractService.getRemainingHireDays(level, villager),
                com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.canOpenPreferred(
                        level, villager, player),
                com.jvn.villagerretaliation.inventory.VillagerJobInventoryAuthorization.canAccess(
                        level, villager, player),
                partyVillager != null,
                partyVillagerAuthorized,
                partyVillagerPartyMember,
                partyRecruitAvailable,
                com.jvn.villagerretaliation.mount.VillagerMountAssignmentService.featureAvailable(),
                com.jvn.villagerretaliation.mount.VillagerMountAssignmentService.hasAssignment(level, villager.getUUID()),
                HiredVillagerContractService.isMountedTravelEnabled(level, villager),
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
                HiredAnimalHandlingOptions.shearSheep(workState),
                dialogueOptions,
                giftKnowledge.preferences(),
                allegianceView(dialogueContext, level, villager, partyVillager != null),
                VillagerSocialGraphService.familySnapshot(level, villager),
                VillagerSocialGraphService.relationshipSnapshot(level, villager)
        );
    }

    private static ReputationSnapshot reputationSnapshot(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return new ReputationSnapshot(reputation.value(), reputation.level());
    }

    private static String hirerName(ServerLevel level, Villager villager) {
        UUID hirerId = HiredVillagerContractService.currentContractHirer(villager).orElse(null);
        if (hirerId == null) {
            return "Player";
        }
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(hirerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        var profileCache = level.getServer().getProfileCache();
        if (profileCache == null) {
            return "Player";
        }
        return profileCache
                .get(hirerId)
                .map(profile -> profile.getName())
                .filter(name -> !name.isBlank())
                .orElse("Player");
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

    private static VillageAllegianceView allegianceView(
            DialogueContext context,
            ServerLevel level,
            Villager villager,
            boolean recruitedPartyVillager) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceData data = VillageAllegianceApi.get(villager).orElse(null);
        Optional<VillageAllegianceRegistrySavedData.AllegianceRecord> home = data != null && data.isKnown()
                ? registry.canonicalRecord(data.primary())
                : Optional.empty();
        Optional<VillageAllegianceId> currentId = registry.peekAt(level, villager.blockPosition());
        Optional<VillageAllegianceRegistrySavedData.AllegianceRecord> current = currentId.flatMap(registry::canonicalRecord);
        String homeName = data == null || data.state() == AllegianceState.UNKNOWN
                ? "Unknown"
                : data.isKnown() && home.isEmpty()
                        ? "Orphaned village identity"
                        : home.map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName).orElse("Wanderer");
        VillageAllegianceView.HomeStatus homeStatus = data == null || data.state() == AllegianceState.UNKNOWN
                ? VillageAllegianceView.HomeStatus.UNKNOWN
                : data.isKnown()
                        ? VillageAllegianceView.HomeStatus.KNOWN
                        : VillageAllegianceView.HomeStatus.WANDERER;
        String currentName = current.map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName)
                .orElse("Outside a tracked village");
        boolean inVillage = current.isPresent();
        boolean atHome = data != null && data.isKnown() && currentId.isPresent()
                && registry.canonical(data.primary()).filter(currentId.get()::equals).isPresent();
        Map<String, String> replacements = Map.of(
                "home_village", homeName,
                "current_village", currentName);
        String homeAnswerKey = switch (homeStatus) {
            case KNOWN -> atHome
                    ? "allegiance.answer.home_here"
                    : "allegiance.answer.home_away";
            case WANDERER -> recruitedPartyVillager
                    ? "allegiance.answer.wanderer_party"
                    : inVillage
                            ? "allegiance.answer.wanderer_settling"
                            : "allegiance.answer.wanderer";
            case UNKNOWN -> "allegiance.answer.unknown";
        };
        String currentAnswerKey;
        if (!inVillage) {
            currentAnswerKey = "allegiance.answer.here_outside";
        } else if (atHome) {
            currentAnswerKey = "allegiance.answer.here_home";
        } else if (homeStatus == VillageAllegianceView.HomeStatus.KNOWN) {
            currentAnswerKey = "allegiance.answer.here_foreign";
        } else if (recruitedPartyVillager) {
            currentAnswerKey = "allegiance.answer.here_party";
        } else {
            currentAnswerKey = "allegiance.answer.here_visiting";
        }
        return new VillageAllegianceView(
                homeName,
                currentName,
                homeStatus,
                inVillage,
                atHome,
                dialogueMessage(context, "allegiance.prompt", replacements,
                        "Is there something you would like to ask about where I belong?"),
                dialogueMessage(context, "allegiance.option.ask_home", replacements,
                        "Where do you call home?"),
                dialogueMessage(context, "allegiance.option.ask_here", replacements,
                        "Do you belong to this village?"),
                dialogueMessage(context, "allegiance.option.reassign", replacements,
                        "Would you make this village your home?"),
                dialogueMessage(context, homeAnswerKey, replacements,
                        fallbackHomeAnswer(homeStatus, inVillage, atHome, recruitedPartyVillager, homeName)),
                dialogueMessage(context, currentAnswerKey, replacements,
                        fallbackCurrentVillageAnswer(inVillage, atHome, homeStatus, recruitedPartyVillager, currentName, homeName)));
    }

    private static String dialogueMessage(
            DialogueContext context,
            String key,
            Map<String, String> replacements,
            String fallback) {
        return VillagerDialogueResources.message(context, key, replacements).orElse(fallback);
    }

    private static String fallbackHomeAnswer(
            VillageAllegianceView.HomeStatus homeStatus,
            boolean inVillage,
            boolean atHome,
            boolean recruitedPartyVillager,
            String homeName) {
        return switch (homeStatus) {
            case KNOWN -> atHome
                    ? "This is my home. I belong to " + homeName + ", and I intend to look after it."
                    : "I come from " + homeName + ". I may be traveling, but that is still my home.";
            case WANDERER -> recruitedPartyVillager
                    ? "I do not have a home village, but I am traveling with this party. I will not settle somewhere unless someone I trust in our party asks me to."
                    : inVillage
                            ? "I do not have a home village yet. If I remain here for a full day, perhaps I will call this place home."
                            : "I do not have a home village. For now, I go where the road takes me.";
            case UNKNOWN -> "I am not certain where I belong. I wish I had a clearer answer for you.";
        };
    }

    private static String fallbackCurrentVillageAnswer(
            boolean inVillage,
            boolean atHome,
            VillageAllegianceView.HomeStatus homeStatus,
            boolean recruitedPartyVillager,
            String currentName,
            String homeName) {
        if (!inVillage) {
            return "We are not standing in a village right now.";
        }
        if (atHome) {
            return "Yes. This is " + currentName + ", and this is where I belong.";
        }
        if (homeStatus == VillageAllegianceView.HomeStatus.KNOWN) {
            return "No. We are in " + currentName + ", but my home is " + homeName + ".";
        }
        if (recruitedPartyVillager) {
            return "No. I am here with my party, but I have not joined " + currentName + ".";
        }
        return "Not yet. I am only staying in " + currentName + " for now.";
    }

    private static boolean hasTradingProfession(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    private record ReputationSnapshot(int value, VillagerReputationLevel level) {
    }
}
