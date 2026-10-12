package com.jvn.villagerretaliation.healing;

import com.jvn.villagerretaliation.combat.ClericHealingService;
import com.jvn.villagerretaliation.combat.ClericHealingService.Offer;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.InteractionRequestValidator;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.VillagerHealingRequestPayload;
import com.jvn.villagerretaliation.network.VillagerHealingStatePayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClericHealingRequestHandler {
    private static final Map<UUID, Quote> QUOTES = new HashMap<>();
    private static final long QUOTE_LIFETIME_TICKS = 1200L;

    private ClericHealingRequestHandler() {}

    public static void handle(ServerPlayer player, VillagerHealingRequestPayload request) {
        process(player, request).ifPresent(state -> PacketDistributor.sendToPlayer(player, state));
    }

    static Optional<VillagerHealingStatePayload> process(ServerPlayer player, VillagerHealingRequestPayload request) {
        if (request.action() == VillagerHealingRequestPayload.Action.CLOSE) {
            QUOTES.remove(player.getUUID());
            return Optional.empty();
        }
        var target = InteractionRequestValidator.requireRoutineChatConversation(player, request.entityId());
        if (target.isEmpty()) {
            QUOTES.remove(player.getUUID());
            return Optional.empty();
        }
        Villager villager = target.get().villager();
        long now = ClericHealingService.gameTime(villager);
        UUID sessionId = VillagerConversationService.sessionId(player, villager);
        QUOTES.values().removeIf(quote -> quote.expiresAt() <= now);
        Offer current = ClericHealingService.offer(villager, player);
        String resultKey = "";
        if (request.action() == VillagerHealingRequestPayload.Action.CONFIRM) {
            Quote quote = QUOTES.get(player.getUUID());
            if (quote == null || !quote.sessionId().equals(sessionId) || !quote.villagerId().equals(villager.getUUID())
                    || !quote.id().equals(request.quoteId()) || !quote.offer().sameTerms(current)) {
                resultKey = "changed";
            } else if (current.available() && ServerboundRequestLimiter.tryAcquire(
                    player, VillagerHealingRequestPayload.TYPE.id(), 10L)) {
                QUOTES.remove(player.getUUID());
                resultKey = ClericHealingService.tryRequestedHeal(villager, player, quote.offer()) ? "success" : "unavailable";
                current = ClericHealingService.offer(villager, player);
            }
        }
        Quote quote = QUOTES.get(player.getUUID());
        if (quote == null || !quote.sessionId().equals(sessionId) || !quote.villagerId().equals(villager.getUUID()) || !quote.offer().sameTerms(current)) {
            quote = new Quote(UUID.randomUUID(), sessionId, villager.getUUID(), current, now + QUOTE_LIFETIME_TICKS);
            QUOTES.put(player.getUUID(), quote);
        }
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
        var currency = VillagerCurrencyResources.text(player.getServer());
        Map<String, String> replacements = Map.of("cost", Integer.toString(current.price()),
                "currency", current.price() == 1 ? currency.name() : currency.pluralName(),
                "time_remaining", Long.toString((current.cooldownTicks() + 19L) / 20L));
        String suffix = current.reason() == ClericHealingService.Reason.READY
                ? current.source().name().toLowerCase(Locale.ROOT) : current.reason().name().toLowerCase(Locale.ROOT);
        if (current.reason() == ClericHealingService.Reason.COOLDOWN && current.source() == ClericHealingService.Source.NONE) {
            suffix = "supplies_depleted";
        }
        String dialogue = VillagerDialogueResources.message(context, "interaction.healing." + suffix, replacements).orElse("");
        if (!resultKey.isEmpty()) {
            String result = VillagerDialogueResources.message(context, "interaction.healing." + resultKey, replacements).orElse("");
            dialogue = result + " " + dialogue;
        }
        String label = VillagerDialogueResources.message(context,
                "interaction.healing.option." + current.source().name().toLowerCase(Locale.ROOT), replacements).orElse("");
        return Optional.of(new VillagerHealingStatePayload(villager.getId(), villager.getUUID(), quote.id(), current, dialogue.trim(), label));
    }

    public static void clear(UUID playerId) {
        QUOTES.remove(playerId);
    }

    public static void clear() {
        QUOTES.clear();
    }

    private record Quote(UUID id, UUID sessionId, UUID villagerId, Offer offer, long expiresAt) {}
}
