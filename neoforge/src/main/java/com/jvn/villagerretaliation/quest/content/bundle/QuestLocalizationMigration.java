package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministically materializes legacy inline fallbacks without arbitrary first-wins behavior. */
public final class QuestLocalizationMigration {
    private QuestLocalizationMigration() {
    }

    public static Result materialize(
            String localizationPrefix,
            Map<String, JsonElement> existingEnglish,
            Iterable<Claim> claims) {
        Map<String, JsonElement> materialized = new LinkedHashMap<>(copyPayloads(existingEnglish));
        Map<String, String> assignments = new LinkedHashMap<>();
        Map<String, List<Claim>> byMessage = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (claims != null) {
            for (Claim claim : claims) {
                if (claim == null) {
                    continue;
                }
                String messageId;
                try {
                    String reference = claim.generated()
                            ? QuestDeterministicLocaleKeys.relativeKey(claim.address())
                            : claim.explicitKey();
                    messageId = new LocalizedReference(reference).expand(localizationPrefix);
                } catch (IllegalArgumentException exception) {
                    diagnostics.add(new Diagnostic(
                            "",
                            List.of(claim.source()),
                            "cannot assign locale key: " + exception.getMessage()));
                    continue;
                }
                String previous = assignments.putIfAbsent(claim.source(), messageId);
                if (previous != null && !previous.equals(messageId)) {
                    diagnostics.add(new Diagnostic(
                            messageId,
                            List.of(claim.source()),
                            "one source claimed multiple locale keys: " + previous + " and " + messageId));
                    continue;
                }
                byMessage.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(claim);
            }
        }

        for (Map.Entry<String, List<Claim>> entry : byMessage.entrySet()) {
            String messageId = entry.getKey();
            List<Claim> messageClaims = entry.getValue();
            Set<QuestDeterministicLocaleKeys.Address> generatedOwners = new LinkedHashSet<>();
            messageClaims.stream().filter(Claim::generated).map(Claim::address).forEach(generatedOwners::add);
            if (generatedOwners.size() > 1) {
                diagnostics.add(new Diagnostic(
                        messageId,
                        sources(messageClaims),
                        "generated locale key collision for " + messageId));
                continue;
            }
            if (materialized.containsKey(messageId)) {
                continue;
            }

            List<Claim> fallbacks = messageClaims.stream()
                    .filter(claim -> claim.inlineFallback() != null)
                    .toList();
            if (fallbacks.isEmpty()) {
                diagnostics.add(new Diagnostic(
                        messageId,
                        sources(messageClaims),
                        "locale key has no existing English value or inline fallback"));
                continue;
            }
            JsonElement agreed = fallbacks.getFirst().inlineFallback();
            boolean conflict = fallbacks.stream()
                    .map(Claim::inlineFallback)
                    .anyMatch(payload -> !agreed.equals(payload));
            if (conflict) {
                diagnostics.add(new Diagnostic(
                        messageId,
                        sources(messageClaims),
                        "distinct inline fallbacks claim " + messageId));
                continue;
            }
            materialized.put(messageId, agreed.deepCopy());
        }

        return new Result(materialized, assignments, diagnostics);
    }

    private static List<String> sources(List<Claim> claims) {
        return claims.stream().map(Claim::source).distinct().sorted().toList();
    }

    private static Map<String, JsonElement> copyPayloads(Map<String, JsonElement> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, JsonElement> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isJsonNull()) {
                copy.put(key, value.deepCopy());
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    public record Claim(
            QuestDeterministicLocaleKeys.Address address,
            String explicitKey,
            JsonElement inlineFallback,
            String source) {
        public Claim {
            if (address == null) {
                throw new IllegalArgumentException("stable localization address is required");
            }
            explicitKey = explicitKey == null ? "" : explicitKey.trim();
            inlineFallback = inlineFallback == null ? null : inlineFallback.deepCopy();
            source = source == null || source.isBlank() ? address.toString() : source;
        }

        public boolean generated() {
            return this.explicitKey.isBlank();
        }

        public JsonElement inlineFallback() {
            return this.inlineFallback == null ? null : this.inlineFallback.deepCopy();
        }
    }

    public record Diagnostic(String messageId, List<String> sources, String message) {
        public Diagnostic {
            messageId = messageId == null ? "" : messageId;
            sources = sources == null ? List.of() : List.copyOf(sources);
            message = message == null ? "" : message;
        }
    }

    public record Result(
            Map<String, JsonElement> english,
            Map<String, String> assignments,
            List<Diagnostic> diagnostics) {
        public Result {
            english = copyPayloads(english);
            assignments = assignments == null ? Map.of() : Map.copyOf(assignments);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public Map<String, JsonElement> english() {
            return copyPayloads(this.english);
        }

        public boolean valid() {
            return this.diagnostics.isEmpty();
        }
    }
}
