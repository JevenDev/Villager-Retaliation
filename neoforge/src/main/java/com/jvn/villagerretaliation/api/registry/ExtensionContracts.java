package com.jvn.villagerretaliation.api.registry;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

public final class ExtensionContracts {
    private ExtensionContracts() {
    }

    @FunctionalInterface
    public interface Parser<T> {
        T parse(JsonObject object) throws IllegalArgumentException;
    }

    @FunctionalInterface
    public interface Validator<T> {
        List<String> validate(T value);
    }

    @FunctionalInterface
    public interface RuntimeImplementation<T, C, R> {
        R apply(T value, C context);
    }

    @FunctionalInterface
    public interface DebugFormatter<T> {
        String format(T value);
    }

    public enum RecoveryMode {
        NATURALLY_IDEMPOTENT,
        RECEIPT_REQUIRED,
        WORLD_RECONCILED,
        UNSAFE_BLOCK
    }

    public enum ClientSync {
        NONE,
        OWNER,
        PARTICIPANTS,
        TRACKING_PLAYERS
    }

    public record ToolingMetadata(
            String title,
            String description,
            Map<String, Object> schema,
            boolean browserAvailable
    ) {
        public ToolingMetadata {
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            schema = schema == null ? Map.of() : Map.copyOf(schema);
        }

        public static ToolingMetadata runtimeOnly(String title, String description) {
            return new ToolingMetadata(title, description, Map.of(), false);
        }
    }
}
