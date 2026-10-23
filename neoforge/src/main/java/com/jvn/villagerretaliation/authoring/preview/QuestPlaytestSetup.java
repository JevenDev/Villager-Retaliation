package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.inventory.QuestInventoryService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import com.jvn.villagerretaliation.util.item.ComponentPredicate;
import java.util.Comparator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class QuestPlaytestSetup {
    private QuestPlaytestSetup() { }

    public static String label(QuestDefinition.Objective objective) {
        return switch (objective.type()) {
            case ITEM_CHECK, GIFT, TRADE -> "Give missing supplies";
            case LOCATION_VISIT, STRUCTURE_VISIT -> "Travel to target";
            case MOB_KILL -> "Spawn matching target";
            case BLOCK_BREAK, BLOCK_INTERACT -> "Place matching block";
            case BLOCK_PLACE -> "Give matching blocks";
            case FACT -> "Set required facts";
            case REPUTATION -> "Set provider reputation";
            default -> "";
        };
    }

    public static String prepare(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective, String value) {
        var player = context.player();
        QuestPlaytestSession.require(player, context.definition().id());
        if (context.progress().state() != com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState.ACTIVE)
            throw new IllegalStateException("Restart the quest before preparing requirements");
        return switch (objective.type()) {
            case ITEM_CHECK, GIFT, TRADE -> giveItems(context, objective);
            case LOCATION_VISIT, STRUCTURE_VISIT -> travel(context, objective);
            case MOB_KILL -> spawn(context, objective);
            case BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT -> block(context, objective);
            case FACT -> facts(context, objective, value);
            case REPUTATION -> reputation(context, objective, value);
            default -> "This requirement needs gameplay or campaign bindings. Use Force complete objective or jump to another stage.";
        };
    }

    public static ItemStack sample(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var item = objective.usesRandomItemSelection() ? context.progress().resolvedObjectiveItem(objective.id()) : objective.item();
        if (item == null && objective.type() == QuestDefinition.ObjectiveType.TRADE)
            return new ItemStack(net.minecraft.world.item.Items.EMERALD);
        if (item == null) throw new IllegalStateException("No resolved item is available for this objective");
        for (var stack : context.player().getInventory().items)
            if (QuestInventoryService.matchesObjectiveItemStack(objective, stack, item)) return stack.copyWithCount(1);
        var stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(item).orElseThrow(() -> new IllegalArgumentException("Unknown item " + item)));
        var requirements = objective.itemRequirements();
        for (var predicate : requirements.stackPredicate().components()) {
            if (predicate instanceof ComponentPredicate.Exact<?> exact) applyExact(stack, exact);
            else throw new IllegalStateException("A numeric component range needs a matching inventory sample. Add one or use Force complete objective.");
        }
        if (requirements.hasCustomData()) {
            var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.merge(requirements.customData());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        var enchantments = context.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var requirement : requirements.enchantments()) {
            var enchantment = enchantments.getHolder(ResourceKey.create(Registries.ENCHANTMENT, requirement.id()))
                    .orElseThrow(() -> new IllegalStateException("Missing enchantment " + requirement.id()));
            stack.enchant(enchantment, requirement.minLevel().orElse(1));
        }
        if (stack.isDamageableItem()) {
            int maximum = stack.getMaxDamage();
            int low = requirements.minDurability().orElse(0);
            int high = Math.min(maximum, requirements.maxDurability().orElse(maximum));
            if (requirements.minDurabilityPercent().isPresent())
                low = Math.max(low, (int) Math.ceil((requirements.minDurabilityPercent().getAsInt() - .5D) * maximum / 100));
            if (requirements.maxDurabilityPercent().isPresent())
                high = Math.min(high, (int) Math.ceil((requirements.maxDurabilityPercent().getAsInt() + .5D) * maximum / 100) - 1);
            var durability = requirements.stackPredicate().durability();
            if (durability != null) {
                if (durability.min() != null) low = Math.max(low, durability.min());
                if (durability.max() != null) high = Math.min(high, durability.max());
            }
            if (low > high) throw new IllegalStateException("The item's durability requirements cannot be satisfied");
            stack.setDamageValue(maximum - high);
        }
        if (!QuestInventoryService.matchesObjectiveItemStack(objective, stack, item))
            throw new IllegalStateException("Could not construct a matching item. Put a matching sample in your inventory or use Force complete objective.");
        return stack;
    }

    private static <T> void applyExact(ItemStack stack, ComponentPredicate.Exact<T> exact) { stack.set(exact.type(), exact.expected()); }

    private static String giveItems(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        ItemStack sample = sample(context, objective);
        int count = objective.item() == null && objective.type() == QuestDefinition.ObjectiveType.TRADE ? 32
                : Math.max(0, objective.count() - context.itemCount(objective));
        if (count > 4096) throw new IllegalStateException("Prepare at most 4096 items at a time. Use Force complete objective for larger counts.");
        int supplied = count;
        while (count > 0) {
            ItemStack stack = sample.copyWithCount(Math.min(count, sample.getMaxStackSize()));
            count -= stack.getCount();
            if (!context.player().getInventory().add(stack)) context.player().drop(stack, false);
        }
        context.player().getInventory().setChanged();
        context.player().containerMenu.broadcastChanges();
        QuestInventoryService.clearRuntimeState(context.player());
        return "Provided " + supplied + " matching items for " + objective.id();
    }

    private static String travel(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var progress = context.progress();
        var prepared = QuestPlaytestSession.require(context.player(), context.definition().id()).target(objective.id());
        var target = prepared != null ? prepared : !objective.campaignLocation().isBlank()
                ? VillagerQuestTargets.locateCampaignLocation(context.level(), context.definition(), objective).orElse(null)
                : objective.location() != null
                    ? new VillagerQuestTargets.LocatedTarget(objective.dimension() == null ? context.level().dimension() : objective.dimension(), objective.location(), objective.id())
                    : objective.id().equals(progress.targetObjectiveId()) && progress.targetPos() != null
                        ? new VillagerQuestTargets.LocatedTarget(progress.targetDimension(), progress.targetPos(), objective.id())
                        : VillagerQuestTargets.locateTarget(context.level(), context.player().blockPosition(), objective).orElse(null);
        if (target == null) throw new IllegalStateException("No target or campaign location is resolved. Bind it or use Force complete objective.");
        var level = context.player().server.getLevel(target.dimension());
        if (level == null) throw new IllegalStateException("The target dimension is not available");
        var pos = target.pos();
        level.getChunkAt(pos);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        context.player().teleportTo(level, pos.getX() + .5D, objective.type() == QuestDefinition.ObjectiveType.LOCATION_VISIT ? pos.getY() : y,
                pos.getZ() + .5D, context.player().getYRot(), context.player().getXRot());
        return "Travelled to " + objective.id();
    }

    private static String spawn(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var type = BuiltInRegistries.ENTITY_TYPE.stream().filter(candidate -> objective.entityTypes().contains(BuiltInRegistries.ENTITY_TYPE.getKey(candidate))
                || objective.entityTags().stream().anyMatch(tag -> candidate.is(TagKey.create(Registries.ENTITY_TYPE, tag))))
                .sorted(Comparator.comparing(candidate -> BuiltInRegistries.ENTITY_TYPE.getKey(candidate).toString())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No matching entity type is available"));
        var entity = type.create(context.level());
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) throw new IllegalStateException("The target is not a living entity");
        entity.setPos(context.player().getX() + 3, context.player().getY(), context.player().getZ());
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        if (entity instanceof net.minecraft.world.entity.monster.Enemy && context.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL)
            context.player().server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        if (!context.level().addFreshEntity(entity)) throw new IllegalStateException("Could not spawn the target");
        return "Spawned " + type.toShortString() + " for " + objective.id();
    }

    private static String block(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var block = BuiltInRegistries.BLOCK.stream().filter(candidate -> objective.blockTypes().contains(BuiltInRegistries.BLOCK.getKey(candidate))
                || objective.blockTags().stream().anyMatch(tag -> candidate.defaultBlockState().is(TagKey.create(Registries.BLOCK, tag))))
                .sorted(Comparator.comparing(candidate -> BuiltInRegistries.BLOCK.getKey(candidate).toString())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No matching block is available"));
        if (objective.type() == QuestDefinition.ObjectiveType.BLOCK_PLACE) {
            var stack = new ItemStack(block, Math.min(64, objective.count()));
            if (stack.isEmpty()) throw new IllegalStateException("This block has no item form");
            if (!context.player().getInventory().add(stack)) context.player().drop(stack, false);
            context.player().containerMenu.broadcastChanges();
        } else {
            var pos = context.player().blockPosition().offset(2, 0, 0);
            if (!context.level().getBlockState(pos).canBeReplaced()) throw new IllegalStateException("The setup position is occupied. Move to an open area.");
            if (!context.level().setBlock(pos, block.defaultBlockState(), 3)) throw new IllegalStateException("Could not place the matching block");
        }
        return "Prepared " + BuiltInRegistries.BLOCK.getKey(block) + " for " + objective.id();
    }

    private static String facts(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective, String value) {
        String scope = context.debugState(objective).factScopeKey();
        if (scope.isBlank() || scope.equals("unresolved")) throw new IllegalStateException("Bind the required fact scope before setting its value");
        Integer desired = null;
        if (objective.factMin() != null || objective.factMax() != null)
            desired = value.isBlank() ? objective.factMin() == null ? objective.factMax() : objective.factMin() : Integer.parseInt(value);
        var facts = VillagerQuestFacts.get(context.level());
        objective.factTags().forEach(tag -> facts.setTag(scope, tag));
        if (!objective.factKey().isBlank()) {
            if (desired == null || !objective.factValues().isEmpty()) facts.setVariable(scope, objective.factKey(),
                    value.isBlank() ? objective.factValues().stream().sorted().findFirst().orElse("true") : value);
            if (desired != null) {
                int current = facts.counter(scope, objective.factKey());
                while (current != desired) {
                    int delta = (int) Math.clamp((long) desired - current, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    current = facts.addCounter(scope, objective.factKey(), delta);
                }
            }
        }
        return "Updated required facts in " + scope;
    }

    private static String reputation(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective, String value) {
        int desired;
        if (!value.isBlank()) desired = Integer.parseInt(value);
        else {
            var candidates = new java.util.ArrayList<Integer>();
            candidates.add(0);
            if (objective.minReputation() != null) candidates.add(objective.minReputation());
            if (objective.maxReputation() != null) candidates.add(objective.maxReputation());
            candidates.addAll(java.util.List.of(
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ROYALTY_THRESHOLD.get(),
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.REVERED_THRESHOLD.get(),
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.RESPECTED_THRESHOLD.get(),
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.TRUSTED_THRESHOLD.get(),
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get(),
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HOSTILE_THRESHOLD.get(),
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.DESPISED_THRESHOLD.get(),
                    com.jvn.villagerretaliation.config.VillagerRetaliationConfig.FEARED_THRESHOLD.get()));
            desired = candidates.stream().filter(candidate -> (objective.minReputation() == null || candidate >= objective.minReputation())
                    && (objective.maxReputation() == null || candidate <= objective.maxReputation())
                    && (objective.reputationLevels().isEmpty() || objective.reputationLevels().contains(
                            com.jvn.villagerretaliation.reputation.VillagerReputationLevel.fromReputation(candidate))))
                    .findFirst().orElseThrow(() -> new IllegalStateException("The reputation requirements do not have a setup value"));
        }
        if (context.dialogueContext() == null || context.dialogueContext().villager() == null)
            throw new IllegalStateException("Return to the quest provider before changing its reputation");
        com.jvn.villagerretaliation.reputation.VillagerReputationManager.setReputation(context.level(), context.dialogueContext().villager(), context.player().getUUID(), desired);
        return "Set provider reputation to " + desired;
    }
}
