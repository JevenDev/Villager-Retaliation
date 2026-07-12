package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.AllegianceConfidence;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceEntityData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceService;
import com.jvn.villagerretaliation.allegiance.VillagerDisciplineService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.event.VillagerEventTriggerSavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.BuilderToolAction;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.scene.SceneOperatorService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.social.VillagerRelationshipStage;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.village.VillageRegistrySavedData;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class VillagerRetaliationCommands {
    private static final double DEFAULT_DEBUG_PROVIDER_RADIUS = 64.0D;
    private static final double MAX_DEBUG_PROVIDER_RADIUS = 256.0D;
    private static final int DEFAULT_VILLAGE_REGISTRY_MERGE_RADIUS = 96;
    private static final int MAX_VILLAGE_REGISTRY_MERGE_RADIUS = 512;
    private static final int DEFAULT_VILLAGE_REGISTRY_MERGE_LIMIT = 10;

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
                                        .executes(context -> showDatapackDiagnostics(context, "", ""))
                                        .then(literal("severity")
                                                .then(argument("severity", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(QuestDiagnostic.Severity.values())
                                                                        .map(value -> value.name().toLowerCase(Locale.ROOT)),
                                                                builder))
                                                        .executes(context -> showDatapackDiagnostics(
                                                                context,
                                                                StringArgumentType.getString(context, "severity"),
                                                                ""))
                                                        .then(literal("resource")
                                                                .then(argument("resource", StringArgumentType.string())
                                                                        .executes(context -> showDatapackDiagnostics(
                                                                                context,
                                                                                StringArgumentType.getString(context, "severity"),
                                                                                StringArgumentType.getString(context, "resource")))))))
                                        .then(literal("resource")
                                                .then(argument("resource", StringArgumentType.string())
                                                        .executes(context -> showDatapackDiagnostics(
                                                                context,
                                                                "",
                                                                StringArgumentType.getString(context, "resource")))))))
                        .then(villageDebugCommands())
                        .then(hiredDebugCommands())
                        .then(questDebugCommands())
                        .then(sceneDebugCommands())
                        .then(debugCommands())
                        .then(allegianceCommands())
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
                                .then(literal("set_gender")
                                        .then(targetArgument()
                                                .then(argument("gender", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(VillagerGender.values())
                                                                        .map(VillagerGender::serializedName),
                                                                builder
                                                        ))
                                                        .executes(VillagerRetaliationCommands::setProfileGender))))
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
        return namedVillagerArgument("target");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> allegianceEntityArgument() {
        return namedVillagerArgument("entity");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> namedVillagerArgument(String argumentName) {
        return argument(argumentName, StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        context.getSource().getLevel().getEntitiesOfClass(AbstractVillager.class, commandSuggestionArea(context.getSource()))
                                .stream()
                                .map(villager -> VillagerPresetNameRegistry.resolveDisplayName(villager).getString())
                                .filter(name -> !name.isBlank())
                                .distinct(),
                        builder
                ));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> allegianceCommands() {
        return literal("allegiance")
                .then(literal("inspect")
                        .then(allegianceEntityArgument()
                                .executes(VillagerRetaliationCommands::inspectAllegiance)))
                .then(literal("assign")
                        .then(allegianceEntityArgument()
                                .then(argument("uuid", StringArgumentType.word())
                                        .executes(VillagerRetaliationCommands::assignAllegiance))))
                .then(literal("unknown")
                        .then(allegianceEntityArgument()
                                .executes(context -> setAllegianceState(context, false))))
                .then(literal("unaffiliated")
                        .then(allegianceEntityArgument()
                                .executes(context -> setAllegianceState(context, true))))
                .then(literal("merge")
                        .then(argument("source", StringArgumentType.word())
                                .then(argument("target", StringArgumentType.word())
                                        .executes(VillagerRetaliationCommands::mergeAllegiances))))
                .then(literal("fork")
                        .then(allegianceEntityArgument()
                                .executes(VillagerRetaliationCommands::forkAllegiance)))
                .then(literal("migrate")
                        .then(allegianceEntityArgument()
                                .executes(VillagerRetaliationCommands::migrateAllegiance)))
                .then(literal("statistics")
                        .executes(VillagerRetaliationCommands::allegianceStatistics))
                .then(literal("reset_abuse")
                        .then(allegianceEntityArgument()
                                .then(argument("player", StringArgumentType.word())
                                        .executes(VillagerRetaliationCommands::resetAbuse))));
    }

    private static int inspectAllegiance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = allegianceTarget(context);
        if (entity == null) {
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        VillageAllegianceData data = VillageAllegianceApi.get(entity).orElse(null);
        if (data == null) {
            context.getSource().sendSuccess(() -> Component.literal("Allegiance: missing (will resolve conservatively)"), false);
            return 1;
        }
        String raw = data.primary() == null ? "-" : data.primary().toString();
        String canonical = data.primary() == null
                ? "-"
                : VillageAllegianceRegistrySavedData.get(level).canonical(data.primary()).map(Object::toString).orElse("invalid");
        context.getSource().sendSuccess(() -> Component.literal(
                "Allegiance state=" + data.state()
                        + " raw=" + raw
                        + " canonical=" + canonical
                        + " source=" + data.assignmentSource()
                        + " confidence=" + data.confidence()
                        + " parents=" + data.protectedParents()), false);
        return 1;
    }

    private static int assignAllegiance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = allegianceTarget(context);
        VillageAllegianceId id = parseAllegianceId(context.getSource(), StringArgumentType.getString(context, "uuid"));
        if (entity == null || id == null) {
            return 0;
        }
        VillageAllegianceApi.assignKnown(context.getSource().getLevel(), entity, id, AllegianceAssignmentSource.ADMIN);
        context.getSource().sendSuccess(() -> Component.literal("Assigned allegiance " + id + " to " + entity.getDisplayName().getString()), true);
        return 1;
    }

    private static int setAllegianceState(CommandContext<CommandSourceStack> context, boolean unaffiliated) throws CommandSyntaxException {
        Entity entity = allegianceTarget(context);
        if (entity == null) {
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        VillageAllegianceData data = unaffiliated
                ? VillageAllegianceData.unaffiliated(AllegianceAssignmentSource.ADMIN, level.getGameTime(), level.dimension().location(), entity.blockPosition())
                : VillageAllegianceData.unknown(AllegianceAssignmentSource.ADMIN, AllegianceConfidence.AUTHORITATIVE,
                        level.getGameTime(), level.dimension().location(), entity.blockPosition());
        VillageAllegianceApi.assign(entity, data);
        context.getSource().sendSuccess(() -> Component.literal("Set allegiance state to " + data.state()), true);
        return 1;
    }

    private static int mergeAllegiances(CommandContext<CommandSourceStack> context) {
        VillageAllegianceId source = parseAllegianceId(context.getSource(), StringArgumentType.getString(context, "source"));
        VillageAllegianceId target = parseAllegianceId(context.getSource(), StringArgumentType.getString(context, "target"));
        if (source == null || target == null) {
            return 0;
        }
        boolean merged = VillageAllegianceRegistrySavedData.get(context.getSource().getLevel()).merge(source, target);
        if (!merged) {
            context.getSource().sendFailure(Component.literal("Allegiance merge rejected (invalid ID or alias cycle)."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Aliased " + source + " -> " + target), true);
        return 1;
    }

    private static int forkAllegiance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = allegianceTarget(context);
        if (entity == null) {
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId id = registry.create(level.getGameTime(), level.dimension().location(), entity.blockPosition(), "");
        VillageAllegianceApi.assignKnown(level, entity, id, AllegianceAssignmentSource.ADMIN);
        context.getSource().sendSuccess(() -> Component.literal("Forked selected entity to new allegiance " + id), true);
        return 1;
    }

    private static int migrateAllegiance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = allegianceTarget(context);
        if (entity == null) {
            return 0;
        }
        boolean resolved = VillageAllegianceService.retryMigration(context.getSource().getLevel(), entity);
        context.getSource().sendSuccess(() -> Component.literal("Migration completed; resolved=" + resolved), false);
        return resolved ? 1 : 0;
    }

    private static int allegianceStatistics(CommandContext<CommandSourceStack> context) {
        var statistics = VillageAllegianceService.statistics();
        var registry = VillageAllegianceRegistrySavedData.get(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "Allegiances records=" + registry.records().size()
                        + " aliases=" + registry.aliasCount()
                        + " known=" + statistics.known()
                        + " unknown=" + statistics.unknown()
                        + " unaffiliated=" + statistics.unaffiliated()
                        + " pending=" + statistics.pending()), false);
        return registry.records().size();
    }

    private static int resetAbuse(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = allegianceTarget(context);
        UUID playerId;
        try {
            playerId = UUID.fromString(StringArgumentType.getString(context, "player"));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Invalid player UUID."));
            return 0;
        }
        if (entity == null) {
            return 0;
        }
        return VillagerDisciplineService.reset(context.getSource().getLevel(), entity.getUUID(), playerId) ? 1 : 0;
    }

    private static Entity allegianceTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String value = StringArgumentType.getString(context, "entity");
        Entity entity = parseEntityTarget(context.getSource(), value);
        if (entity == null) {
            entity = findVillagerByName(context.getSource(), value);
        }
        if (entity == null) {
            context.getSource().sendFailure(Component.literal("No single loaded entity matched " + value));
        }
        return entity;
    }

    private static VillageAllegianceId parseAllegianceId(CommandSourceStack source, String value) {
        try {
            return new VillageAllegianceId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Invalid allegiance UUID: " + value));
            return null;
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugCommands() {
        return literal("debug")
                .then(literal("builder")
                        .then(literal("materials")
                                .then(argument("structure", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                builderStructureIdSuggestions(context.getSource()),
                                                builder))
                                        .executes(VillagerRetaliationCommands::placeBuilderMaterialsChests))));
    }

    private static int placeBuilderMaterialsChests(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String structureValue = StringArgumentType.getString(context, "structure");
        ResourceLocation structureId = parseBuilderStructureId(structureValue);
        if (structureId == null) {
            source.sendFailure(Component.literal("Invalid builder structure id: " + structureValue));
            return 0;
        }

        Optional<BuilderStructureCatalog.Entry> entry = BuilderStructureCatalog.byId(source.getServer(), structureId);
        if (entry.isEmpty()) {
            source.sendFailure(Component.literal("Unknown builder structure: " + structureId));
            return 0;
        }

        Optional<BuilderStructureScanner.StructurePlan> plan = BuilderStructureScanner.scan(level, entry.get(), Rotation.NONE);
        if (plan.isEmpty()) {
            source.sendFailure(Component.literal("Could not scan builder structure: " + entry.get().menuLabel()));
            return 0;
        }

        List<ItemStack> stacks = builderDebugSupplyStacks(plan.get());
        int chestCount = Math.max(1, (stacks.size() + 26) / 27);
        Direction lineDirection = debugChestLineDirection(source);
        BlockPos preferred = debugChestPreferredPos(source);
        List<BlockPos> chestPositions = findDebugChestPositions(level, preferred, lineDirection, chestCount);
        if (chestPositions.size() < chestCount) {
            source.sendFailure(Component.literal("Could not find " + chestCount + " empty chest position(s) near "
                    + formatPos(preferred) + "."));
            return 0;
        }

        int stackIndex = 0;
        for (BlockPos chestPos : chestPositions) {
            level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
                source.sendFailure(Component.literal("Failed to create chest at " + formatPos(chestPos) + "."));
                return 0;
            }
            for (int slot = 0; slot < chest.getContainerSize() && stackIndex < stacks.size(); slot++) {
                chest.setItem(slot, stacks.get(stackIndex++));
            }
            chest.setChanged();
        }

        String chestLabel = chestPositions.size() == 1 ? "chest" : "chests";
        source.sendSuccess(() -> Component.literal("Placed " + chestPositions.size() + " debug " + chestLabel
                + " for " + entry.get().menuLabel()
                + " at " + formatPos(chestPositions.getFirst())
                + " (" + plan.get().materialSummary(6) + ")."), true);
        return chestPositions.size();
    }

    private static Iterable<String> builderStructureIdSuggestions(CommandSourceStack source) {
        return BuilderStructureCatalog.entries(source.getServer())
                .stream()
                .map(entry -> entry.id().toString())
                .toList();
    }

    private static ResourceLocation parseBuilderStructureId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return ResourceLocation.tryParse(normalized);
    }

    private static List<ItemStack> builderDebugSupplyStacks(BuilderStructureScanner.StructurePlan plan) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BuilderStructureScanner.MaterialRequirement material : plan.materials()) {
            addSplitStacks(stacks, material.item(), material.count());
        }
        for (ItemStack tool : builderDebugToolStacks(plan)) {
            stacks.add(tool);
        }
        return stacks;
    }

    private static List<ItemStack> builderDebugToolStacks(BuilderStructureScanner.StructurePlan plan) {
        Map<BuilderToolAction, ItemStack> tools = new LinkedHashMap<>();
        for (BuilderStructureScanner.BuildBlock block : plan.blocks()) {
            switch (block.toolAction()) {
                case AXE_STRIP -> tools.putIfAbsent(BuilderToolAction.AXE_STRIP, new ItemStack(Items.IRON_AXE));
                case SHOVEL_FLATTEN -> tools.putIfAbsent(BuilderToolAction.SHOVEL_FLATTEN, new ItemStack(Items.IRON_SHOVEL));
                case HOE_TILL -> tools.putIfAbsent(BuilderToolAction.HOE_TILL, new ItemStack(Items.IRON_HOE));
                case NONE -> {
                }
            }
        }
        return List.copyOf(tools.values());
    }

    private static void addSplitStacks(List<ItemStack> stacks, ItemStack template, int count) {
        if (template == null || template.isEmpty() || count <= 0) {
            return;
        }
        int maxStackSize = Math.max(1, template.getMaxStackSize());
        int remaining = count;
        while (remaining > 0) {
            ItemStack stack = template.copy();
            stack.setCount(Math.min(maxStackSize, remaining));
            stacks.add(stack);
            remaining -= stack.getCount();
        }
    }

    private static BlockPos debugChestPreferredPos(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.blockPosition().relative(player.getDirection());
        }
        return BlockPos.containing(source.getPosition());
    }

    private static Direction debugChestLineDirection(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Direction direction = player.getDirection().getClockWise();
            return direction.getAxis().isHorizontal() ? direction : Direction.EAST;
        }
        return Direction.EAST;
    }

    private static List<BlockPos> findDebugChestPositions(
            ServerLevel level,
            BlockPos preferred,
            Direction lineDirection,
            int chestCount) {
        Direction safeDirection = lineDirection == null || !lineDirection.getAxis().isHorizontal()
                ? Direction.EAST
                : lineDirection;
        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            for (int radius = 0; radius <= 6; radius++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
                            continue;
                        }
                        BlockPos start = preferred.offset(x, yOffset, z);
                        List<BlockPos> positions = debugChestLine(start, safeDirection, chestCount);
                        if (positions.stream().allMatch(level::isEmptyBlock)) {
                            return positions;
                        }
                    }
                }
            }
        }
        return List.of();
    }

    private static List<BlockPos> debugChestLine(BlockPos start, Direction lineDirection, int chestCount) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < chestCount; i++) {
            positions.add(start.relative(lineDirection, i * 2));
        }
        return positions;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
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
                        .then(literal("inspect")
                                .then(questIdArgument()
                                        .executes(VillagerRetaliationCommands::inspectQuestDebug)))
                        .then(literal("rebind")
                                .then(questIdArgument()
                                        .then(providerNameArgument()
                                                .executes(context -> rebindQuestDebug(
                                                        context,
                                                        DEFAULT_DEBUG_PROVIDER_RADIUS)))))
                        .then(literal("why_available")
                                .then(questIdArgument()
                                        .then(providerNameArgument()
                                                .executes(context -> explainQuestAvailabilityDebug(
                                                        context,
                                                        DEFAULT_DEBUG_PROVIDER_RADIUS)))))
                        .then(literal("why_hidden")
                                .then(questIdArgument()
                                        .executes(context -> explainQuestHiddenDebug(context, DEFAULT_DEBUG_PROVIDER_RADIUS, false))
                                        .then(providerNameArgument()
                                                .executes(context -> explainQuestHiddenDebug(
                                                        context,
                                                        DEFAULT_DEBUG_PROVIDER_RADIUS,
                                                        true)))))
                        .then(literal("trace")
                                .then(literal("show")
                                        .executes(context -> showQuestTraceDebug(context, QuestDebugTraceService.capacity()))
                                        .then(argument("limit", IntegerArgumentType.integer(1, QuestDebugTraceService.capacity()))
                                                .executes(context -> showQuestTraceDebug(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "limit")))))
                                .then(literal("clear")
                                        .executes(VillagerRetaliationCommands::clearQuestTraceDebug))
                                .then(literal("on")
                                        .executes(context -> setQuestTraceDebug(context, true)))
                                .then(literal("off")
                                        .executes(context -> setQuestTraceDebug(context, false)))
                                .then(literal("capture")
                                        .then(questIdArgument()
                                                .then(providerNameArgument()
                                                        .executes(context -> captureQuestTraceDebug(
                                                                context,
                                                                DEFAULT_DEBUG_PROVIDER_RADIUS))))))
                        .then(literal("objectives")
                                .then(questIdArgument()
                                        .executes(VillagerRetaliationCommands::showQuestObjectivesDebug)))
                        .then(literal("set_stage")
                                .then(questIdArgument()
                                        .then(argument("stage", StringArgumentType.word())
                                                .executes(VillagerRetaliationCommands::setQuestStageDebug))))
                        .then(literal("fire_trigger")
                                .then(questIdArgument()
                                        .then(argument("event", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        QuestTriggerRegistry.descriptors().stream()
                                                                .map(descriptor -> descriptor.id()),
                                                        builder))
                                                .executes(VillagerRetaliationCommands::fireQuestTriggerDebug))))
                        .then(literal("actions")
                                .then(literal("dry_run")
                                        .then(questIdArgument()
                                                .then(argument("trigger_id", StringArgumentType.word())
                                                        .executes(VillagerRetaliationCommands::dryRunQuestTriggerActionsDebug)))))
                        .then(literal("facts")
                                .then(argument("scope_key", StringArgumentType.greedyString())
                                        .executes(VillagerRetaliationCommands::showQuestFactsDebug)))
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
                        .then(literal("previews")
                                .then(literal("toggle")
                                        .executes(context -> toggleHiredDebugPreviews(context, HiredDebugPreviewService.DEFAULT_RADIUS))
                                        .then(argument("radius", DoubleArgumentType.doubleArg(1.0D, HiredDebugPreviewService.MAX_RADIUS))
                                                .executes(context -> toggleHiredDebugPreviews(
                                                        context,
                                                        DoubleArgumentType.getDouble(context, "radius")))))
                                .then(literal("on")
                                        .executes(context -> setHiredDebugPreviews(context, true, HiredDebugPreviewService.DEFAULT_RADIUS))
                                        .then(argument("radius", DoubleArgumentType.doubleArg(1.0D, HiredDebugPreviewService.MAX_RADIUS))
                                                .executes(context -> setHiredDebugPreviews(
                                                        context,
                                                        true,
                                                        DoubleArgumentType.getDouble(context, "radius")))))
                                .then(literal("off")
                                        .executes(context -> setHiredDebugPreviews(context, false, HiredDebugPreviewService.DEFAULT_RADIUS))))
                        .then(targetArgument()
                                .executes(VillagerRetaliationCommands::debugHiredWork)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> villageDebugCommands() {
        return literal("village")
                .then(literal("registry")
                        .then(literal("inspect")
                                .executes(context -> inspectVillageRegistry(context, 10))
                                .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                        .executes(context -> inspectVillageRegistry(
                                                context,
                                                IntegerArgumentType.getInteger(context, "limit")))))
                        .then(literal("prune_older_than")
                                .then(argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(context -> pruneVillageRegistry(
                                                context,
                                                IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(literal("suggest_merges")
                                .executes(context -> suggestVillageRegistryMerges(
                                        context,
                                        DEFAULT_VILLAGE_REGISTRY_MERGE_RADIUS,
                                        DEFAULT_VILLAGE_REGISTRY_MERGE_LIMIT))
                                .then(argument("radius", IntegerArgumentType.integer(
                                                1,
                                                MAX_VILLAGE_REGISTRY_MERGE_RADIUS))
                                        .executes(context -> suggestVillageRegistryMerges(
                                                context,
                                                IntegerArgumentType.getInteger(context, "radius"),
                                                DEFAULT_VILLAGE_REGISTRY_MERGE_LIMIT))
                                        .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                                .executes(context -> suggestVillageRegistryMerges(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "radius"),
                                                        IntegerArgumentType.getInteger(context, "limit"))))))
                        .then(literal("merge")
                                .then(argument("source_key", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                villageRegistryKeySuggestions(context.getSource()),
                                                builder))
                                        .then(argument("target_key", StringArgumentType.string())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        villageRegistryKeySuggestions(context.getSource()),
                                                        builder))
                                                .executes(VillagerRetaliationCommands::mergeVillageRegistryKeys)))));
    }

    private static int inspectVillageRegistry(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        VillageRegistrySavedData registry = VillageRegistrySavedData.get(level);
        long gameTime = level.getGameTime();
        List<VillageRegistrySavedData.EntrySnapshot> entries = registry.entries()
                .stream()
                .sorted(Comparator.comparingLong((VillageRegistrySavedData.EntrySnapshot entry) -> entry.ageTicks(gameTime))
                        .reversed())
                .limit(limit)
                .toList();
        source.sendSuccess(() -> Component.literal("Village registry: " + registry.size()
                + " entries. Showing " + entries.size() + "."), false);
        entries.forEach(entry -> source.sendSuccess(() -> Component.literal(villageRegistryLine(entry, gameTime)), false));
        return registry.size();
    }

    private static int pruneVillageRegistry(CommandContext<CommandSourceStack> context, int olderThanTicks) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        VillageRegistrySavedData registry = VillageRegistrySavedData.get(level);
        long cutoffGameTime = Math.max(0L, level.getGameTime() - olderThanTicks);
        int removed = registry.pruneNotSeenSince(cutoffGameTime);
        source.sendSuccess(() -> Component.literal("Pruned " + removed
                + " village registry entries not seen in the last " + olderThanTicks
                + " ticks. Remaining: " + registry.size() + "."), true);
        return removed;
    }

    private static int suggestVillageRegistryMerges(CommandContext<CommandSourceStack> context, int radius, int limit) {
        CommandSourceStack source = context.getSource();
        List<VillageRegistrySavedData.EntrySnapshot> entries = VillageRegistrySavedData.get(source.getLevel()).entries();
        long radiusSquared = (long) radius * radius;
        List<VillageRegistryMergeCandidate> candidates = villageRegistryMergeCandidates(entries, radiusSquared)
                .stream()
                .sorted(Comparator.comparingLong(VillageRegistryMergeCandidate::distanceSquared)
                        .thenComparing(candidate -> candidate.source().key())
                        .thenComparing(candidate -> candidate.target().key()))
                .toList();
        List<VillageRegistryMergeCandidate> shown = candidates.stream()
                .limit(limit)
                .toList();
        source.sendSuccess(() -> Component.literal("Village registry merge suggestions within "
                + radius + " blocks: " + candidates.size()
                + " candidate pairs. Showing " + shown.size()
                + ". Suggested direction is older source -> newer target."), false);
        shown.forEach(candidate -> source.sendSuccess(
                () -> Component.literal(villageRegistryMergeCandidateLine(candidate)),
                false));
        return candidates.size();
    }

    private static List<VillageRegistryMergeCandidate> villageRegistryMergeCandidates(
            List<VillageRegistrySavedData.EntrySnapshot> entries,
            long radiusSquared) {
        List<VillageRegistryMergeCandidate> candidates = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < entries.size(); leftIndex++) {
            VillageRegistrySavedData.EntrySnapshot left = entries.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < entries.size(); rightIndex++) {
                VillageRegistrySavedData.EntrySnapshot right = entries.get(rightIndex);
                if (!left.dimension().equals(right.dimension())) {
                    continue;
                }
                long distanceSquared = villageRegistryDistanceSquared(left.center(), right.center());
                if (distanceSquared > radiusSquared) {
                    continue;
                }
                candidates.add(villageRegistryMergeCandidate(left, right, distanceSquared));
            }
        }
        return candidates;
    }

    private static VillageRegistryMergeCandidate villageRegistryMergeCandidate(
            VillageRegistrySavedData.EntrySnapshot left,
            VillageRegistrySavedData.EntrySnapshot right,
            long distanceSquared) {
        if (left.lastSeenGameTime() > right.lastSeenGameTime()) {
            return new VillageRegistryMergeCandidate(right, left, distanceSquared);
        }
        if (right.lastSeenGameTime() > left.lastSeenGameTime()) {
            return new VillageRegistryMergeCandidate(left, right, distanceSquared);
        }
        return left.key().compareTo(right.key()) <= 0
                ? new VillageRegistryMergeCandidate(right, left, distanceSquared)
                : new VillageRegistryMergeCandidate(left, right, distanceSquared);
    }

    private static long villageRegistryDistanceSquared(BlockPos left, BlockPos right) {
        long dx = left.getX() - right.getX();
        long dy = left.getY() - right.getY();
        long dz = left.getZ() - right.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String villageRegistryMergeCandidateLine(VillageRegistryMergeCandidate candidate) {
        VillageRegistrySavedData.EntrySnapshot source = candidate.source();
        VillageRegistrySavedData.EntrySnapshot target = candidate.target();
        long distance = Math.round(Math.sqrt(candidate.distanceSquared()));
        return "source=" + source.key()
                + " -> target=" + target.key()
                + " | dimension=" + source.dimension()
                + " | distance~=" + distance
                + " | sourceLastSeen=" + source.lastSeenGameTime()
                + " | targetLastSeen=" + target.lastSeenGameTime()
                + " | command=/villagerretaliation village registry merge \""
                + source.key() + "\" \"" + target.key() + "\"";
    }

    private static int mergeVillageRegistryKeys(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String sourceKey = unquote(StringArgumentType.getString(context, "source_key"));
        String targetKey = unquote(StringArgumentType.getString(context, "target_key"));
        if (!VillageScopeKeys.isVillageKey(sourceKey)) {
            source.sendFailure(Component.literal("Source village key is invalid: " + sourceKey));
            return 0;
        }
        if (!VillageScopeKeys.isVillageKey(targetKey)) {
            source.sendFailure(Component.literal("Target village key is invalid: " + targetKey));
            return 0;
        }
        if (sourceKey.equals(targetKey)) {
            source.sendFailure(Component.literal("Source and target village keys are the same."));
            return 0;
        }

        VillageRegistrySavedData.MergeResult registryResult =
                VillageRegistrySavedData.get(level).mergeKey(sourceKey, targetKey);
        VillagerQuestFacts.ScopeMergeResult factsResult =
                VillagerQuestFacts.get(level).mergeScope(sourceKey, targetKey);
        int progressUpdated = VillagerQuestSavedData.get(level).replaceIssuerVillageKey(sourceKey, targetKey);
        VillagerEventTriggerSavedData.CooldownMergeResult cooldownResult =
                VillagerEventTriggerSavedData.get(level).mergeScopeKey(sourceKey, targetKey);
        int socialProfilesUpdated = VillagerSocialGraphSavedData.get(level).replaceVillageKey(sourceKey, targetKey);

        int changed = (registryResult.changed() ? 1 : 0)
                + (factsResult.changed() ? 1 : 0)
                + progressUpdated
                + (cooldownResult.changed() ? 1 : 0)
                + socialProfilesUpdated;
        source.sendSuccess(() -> Component.literal("Merged village key " + sourceKey + " into " + targetKey
                + ": registry_changed=" + registryResult.changed()
                + " registry_source_found=" + registryResult.sourceFound()
                + " registry_target_created=" + registryResult.targetCreated()
                + " fact_tags_added=" + factsResult.tagsAdded()
                + " fact_variables_added=" + factsResult.variablesAdded()
                + " fact_variable_conflicts=" + factsResult.variableConflicts()
                + " fact_counters_merged=" + factsResult.countersMerged()
                + " quest_progress_updated=" + progressUpdated
                + " cooldowns_moved=" + cooldownResult.moved()
                + " cooldowns_merged=" + cooldownResult.merged()
                + " social_profiles_updated=" + socialProfilesUpdated + "."), true);
        return changed;
    }

    private static Iterable<String> villageRegistryKeySuggestions(CommandSourceStack source) {
        return VillageRegistrySavedData.get(source.getLevel()).entries()
                .stream()
                .map(VillageRegistrySavedData.EntrySnapshot::key)
                .toList();
    }

    private static String villageRegistryLine(VillageRegistrySavedData.EntrySnapshot entry, long gameTime) {
        BlockPos center = entry.center();
        return entry.key()
                + " | " + entry.dimension()
                + " | " + center.getX() + " " + center.getY() + " " + center.getZ()
                + " | lastSeen=" + entry.lastSeenGameTime()
                + " | ageTicks=" + entry.ageTicks(gameTime);
    }

    private record VillageRegistryMergeCandidate(
            VillageRegistrySavedData.EntrySnapshot source,
            VillageRegistrySavedData.EntrySnapshot target,
            long distanceSquared) {
    }

    private static int toggleHiredDebugPreviews(CommandContext<CommandSourceStack> context, double radius) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so nearby hired villagers can be previewed."));
            return 0;
        }
        HiredDebugPreviewService.DebugPreviewSummary summary = HiredDebugPreviewService.toggle(player, radius);
        sendHiredDebugPreviewSummary(source, summary);
        return summary.enabled() ? 1 : 0;
    }

    private static int setHiredDebugPreviews(CommandContext<CommandSourceStack> context, boolean enabled, double radius) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so nearby hired villagers can be previewed."));
            return 0;
        }
        HiredDebugPreviewService.DebugPreviewSummary summary = HiredDebugPreviewService.setEnabled(player, enabled, radius);
        sendHiredDebugPreviewSummary(source, summary);
        return summary.enabled() ? 1 : 0;
    }

    private static void sendHiredDebugPreviewSummary(CommandSourceStack source, HiredDebugPreviewService.DebugPreviewSummary summary) {
        if (!summary.enabled()) {
            source.sendSuccess(() -> Component.literal("Hired debug previews disabled."), false);
            return;
        }
        source.sendSuccess(() -> Component.literal("Hired debug previews enabled within "
                + (int) Math.round(summary.radius())
                + " blocks: "
                + summary.villagers()
                + " hired villagers, "
                + summary.workAreas()
                + " job sites, "
                + summary.storage()
                + " containers."), false);
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

    private static LiteralArgumentBuilder<CommandSourceStack> sceneDebugCommands() {
        return literal("scene")
                .then(literal("list").executes(VillagerRetaliationCommands::listScenesDebug))
                .then(literal("inspect").then(argument("scene_id", StringArgumentType.word())
                        .executes(context -> showSceneLines(context, false))))
                .then(literal("trace").then(argument("scene_id", StringArgumentType.word())
                        .executes(context -> showSceneLines(context, true))))
                .then(literal("retry").then(argument("scene_id", StringArgumentType.word())
                        .executes(context -> mutateScene(context, "retry"))))
                .then(literal("cancel").then(argument("scene_id", StringArgumentType.word())
                        .executes(context -> mutateScene(context, "cancel"))))
                .then(literal("resume").then(argument("scene_id", StringArgumentType.word())
                        .executes(context -> mutateScene(context, "resume"))))
                .then(literal("rebind").then(argument("scene_id", StringArgumentType.word())
                        .then(argument("alias", StringArgumentType.word())
                                .then(argument("target", EntityArgument.entity())
                                        .executes(VillagerRetaliationCommands::rebindSceneActorDebug)))))
                .then(literal("cleanup_encounter").then(argument("encounter_id", StringArgumentType.word())
                        .executes(VillagerRetaliationCommands::cleanupEncounterDebug)));
    }

    private static int listScenesDebug(CommandContext<CommandSourceStack> context) {
        SceneSavedData data=SceneSavedData.get(context.getSource().getLevel());
        List<com.jvn.villagerretaliation.scene.runtime.SceneInstance> scenes=data.active().stream()
                .sorted(Comparator.comparing(value->value.id().toString())).toList();
        context.getSource().sendSuccess(()->Component.literal("Active scenes: "+scenes.size()+" (blocked="+data.byState(com.jvn.villagerretaliation.scene.runtime.SceneState.BLOCKED).size()+")"),false);
        scenes.forEach(scene->context.getSource().sendSuccess(()->Component.literal(scene.id()+" "+scene.sceneId()+" "+scene.state()+" step="+scene.currentStep()),false));
        return scenes.size();
    }

    private static int showSceneLines(CommandContext<CommandSourceStack> context,boolean trace) {
        UUID id=parseUuid(context,"scene_id");if(id==null)return 0;List<String> lines=trace?SceneOperatorService.trace(context.getSource().getLevel(),id):SceneOperatorService.inspect(context.getSource().getLevel(),id);lines.forEach(line->context.getSource().sendSuccess(()->Component.literal(line),false));return lines.size();
    }

    private static int mutateScene(CommandContext<CommandSourceStack> context,String action) {
        UUID id=parseUuid(context,"scene_id");if(id==null)return 0;String operator=context.getSource().getTextName();var result=switch(action){case "retry"->SceneOperatorService.retry(context.getSource().getLevel(),id,"operator_retry",operator);case "cancel"->SceneOperatorService.cancel(context.getSource().getLevel(),id,"operator_cancel",operator);default->SceneOperatorService.resume(context.getSource().getLevel(),id,"operator_resume",operator);};context.getSource().sendSuccess(()->Component.literal(result.message()),false);return result.success()?1:0;
    }

    private static int rebindSceneActorDebug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID id=parseUuid(context,"scene_id");if(id==null)return 0;var result=SceneOperatorService.rebind(context.getSource().getLevel(),id,StringArgumentType.getString(context,"alias"),EntityArgument.getEntity(context,"target"),"operator_rebind",context.getSource().getTextName());context.getSource().sendSuccess(()->Component.literal(result.message()),false);return result.success()?1:0;
    }

    private static int cleanupEncounterDebug(CommandContext<CommandSourceStack> context) {
        UUID id=parseUuid(context,"encounter_id");if(id==null)return 0;var result=SceneOperatorService.forceCleanup(context.getSource().getLevel(),id,"operator_force_cleanup",context.getSource().getTextName());context.getSource().sendSuccess(()->Component.literal(result.message()),false);return result.success()?1:0;
    }

    private static UUID parseUuid(CommandContext<CommandSourceStack> context,String key) {
        try{return UUID.fromString(StringArgumentType.getString(context,key));}catch(IllegalArgumentException exception){context.getSource().sendFailure(Component.literal(key+" must be a UUID"));return null;}
    }

    private static int rebindQuestDebug(CommandContext<CommandSourceStack> context, double radius) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player near the replacement provider."));
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
            return 0;
        }
        VillagerQuestService.ProviderRebindResult result =
                VillagerQuestService.debugRebindQuest(player, provider.provider(), quest.questId());
        if (!result.rebound()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int inspectQuestDebug(CommandContext<CommandSourceStack> context) {
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

        VillagerQuestService.DebugInspectResult result = VillagerQuestService.debugInspectQuest(
                player,
                quest.questId());
        if (!result.found()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        result.lines().forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return result.lines().size();
    }

    private static int explainQuestAvailabilityDebug(CommandContext<CommandSourceStack> context, double radius) {
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
        return sendQuestDebugLines(source, VillagerQuestService.debugWhyAvailable(
                player,
                provider.provider(),
                quest.questId()));
    }

    private static int explainQuestHiddenDebug(
            CommandContext<CommandSourceStack> context,
            double radius,
            boolean hasProvider) {
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
        Villager provider = null;
        if (hasProvider) {
            ProviderResolution resolvedProvider = resolveQuestDebugProvider(
                    player,
                    StringArgumentType.getString(context, "provider_name"),
                    debugProviderRadius(radius));
            if (!resolvedProvider.error().isBlank()) {
                source.sendFailure(Component.literal(resolvedProvider.error()));
                resolvedProvider.matches().forEach(match -> source.sendFailure(Component.literal(providerDebugLine(match))));
                return 0;
            }
            provider = resolvedProvider.provider();
        }
        return sendQuestDebugLines(source, VillagerQuestService.debugWhyHidden(player, provider, quest.questId()));
    }

    private static int captureQuestTraceDebug(CommandContext<CommandSourceStack> context, double radius) {
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
        return sendQuestDebugLines(source, VillagerQuestService.debugTraceQuest(player, provider.provider(), quest.questId()));
    }

    private static int showQuestTraceDebug(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so trace state can be resolved."));
            return 0;
        }
        return sendQuestDebugLines(source, VillagerQuestService.debugTraceRecent(player, limit));
    }

    private static int clearQuestTraceDebug(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so trace state can be resolved."));
            return 0;
        }
        return sendQuestDebugLines(source, VillagerQuestService.debugTraceClear(player));
    }

    private static int setQuestTraceDebug(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so trace state can be resolved."));
            return 0;
        }
        return sendQuestDebugLines(source, VillagerQuestService.debugTraceSetEnabled(player, enabled));
    }

    private static int showQuestObjectivesDebug(CommandContext<CommandSourceStack> context) {
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
        return sendQuestDebugLines(source, VillagerQuestService.debugObjectives(player, quest.questId()));
    }

    private static int setQuestStageDebug(CommandContext<CommandSourceStack> context) {
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
        return sendQuestDebugLines(source, VillagerQuestService.debugSetQuestStage(
                player,
                quest.questId(),
                StringArgumentType.getString(context, "stage")));
    }

    private static int fireQuestTriggerDebug(CommandContext<CommandSourceStack> context) {
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
        QuestDefinition.TriggerEvent event = QuestTriggerRegistry.eventBySerializedName(
                StringArgumentType.getString(context, "event"));
        return sendQuestDebugLines(source, VillagerQuestService.debugFireTrigger(player, quest.questId(), event));
    }

    private static int dryRunQuestTriggerActionsDebug(CommandContext<CommandSourceStack> context) {
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
        return sendQuestDebugLines(source, VillagerQuestService.debugDryRunTriggerActions(
                player,
                quest.questId(),
                StringArgumentType.getString(context, "trigger_id")));
    }

    private static int showQuestFactsDebug(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This debug command must be run by a player so fact state can be resolved."));
            return 0;
        }
        return sendQuestDebugLines(source, VillagerQuestService.debugFactScope(
                player,
                StringArgumentType.getString(context, "scope_key")));
    }

    private static int sendQuestDebugLines(
            CommandSourceStack source,
            VillagerQuestService.DebugInspectResult result) {
        if (!result.found()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        result.lines().forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return Math.max(1, result.lines().size());
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

    private static int setProfileGender(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AbstractVillager villager = profileTarget(context);
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }

        String genderName = StringArgumentType.getString(context, "gender");
        VillagerGender gender = VillagerGender.bySerializedName(genderName);
        if (gender == null) {
            source.sendFailure(Component.literal("Unknown villager gender: " + genderName));
            return 0;
        }

        VillagerGender currentGender = VillagerPresetNameRegistry.resolveGender(villager);
        boolean changed = currentGender != gender;
        VillagerPresetNameRegistry.setStoredGender(villager, gender);
        if (villager instanceof Villager socialVillager) {
            VillagerSocialGraphSavedData.get(level).ensureProfile(level, socialVillager);
        }

        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        source.sendSuccess(
                () -> Component.literal("Set " + name + "'s gender to " + gender.serializedName()
                        + (changed ? "." : " (unchanged).")),
                true
        );
        return changed ? 1 : 0;
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

    private static int showDatapackDiagnostics(
            CommandContext<CommandSourceStack> context,
            String severityFilter,
            String resourceFilter) {
        List<DatapackDiagnostics.Entry> diagnostics = DatapackDiagnostics.recent();
        CommandSourceStack source = context.getSource();
        if (diagnostics.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Villager Retaliation datapack diagnostics since the last resource reload."), false);
            return 1;
        }

        QuestDiagnostic.Severity severity = parseSeverity(severityFilter);
        String resource = unquote(resourceFilter);
        List<DatapackDiagnostics.Entry> filtered = diagnostics.stream()
                .filter(entry -> severity == null || entry.diagnostic().severity() == severity)
                .filter(entry -> resource.isBlank()
                        || entry.diagnostic().resourceId() != null
                        && (entry.diagnostic().resourceId().toString().equals(resource)
                        || entry.diagnostic().resourceId().getPath().equals(resource)))
                .toList();

        if (filtered.isEmpty()) {
            String severityText = severity == null ? "any severity" : severity.name().toLowerCase(Locale.ROOT);
            String resourceText = resource.isBlank() ? "any resource" : resource;
            source.sendFailure(Component.literal("No Villager Retaliation datapack diagnostics matched severity="
                    + severityText + ", resource=" + resourceText + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Villager Retaliation datapack diagnostics: "
                + filtered.size() + " of " + diagnostics.size()
                + " matched since the last resource reload. Showing latest 10."), false);
        filtered.stream()
                .skip(Math.max(0, filtered.size() - 10))
                .forEach(entry -> source.sendSuccess(() -> Component.literal("- " + diagnosticLine(entry.diagnostic())), false));
        return filtered.size();
    }

    private static QuestDiagnostic.Severity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return QuestDiagnostic.Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String diagnosticLine(QuestDiagnostic diagnostic) {
        String resource = diagnostic.resourceId() == null ? "unknown" : diagnostic.resourceId().toString();
        String pointer = diagnostic.jsonPointer().isBlank() ? "" : " " + diagnostic.jsonPointer();
        String fix = diagnostic.suggestedFix().isBlank() ? "" : " suggestion=" + diagnostic.suggestedFix();
        return "[" + diagnostic.severity().name().toLowerCase(Locale.ROOT) + "] "
                + diagnostic.code()
                + " resource=" + resource
                + pointer
                + " message=" + diagnostic.message()
                + fix;
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
