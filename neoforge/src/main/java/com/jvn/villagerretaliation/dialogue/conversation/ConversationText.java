package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import net.minecraft.server.level.ServerPlayer;

public final class ConversationText {
    private ConversationText() { }

    public static String text(ServerPlayer viewer, ConversationSession.Line line) {
        return resolve(viewer, line.textKey(), line.fallback(), line.replacements(), line.localized());
    }

    static java.util.Map<String, String> prepare(ServerPlayer viewer, ConversationSession session, String entry,
            String key, java.util.Map<String, String> values) {
        var catalog = QuestContentCatalogs.current(viewer.server).localization();
        var locales = new java.util.LinkedHashSet<>(catalog.locales());
        locales.addAll(com.jvn.villagerretaliation.dialogue.resources.DialogueWordPoolResources.locales(viewer.server));
        locales.add("en_us");
        locales.add(com.jvn.villagerretaliation.util.VillagerLocale.normalize(viewer.clientInformation().language()));
        var templates = new java.util.LinkedHashMap<String, String>();
        for (String locale : locales) catalog.plainText(locale, key).ifPresent(text -> templates.put(locale, text));
        return prepare(viewer.server, session, entry + "/" + key, templates, values);
    }

    static java.util.Map<String, String> prepare(net.minecraft.server.MinecraftServer server, ConversationSession session,
            String entry, java.util.Map<String, String> templates, java.util.Map<String, String> values) {
        var result = new java.util.LinkedHashMap<String, String>();
        values.forEach((key, value) -> { if (!key.startsWith("_vr_word_")) result.put(key, value); });
        String cachePrefix = "_vr_word_prepared/" + session.visit + "/" + entry + "/";
        if (templates.values().stream().noneMatch(text -> text.contains("{word"))
                && session.replacements.keySet().stream().noneMatch(key -> key.startsWith(cachePrefix))) return java.util.Map.copyOf(result);
        long seed = session.identity.id().getMostSignificantBits() ^ session.identity.id().getLeastSignificantBits()
                ^ session.visit ^ entry.hashCode();
        for (var template : templates.entrySet()) {
            String cacheKey = cachePrefix + template.getKey();
            String prepared = session.replacements.computeIfAbsent(cacheKey, ignored ->
                    VillagerDialogueResources.resolveTemplate(template.getValue(), result, server, template.getKey(),
                            net.minecraft.util.RandomSource.create(seed)));
            result.put("_vr_word_text/" + template.getKey(), prepared);
        }
        return java.util.Map.copyOf(result);
    }

    static String render(ServerPlayer viewer, String template, java.util.Map<String, String> replacements) {
        String locale = com.jvn.villagerretaliation.util.VillagerLocale.normalize(viewer.clientInformation().language());
        String prepared = replacements.getOrDefault("_vr_word_text/" + locale, replacements.get("_vr_word_text/en_us"));
        return prepared != null ? prepared : VillagerDialogueResources.resolveTemplate(template, replacements, viewer.server,
                locale, net.minecraft.util.RandomSource.create(template.hashCode()));
    }

    public static String resolve(ServerPlayer viewer, String key, String fallback, java.util.Map<String, String> replacements, boolean localized) {
        if (!localized) return fallback;
        return QuestContentCatalogs.current(viewer.server).localization()
                .plainText(viewer.clientInformation().language(), key)
                .map(template -> render(viewer, template, replacements))
                .orElse(fallback);
    }
}
