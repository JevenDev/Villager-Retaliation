package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.campaign.CampaignCharacterService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.runtime.QuestStageBranchOptionIds;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

public final class QuestPlaytestOffers {
    private QuestPlaytestOffers() { }

    public static Villager provider(ServerPlayer player, ResourceLocation quest, UUID requested) {
        var definition = VillagerQuestResources.quest(player.server, quest)
                .orElseThrow(() -> new IllegalArgumentException("Unknown quest " + quest));
        String role = VillagerQuestResources.compiledQuest(player.server, quest).map(value -> value.provider().campaignRole()).orElse("");
        if (definition.campaign() != null && !role.isBlank()) {
            var character = CampaignCharacterService.resolve(player.server, definition.campaign(), role);
            if (!character.available()) throw new IllegalStateException(character.diagnostic());
            if (requested != null && !requested.equals(character.entity().getUUID()))
                throw new IllegalStateException("This quest must be offered by its bound campaign character");
            return character.entity();
        }
        if (requested != null) {
            var villager = VillagerEntityResolver.loaded(player.server, requested);
            if (!eligible(player, villager, definition)) throw new IllegalStateException("The selected villager does not match this quest's provider requirements");
            return villager;
        }
        var selection = QuestPlaytestSession.selection(player);
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest);
        UUID previous = selection != null && selection.quest().equals(quest) ? selection.provider()
                : progress == null ? null : progress.startedVillagerId();
        if (selection != null && selection.quest().equals(quest) && player.server.getLevel(selection.dimension()) != null)
            player.server.getLevel(selection.dimension()).getChunkAt(selection.position());
        var existing = VillagerEntityResolver.loaded(player.server, previous);
        if (selection != null && selection.quest().equals(quest) && (existing == null || !existing.isAlive()))
            throw new IllegalStateException("The bound test provider is unavailable. Stop this playtest before selecting a replacement.");
        if (eligible(player, existing, definition)) return existing;
        var nearby = player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(24)).stream()
                .filter(villager -> eligible(player, villager, definition))
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
        if (nearby != null) return nearby;
        var villager = EntityType.VILLAGER.create(player.serverLevel());
        if (villager == null) throw new IllegalStateException("Unable to create a test provider");
        var profession = definition.offer().professions().stream()
                .min(Comparator.comparing(value -> BuiltInRegistries.VILLAGER_PROFESSION.getKey(value).toString())).orElse(villager.getVillagerData().getProfession());
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(definition.offer().minVillagerLevel()));
        boolean positioned = false;
        for (var position : java.util.List.of(player.blockPosition().north(2), player.blockPosition().south(2),
                player.blockPosition().east(2), player.blockPosition().west(2))) {
            villager.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
            if (player.serverLevel().noCollision(villager) && player.serverLevel().getFluidState(position).isEmpty()
                    && player.serverLevel().getBlockState(position.below()).isFaceSturdy(player.serverLevel(), position.below(), net.minecraft.core.Direction.UP)) {
                positioned = true;
                break;
            }
        }
        if (!positioned) throw new IllegalStateException("No clear ground is available for a quest provider. Move to an open area and retry.");
        villager.setPersistenceRequired();
        villager.setInvulnerable(true);
        if (!player.serverLevel().addFreshEntity(villager)) throw new IllegalStateException("Unable to spawn a test provider");
        definition.offer().minSkills().forEach((skill, value) -> com.jvn.villagerretaliation.profile.VillagerProfileManager.setSkill(player.serverLevel(), villager, skill, value));
        return villager;
    }

    private static boolean eligible(ServerPlayer player, Villager villager, QuestDefinition definition) {
        return villager != null && villager.isAlive() && !villager.isBaby()
                && VillagerQuestProviderType.INSTANCE.matchesProviderFilters(
                        VillagerInteractionService.createDialogueContext((net.minecraft.server.level.ServerLevel) villager.level(), player, villager), definition);
    }

    public static List<DialogueOptionDefinition> filter(DialogueContext context, List<DialogueOptionDefinition> options, boolean activeTree) {
        var selection = QuestPlaytestSession.selection(context.player());
        if (selection == null || selection.provider() == null) return List.copyOf(options);
        var quest = selection.quest();
        var bindings = VillagerQuestResources.questDialogueCatalog(context.player().server).bindings(quest);
        var definition = VillagerQuestResources.quest(context.player().server, quest).orElse(null);
        if (definition == null) return List.copyOf(options);
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest);
        if (activeTree || !selection.provider().equals(context.providerId())) return List.copyOf(options);
        var relevant = options.stream().filter(option -> quest.equals(option.questAction().questId())
                || quest.toString().equals(option.metadata().quest())
                || QuestStageBranchOptionIds.parse(option.id()).map(key -> key.questId().equals(quest)).orElse(false)
                || option.treeReference() != null && (bindings.stream().anyMatch(binding -> binding.treeId().equals(option.treeReference().treeId()))
                        || definition.links().dialogueTree() != null && definition.links().dialogueTree().equals(option.treeReference().treeId())))
                .toList();
        boolean focused = QuestPlaytestSession.require(context.player(), quest).focusDialogue();
        if (!bindings.isEmpty() || !definition.links().isEmpty() || !relevant.isEmpty())
            return focused && !relevant.isEmpty() ? relevant : List.copyOf(options);
        var action = progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                ? DialogueQuestAction.Action.START : DialogueQuestAction.Action.TURN_IN;
        var metadata = new DialogueEntryMetadata("quest", Set.of("quest_v2"), "", quest.toString(), "", "");
        var option = DialogueOptionDefinition.quest("playtest_" + quest + "_" + action.name().toLowerCase(java.util.Locale.ROOT),
                action == DialogueQuestAction.Action.START ? "Accept quest" : "Turn in quest", DialogueRequestType.QUESTION,
                new DialogueQuestAction(quest, action), metadata, 0);
        boolean available = action != DialogueQuestAction.Action.START || VillagerQuestService.matchesState(context, quest, Set.of("available"));
        var result = new java.util.ArrayList<>(focused ? List.<DialogueOptionDefinition>of() : options);
        result.add(option.withChoice(option.choice(), available, available ? "" : "Quest offer conditions or prerequisites are not met. Check runtime diagnostics."));
        return List.copyOf(result);
    }
}
