package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.action.NarrativeActions;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface InteractionActorAdapter extends InteractionSessionPolicy {
    void show(ActorInteractionContext context, InteractionMenu menu);
    void closed(ServerPlayer player, InteractionMenu.SessionRef session, boolean notifyClient, boolean finishExchanges);

    default boolean handles(ServerPlayer player, LivingEntity actor, InteractionHand hand, ItemStack stack) {
        return hand == InteractionHand.MAIN_HAND && stack.isEmpty();
    }

    default NarrativeActions actions(ServerPlayer player, LivingEntity actor) { return NarrativeActions.common(); }
    default String locale(ServerPlayer player) { return "en_us"; }
    default String groupKey(LivingEntity actor) { return ""; }
    default Set<ResourceLocation> dialogueTrees(DialogueRuntimeContext context) { return Set.of(); }

    default DialogueRuntimeContext context(ServerPlayer player, LivingEntity actor) {
        return new EntityDialogueContext(player.serverLevel(), player, actor, locale(player), groupKey(actor),
                actions(player, actor), InteractionDialogueRuntime.INSTANCE);
    }

    default List<DialogueOptionDefinition> options(DialogueRuntimeContext context) {
        var trees = dialogueTrees(context);
        var active = DialogueTreeService.activeOptions(context);
        if (active.isPresent()) return active.orElseThrow().stream()
                .filter(option -> trees.contains(option.treeReference().treeId())).toList();
        return DialogueTreeResources.entryOptions(context, context.runtime().disposition(context), trees);
    }

    default InteractionMenu.Content content(DialogueRuntimeContext context) {
        return new InteractionMenu.Content(context.displayName(context.speaker()), options(context).stream()
                .map(option -> new InteractionMenu.Choice(option.id(), option.label(), option.available(), option.unavailableReason())).toList());
    }

    default boolean select(DialogueRuntimeContext context, String choice) {
        var option = options(context).stream().filter(value -> value.available() && value.id().equals(choice)).findFirst().orElse(null);
        if (option == null) return false;
        var result = DialogueTreeService.handleDialogueOption(context, option);
        result.ifPresent(value -> context.runtime().present(context, value, context.runtime().forced(context)));
        return result.isPresent();
    }

    default boolean leave(ActorInteractionContext context) { return true; }
    default void trackerChanged(DialogueRuntimeContext context) { }
}
