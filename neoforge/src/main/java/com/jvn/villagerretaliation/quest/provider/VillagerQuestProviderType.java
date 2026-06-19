package com.jvn.villagerretaliation.quest.provider;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerQuestProviderType implements QuestProviderType {
    public static final VillagerQuestProviderType INSTANCE = new VillagerQuestProviderType();
    public static final ResourceLocation ID = VillagerRetaliation.id("villager");

    private VillagerQuestProviderType() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition) {
        if (context == null || definition == null) {
            return false;
        }
        Optional<DialogueContext> dialogueContext = context.dialogueContext();
        if (dialogueContext.isPresent()) {
            return definition.offer().matches(dialogueContext.get());
        }
        return context.providerBinding()
                .filter(binding -> ID.equals(binding.providerType()))
                .map(binding -> matchesProviderRequirements(binding, definition.offer()))
                .orElse(false);
    }

    @Override
    public boolean matchesIssuerLock(
            QuestExecutionContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || definition == null || !definition.rules().lockedToVillager()) {
            return true;
        }
        if (progress.startedVillagerId() == null) {
            return true;
        }
        return context != null
                && context.providerBinding()
                        .filter(binding -> ID.equals(binding.providerType()))
                        .map(binding -> binding.matchesProviderId(progress.startedVillagerId()))
                        .orElse(false);
    }

    public QuestProviderBinding bindingFromDialogueContext(DialogueContext context) {
        if (context == null) {
            throw new IllegalArgumentException("dialogue context must not be null");
        }
        return new QuestProviderBinding(
                ID,
                context.villager().getUUID(),
                VillagerPresetNameRegistry.resolveDisplayName(context.villager()).getString(),
                VillagerProfessionUtil.id(context.profession()),
                context.villager().getVillagerData().getLevel(),
                context.level().dimension(),
                context.villager().blockPosition(),
                VillageScopeKeys.forVillager(context.level(), context.villager()),
                skillsFromContext(context),
                true);
    }

    public Optional<QuestProviderBinding> bindingFromProgress(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.startedVillagerId() == null) {
            return Optional.empty();
        }
        return Optional.of(new QuestProviderBinding(
                ID,
                progress.startedVillagerId(),
                progress.issuerName(),
                ResourceLocation.tryParse(progress.issuerProfession()),
                progress.issuerLevel(),
                progress.issuerDimension(),
                progress.issuerPos(),
                progress.issuerVillageKey(),
                skillsFromSavedProfile(level, progress),
                false));
    }

    private static boolean matchesProviderRequirements(
            QuestProviderBinding binding,
            QuestDefinition.Offer offer) {
        if (offer == null) {
            return true;
        }
        if (!offer.professions().isEmpty() && !offer.professions().contains(profession(binding.professionId()))) {
            return false;
        }
        if (binding.level() < offer.minVillagerLevel()) {
            return false;
        }
        for (Map.Entry<VillagerSkill, Integer> entry : offer.minSkills().entrySet()) {
            if (binding.skillValue(entry.getKey()) < VillagerSkillSet.clamp(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static VillagerProfession profession(ResourceLocation professionId) {
        return professionId == null
                ? null
                : VillagerProfessionUtil.parse(professionId.toString()).orElse(null);
    }

    private static Map<VillagerSkill, Integer> skillsFromContext(DialogueContext context) {
        Map<VillagerSkill, Integer> skills = new EnumMap<>(VillagerSkill.class);
        for (VillagerSkill skill : VillagerSkill.values()) {
            skills.put(skill, context.skillValue(skill));
        }
        return skills;
    }

    private static Map<VillagerSkill, Integer> skillsFromSavedProfile(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress) {
        if (level == null || progress == null || progress.startedVillagerId() == null) {
            return Map.of();
        }
        Optional<VillagerProfile> profile = VillagerProfileManager.getProfile(level, progress.startedVillagerId());
        if (profile.isEmpty() || profile.get().skills() == null) {
            return Map.of();
        }
        Map<VillagerSkill, Integer> skills = new EnumMap<>(VillagerSkill.class);
        for (VillagerSkill skill : VillagerSkill.values()) {
            skills.put(skill, profile.get().skills().get(skill));
        }
        return skills;
    }
}
