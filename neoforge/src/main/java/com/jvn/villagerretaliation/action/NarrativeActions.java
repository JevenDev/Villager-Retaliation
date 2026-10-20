package com.jvn.villagerretaliation.action;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueTemplates;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.quest.runtime.QuestFactStore;
import com.jvn.villagerretaliation.reputation.runtime.ReputationService;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class NarrativeActions {
    private static final NarrativeActions COMMON = commonActions();
    private final Map<VillagerActionDefinition.Kind, Handler> handlers;
    private final Handler fallback;
    private final QuestFactStore facts;
    private final ToIntFunction<DialogueRuntimeContext> reputation;

    private NarrativeActions(Map<VillagerActionDefinition.Kind, Handler> handlers, Handler fallback,
            QuestFactStore facts, ToIntFunction<DialogueRuntimeContext> reputation) {
        this.handlers = Map.copyOf(handlers);
        this.fallback = Objects.requireNonNull(fallback);
        this.facts = facts;
        this.reputation = reputation;
    }

    public static NarrativeActions common() { return COMMON; }

    public static NarrativeActions adapt(Handler handler) {
        return new NarrativeActions(Map.of(), handler, null, null);
    }

    public NarrativeActions with(VillagerActionDefinition.Kind kind, Handler handler) {
        var updated = new EnumMap<VillagerActionDefinition.Kind, Handler>(VillagerActionDefinition.Kind.class);
        updated.putAll(this.handlers);
        updated.put(Objects.requireNonNull(kind), Objects.requireNonNull(handler));
        return new NarrativeActions(updated, this.fallback, this.facts, this.reputation);
    }

    public NarrativeActions withReputation(ReputationService<?> service) {
        Objects.requireNonNull(service);
        var updated = with(VillagerActionDefinition.Kind.REPUTATION, (context, action, replacements) -> {
            if (context.speaker() == null) return unavailable("Reputation requires an entity provider.");
            var change = service.add(context.providerId(), context.player().getUUID(), action.amount(),
                    context.level().getGameTime(), context.origin());
            return change.changed() ? success(VillagerActionResult.success())
                    : ActionResult.alreadySatisfied("reputation unchanged", Set.of(), VillagerActionResult.EMPTY);
        });
        return new NarrativeActions(updated.handlers, updated.fallback, updated.facts,
                context -> service.score(context.providerId(), context.player().getUUID()));
    }

    public NarrativeActions withFacts(QuestFactStore store, FactActions.StageSetter stages) {
        Objects.requireNonNull(store);
        NarrativeActions updated = this;
        for (var kind : java.util.List.of(VillagerActionDefinition.Kind.SET_TAG, VillagerActionDefinition.Kind.CLEAR_TAG,
                VillagerActionDefinition.Kind.SET_VARIABLE, VillagerActionDefinition.Kind.COUNTER)) {
            updated = updated.with(kind, (context, action, replacements) -> {
                if ("stage".equals(action.factKey()) && action.factScope() == QuestFactScope.QUEST && stages == null) {
                    return unavailable("Quest stage changes require a quest runtime.");
                }
                var result = FactActions.execute(context, action, replacements, store, stages);
                if (result.ran()) return success(result);
                String scope = action.factScope().scopeKey(context, action.questId());
                if (scope.isBlank()) return unavailable("The fact scope is unavailable.");
                boolean satisfied = switch (action.kind()) {
                    case SET_TAG -> store.hasTag(scope, action.factTag());
                    case CLEAR_TAG -> !store.hasTag(scope, action.factTag());
                    case SET_VARIABLE -> store.variable(scope, action.factKey()).filter(value ->
                            value.equals(DialogueTemplates.resolveTemplate(action.factValue(), replacements))).isPresent();
                    default -> false;
                };
                return satisfied ? ActionResult.alreadySatisfied("fact already applied", Set.of(), result)
                        : unavailable("The fact action is unavailable.");
            });
        }
        return new NarrativeActions(updated.handlers, updated.fallback, store, updated.reputation);
    }

    public Optional<QuestFactStore> facts() { return Optional.ofNullable(this.facts); }

    public OptionalInt reputation(DialogueRuntimeContext context) {
        return this.reputation == null || context.providerId() == null ? OptionalInt.empty()
                : OptionalInt.of(this.reputation.applyAsInt(context));
    }

    public ActionResult execute(DialogueRuntimeContext context, VillagerActionDefinition action, Map<String, String> inherited) {
        if (action == null || action.kind() == VillagerActionDefinition.Kind.NONE) return ActionResult.skipped("action is empty", Set.of());
        if (context == null || context.player() == null || context.level() == null) return unavailable("Narrative context is unavailable.");
        Map<String, String> replacements = DialoguePlaceholders.merge(DialoguePlaceholders.base(context), inherited);
        return this.handlers.getOrDefault(action.kind(), this.fallback).execute(context, action, replacements);
    }

    public static boolean awardExperience(DialogueRuntimeContext context, int amount) {
        if (context == null || context.player() == null || amount <= 0) return false;
        context.player().giveExperiencePoints(amount);
        return true;
    }

    public static LootGrant grantLoot(DialogueRuntimeContext context, ResourceLocation lootTable, Consumer<ItemStack> received) {
        if (context == null || lootTable == null) return new LootGrant(false, false, "Loot context is unavailable.");
        var roll = QuestRewardResolver.roll(context.level(), context.player().getLuck(), lootTable, context.random());
        if (!roll.resolution().resolved()) return new LootGrant(false, false, roll.resolution().diagnostic());
        boolean gaveAny = false;
        for (ItemStack stack : roll.items()) {
            if (stack.isEmpty()) continue;
            ItemStack reward = stack.copy();
            ItemStack notice = reward.copy();
            if (!context.player().addItem(reward) && !reward.isEmpty()) context.player().drop(reward, false);
            received.accept(notice);
            gaveAny = true;
        }
        return new LootGrant(true, gaveAny, "");
    }

    private static NarrativeActions commonActions() {
        var actions = adapt((context, action, replacements) -> unavailable("This action requires a registered narrative handler."));
        actions = actions.with(VillagerActionDefinition.Kind.EXPERIENCE, (context, action, replacements) ->
                awardExperience(context, action.amount()) ? success(VillagerActionResult.success()) : ActionResult.skipped("experience is empty", Set.of()));
        actions = actions.with(VillagerActionDefinition.Kind.TRACKER, (context, action, replacements) ->
                success(VillagerActionResult.tracker(action.flashTracker())));
        actions = actions.with(VillagerActionDefinition.Kind.NOTIFICATION, (context, action, replacements) -> {
            context.player().sendSystemMessage(Component.literal(DialogueTemplates.resolveTemplate(action.text(), replacements)));
            return success(VillagerActionResult.success());
        });
        return actions.with(VillagerActionDefinition.Kind.LOOT, (context, action, replacements) -> {
            var result = grantLoot(context, action.lootTable(), received -> { });
            return result.resolved() ? success(VillagerActionResult.success()) : unavailable(result.diagnostic());
        });
    }

    private static ActionResult success(VillagerActionResult result) { return ActionResult.success(result, Set.of()); }
    private static ActionResult unavailable(String message) { return ActionResult.unavailable(message, Set.of(), VillagerActionResult.EMPTY); }

    @FunctionalInterface
    public interface Handler {
        ActionResult execute(DialogueRuntimeContext context, VillagerActionDefinition action, Map<String, String> replacements);
    }

    public record LootGrant(boolean resolved, boolean gaveAny, String diagnostic) { }
}
