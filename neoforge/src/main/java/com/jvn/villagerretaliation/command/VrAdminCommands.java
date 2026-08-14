package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.debug.HiredStressGridService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.duel.DuelLoadout;
import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.social.VillagerRelationshipStage;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;

final class VrAdminCommands {
    private VrAdminCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("admin")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("villagerretaliation.command.admin.help"), false);
                    return 1;
                })
                .then(villager())
                .then(village())
                .then(datapack())
                .then(dialogue())
                .then(quest())
                .then(scene())
                .then(debug());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> villager() {
        return literal("villager")
                .then(profile())
                .then(skill())
                .then(literal("gender")
                        .then(literal("set")
                                .then(villagerArgument()
                                        .then(argument("gender", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(VillagerGender.values())
                                                                .map(VillagerGender::serializedName),
                                                        builder))
                                                .executes(VillagerRetaliationCommands::setProfileGender)))))
                .then(literal("reputation")
                        .then(literal("set")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("player", GameProfileArgument.gameProfile())
                                                .then(argument("value", IntegerArgumentType.integer())
                                                        .executes(VillagerRetaliationCommands::setReputation))))))
                .then(literal("relationship")
                        .then(literal("set")
                                .then(argument("first", EntityArgument.entity())
                                        .then(argument("second", EntityArgument.entity())
                                                .then(argument("stage", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(VillagerRelationshipStage.values())
                                                                        .map(VillagerRelationshipStage::serializedName),
                                                                builder))
                                                        .executes(VillagerRetaliationCommands::setRelationship))))))
                .then(allegiance());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> profile() {
        return literal("profile")
                .then(literal("get")
                        .then(villagerArgument().executes(VillagerRetaliationCommands::getProfile)))
                .then(literal("set")
                        .then(villagerArgument()
                                .then(argument("attribute", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(VillagerSocialAttribute.values())
                                                        .map(VillagerSocialAttribute::serializedName),
                                                builder))
                                        .then(argument("value", IntegerArgumentType.integer(
                                                VillagerSocialAttributes.MIN_VALUE,
                                                VillagerSocialAttributes.MAX_VALUE))
                                                .executes(VillagerRetaliationCommands::setProfileAttribute)))))
                .then(literal("reroll")
                        .then(villagerArgument().executes(VillagerRetaliationCommands::rerollProfile)))
                .then(literal("export")
                        .then(villagerArgument().executes(VillagerRetaliationCommands::exportProfile)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> skill() {
        return literal("skill")
                .then(literal("get")
                        .then(villagerArgument()
                                .executes(VillagerRetaliationCommands::getSkills)
                                .then(skillArgument().executes(VillagerRetaliationCommands::getSkill))))
                .then(literal("set")
                        .then(villagerArgument()
                                .then(skillArgument()
                                        .then(argument("value", IntegerArgumentType.integer(
                                                VillagerSkillSet.MIN_VALUE,
                                                VillagerSkillSet.MAX_VALUE))
                                                .executes(VillagerRetaliationCommands::setSkill)))))
                .then(literal("reroll")
                        .then(villagerArgument().executes(VillagerRetaliationCommands::rerollSkills)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> skillArgument() {
        return argument("skill", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(VillagerSkill.values()).map(VillagerSkill::serializedName),
                        builder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> allegiance() {
        return literal("allegiance")
                .then(literal("inspect").then(entityArgument()
                        .executes(VillagerRetaliationCommands::inspectAllegiance)))
                .then(literal("explain").then(entityArgument()
                        .executes(VillagerRetaliationCommands::explainAllegiance)))
                .then(literal("assign").then(entityArgument()
                        .then(argument("uuid", StringArgumentType.word())
                                .executes(VillagerRetaliationCommands::assignAllegiance))))
                .then(literal("unknown").then(entityArgument()
                        .executes(context -> VillagerRetaliationCommands.setAllegianceState(context, false))))
                .then(literal("unaffiliated").then(entityArgument()
                        .executes(context -> VillagerRetaliationCommands.setAllegianceState(context, true))))
                .then(literal("merge")
                        .then(argument("source", StringArgumentType.word())
                                .then(argument("target", StringArgumentType.word())
                                        .executes(VillagerRetaliationCommands::mergeAllegiances))))
                .then(literal("undoMerge")
                        .then(argument("source", StringArgumentType.word())
                                .executes(VillagerRetaliationCommands::undoAllegianceMerge)))
                .then(literal("fork").then(entityArgument()
                        .executes(VillagerRetaliationCommands::forkAllegiance)))
                .then(literal("repair").then(entityArgument()
                        .executes(VillagerRetaliationCommands::migrateAllegiance)))
                .then(literal("statistics")
                        .executes(VillagerRetaliationCommands::allegianceStatistics))
                .then(literal("resetAbuse").then(entityArgument()
                        .then(argument("player", GameProfileArgument.gameProfile())
                                .executes(VillagerRetaliationCommands::resetAbuse))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> village() {
        return literal("village")
                .then(literal("inspectHere").executes(VillagerRetaliationCommands::inspectVillageHere))
                .then(literal("renameHere")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(VillagerRetaliationCommands::renameVillageHere)))
                .then(literal("list").executes(VillagerRetaliationCommands::listTrackedVillages))
                .then(literal("registry")
                        .then(literal("inspect")
                                .executes(context -> VillagerRetaliationCommands.inspectVillageRegistry(context, 10))
                                .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                        .executes(context -> VillagerRetaliationCommands.inspectVillageRegistry(
                                                context,
                                                IntegerArgumentType.getInteger(context, "limit")))))
                        .then(literal("pruneOlderThan")
                                .then(argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(context -> VillagerRetaliationCommands.pruneVillageRegistry(
                                                context,
                                                IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(literal("suggestMerges")
                                .executes(context -> VillagerRetaliationCommands.suggestVillageRegistryMerges(
                                        context,
                                        VillagerRetaliationCommands.DEFAULT_VILLAGE_REGISTRY_MERGE_RADIUS,
                                        VillagerRetaliationCommands.DEFAULT_VILLAGE_REGISTRY_MERGE_LIMIT))
                                .then(argument("radius", IntegerArgumentType.integer(
                                                1,
                                                VillagerRetaliationCommands.MAX_VILLAGE_REGISTRY_MERGE_RADIUS))
                                        .executes(context -> VillagerRetaliationCommands.suggestVillageRegistryMerges(
                                                context,
                                                IntegerArgumentType.getInteger(context, "radius"),
                                                VillagerRetaliationCommands.DEFAULT_VILLAGE_REGISTRY_MERGE_LIMIT))
                                        .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                                .executes(context ->
                                                        VillagerRetaliationCommands.suggestVillageRegistryMerges(
                                                                context,
                                                                IntegerArgumentType.getInteger(context, "radius"),
                                                                IntegerArgumentType.getInteger(context, "limit"))))))
                        .then(literal("merge")
                                .then(villageKeyArgument("source_key")
                                        .then(villageKeyArgument("target_key")
                                                .executes(VillagerRetaliationCommands::mergeVillageRegistryKeys)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> villageKeyArgument(String name) {
        return argument(name, StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        VillagerRetaliationCommands.villageRegistryKeySuggestions(context.getSource()),
                        builder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> datapack() {
        return literal("datapack")
                .then(literal("diagnostics")
                        .executes(context -> VillagerRetaliationCommands.showDatapackDiagnostics(context, "", ""))
                        .then(literal("severity")
                                .then(argument("severity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(QuestDiagnostic.Severity.values())
                                                        .map(value -> value.name().toLowerCase(Locale.ROOT)),
                                                builder))
                                        .executes(context -> VillagerRetaliationCommands.showDatapackDiagnostics(
                                                context,
                                                StringArgumentType.getString(context, "severity"),
                                                ""))
                                        .then(literal("resource")
                                                .then(argument("resource", StringArgumentType.string())
                                                        .executes(context ->
                                                                VillagerRetaliationCommands.showDatapackDiagnostics(
                                                                        context,
                                                                        StringArgumentType.getString(
                                                                                context, "severity"),
                                                                        StringArgumentType.getString(
                                                                                context, "resource")))))))
                        .then(literal("resource")
                                .then(argument("resource", StringArgumentType.string())
                                        .executes(context -> VillagerRetaliationCommands.showDatapackDiagnostics(
                                                context,
                                                "",
                                                StringArgumentType.getString(context, "resource"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dialogue() {
        return literal("dialogue")
                .then(literal("explain")
                        .then(villagerArgument()
                                .then(argument("request", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(DialogueRequestType.values())
                                                        .map(value -> value.name().toLowerCase(Locale.ROOT)),
                                                builder))
                                        .executes(context -> VillagerRetaliationCommands.explainDialogue(context, ""))
                                        .then(argument("option", StringArgumentType.string())
                                                .executes(context -> VillagerRetaliationCommands.explainDialogue(
                                                        context,
                                                        StringArgumentType.getString(context, "option")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> quest() {
        return literal("quest")
                .then(literal("providers")
                        .executes(context -> VillagerRetaliationCommands.listQuestDebugProviders(
                                context,
                                VillagerRetaliationCommands.DEFAULT_DEBUG_PROVIDER_RADIUS))
                        .then(argument("radius", DoubleArgumentType.doubleArg(
                                        1.0D,
                                        VillagerRetaliationCommands.MAX_DEBUG_PROVIDER_RADIUS))
                                .executes(context -> VillagerRetaliationCommands.listQuestDebugProviders(
                                        context,
                                        DoubleArgumentType.getDouble(context, "radius")))))
                .then(questProviderCommand("start", (context) ->
                        VillagerRetaliationCommands.startQuestDebug(
                                context,
                                VillagerRetaliationCommands.DEFAULT_DEBUG_PROVIDER_RADIUS,
                                false)))
                .then(questProviderCommand("forceStart", (context) ->
                        VillagerRetaliationCommands.startQuestDebug(
                                context,
                                VillagerRetaliationCommands.DEFAULT_DEBUG_PROVIDER_RADIUS,
                                true)))
                .then(literal("remove").then(questIdArgument()
                        .executes(VillagerRetaliationCommands::removeQuestDebug)))
                .then(literal("inspect").then(questIdArgument()
                        .executes(VillagerRetaliationCommands::inspectQuestDebug)))
                .then(questProviderCommand("rebind", (context) ->
                        VillagerRetaliationCommands.rebindQuestDebug(
                                context,
                                VillagerRetaliationCommands.DEFAULT_DEBUG_PROVIDER_RADIUS)))
                .then(questProviderCommand("whyAvailable", (context) ->
                        VillagerRetaliationCommands.explainQuestAvailabilityDebug(
                                context,
                                VillagerRetaliationCommands.DEFAULT_DEBUG_PROVIDER_RADIUS)))
                .then(literal("whyHidden")
                        .then(questIdArgument()
                                .executes(context -> VillagerRetaliationCommands.explainQuestHiddenDebug(
                                        context,
                                        VillagerRetaliationCommands.DEFAULT_DEBUG_PROVIDER_RADIUS,
                                        false))
                                .then(providerArgument()
                                        .executes(context ->
                                                VillagerRetaliationCommands.explainQuestHiddenDebug(
                                                        context,
                                                        VillagerRetaliationCommands
                                                                .DEFAULT_DEBUG_PROVIDER_RADIUS,
                                                        true)))))
                .then(questTrace())
                .then(literal("objectives").then(questIdArgument()
                        .executes(VillagerRetaliationCommands::showQuestObjectivesDebug)))
                .then(literal("setStage").then(questIdArgument()
                        .then(argument("stage", StringArgumentType.word())
                                .executes(VillagerRetaliationCommands::setQuestStageDebug))))
                .then(literal("fireTrigger").then(questIdArgument()
                        .then(argument("event", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        QuestTriggerRegistry.descriptors().stream()
                                                .map(descriptor -> descriptor.id()),
                                        builder))
                                .executes(VillagerRetaliationCommands::fireQuestTriggerDebug))))
                .then(literal("actions").then(literal("dryRun").then(questIdArgument()
                        .then(argument("trigger_id", StringArgumentType.word())
                                .executes(VillagerRetaliationCommands::dryRunQuestTriggerActionsDebug)))))
                .then(literal("facts")
                        .then(argument("scope_key", StringArgumentType.greedyString())
                                .executes(VillagerRetaliationCommands::showQuestFactsDebug)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> questProviderCommand(
            String name,
            com.mojang.brigadier.Command<CommandSourceStack> command) {
        return literal(name).then(questIdArgument().then(providerArgument().executes(command)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> questTrace() {
        return literal("trace")
                .then(literal("show")
                        .executes(context -> VillagerRetaliationCommands.showQuestTraceDebug(
                                context,
                                QuestDebugTraceService.capacity()))
                        .then(argument("limit", IntegerArgumentType.integer(
                                        1,
                                        QuestDebugTraceService.capacity()))
                                .executes(context -> VillagerRetaliationCommands.showQuestTraceDebug(
                                        context,
                                        IntegerArgumentType.getInteger(context, "limit")))))
                .then(literal("clear").executes(VillagerRetaliationCommands::clearQuestTraceDebug))
                .then(literal("on")
                        .executes(context -> VillagerRetaliationCommands.setQuestTraceDebug(context, true)))
                .then(literal("off")
                        .executes(context -> VillagerRetaliationCommands.setQuestTraceDebug(context, false)))
                .then(questProviderCommand("capture", (context) ->
                        VillagerRetaliationCommands.captureQuestTraceDebug(
                                context,
                                VillagerRetaliationCommands.DEFAULT_DEBUG_PROVIDER_RADIUS)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scene() {
        return literal("scene")
                .then(literal("list").executes(VillagerRetaliationCommands::listScenesDebug))
                .then(sceneIdCommand("inspect", false))
                .then(sceneIdCommand("trace", true))
                .then(sceneMutation("retry"))
                .then(sceneMutation("cancel"))
                .then(sceneMutation("resume"))
                .then(literal("rebind")
                        .then(argument("scene_id", StringArgumentType.word())
                                .then(argument("alias", StringArgumentType.word())
                                        .then(argument("target", EntityArgument.entity())
                                                .executes(VillagerRetaliationCommands::rebindSceneActorDebug)))))
                .then(literal("cleanupEncounter")
                        .then(argument("encounter_id", StringArgumentType.word())
                                .executes(VillagerRetaliationCommands::cleanupEncounterDebug)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sceneIdCommand(
            String command,
            boolean trace) {
        return literal(command)
                .then(argument("scene_id", StringArgumentType.word())
                        .executes(context -> VillagerRetaliationCommands.showSceneLines(context, trace)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sceneMutation(String command) {
        return literal(command)
                .then(argument("scene_id", StringArgumentType.word())
                        .executes(context -> VillagerRetaliationCommands.mutateScene(context, command)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debug() {
        return literal("debug")
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("villagerretaliation.command.admin.debug.help"), false);
                    return 1;
                })
                .then(debugDuel())
                .then(debugHired())
                .then(literal("raid")
                        .then(literal("win").executes(context ->
                                VillagerRetaliationCommands.debugFinishRaid(context, true)))
                        .then(literal("lose").executes(context ->
                                VillagerRetaliationCommands.debugFinishRaid(context, false))))
                .then(literal("builder")
                        .then(literal("materials")
                                .then(argument("structure", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                VillagerRetaliationCommands.builderStructureIdSuggestions(
                                                        context.getSource()),
                                                builder))
                                        .executes(VillagerRetaliationCommands::placeBuilderMaterialsChests))))
                .then(literal("transferVillagerOwnership")
                        .then(villagerArgument()
                                .then(argument("player", GameProfileArgument.gameProfile())
                                        .executes(VillagerRetaliationCommands::debugTransferVillagerOwnership))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugDuel() {
        return literal("duel")
                .then(villagerArgument()
                        .executes(context -> VillagerRetaliationCommands.startDebugDuel(
                                context,
                                DuelLoadout.BRING_YOUR_OWN.name(),
                                0))
                        .then(argument("kit", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("byo", "bring_your_own", "bare_handed", "melee", "ranged", "armored"),
                                        builder))
                                .executes(context -> VillagerRetaliationCommands.startDebugDuel(
                                        context,
                                        StringArgumentType.getString(context, "kit"),
                                        0))
                                .then(argument("wager", IntegerArgumentType.integer(0))
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(DuelService.FIXED_STAKES)
                                                        .mapToObj(Integer::toString),
                                                builder))
                                        .executes(context -> VillagerRetaliationCommands.startDebugDuel(
                                                context,
                                                StringArgumentType.getString(context, "kit"),
                                                IntegerArgumentType.getInteger(context, "wager"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugHired() {
        return literal("hired")
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("villagerretaliation.command.admin.debug.hired.help"), false);
                    return 1;
                })
                .then(literal("previews")
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(context -> VillagerRetaliationCommands.setHiredDebugPreviews(
                                        context,
                                        BoolArgumentType.getBool(context, "enabled"),
                                        HiredDebugPreviewService.DEFAULT_RADIUS))
                                .then(argument("radius", DoubleArgumentType.doubleArg(
                                                1.0D,
                                                HiredDebugPreviewService.MAX_RADIUS))
                                        .executes(context -> VillagerRetaliationCommands.setHiredDebugPreviews(
                                                context,
                                                BoolArgumentType.getBool(context, "enabled"),
                                                DoubleArgumentType.getDouble(context, "radius"))))))
                .then(literal("stressGrid")
                        .executes(context -> VillagerRetaliationCommands.spawnHiredStressGrid(
                                context,
                                HiredStressGridService.ROLE_COUNT))
                        .then(argument("count", IntegerArgumentType.integer(
                                        1,
                                        HiredStressGridService.MAX_COUNT))
                                .executes(context -> VillagerRetaliationCommands.spawnHiredStressGrid(
                                        context,
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(literal("inspect")
                        .then(villagerArgument().executes(VillagerRetaliationCommands::debugHiredWork)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> villagerArgument() {
        return argument("villager", EntityArgument.entity());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> entityArgument() {
        return argument("entity", EntityArgument.entity());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> questIdArgument() {
        return argument("quest_id", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        VillagerRetaliationCommands.questIdSuggestions(context.getSource()),
                        builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> providerArgument() {
        return argument("provider", EntityArgument.entity());
    }
}
