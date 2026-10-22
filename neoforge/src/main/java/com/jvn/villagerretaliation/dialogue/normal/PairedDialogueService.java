package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.resources.PairedDialogueResources;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot.FamilyMember;
import java.util.*;
import java.util.regex.Pattern;

/** Presentation adapter. Outcomes are captured by the original action, never recomputed here. */
public final class PairedDialogueService {
    private static final Pattern TOKEN = Pattern.compile("<([a-z_]+)>");
    private static final DialogueUsagePolicy USAGE = new DialogueUsagePolicy(0, 0, true, DialogueUsagePolicy.Scope.PLAYER_VILLAGER);
    public static final Set<String> OPTIONS = Set.of("adult_general_question", "adult_family", "adult_relationships",
            "adult_story", "adult_joke", "adult_insult", "baby_family", "baby_story", "baby_joke", "baby_greeting",
            "baby_today_question", "baby_question", "baby_insult", "apologize_for_harm", "cured_recognition",
            "village_event_report", "combat_survival_report", "gear_report", "recruitment_followup", "recruitment_left_behind",
            "village_defense_report", "raid_victory_acknowledgement", "adult_gift_preferences", "baby_gift_preferences",
            "gift_advice_followup", "cartographer_map_report", "story_hint_report", "adult_share_story", "baby_share_story",
            "trade_refresh.special_order.status", "mount.ownership", "weaponsmith_inspect_weapon");
    private PairedDialogueService() { }

    private static final ThreadLocal<UUID> REQUEST = new ThreadLocal<>();
    public static void withRequest(UUID id, Runnable work) {
        try { REQUEST.set(id); work.run(); } finally { REQUEST.remove(); }
    }

    /** Called only after the special-order service has checked the selected real order. */
    public static boolean sendOrder(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.entity.npc.Villager villager,
            String branch, Map<String, String> tokens, String fallback) {
        UUID request = REQUEST.get();
        if (request == null) return false;
        var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
        var option = DialogueOptionDefinition.transmitted("trade_refresh.special_order.status", "Ask about orders", DialogueRequestType.QUESTION, false, 0);
        var original = new VillagerDialogueService.DialogueResult("special_order_status", fallback).withOutcome(branch, tokens);
        var result = attach(context, option, original, DialogueReputationEffect.none(DialogueRequestType.QUESTION), false);
        if (result == original) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendPersonalVillagerChat(player, villager, fallback, original.textSegments(), request);
            return true;
        }
        com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendDialogueExchanges(player, villager, result.exchanges(), true, request);
        return true;
    }

    public record Outcome(String branch, Map<String, String> tokens) {
        public Outcome { tokens = Map.copyOf(tokens); }
    }
    public record Pair(String id, String option, String request, String audience, String reputation,
            String familiarity, String branch, String player, String npc, List<String> owners, List<String> tokens, String group) { }

    public static Optional<String> render(String template, Map<String, String> values) {
        return render(template, values, false);
    }

    public static Optional<String> renderTemplate(String template, Map<String, String> values) {
        return render(template, values, true);
    }

    private static Optional<String> render(String template, Map<String, String> values, boolean words) {
        var matcher = TOKEN.matcher(template);
        var result = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (!safe(value)) return Optional.empty();
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return safe(words ? com.jvn.villagerretaliation.dialogue.DialogueWordPools.templateLiterals(result.toString()) : result.toString())
                ? Optional.of(result.toString()) : Optional.empty();
    }

    public static boolean safe(String value) {
        return value != null && !value.isBlank() && value.length() <= 512 && value.indexOf('<') < 0 && value.indexOf('>') < 0
                && value.indexOf('{') < 0 && value.indexOf('}') < 0 && value.indexOf('\u00a7') < 0
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    public static VillagerDialogueService.DialogueResult attach(DialogueContext context, DialogueOptionDefinition option,
            VillagerDialogueService.DialogueResult result, DialogueReputationEffect effect, boolean replaced) {
        if (!OPTIONS.contains(option.id()) || !option.treeReference().isEmpty() || !option.questAction().isEmpty()
                || !option.itemPayment().isEmpty() || !result.exchanges().isEmpty()) return result;
        boolean joke = option.requestType() == DialogueRequestType.JOKE;
        boolean insult = option.requestType() == DialogueRequestType.INSULT;
        if (replaced && !(joke && Set.of("joke_landed", "joke_missed", "none").contains(effect.reason()))
                && !(insult && Set.of("insult", "first_insult", "insult_child").contains(effect.reason()))) return result;
        var outcomes = result.outcomes().isEmpty()
                ? List.of(new Outcome(joke ? effect.reason() : "", Map.of())) : result.outcomes();
        var selected = new ArrayList<Rendered>();
        for (Outcome outcome : outcomes) {
            var candidates = new ArrayList<Rendered>();
            for (Pair pair : PairedDialogueResources.pairs(context)) {
                if (!pair.option().equals(option.id()) || !pair.request().equals(option.requestType().name().toLowerCase(Locale.ROOT))
                        || !eligible(pair, context, result.lineId(), outcome)) continue;
                var tokens = new HashMap<>(outcome.tokens());
                if (!bind(pair, context, result.lineId(), tokens)) continue;
                var player = renderTemplate(pair.player(), tokens);
                var npc = renderTemplate(pair.npc(), tokens);
                if (player.isPresent() && npc.isPresent()) candidates.add(new Rendered(pair, player.get(), npc.get()));
            }
            if (candidates.isEmpty()) return result;
            selected.add(select(context, candidates));
        }
        // Keep original prose reachable; never discard a real multi-result gift disclosure.
        if (!joke && !insult && context.random().nextInt(4) == 0) return result;
        var usageIds = selected.stream().map(Rendered::usageId).toList();
        selected.replaceAll(value -> new Rendered(value.pair(),
                com.jvn.villagerretaliation.dialogue.DialogueTemplates.resolveTemplate(value.player(), Map.of(), context),
                com.jvn.villagerretaliation.dialogue.DialogueTemplates.resolveTemplate(value.npc(), Map.of(), context)));
        String npc = selected.stream().map(Rendered::npc).collect(java.util.stream.Collectors.joining(" "));
        if (!safe(com.jvn.villagerretaliation.dialogue.DialogueWordPools.templateLiterals(npc))) return result;
        var segments = DialogueTextSegment.plain(npc, DialogueTextEffects.NONE);
        var first = selected.getFirst();
        usageIds.forEach(id -> DialogueUsageService.record(context, id, USAGE));
        var speech = new DialogueSpeech(context.player().getUUID(), context.player().getId(), context.player().getName().getString(),
                DialogueTextSegment.plain(first.player(), DialogueTextEffects.NONE));
        return new VillagerDialogueService.DialogueResult(result.lineId(), npc, DialogueTextEffects.NONE, segments,
                List.of(speech, DialogueSpeech.npc(context.villager(), segments, null)), result.outcomes());
    }

    private static Rendered select(DialogueContext context, List<Rendered> candidates) {
        Set<String> ids = candidates.stream().map(Rendered::usageId).collect(java.util.stream.Collectors.toSet());
        var last = DialogueUsageService.lastUsedAmong(context, ids, USAGE);
        var fresh = candidates.stream().filter(v -> ids.size() == 1 || last.isEmpty() || !v.usageId().equals(last.get())).toList();
        int count = fresh.stream().mapToInt(v -> DialogueUsageService.usage(context, v.usageId(), USAGE).count()).min().orElse(0);
        var balanced = fresh.stream().filter(v -> DialogueUsageService.usage(context, v.usageId(), USAGE).count() == count).toList();
        return balanced.get(context.random().nextInt(balanced.size()));
    }

    private static boolean eligible(Pair p, DialogueContext c, String owner, Outcome o) {
        if (!p.reputation().equals(c.reputationLevel().name().toLowerCase(Locale.ROOT))
                || p.audience().equals("adult") && c.villager().isBaby() || p.audience().equals("child") && !c.villager().isBaby()
                || p.familiarity().equals("first") && !c.firstConversation() || p.familiarity().equals("familiar") && c.firstConversation()) return false;
        if(p.group().equals("social-child") && p.owners().stream().noneMatch(id -> owner.equals(id) || owner.startsWith(id+"#"))) return false;
        if (!o.branch().isEmpty()) return p.branch().equals(o.branch());
        if (p.group().equals("dynamic-results")) return false;
        if (p.group().equals("guarded-events")) return switch (p.branch()) {
            case "apology_adult", "concern_adult" -> !c.villager().isBaby();
            case "apology_child", "concern_child" -> c.villager().isBaby();
            case "gear_used" -> c.hasUnreportedGearReportUsedInCombat();
            case "gear_unused" -> c.hasUnreportedGearReportUnusedInCombat();
            case "trip_betrayed" -> c.hasRecruitmentFollowupScenario("betrayed");
            case "trip_injured" -> c.hasRecruitmentFollowupScenario("injured");
            case "trip_safe" -> c.hasRecruitmentFollowupScenario("safe");
            case "separation" -> c.hasRecruitmentFollowupScenario("left_behind");
            default -> true; // Original option eligibility supplied the pending report guard.
        };
        if (p.option().equals("weaponsmith_inspect_weapon")) return owner.startsWith("weaponsmith_inspect_weapon_" + p.branch().replace("durability_", ""));
        if (p.option().equals("mount.ownership")) return owner.startsWith("mount_ownership_boss");
        if (p.option().equals("trade_refresh.special_order.status")) return false;
        if (p.option().endsWith("_joke")) return false;
        if (p.option().endsWith("_family") || p.option().equals("adult_relationships")) {
            if (p.branch().startsWith("privacy_")) return true;
            if (p.branch().equals("factual_crush")) return owner.startsWith("adult_relationship_crush") && c.hasKnownCrush();
            return true; // Exact disclosure-owner and same-record identity checked by bind below.
        }
        return p.owners().stream().anyMatch(id -> owner.equals(id) || owner.startsWith(id + "#"));
    }

    private static boolean bind(Pair p, DialogueContext c, String owner, Map<String, String> tokens) {
        for (String token : p.tokens()) {
            if (tokens.containsKey(token)) continue;
            String value = null;
            if (token.equals("mount") && c.villager().getVehicle() != null) value = c.villager().getVehicle().getDisplayName().getString();
            var held = c.player().getMainHandItem();
            if (token.equals("held_item") && !held.isEmpty()) value = held.getHoverName().getString();
            if (token.equals("gear_kind") && c.hasUnreportedGearReport()) value = c.gearReportKind();
            if (token.equals("held_item_durability") && held.isDamageableItem()) value = Integer.toString(held.getMaxDamage() - held.getDamageValue());
            if (token.equals("held_item_max_durability") && held.isDamageableItem()) value = Integer.toString(held.getMaxDamage());
            if (p.option().endsWith("_family")) {
                String ownerPart = switch (token) { case "extended_relative" -> "extended"; case "deceased_family" -> "deceased"; default -> token; };
                if (!owner.startsWith(p.option() + "_" + ownerPart)) return false;
                var f = c.familyTree();
                List<FamilyMember> members = switch (token) {
                    case "parent" -> f.parents(); case "child" -> f.children(); case "sibling" -> f.siblings(); case "cousin" -> f.cousins();
                    case "grandparent" -> f.ancestry().stream().filter(g -> g.generation() == 2).flatMap(g -> g.ancestors().stream()).toList();
                    case "descendant" -> java.util.stream.Stream.concat(f.children().stream(), f.descendants().stream().flatMap(g -> g.descendants().stream())).toList();
                    case "extended_relative", "deceased_family" -> java.util.stream.Stream.of(f.parents(), f.children(), f.siblings(), f.spouses(), f.auntsUncles(), f.cousins(), f.niecesNephews(),
                            f.ancestry().stream().flatMap(g -> g.ancestors().stream()).toList(), f.descendants().stream().flatMap(g -> g.descendants().stream()).toList()).flatMap(List::stream)
                            .filter(m -> !token.equals("deceased_family") || !m.alive()).toList();
                    default -> List.of();
                };
                value = members.stream().map(FamilyMember::name).filter(PairedDialogueService::safe).findFirst().orElse(null);
            }
            if (p.option().equals("adult_relationships")) {
                var stage = switch (token) {
                    case "dating_partner" -> "DATING"; case "fiance" -> "ENGAGED"; case "romantic_spouse" -> "MARRIED";
                    case "ex_partner" -> "SEPARATED"; case "late_partner" -> "WIDOWED"; default -> "";
                };
                String ownerPart = switch (token) { case "partner" -> "current"; case "dating_partner" -> "dating";
                    case "fiance" -> "engaged"; case "romantic_spouse" -> "married"; case "ex_partner" -> "separated"; case "late_partner" -> "widowed"; default -> "unknown"; };
                if (!owner.startsWith("adult_relationship_" + ownerPart)) return false;
                var stages = stage.isEmpty() ? Set.of(com.jvn.villagerretaliation.social.VillagerRelationshipStage.DATING,
                        com.jvn.villagerretaliation.social.VillagerRelationshipStage.ENGAGED, com.jvn.villagerretaliation.social.VillagerRelationshipStage.MARRIED)
                        : Set.of(com.jvn.villagerretaliation.social.VillagerRelationshipStage.valueOf(stage));
                value = com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get(c.level()).adultPartnerName(c.villager().getUUID(), stages).orElse(null);
            }
            if (!safe(value)) return false;
            tokens.put(token, value);
        }
        return true;
    }

    private record Rendered(Pair pair, String player, String npc) {
        String usageId() { return "paired_dialogue:" + UUID.nameUUIDFromBytes((player + "\n" + npc).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
}
