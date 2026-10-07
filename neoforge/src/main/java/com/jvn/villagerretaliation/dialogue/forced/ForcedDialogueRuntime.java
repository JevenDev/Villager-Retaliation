package com.jvn.villagerretaliation.dialogue.forced;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager.ReputationSnapshot;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class ForcedDialogueRuntime<S extends ForcedDialogueRuntime.Session> {
    private static final String LEAVE_OPTION_ID = "leave";
    private final Map<UUID, S> sessions = new HashMap<>();
    private final long timeoutTicks;
    private final BiPredicate<S, ForcedDialogueOption> optionPolicy;
    private final SelectionHandler<S> selectionHandler;

    public ForcedDialogueRuntime(long timeoutTicks, BiPredicate<S, ForcedDialogueOption> optionPolicy,
            SelectionHandler<S> selectionHandler) {
        this.timeoutTicks = timeoutTicks;
        this.optionPolicy = optionPolicy;
        this.selectionHandler = selectionHandler;
    }

    public S get(UUID playerId) { return sessions.get(playerId); }
    public void put(UUID playerId, S session) { sessions.put(playerId, session); }
    public void remove(UUID playerId) { sessions.remove(playerId); }
    public boolean containsKey(UUID playerId) { return sessions.containsKey(playerId); }
    public boolean isEmpty() { return sessions.isEmpty(); }
    public void clear() { sessions.clear(); }
    public Map<UUID, S> snapshot() { return Collections.unmodifiableMap(new LinkedHashMap<>(sessions)); }

    public boolean hasSession(ServerPlayer player, Villager villager) {
        S session = sessions.get(player.getUUID());
        return session != null && session.villagerId().equals(villager.getUUID());
    }

    public boolean isStaleConversationEndRequest(ServerPlayer player, int entityId) {
        S session = sessions.get(player.getUUID());
        if (session == null) return false;
        Entity entity = player.serverLevel().getEntity(entityId);
        return entity instanceof Villager villager && !session.villagerId().equals(villager.getUUID());
    }

    public boolean handleRequest(ServerPlayer player, Villager villager, String optionId) {
        S session = sessions.get(player.getUUID());
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || session == null
                || !session.villagerId().equals(villager.getUUID())) {
            return false;
        }
        if (player.serverLevel().getGameTime() - session.startedGameTime() > timeoutTicks) {
            sessions.remove(player.getUUID());
            return false;
        }
        if (LEAVE_OPTION_ID.equals(optionId)) {
            return handleLeaveRequest(player, villager, false);
        }

        Optional<ForcedDialogueOption> selected = session.definition().options().stream()
                .filter(option -> option.id().equals(optionId))
                .filter(option -> optionPolicy.test(session, option))
                .filter(option -> !session.disabledOptionIds().contains(option.id()))
                .filter(option -> optionMatches(player.serverLevel(), villager, player, option))
                .findFirst();
        if (selected.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.unknown_dialogue_option");
            sessions.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }

        return selectionHandler.handle(player, villager, session, selected.get(), false, false);
    }

    public boolean handleLeaveRequest(ServerPlayer player, Villager villager, boolean forceEndConversation) {
        S session = sessions.get(player.getUUID());
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || session == null
                || !session.villagerId().equals(villager.getUUID())) {
            return false;
        }
        Optional<ForcedDialogueOption> selected = selectLeaveOption(player, villager, session);
        if (selected.isEmpty()) {
            sessions.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        return selectionHandler.handle(player, villager, session, selected.get(), forceEndConversation, true);
    }

    private Optional<ForcedDialogueOption> selectLeaveOption(
            ServerPlayer player,
            Villager villager,
            S session) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(player.serverLevel(), villager, player.getUUID());
        return session.definition().options().stream()
                .filter(option -> optionPolicy.test(session, option))
                .filter(option -> option.id().equals(LEAVE_OPTION_ID))
                .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                .findFirst()
                .or(() -> session.definition().leaveOptions().stream()
                        .filter(option -> optionPolicy.test(session, option))
                        .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                        .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                        .findFirst())
                .or(() -> session.definition().options().stream()
                        .filter(option -> optionPolicy.test(session, option))
                        .filter(option -> option.id().equals(LEAVE_OPTION_ID))
                        .findFirst())
                .or(() -> optionPolicy.test(session, session.definition().leaveOption())
                        ? Optional.of(session.definition().leaveOption())
                        : Optional.empty());
    }

    private static boolean optionMatches(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueOption option) {
        ReputationSnapshot reputation = VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        if (!option.reputationCondition().matches(reputation.value(), reputation.level())) {
            return false;
        }
        return optionConditionsMatch(level, villager, player, option);
    }

    private static boolean optionConditionsMatch(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueResources.ForcedDialogueOption option) {
        if (option.socialAttributeCondition().isEmpty() && option.conditions().isEmpty()) {
            return true;
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        if (!option.socialAttributeCondition().isEmpty()
                && (!VillagerSocialAttributeBehavior.enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS)
                || !option.socialAttributeCondition().matches(context))) {
            return false;
        }
        return DialogueCondition.matchesAll(context, option.conditions());
    }

    public List<DialogueOptionDefinition> options(
            ForcedDialogueDefinition definition,
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            S session) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return definition.options().stream()
                .filter(option -> session == null || optionPolicy.test(session, option))
                .filter(option -> session == null || !session.disabledOptionIds().contains(option.id()))
                .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                .filter(option -> optionConditionsMatch(level, villager, player, option))
                .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                .map(option -> DialogueOptionDefinition.simple(
                        option.id(),
                        forcedOptionLabel(option, level, player, session),
                        DialogueRequestType.QUESTION,
                        option.order()))
                .toList();
    }

    private String forcedOptionLabel(
            ForcedDialogueOption option,
            ServerLevel level,
            ServerPlayer player,
            S session) {
        if (session == null) {
            ForcedDialogueContext context = new ForcedDialogueContext(
                    "",
                    player == null ? "" : player.getDisplayName().getString(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    0,
                    "",
                    "",
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    level == null ? null : level.getServer(),
                    level == null ? null : level.getRandom(),
                    player == null ? VillagerLocale.DEFAULT_LOCALE : VillagerLocale.locale(player));
            return ForcedDialogueResources.resolveTemplate(option.label(), context);
        }
        return ForcedDialogueResources.resolveTemplate(
                option.label(),
                session.context(),
                option.itemPayment().removal().replacements());
    }

    public interface Session {
        UUID villagerId();
        ForcedDialogueDefinition definition();
        ForcedDialogueContext context();
        long startedGameTime();
        List<String> disabledOptionIds();
    }

    @FunctionalInterface
    public interface SelectionHandler<S extends Session> {
        boolean handle(ServerPlayer player, Villager villager, S session, ForcedDialogueOption option,
                boolean forceEndConversation, boolean leaveRequest);
    }
}
