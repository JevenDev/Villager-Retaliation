package com.jvn.villagerretaliation.scene.actor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

public record SceneActorDeclaration(
        String alias,
        ResourceLocation actorType,
        Set<ResourceLocation> requiredCapabilities,
        boolean required,
        BindingSource bindingSource,
        String bindingReference,
        ReplacementPolicy replacementPolicy,
        MissingActorPolicy missingActorPolicy,
        LethalDamagePolicy lethalDamagePolicy,
        DeathPolicy deathPolicy,
        Map<String, String> filters,
        long timeoutTicks
) {
    private static final Pattern ALIAS = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");

    public SceneActorDeclaration {
        alias = alias == null ? "" : alias.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALIAS.matcher(alias).matches()) {
            throw new IllegalArgumentException("actor alias must match " + ALIAS.pattern() + ": " + alias);
        }
        if (actorType == null) {
            throw new IllegalArgumentException("actor type must be a namespaced id for " + alias);
        }
        requiredCapabilities = immutableIds(requiredCapabilities);
        bindingSource = bindingSource == null ? BindingSource.UNBOUND : bindingSource;
        bindingReference = bindingReference == null ? "" : bindingReference.trim();
        replacementPolicy = replacementPolicy == null ? ReplacementPolicy.FIXED : replacementPolicy;
        missingActorPolicy = missingActorPolicy == null
                ? (required ? MissingActorPolicy.BLOCK : MissingActorPolicy.SKIP)
                : missingActorPolicy;
        lethalDamagePolicy = lethalDamagePolicy == null ? LethalDamagePolicy.NORMAL : lethalDamagePolicy;
        deathPolicy = deathPolicy == null ? DeathPolicy.APPLY_MISSING_POLICY : deathPolicy;
        filters = immutableStrings(filters);
        timeoutTicks = Math.max(0L, timeoutTicks);
    }

    public SceneActorDeclaration(
            String alias,
            ResourceLocation actorType,
            Set<ResourceLocation> requiredCapabilities,
            boolean required,
            BindingSource bindingSource,
            String bindingReference,
            ReplacementPolicy replacementPolicy,
            MissingActorPolicy missingActorPolicy,
            DeathPolicy deathPolicy,
            Map<String, String> filters,
            long timeoutTicks) {
        this(alias, actorType, requiredCapabilities, required, bindingSource, bindingReference, replacementPolicy,
                missingActorPolicy, LethalDamagePolicy.NORMAL, deathPolicy, filters, timeoutTicks);
    }

    private static Set<ResourceLocation> immutableIds(Set<ResourceLocation> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<ResourceLocation> copy = new LinkedHashSet<>();
        for (ResourceLocation value : values) {
            if (value == null) throw new IllegalArgumentException("actor capability must not be null");
            copy.add(value);
        }
        return Set.copyOf(copy);
    }

    private static Map<String, String> immutableStrings(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) copy.put(key, value);
        });
        return Map.copyOf(copy);
    }

    public enum BindingSource {
        OWNER_PLAYER,
        PARTY_MEMBER,
        QUEST_PROVIDER,
        UUID,
        MARKER,
        ENCOUNTER,
        OWNED_SPAWN,
        UNBOUND
    }

    public enum ReplacementPolicy {
        FIXED,
        OPERATOR_REBINDABLE,
        COMPATIBLE_REPLACEMENT,
        RESPAWN_IF_OWNED,
        OPTIONAL
    }

    public enum MissingActorPolicy {
        BLOCK,
        FAIL,
        SKIP,
        WAIT_UNTIL_TIMEOUT
    }

    public enum DeathPolicy {
        FAIL,
        BLOCK,
        APPLY_MISSING_POLICY,
        RESPAWN_IF_OWNED,
        CONTINUE_WITH_SNAPSHOT
    }

    public enum LethalDamagePolicy {
        NORMAL,
        DOWNED
    }
}
