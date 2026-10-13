package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds an authored player opener only to an explicitly certified, unchanged NPC reply. */
public final class PairedGreetingService {
    private static final DialogueUsagePolicy USAGE = new DialogueUsagePolicy(
            0L, 0, true, DialogueUsagePolicy.Scope.PLAYER_VILLAGER);
    private static final Pattern TOKEN = Pattern.compile("<([a-z_]+)>");
    private static final Set<String> TOKENS = Set.of("time", "day_phase", "time_greeting", "villager", "player");
    private static final Map<String, DialogueRequestType> SUPPORTED_OPTIONS = Map.of(
            "adult_greeting", DialogueRequestType.GREETING,
            "adult_question", DialogueRequestType.QUESTION);

    private PairedGreetingService() { }

    /** Explicit menu-entry opt-in; a shared request type alone never enables pairing. */
    public static boolean supportsOptionId(String optionId) {
        return optionId != null && (SUPPORTED_OPTIONS.containsKey(optionId) || PairedDialogueService.OPTIONS.contains(optionId) || optionId.startsWith("trade_refresh.special_order.status."));
    }

    public static VillagerDialogueService.DialogueResult attach(
            DialogueContext context, DialogueOptionDefinition option,
            VillagerDialogueService.DialogueResult result, boolean responseTextReplaced) {
        if (!eligible(context, option, result, responseTextReplaced)) return result;
        return attach(context, option, result, false, PairedGreetingResources.pairs(context));
    }

    static VillagerDialogueService.DialogueResult attach(
            DialogueContext context, DialogueOptionDefinition option,
            VillagerDialogueService.DialogueResult result, boolean responseTextReplaced, List<Pair> pairs) {
        if (!eligible(context, option, result, responseTextReplaced)) return result;
        List<RenderedPair> candidates = new ArrayList<>();
        for (Pair pair : pairs) {
            if (!pair.optionId().equals(option.id()) || !pair.npcVariantId().equals(result.lineId())
                    || !DialogueTextSegment.plainText(DialogueTextSegment.parse(
                            pair.npcText(), DialogueTextEffects.NONE)).equals(result.text())
                    || pair.reputationLevel() != context.reputationLevel()
                    || pair.firstConversation() != context.firstConversation()
                    || pair.weight() <= 0) continue;
            renderPlayer(pair.player(), context).ifPresent(text -> candidates.add(new RenderedPair(pair, text)));
        }
        if (candidates.isEmpty()) return result;

        // Keep the NPC's existing recent-ID and request-use bookkeeping intact.
        // The same saved usage ledger tracks the opener separately, per player/villager.
        Set<String> ids = candidates.stream().map(RenderedPair::usageId)
                .collect(java.util.stream.Collectors.toSet());
        Optional<String> previous = DialogueUsageService.lastUsedAmong(context, ids, USAGE);
        List<RenderedPair> fresh = ids.size() > 1 && previous.isPresent()
                ? candidates.stream().filter(candidate -> !candidate.usageId().equals(previous.get())).toList()
                : candidates;
        int minimumUses = fresh.stream().mapToInt(candidate ->
                DialogueUsageService.usage(context, candidate.usageId(), USAGE).count()).min().orElse(0);
        List<RenderedPair> balanced = fresh.stream().filter(candidate ->
                DialogueUsageService.usage(context, candidate.usageId(), USAGE).count() == minimumUses).toList();
        long totalWeight = balanced.stream().mapToLong(candidate -> candidate.pair().weight()).sum();
        long roll = totalWeight <= Integer.MAX_VALUE ? context.random().nextInt((int) totalWeight)
                : Long.remainderUnsigned(context.random().nextLong(), totalWeight);
        RenderedPair selected = balanced.getLast();
        for (RenderedPair candidate : balanced) {
            roll -= candidate.pair().weight();
            if (roll < 0) {
                selected = candidate;
                break;
            }
        }
        DialogueUsageService.record(context, selected.usageId(), USAGE);
        // Plain segments deliberately avoid interpreting names as text-effect markup.
        DialogueSpeech player = new DialogueSpeech(context.player().getUUID(), context.player().getId(),
                context.player().getName().getString(), DialogueTextSegment.plain(selected.text(), DialogueTextEffects.NONE));
        DialogueSpeech npc = DialogueSpeech.npc(context.villager(), result.textSegments(), null);
        return new VillagerDialogueService.DialogueResult(result.lineId(), result.text(), result.textEffects(),
                result.textSegments(), List.of(player, npc));
    }

    private static boolean eligible(DialogueContext context, DialogueOptionDefinition option,
            VillagerDialogueService.DialogueResult result, boolean responseTextReplaced) {
        return context != null && context.villager() != null && !context.villager().isBaby()
                && option != null && option.requestType() == SUPPORTED_OPTIONS.get(option.id())
                && option.itemPayment().isEmpty() && option.questAction().isEmpty() && option.treeReference().isEmpty()
                && !responseTextReplaced && result != null && !result.text().isBlank()
                && result.exchanges().isEmpty();
    }

    public static boolean validTemplate(String template) {
        if (template == null || template.isBlank() || template.length() > 512
                || template.codePoints().anyMatch(Character::isISOControl)) return false;
        Matcher matcher = TOKEN.matcher(template);
        while (matcher.find()) if (!TOKENS.contains(matcher.group(1))) return false;
        String literal = matcher.replaceAll("");
        return literal.indexOf('<') < 0 && literal.indexOf('>') < 0
                && literal.indexOf('{') < 0 && literal.indexOf('}') < 0;
    }

    static Optional<String> renderPlayer(String template, DialogueContext context) {
        if (!validTemplate(template)) return Optional.empty();
        DialogueContext.TimeOfDay phase = context.timeOfDay();
        Map<String, String> times = phase == null ? Map.of() : Map.of(
                "time", phase.name().toLowerCase(Locale.ROOT),
                "day_phase", phase == DialogueContext.TimeOfDay.NIGHT ? "tonight"
                        : "this " + phase.name().toLowerCase(Locale.ROOT),
                "time_greeting", switch (phase) {
                    case MORNING -> "Good morning";
                    case AFTERNOON -> "Good afternoon";
                    case EVENING -> "Good evening";
                    case NIGHT -> "Hello";
                });
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String value = times.get(token);
            if (token.equals("villager")) value = knownVillagerName(context).orElse(null);
            if (token.equals("player")) value = context.firstConversation() ? null
                    : safeName(context.player().getName().getString()).orElse(null);
            if (value == null) return Optional.empty();
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        String text = rendered.toString();
        return text.isBlank() || text.length() > 512 ? Optional.empty() : Optional.of(text);
    }

    private static Optional<String> knownVillagerName(DialogueContext context) {
        if (context.firstConversation() || context.villager() == null) return Optional.empty();
        // Do not generate a name or substitute the profession label to satisfy a template.
        String name = context.villager().getCustomName() == null
                ? context.villager().getPersistentData().getString(VillagerPresetNameRegistry.PERSISTENT_NAME_KEY)
                : context.villager().getCustomName().getString();
        if (name.startsWith("villagerretaliation.villager_name.")) return Optional.empty();
        return safeName(name);
    }

    private static Optional<String> safeName(String value) {
        if (value == null) return Optional.empty();
        String name = value.trim();
        if (name.isBlank() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl)
                || name.indexOf('<') >= 0 || name.indexOf('>') >= 0
                || name.indexOf('{') >= 0 || name.indexOf('}') >= 0 || name.indexOf('\u00a7') >= 0) return Optional.empty();
        return Optional.of(name);
    }

    public record Pair(String id, String ownerId, String npcVariantId, String npcText,
            VillagerReputationLevel reputationLevel, boolean firstConversation, String player, int weight, String optionId) {
        public Pair(String id, String ownerId, String npcVariantId, String npcText,
                VillagerReputationLevel reputationLevel, boolean firstConversation, String player, int weight) {
            this(id, ownerId, npcVariantId, npcText, reputationLevel, firstConversation, player, weight, "adult_greeting");
        }

        public Pair {
            if (id == null || id.isBlank() || ownerId == null || ownerId.isBlank()
                    || npcVariantId == null || !(npcVariantId.equals(ownerId) || npcVariantId.startsWith(ownerId + "#"))
                    || npcText == null || npcText.isBlank() || reputationLevel == null
                    || !validTemplate(player) || weight < 1 || weight > 10_000 || !SUPPORTED_OPTIONS.containsKey(optionId)) {
                throw new IllegalArgumentException("Invalid exact dialogue pair binding");
            }
        }

    }

    /** The same spoken wording shares history even when it is bound to another NPC variant. */
    static String playerUsageId(String text) {
        String normalized = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return "paired_greeting:player:" + java.util.UUID.nameUUIDFromBytes(
                normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record RenderedPair(Pair pair, String text) {
        String usageId() { return playerUsageId(text); }
    }
}
