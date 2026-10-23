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
            case ITEM_CHECK, GIFT -> "Give missing supplies";
            case TRADE -> "Prepare matching trade";
            case LOCATION_VISIT, STRUCTURE_VISIT -> "Travel to target";
            case MOB_KILL -> "Spawn matching target";
            case BLOCK_BREAK, BLOCK_INTERACT -> "Place matching block";
            case BLOCK_PLACE -> "Give matching blocks";
            case FACT -> "Set required facts";
            case REPUTATION -> "Set provider reputation";
            case CAMPAIGN_SPEAK, CAMPAIGN_FOLLOW -> "Travel to campaign character";
            case CAMPAIGN_INVESTIGATE -> "Travel to evidence";
            case FOLLOWER_ARRIVAL -> "Travel to escort destination";
            case MEMORY_EVENT, CHOICE, CRITERION, CAMPAIGN_OUTCOME, CONDITION -> "";
        };
    }

    public static String guidance(QuestDefinition.Objective objective) {
        return switch (objective.type()) {
            case ITEM_CHECK -> "Give supplies, then let the quest check your inventory. Consumed items are still required at turn-in.";
            case GIFT -> "Give the matching item to the quest provider through the normal gift interaction. Gift reaction filters still apply.";
            case TRADE -> "Prepare an offer and its payment, then trade with the quest provider. Receiving an item through setup does not count as trading.";
            case LOCATION_VISIT -> "Travel to the authored coordinates or bound campaign location. Distance and dimension are checked normally.";
            case STRUCTURE_VISIT -> "Prepare an objective structure, then travel to it. Required structure pieces still need to be visited.";
            case MOB_KILL -> "Spawn a matching target and kill it yourself in the required area. Each kill is recorded by normal gameplay.";
            case BLOCK_BREAK -> "Place a matching block, then break it yourself in the required area.";
            case BLOCK_PLACE -> "Give matching blocks, then place them yourself in the required area.";
            case BLOCK_INTERACT -> "Place a matching block, then interact with it in the required area.";
            case MEMORY_EVENT -> "Perform a village event matching " + objective.memoryTags() + ". Location and dimension filters still apply.";
            case REPUTATION -> "Set a matching reputation with the provider, or change it through normal interactions.";
            case CHOICE -> "Choose the authored dialogue response. Other characters and all responses in open conversations remain available.";
            case FACT -> "Set required tags, variables and counters in the authored scope. Stage references must be reached through their quest.";
            case CRITERION -> "Trigger " + objective.criterion() + " through its gameplay integration. Debug event injection tests the listener separately.";
            case CAMPAIGN_OUTCOME -> "Resolve the named milestone through its campaign. World outcomes, discovery and participation are separate requirements.";
            case CAMPAIGN_SPEAK -> "Travel to the bound character and play the authored conversation. Hearing the specified node records evidence for this run.";
            case CAMPAIGN_INVESTIGATE -> "Travel to the bound evidence and interact with it. The real investigation records evidence for this run.";
            case CAMPAIGN_FOLLOW -> "Travel to the bound guide and accompany the authored scene to its destination.";
            case FOLLOWER_ARRIVAL -> "Escort the bound actor to the destination. Travelling there alone does not complete the objective.";
            case CONDITION -> "Meet the authored conditions through gameplay or the world controls. Runtime diagnostics show what is still missing.";
        };
    }

    public static String prepare(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective, String value) {
        var player = context.player();
        QuestPlaytestSession.require(player, context.definition().id());
        if (context.progress().state() != com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState.ACTIVE)
            throw new IllegalStateException("Restart the quest before preparing requirements");
        return switch (objective.type()) {
            case ITEM_CHECK, GIFT -> giveItems(context, objective);
            case TRADE -> trade(context, objective);
            case LOCATION_VISIT, STRUCTURE_VISIT -> travel(context, objective);
            case MOB_KILL -> spawn(context, objective);
            case BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT -> block(context, objective);
            case FACT -> facts(context, objective, value);
            case REPUTATION -> reputation(context, objective, value);
            case CAMPAIGN_SPEAK, CAMPAIGN_FOLLOW, CAMPAIGN_INVESTIGATE, FOLLOWER_ARRIVAL -> travel(context, objective);
            case MEMORY_EVENT, CHOICE, CRITERION, CAMPAIGN_OUTCOME, CONDITION -> guidance(objective);
        };
    }

    public static ItemStack sample(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var item = objective.usesRandomItemSelection() ? context.progress().resolvedObjectiveItem(objective.id()) : objective.item();
        if (item == null && objective.type() == QuestDefinition.ObjectiveType.TRADE)
            return new ItemStack(net.minecraft.world.item.Items.EMERALD);
        if (item == null && objective.type() == QuestDefinition.ObjectiveType.GIFT) {
            var provider = context.dialogueContext() == null ? null : context.dialogueContext().villager();
            if (provider == null) throw new IllegalStateException("Return to the provider to choose a matching gift");
            return BuiltInRegistries.ITEM.stream().filter(candidate -> candidate != net.minecraft.world.item.Items.AIR)
                    .map(ItemStack::new).filter(stack -> objective.giftReactions().isEmpty() || objective.giftReactions().contains(
                            com.jvn.villagerretaliation.interaction.VillagerGiftPreferences.evaluate(context.level(), provider, stack)
                                    .reaction().name().toLowerCase(java.util.Locale.ROOT)))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No item has the required gift reaction for this provider"));
        }
        if (item == null) throw new IllegalStateException("No resolved item is available for this objective");
        for (var stack : context.player().getInventory().items)
            if (QuestInventoryService.matchesObjectiveItemStack(objective, stack, item)) return stack.copyWithCount(1);
        var stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(item).orElseThrow(() -> new IllegalArgumentException("Unknown item " + item)));
        var requirements = objective.itemRequirements();
        for (var predicate : requirements.stackPredicate().components()) {
            if (predicate instanceof ComponentPredicate.Exact<?> exact) applyExact(stack, exact);
            else if (predicate instanceof ComponentPredicate.NumericRange range) applyRange(stack, range);
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
        if (stack.isDamageableItem() && (requirements.minDurability().isPresent() || requirements.maxDurability().isPresent()
                || requirements.minDurabilityPercent().isPresent() || requirements.maxDurabilityPercent().isPresent()
                || requirements.stackPredicate().durability() != null)) {
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
            for (var predicate : requirements.stackPredicate().components()) {
                if (predicate.type() != DataComponents.DAMAGE) continue;
                if (predicate instanceof ComponentPredicate.Exact<?> exact && exact.expected() instanceof Integer damage) {
                    low = Math.max(low, maximum - damage);
                    high = Math.min(high, maximum - damage);
                } else if (predicate instanceof ComponentPredicate.NumericRange range) {
                    if (range.min() != null) high = Math.min(high, maximum - (int) Math.ceil(range.min()));
                    if (range.max() != null) low = Math.max(low, maximum - (int) Math.floor(range.max()));
                }
            }
            if (low > high) throw new IllegalStateException("The item's durability requirements cannot be satisfied");
            stack.setDamageValue(maximum - high);
        }
        if (!QuestInventoryService.matchesObjectiveItemStack(objective, stack, item))
            throw new IllegalStateException("Could not construct a matching item. Put a matching sample in your inventory or use Force complete objective.");
        return stack;
    }

    static <T> void applyExact(ItemStack stack, ComponentPredicate.Exact<T> exact) { stack.set(exact.type(), exact.expected()); }

    static void applyRange(ItemStack stack, ComponentPredicate.NumericRange range) {
        double low = range.min() == null ? -Double.MAX_VALUE : range.min();
        double high = range.max() == null ? Double.MAX_VALUE : range.max();
        for (double candidate : new double[] {Math.clamp(0D, low, high), Math.ceil(low), Math.floor(high)}) {
            if (!Double.isFinite(candidate) || candidate < low || candidate > high) continue;
            if (applyNumber(stack, range.type(), candidate) && range.matches(stack)) return;
        }
        throw new IllegalStateException("Cannot construct a value for component " + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(range.type())
                + ". Add a matching inventory sample.");
    }

    private static <T> boolean applyNumber(ItemStack stack, net.minecraft.core.component.DataComponentType<T> type, double value) {
        if (type.codec() == null) return false;
        var decoded = type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(value)).result();
        decoded.ifPresent(component -> stack.set(type, component));
        return decoded.isPresent();
    }

    private static String trade(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var dialogue = context.dialogueContext();
        if (dialogue == null || dialogue.villager() == null)
            throw new IllegalStateException("Return to the quest provider before preparing a trade");
        var provider = dialogue.villager();
        var offers = provider.getOffers();
        var offer = offers.stream().filter(candidate -> objective.item() == null
                || context.matchesItem(objective, candidate.getResult())).filter(candidate -> !candidate.isOutOfStock()).findFirst().orElse(null);
        boolean created = offer == null;
        if (created) {
            offer = new net.minecraft.world.item.trading.MerchantOffer(
                    new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD),
                    sample(context, objective), Math.max(1, objective.count()), 0, 0);
            offers.add(offer);
        }
        int trades = Math.min(64, Math.max(1, objective.count() - context.model().objectiveCounter(objective.id())));
        supply(context, offer.getCostA(), Math.min(4096, offer.getCostA().getCount() * trades));
        if (!offer.getCostB().isEmpty()) supply(context, offer.getCostB(), Math.min(4096, offer.getCostB().getCount() * trades));
        return (created ? "Added a test trade and supplied its payment. " : "Supplied payment for a matching trade. ")
                + "Trade with the quest provider to record progress.";
    }

    private static void supply(QuestObjectiveEvaluationContext context, ItemStack sample, int count) {
        while (count > 0) {
            var stack = sample.copyWithCount(Math.min(count, sample.getMaxStackSize()));
            count -= stack.getCount();
            if (!context.player().getInventory().add(stack)) context.player().drop(stack, false);
        }
        context.player().getInventory().setChanged();
        context.player().containerMenu.broadcastChanges();
        QuestInventoryService.clearRuntimeState(context.player());
    }

    private static String giveItems(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        ItemStack sample = sample(context, objective);
        int remaining = objective.type() == QuestDefinition.ObjectiveType.GIFT
                ? Math.max(0, objective.count() - context.model().objectiveCounter(objective.id())) : objective.count();
        int available = objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK ? context.itemCount(objective)
                : context.player().getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameComponents(sample, stack))
                        .mapToInt(ItemStack::getCount).sum();
        int count = Math.min(4096, Math.max(0, remaining - available));
        int supplied = count;
        supply(context, sample, count);
        return "Provided " + supplied + " matching items for " + objective.id();
    }

    public static String travel(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        QuestPlaytestSession.require(context.player(), context.definition().id());
        if (objective.story() instanceof com.jvn.villagerretaliation.campaign.CampaignStoryObjective.Speak speak)
            return character(context, speak.role());
        if (objective.story() instanceof com.jvn.villagerretaliation.campaign.CampaignStoryObjective.Follow follow)
            return character(context, follow.role());
        if (objective.story() instanceof com.jvn.villagerretaliation.campaign.CampaignStoryObjective.Investigate investigate) {
            var evidence = com.jvn.villagerretaliation.campaign.CampaignEvidenceService.resolve(
                    context.player().server, context.definition().campaign(), investigate.evidence());
            if (!evidence.available()) throw new IllegalStateException(evidence.diagnostic());
            teleport(context.player(), (net.minecraft.server.level.ServerLevel) evidence.entity().level(), evidence.entity().blockPosition());
            return "Travelled to evidence. Interact with it to investigate.";
        }
        if (objective.type() == QuestDefinition.ObjectiveType.FOLLOWER_ARRIVAL) {
            var destination = com.jvn.villagerretaliation.quest.follower.QuestFollowerArrivalObjective.destination(context.level(), objective)
                    .orElseThrow(() -> new IllegalStateException("The escort destination is missing or ambiguous"));
            teleport(context.player(), context.player().server.getLevel(destination.dimension()), destination.position());
            return "Travelled to the destination. The escorted actor must arrive through normal gameplay.";
        }
        var progress = context.progress();
        var prepared = QuestPlaytestSession.require(context.player(), context.definition().id()).target(objective.id());
        var target = prepared != null ? prepared : !objective.campaignLocation().isBlank()
                ? VillagerQuestTargets.locateCampaignLocation(context.level(), context.definition(), objective).orElse(null)
                : objective.location() != null
                    ? new VillagerQuestTargets.LocatedTarget(objective.dimension() == null ? context.level().dimension() : objective.dimension(), objective.location(), objective.id())
                    : objective.id().equals(progress.targetObjectiveId()) && progress.targetPos() != null
                        ? new VillagerQuestTargets.LocatedTarget(progress.targetDimension(), progress.targetPos(), objective.id())
                        : objective.structure() != null ? null
                            : new VillagerQuestTargets.LocatedTarget(objective.dimension() == null ? context.level().dimension() : objective.dimension(),
                                    context.player().blockPosition(), objective.id());
        if (target == null) throw new IllegalStateException("Prepare an objective structure or bind its campaign location first. No world-wide structure search was started.");
        var level = context.player().server.getLevel(target.dimension());
        if (level == null) throw new IllegalStateException("The target dimension is not available");
        teleport(context.player(), level, target.pos());
        return "Travelled to " + objective.id();
    }

    private static String character(QuestObjectiveEvaluationContext context, String role) {
        var character = com.jvn.villagerretaliation.campaign.CampaignCharacterService.resolve(
                context.player().server, context.definition().campaign(), role);
        if (!character.available()) throw new IllegalStateException(character.diagnostic());
        teleport(context.player(), (net.minecraft.server.level.ServerLevel) character.entity().level(), character.entity().blockPosition());
        return "Travelled to " + role + ". Continue through the authored conversation or scene.";
    }

    public static void teleport(net.minecraft.server.level.ServerPlayer player, net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos target) {
        if (level == null) throw new IllegalStateException("The target dimension is unavailable");
        level.getChunkAt(target);
        for (var candidate : net.minecraft.core.BlockPos.withinManhattan(target, 3, 8, 3)) {
            if (!level.isInWorldBounds(candidate) || !level.getWorldBorder().isWithinBounds(candidate)) continue;
            var bounds = player.getBoundingBox().move(candidate.getX() + .5 - player.getX(),
                    candidate.getY() - player.getY(), candidate.getZ() + .5 - player.getZ());
            if (!level.noCollision(player, bounds) || !level.getFluidState(candidate).isEmpty()
                    || !level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), net.minecraft.core.Direction.UP)) continue;
            player.teleportTo(level, candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5, player.getYRot(), player.getXRot());
            return;
        }
        throw new IllegalStateException("No safe arrival space near " + target.toShortString() + ". Travel there manually.");
    }

    private static net.minecraft.core.BlockPos setupPosition(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        if (objective.dimension() != null && !objective.dimension().equals(context.level().dimension()))
            throw new IllegalStateException("Travel to the required area before preparing this objective");
        var position = objective.location() == null ? context.player().blockPosition().offset(2, 0, 0) : objective.location();
        if (position.distSqr(context.player().blockPosition()) > 32 * 32)
            throw new IllegalStateException("Travel to the required area before preparing this objective");
        if (!context.level().isInWorldBounds(position) || !context.level().getWorldBorder().isWithinBounds(position))
            throw new IllegalStateException("The setup position is outside the world bounds");
        return position;
    }

    public static String simulateCriterion(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        QuestPlaytestSession.require(context.player(), context.definition().id());
        if (objective.type() != QuestDefinition.ObjectiveType.CRITERION)
            throw new IllegalArgumentException("Choose a criterion objective");
        net.minecraft.world.entity.LivingEntity entity = null;
        if (!objective.entityTypes().isEmpty() || !objective.entityTags().isEmpty()) {
            var type = BuiltInRegistries.ENTITY_TYPE.stream().filter(candidate -> objective.entityTypes().contains(BuiltInRegistries.ENTITY_TYPE.getKey(candidate))
                    || objective.entityTags().stream().anyMatch(tag -> candidate.is(TagKey.create(Registries.ENTITY_TYPE, tag))))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No matching entity is available"));
            if (!(type.create(context.level()) instanceof net.minecraft.world.entity.LivingEntity living))
                throw new IllegalStateException("The criterion requires a living entity");
            entity = living;
            var pos = setupPosition(context, objective);
            entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        }
        com.jvn.villagerretaliation.api.QuestCriterionApi.trigger(context.player(), objective.criterion(),
                objective.criterionData(), objective.item() == null ? ItemStack.EMPTY : sample(context, objective), entity);
        return "Simulated one criterion event for " + objective.id() + ". This tests the listener, not the external event source.";
    }

    private static String spawn(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var type = BuiltInRegistries.ENTITY_TYPE.stream().filter(candidate -> objective.entityTypes().contains(BuiltInRegistries.ENTITY_TYPE.getKey(candidate))
                || objective.entityTags().stream().anyMatch(tag -> candidate.is(TagKey.create(Registries.ENTITY_TYPE, tag))))
                .sorted(Comparator.comparing(candidate -> BuiltInRegistries.ENTITY_TYPE.getKey(candidate).toString())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No matching entity type is available"));
        var entity = type.create(context.level());
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) throw new IllegalStateException("The target is not a living entity");
        var position = setupPosition(context, objective);
        entity.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        if (!context.level().noCollision(entity)) throw new IllegalStateException("The target position is occupied. Clear it before spawning.");
        if (entity instanceof Mob mob) mob.finalizeSpawn(context.level(), context.level().getCurrentDifficultyAt(position),
                net.minecraft.world.entity.MobSpawnType.COMMAND, null);
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        if (entity instanceof net.minecraft.world.entity.monster.Enemy && context.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL)
            context.player().server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        if (!context.level().addFreshEntity(entity)) throw new IllegalStateException("Could not spawn the target");
        return "Spawned " + type.toShortString() + " for " + objective.id();
    }

    private static String block(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var block = BuiltInRegistries.BLOCK.stream().filter(candidate -> objective.type() != QuestDefinition.ObjectiveType.BLOCK_PLACE
                || candidate.asItem() != net.minecraft.world.item.Items.AIR).filter(candidate -> objective.blockTypes().contains(BuiltInRegistries.BLOCK.getKey(candidate))
                || objective.blockTags().stream().anyMatch(tag -> candidate.defaultBlockState().is(TagKey.create(Registries.BLOCK, tag))))
                .sorted(Comparator.comparing(candidate -> BuiltInRegistries.BLOCK.getKey(candidate).toString())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No matching block is available"));
        if (objective.type() == QuestDefinition.ObjectiveType.BLOCK_PLACE) {
            var stack = new ItemStack(block, Math.min(64, objective.count()));
            if (stack.isEmpty()) throw new IllegalStateException("This block has no item form");
            if (!context.player().getInventory().add(stack)) context.player().drop(stack, false);
            context.player().containerMenu.broadcastChanges();
        } else {
            var pos = setupPosition(context, objective);
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
