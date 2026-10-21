package com.jvn.villagerretaliation.interaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

public final class InteractionChoiceLedger {
    private final Map<ServerPlayer, View> views = new WeakHashMap<>();

    public List<InteractionMenu.Choice> publish(ServerPlayer player, Target target, List<InteractionMenu.Choice> choices) {
        var published = choices.stream().map(choice -> choice.withToken(UUID.randomUUID())).toList();
        var tokens = new LinkedHashMap<String, UUID>();
        for (var choice : published) if (choice.available()) tokens.put(choice.id(), choice.token());
        this.views.put(player, new View(target, Map.copyOf(tokens)));
        return published;
    }

    public boolean consume(ServerPlayer player, Target target, String choice, UUID token) {
        var view = this.views.get(player);
        if (token == null || view == null || !view.target.equals(target) || !token.equals(view.tokens.get(choice))) return false;
        this.views.remove(player);
        return true;
    }

    public void clear(ServerPlayer player) { this.views.remove(player); }

    public record Target(int entityId, UUID actorId, UUID sessionId, UUID menuId) {
        public static Target of(InteractionMenu menu) {
            return new Target(menu.session().entityId(), menu.session().actorId(), menu.session().id(), menu.id());
        }
    }

    private record View(Target target, Map<String, UUID> tokens) { }
}
