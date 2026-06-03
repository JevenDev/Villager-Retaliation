package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.social.VillagerRelationshipStage;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class VillagerRetaliationCommands {
    private static final double DEFAULT_DEBUG_PROVIDER_RADIUS = 64.0D;
    private static final double MAX_DEBUG_PROVIDER_RADIUS = 256.0D;

    private VillagerRetaliationCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal(VillagerRetaliation.MOD_ID)
                        .requires(source -> source.hasPermission(2))
                        .then(literal("setNearbyReputation")
                                .then(argument("integer", IntegerArgumentType.integer())
                                        .executes(context -> setNearbyReputation(
                                                context,
                                                IntegerArgumentType.getInteger(context, "integer")
                                        ))))
                        .then(literal("setNearestRelationship")
                                .then(argument("stage", StringArgumentType.word())
                                        .executes(context -> setNearestRelationship(
                                                context,
                                                StringArgumentType.getString(context, "stage")
                                        ))))
                        .then(literal("dialogue")
                                .then(literal("explain")
                                        .then(targetArgument()
                                                .then(argument("request", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(DialogueRequestType.values())
                                                                        .map(value -> value.name().toLowerCase(java.util.Locale.ROOT)),
                                                                builder
                                                        ))
                                                        .executes(context -> explainDialogue(context, ""))
                                                        .then(argument("option", StringArgumentType.string())
                                                                .executes(context -> explainDialogue(
                                                                        context,
                                                                        StringArgumentType.getString(context, "option"))))))))
                        .then(literal("datapack")
                                .then(literal("diagnostics")
                                        .executes(VillagerRetaliationCommands::showDatapackDiagnostics)))
                        .then(hiredDebugCommands())
                        .then(questDebugCommands())
                        .then(literal("profile")
                                .then(literal("get")
                                        .then(targetArgument()
                                                .executes(VillagerRetaliationCommands::getProfile)))
                                .then(literal("reroll")
                                        .then(targetArgument()
                                                .executes(VillagerRetaliationCommands::rerollProfile)))
                                .then(literal("set")
                                        .then(targetArgument()
                                                .then(argument("attribute", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(VillagerSocialAttribute.values())
                                                                        .map(VillagerSocialAttribute::serializedName),
                                                                builder
                                                        ))
                                                        .then(argument("value", IntegerArgumentType.integer(
                                                                VillagerSocialAttributes.MIN_VALUE,
                                                                VillagerSocialAttributes.MAX_VALUE
                                                        ))
                                                                .executes(VillagerRetaliationCommands::setProfileAttribute)))))
                                .then(literal("export")
                                        .then(targetArgument()
                                                .executes(VillagerRetaliationCommands::exportProfile))))
                        .then(literal("skill")
                                .then(literal("get")
                                        .then(targetArgument()
                                                .executes(VillagerRetaliationCommands::getSkills)
                                                .then(argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(VillagerSkill.values())
                                                                        .map(VillagerSkill::serializedName),
                                                                builder
                                                        ))
                                                        .executes(VillagerRetaliationCommands::getSkill))))
                                .then(literal("set")
                                        .then(targetArgument()
                                                .then(argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(VillagerSkill.values())
                                                                        .map(VillagerSkill::serializedName),
                                                                builder
                                                        ))
                                                        .then(argument("value", IntegerArgumentType.integer(
                                                                VillagerSkillSet.MIN_VALUE,
                                                                VillagerSkillSet.MAX_VALUE
                                                        ))
                                                                .executes(VillagerRetaliationCommands::setSkill)))))
                                .then(literal("reroll")
                                        .then(targetArgument()
                                                .executes(VillagerRetaliationCommands::rerollSkills)))
                                .then(literal("export")
                                        .then(targetArgument()
                                                .executes(VillagerRetaliationCommands::exportSkills))))
        );
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> targetArgument() {
        return argument("target", StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        context.getSource().getLevel().getEntitiesOfClass(AbstractVillager.class, commandSuggestionArea(context.getSource()))
                                .stream()
                                .map(villager -> VillagerPresetNameRegistry.resolveDisplayName(villager).getString())
                                .filter(name -> !name.isBlank())
                                .distinct(),
                        builder
                ));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> questDebugCommands() {
        return literal("quest")
                .then(literal("debug")
                        .then(literal("providers")
                                .executes(context -> listQuestDebugProviders(context, DEFAULT_DEBUG_PROVIDER_RADIUS))
                                .then(argument("radius", DoubleArgumentType.doubleArg(1.0D, MAX_DEBUG_PROVIDER_RADIUS))
                                        .executes(context -> listQuestDebugProviders(
                                                context,
                                                DoubleArgumentType.getDouble(context, "radius")))))
                        .then(literal("start")
                                .then(questIdArgument()
                                        .then(providerNameArgument()
                                                .executes(context -> startQuestDebug(
                                                        context,
                                                        DEFAULT_DEBUG_PROVIDER_RADIUS,
                                                        false)))))
                        .then(literal("start_near")
                                .then(argument("radius", DoubleArgumentType.doubleArg(1.0D, MAX_DEBUG_PROVIDER_RADIUS))
                                        .then(questIdArgument()
                                                .then(providerNameArgument()
                                                        .executes(context -> startQuestDebug(
                                                                context,
                                                                DoubleArgumentType.getDouble(context, "radius"),
                                                                false))))))
                        .then(literal("remove")
                                .then(questIdArgument()
                                        .executes(VillagerRetaliationCommands::removeQuestDebug)))
                        .then(literal("force_start")
                                .then(questIdArgument()
                                        .then(providerNameArgument()
                                                .executes(context -> startQuestDebug(
                                                        context,
                                                        DEFAULT_DEBUG_PROVIDER_RADIUS,
                                                        true)))))
                        .then(literal("force_start_near")
                                .then(argument("radius", DoubleArgumentType.doubleArg(1.0D, MAX_DEBUG_PROVIDER_RADIUS))
                                        .then(questIdArgument()
                                                .then(providerNameArgument()
                                                        .executes(context -> startQuestDebug(
                                                                context,
                                                                DoubleArgumentType.getDouble(context, "radius"),
                                                                true)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hiredDebugCommands() {
        return literal("hired")
                .then(literal("debug")
                        .then(targetArgument()
                                .executes(VillagerRetaliationCommands::debugHiredWork)));
    }

    private static int debugHiredWork(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager target = profileTarget(context);
        if (!(target instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target must be a villager."));
            return 0;
        }

        List<String> lines = HiredVillagerWorkService.debugLines(level, villager);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return lines.size();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> questIdArgument() {
        return argument("quest_id", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        questIdSuggestions(context.getSource()),
                        builder));
    }

    private static Iterable<String> questIdSuggestions(CommandSourceStack source) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        VillagerQuestResources.quests(source.getServer()).forEach(quest -> {
            ResourceLocation id = quest.id();
            suggestions.add(id.toString());
            if (VillagerRetaliation.MOD_ID.equals(id.getNamespace())) {
                suggestions.add(id.getPath());
            }
        });
        return suggestions;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> providerNameArgument() {
        return argument("provider_name", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer serverPlayer
                            ? serverPlayer
                            : null;
                    if (player == null) {
                        return builder.buildFuture();
                    }
                    return SharedSuggestionProvider.suggest(
                            nearbyQuestProviders(player, DEFAULT_DEBUG_PROVIDER_RADIUS)
                                    .stream()
                                    .map(VillagerRetaliationCommands::displayName),
                            builder);
                });
    }

    private static int listQuestDebugProviders(CommandContext<CommandSourceStack> context, double radius) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so nearby villagers can be resolved."));
            return 0;
        }

        double clampedRadius = debugProviderRadius(radius);
        List<Villager> providers = nearbyQuestProviders(player, clampedRadius);
        if (providers.isEmpty()) {
            source.sendFailure(Component.literal("No nearby villager providers found within " + formatRadius(clampedRadius) + " blocks."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Nearby quest debug providers within "
                + formatRadius(clampedRadius)
                + " blocks. Type the displayed name in start commands:"), false);
        providers.forEach(provider -> source.sendSuccess(() -> Component.literal(providerDebugLine(provider)), false));
        return providers.size();
    }

    private static int startQuestDebug(CommandContext<CommandSourceStack> context, double radius, boolean force) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so nearby villagers can be resolved."));
            return 0;
        }

        QuestResolution quest = resolveQuestDebugQuest(source, StringArgumentType.getString(context, "quest_id"));
        if (!quest.error().isBlank()) {
            source.sendFailure(Component.literal(quest.error()));
            return 0;
        }

        ProviderResolution provider = resolveQuestDebugProvider(
                player,
                StringArgumentType.getString(context, "provider_name"),
                debugProviderRadius(radius));
        if (!provider.error().isBlank()) {
            source.sendFailure(Component.literal(provider.error()));
            provider.matches().forEach(match -> source.sendFailure(Component.literal(providerDebugLine(match))));
            return 0;
        }

        VillagerQuestService.DebugStartResult result = VillagerQuestService.debugStartQuest(
                player,
                provider.provider(),
                quest.questId(),
                force);
        if (!result.started()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int removeQuestDebug(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so quest state can be resolved."));
            return 0;
        }

        QuestResolution quest = resolveQuestDebugQuest(source, StringArgumentType.getString(context, "quest_id"));
        if (!quest.error().isBlank()) {
            source.sendFailure(Component.literal(quest.error()));
            return 0;
        }

        VillagerQuestService.DebugRemoveResult result = VillagerQuestService.debugRemoveQuest(
                player,
                quest.questId());
        if (!result.removed()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static QuestResolution resolveQuestDebugQuest(CommandSourceStack source, String rawQuestId) {
        String query = unquote(rawQuestId);
        if (query.isBlank()) {
            return new QuestResolution(null, List.of(), "Quest id cannot be blank.");
        }

        List<ResourceLocation> questIds = VillagerQuestResources.quests(source.getServer())
                .stream()
                .map(quest -> quest.id())
                .toList();
        LinkedHashSet<ResourceLocation> candidates = new LinkedHashSet<>();
        ResourceLocation direct = ResourceLocation.tryParse(query.toLowerCase(java.util.Locale.ROOT));
        if (direct != null) {
            candidates.add(direct);
        }
        ResourceLocation modDefault = QuestIds.parseInModNamespace(query);
        if (modDefault != null) {
            candidates.add(modDefault);
        }
        for (ResourceLocation candidate : candidates) {
            if (questIds.contains(candidate)) {
                return new QuestResolution(candidate, List.of(), "");
            }
        }

        if (!query.contains(":")) {
            String normalizedPath = query.toLowerCase(java.util.Locale.ROOT);
            List<ResourceLocation> pathMatches = questIds.stream()
                    .filter(id -> id.getPath().equals(normalizedPath))
                    .toList();
            if (pathMatches.size() == 1) {
                return new QuestResolution(pathMatches.get(0), List.of(), "");
            }
            if (pathMatches.size() > 1) {
                return new QuestResolution(
                        null,
                        pathMatches,
                        "Multiple quests use path \"" + rawQuestId + "\": " + formatQuestMatches(pathMatches) + ". Use the full namespaced id.");
            }
        }

        String normalizedQuery = query.toLowerCase(java.util.Locale.ROOT);
        List<ResourceLocation> suggestions = questIds.stream()
                .filter(id -> id.toString().contains(normalizedQuery) || id.getPath().contains(normalizedQuery))
                .limit(5)
                .toList();
        String suggestionText = suggestions.isEmpty()
                ? " Tab-complete an available quest id."
                : " Did you mean " + formatQuestMatches(suggestions) + "?";
        return new QuestResolution(null, suggestions, "Unknown quest: " + rawQuestId + "." + suggestionText);
    }

    private static String formatQuestMatches(List<ResourceLocation> questIds) {
        return String.join(", ", questIds.stream().map(ResourceLocation::toString).toList());
    }

    private static ProviderResolution resolveQuestDebugProvider(ServerPlayer player, String rawProviderName, double radius) {
        String normalizedQuery = normalizeName(unquote(rawProviderName));
        if (normalizedQuery.isBlank()) {
            return new ProviderResolution(null, List.of(), "Provider name cannot be blank.");
        }

        List<Villager> providers = nearbyQuestProviders(player, radius);
        List<Villager> exact = providers.stream()
                .filter(provider -> normalizeName(displayName(provider)).equals(normalizedQuery))
                .toList();
        if (exact.size() == 1) {
            return new ProviderResolution(exact.get(0), List.of(), "");
        }
        if (exact.size() > 1) {
            return new ProviderResolution(null, exact, "Multiple nearby villagers exactly match \"" + rawProviderName + "\". Be more specific.");
        }

        List<Villager> contains = providers.stream()
                .filter(provider -> normalizeName(displayName(provider)).contains(normalizedQuery))
                .toList();
        if (contains.size() == 1) {
            return new ProviderResolution(contains.get(0), List.of(), "");
        }
        if (contains.size() > 1) {
            return new ProviderResolution(null, contains, "Multiple nearby villagers match \"" + rawProviderName + "\". Be more specific.");
        }
        return new ProviderResolution(
                null,
                List.of(),
                "No nearby villager provider named \"" + rawProviderName + "\" was found within " + formatRadius(radius) + " blocks.");
    }

    private static List<Villager> nearbyQuestProviders(ServerPlayer player, double radius) {
        double clampedRadius = debugProviderRadius(radius);
        AABB area = player.getBoundingBox().inflate(clampedRadius);
        return player.serverLevel()
                .getEntitiesOfClass(
                        Villager.class,
                        area,
                        villager -> villager.isAlive() && !villager.isBaby())
                .stream()
                .sorted(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
                .toList();
    }

    private static String providerDebugLine(Villager villager) {
        BlockPos pos = villager.blockPosition();
        return displayName(villager)
                + " | " + VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "villager")
                + " | " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " | " + villager.getUUID();
    }

    private static String displayName(Villager villager) {
        return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
    }

    private static double debugProviderRadius(double radius) {
        return Math.max(1.0D, Math.min(MAX_DEBUG_PROVIDER_RADIUS, radius));
    }

    private static String formatRadius(double radius) {
        return radius == Math.rint(radius)
                ? Integer.toString((int) radius)
                : String.format(java.util.Locale.ROOT, "%.1f", radius);
    }

    private static String unquote(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static int setNearbyReputation(CommandContext<CommandSourceStack> context, int reputation) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double radius = VillagerRetaliationConfig.WITNESS_RADIUS.get();
        AABB area = player.getBoundingBox().inflate(radius);
        int changedCount = 0;
        int foundCount = 0;

        for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, area, AbstractVillager::isAlive)) {
            foundCount++;
            if (VillagerReputationManager.setReputationForDebug(level, villager, player.getUUID(), reputation)) {
                changedCount++;
            }
        }

        final int totalFound = foundCount;
        final int totalChanged = changedCount;
        source.sendSuccess(
            () -> Component.literal("Set nearby villager/trader reputation to " + reputation
                + " for " + totalFound + " merchants (" + totalChanged + " changed)."),
                true
        );
        return changedCount;
    }

    private static int setNearestRelationship(CommandContext<CommandSourceStack> context, String stageName) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        VillagerRelationshipStage stage = VillagerRelationshipStage.bySerializedName(stageName);
        if (stage == null) {
            source.sendFailure(Component.literal("Unknown relationship stage: " + stageName));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double radius = VillagerRetaliationConfig.WITNESS_RADIUS.get();
        AABB area = player.getBoundingBox().inflate(radius);
        List<Villager> villagers = level.getEntitiesOfClass(
                        Villager.class,
                        area,
                        villager -> villager.isAlive() && !villager.isBaby()
                )
                .stream()
                .sorted(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
                .toList();
        if (villagers.size() < 2) {
            source.sendFailure(Component.literal("Need at least two nearby adult villagers."));
            return 0;
        }

        Villager first = villagers.get(0);
        Villager second = villagers.get(1);
        VillagerSocialGraphSavedData.RelationshipValidation validation =
                VillagerSocialGraphSavedData.get(level).setRomanticRelationshipStage(level, first, second, stage);
        if (!validation.allowed()) {
            source.sendFailure(Component.literal(validation.reason()));
            return 0;
        }

        String firstName = VillagerPresetNameRegistry.resolveDisplayName(first).getString();
        String secondName = VillagerPresetNameRegistry.resolveDisplayName(second).getString();
        source.sendSuccess(
                () -> Component.literal("Set " + firstName + " and " + secondName + " to " + stage.displayName() + "."),
                true
        );
        return 1;
    }

    private static int getProfile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        source.sendSuccess(() -> Component.literal(VillagerProfileManager.displayLine(profile, true)), false);
        return 1;
    }

    private static int rerollProfile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        VillagerProfile profile = VillagerProfileManager.rerollProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        source.sendSuccess(() -> Component.literal("Rerolled profile for " + name + "."), true);
        return 1;
    }

    private static int setProfileAttribute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        String attributeName = StringArgumentType.getString(context, "attribute");
        VillagerSocialAttribute attribute = VillagerSocialAttribute.bySerializedName(attributeName);
        if (attribute == null) {
            source.sendFailure(Component.literal("Unknown social attribute: " + attributeName));
            return 0;
        }

        int value = IntegerArgumentType.getInteger(context, "value");
        boolean changed = VillagerProfileManager.setAttribute(level, villager, attribute, value);
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        source.sendSuccess(
                () -> Component.literal("Set " + name + "'s " + attribute.serializedName() + " to "
                        + VillagerSocialAttributes.clamp(value) + (changed ? "." : " (unchanged).")),
                true
        );
        return changed ? 1 : 0;
    }

    private static int exportProfile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        source.sendSuccess(() -> Component.literal(VillagerProfileManager.exportProfile(profile)), false);
        return 1;
    }

    private static int explainDialogue(CommandContext<CommandSourceStack> context, String optionId) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        AbstractVillager target = profileTarget(context);
        if (!(target instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target must be a villager."));
            return 0;
        }

        String requestName = StringArgumentType.getString(context, "request");
        DialogueRequestType requestType = parseDialogueRequest(requestName);
        if (requestType == null) {
            source.sendFailure(Component.literal("Unknown dialogue request: " + requestName));
            return 0;
        }

        DialogueContext dialogueContext = VillagerInteractionService.createDialogueContext(level, player, villager);
        List<String> recentDialogueIds = VillagerInteractionTracker.getState(level, villager, player).recentDialogueIds();
        VillagerDialogueService.DialogueExplanation explanation = VillagerDialogueService.explain(
                dialogueContext,
                requestType,
                optionId == null ? "" : optionId,
                recentDialogueIds);
        sendDialogueExplanation(source, villager, requestType, optionId, explanation);
        return Math.max(1, explanation.candidates().size());
    }

    private static DialogueRequestType parseDialogueRequest(String requestName) {
        if (requestName == null || requestName.isBlank()) {
            return null;
        }
        try {
            return DialogueRequestType.valueOf(requestName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int showDatapackDiagnostics(CommandContext<CommandSourceStack> context) {
        List<DatapackDiagnostics.Entry> diagnostics = DatapackDiagnostics.recent();
        CommandSourceStack source = context.getSource();
        if (diagnostics.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Villager Retaliation datapack diagnostics since the last resource reload."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Villager Retaliation datapack diagnostics: "
                + diagnostics.size() + " warning" + (diagnostics.size() == 1 ? "" : "s")
                + " since the last resource reload. Showing latest 10."), false);
        diagnostics.stream()
                .skip(Math.max(0, diagnostics.size() - 10))
                .forEach(entry -> source.sendSuccess(() -> Component.literal("- " + entry.message()), false));
        return diagnostics.size();
    }

    private static void sendDialogueExplanation(
            CommandSourceStack source,
            Villager villager,
            DialogueRequestType requestType,
            String optionId,
            VillagerDialogueService.DialogueExplanation explanation) {
        String villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        String optionText = optionId == null || optionId.isBlank() ? "<none>" : optionId;
        source.sendSuccess(() -> Component.literal("Dialogue explain for " + villagerName
                + ": request=" + requestType.name().toLowerCase(java.util.Locale.ROOT)
                + ", option=" + optionText
                + ", disposition=" + explanation.disposition().name().toLowerCase(java.util.Locale.ROOT)), false);
        source.sendSuccess(() -> Component.literal("Pool: " + explanation.totalLines()
                + " loaded lines, " + explanation.candidates().size()
                + " candidates, total effective weight " + explanation.totalEffectiveWeight() + "."), false);
        if (!explanation.fallbackReason().isBlank()) {
            source.sendSuccess(() -> Component.literal(explanation.fallbackReason()), false);
        }
        explanation.candidates().stream()
                .limit(8)
                .forEach(candidate -> source.sendSuccess(() -> Component.literal("Candidate "
                        + candidate.id()
                        + (candidate.source().isBlank() ? "" : ", source=" + candidate.source())
                        + ": priority=" + candidate.priority()
                        + (candidate.category().isBlank() ? "" : ", category=" + candidate.category())
                        + (candidate.metadata().summary().isBlank() ? "" : ", " + candidate.metadata().summary())
                        + ", weight=" + candidate.weight()
                        + ", specificity=" + candidate.specificityScore()
                        + ", effective=" + candidate.effectiveWeight()
                        + ", recent=" + candidate.recentlyUsed()
                        + ", freshVariant=" + candidate.hasFreshVariant()), false));
        if (!explanation.rejectionCounts().isEmpty()) {
            String rejectionSummary = explanation.rejectionCounts().entrySet().stream()
                    .limit(8)
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("Top rejection reasons: " + rejectionSummary), false);
        }
        source.sendSuccess(() -> Component.literal("Note: story, gift-memory, and container-theft memory preselectors can run before the weighted line pool."), false);
    }

    private static int getSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        source.sendSuccess(() -> Component.literal(VillagerProfileManager.skillDisplayLine(profile, true)), false);
        return 1;
    }

    private static int getSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        String skillName = StringArgumentType.getString(context, "skill");
        VillagerSkill skill = VillagerSkill.bySerializedName(skillName);
        if (skill == null) {
            source.sendFailure(Component.literal("Unknown villager skill: " + skillName));
            return 0;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        source.sendSuccess(() -> Component.literal(VillagerProfileManager.skillDisplayLine(profile, skill, true)), false);
        return 1;
    }

    private static int setSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        String skillName = StringArgumentType.getString(context, "skill");
        VillagerSkill skill = VillagerSkill.bySerializedName(skillName);
        if (skill == null) {
            source.sendFailure(Component.literal("Unknown villager skill: " + skillName));
            return 0;
        }

        int value = IntegerArgumentType.getInteger(context, "value");
        boolean changed = VillagerProfileManager.setSkill(level, villager, skill, value);
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        source.sendSuccess(
                () -> Component.literal("Set " + name + "'s " + skill.serializedName() + " skill to "
                        + VillagerSkillSet.clamp(value) + (changed ? "." : " (unchanged).")),
                true
        );
        return changed ? 1 : 0;
    }

    private static int rerollSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        VillagerProfile profile = VillagerProfileManager.rerollSkills(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        source.sendSuccess(() -> Component.literal("Rerolled skills for " + name + "."), true);
        return 1;
    }

    private static int exportSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        syncProfileIfPlayer(source, villager, profile);
        source.sendSuccess(() -> Component.literal(VillagerProfileManager.exportProfile(profile)), false);
        return 1;
    }

    private static AbstractVillager profileTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String targetValue = StringArgumentType.getString(context, "target");
        Entity target = parseEntityTarget(source, targetValue);
        if (target instanceof AbstractVillager villager) {
            return villager;
        }
        if (target != null) {
            source.sendFailure(Component.literal("Target must be a villager or wandering trader."));
            return null;
        }

        AbstractVillager namedVillager = findVillagerByName(source, targetValue);
        if (namedVillager != null) {
            return namedVillager;
        }
        source.sendFailure(Component.literal("No villager or wandering trader named \"" + targetValue + "\" was found."));
        return null;
    }

    private static Entity parseEntityTarget(CommandSourceStack source, String targetValue) throws CommandSyntaxException {
        StringReader reader = new StringReader(targetValue);
        EntitySelector selector;
        try {
            selector = EntityArgument.entity().parse(reader);
        } catch (CommandSyntaxException exception) {
            return null;
        }
        if (reader.canRead()) {
            return null;
        }
        try {
            return selector.findSingleEntity(source);
        } catch (CommandSyntaxException exception) {
            return null;
        }
    }

    private static AbstractVillager findVillagerByName(CommandSourceStack source, String targetName) {
        String normalizedTargetName = normalizeName(targetName);
        if (normalizedTargetName.isBlank()) {
            return null;
        }

        return source.getLevel()
                .getEntitiesOfClass(AbstractVillager.class, commandSearchArea(source), AbstractVillager::isAlive)
                .stream()
                .filter(villager -> normalizeName(VillagerPresetNameRegistry.resolveDisplayName(villager).getString()).equals(normalizedTargetName))
                .min(Comparator.comparingDouble(villager -> distanceToSourceSqr(source, villager)))
                .orElse(null);
    }

    private static AABB commandSuggestionArea(CommandSourceStack source) {
        return commandSearchArea(source);
    }

    private static AABB commandSearchArea(CommandSourceStack source) {
        double radius = Math.max(32.0D, VillagerRetaliationConfig.WITNESS_RADIUS.get());
        if (source.getEntity() != null) {
            return source.getEntity().getBoundingBox().inflate(radius);
        }
        return AABB.ofSize(source.getPosition(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
    }

    private static double distanceToSourceSqr(CommandSourceStack source, Entity entity) {
        return source.getPosition().distanceToSqr(entity.position());
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void syncProfileIfPlayer(CommandSourceStack source, AbstractVillager villager, VillagerProfile profile) {
        if (source.getEntity() instanceof ServerPlayer player) {
            VillagerReputationNetworking.sendProfile(player, villager, profile);
        }
    }

    private record ProviderResolution(Villager provider, List<Villager> matches, String error) {
        private ProviderResolution {
            matches = matches == null ? List.of() : List.copyOf(matches);
            error = error == null ? "" : error;
        }
    }

    private record QuestResolution(ResourceLocation questId, List<ResourceLocation> matches, String error) {
        private QuestResolution {
            matches = matches == null ? List.of() : List.copyOf(matches);
            error = error == null ? "" : error;
        }
    }
}
