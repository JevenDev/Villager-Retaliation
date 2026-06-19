package com.jvn.villagerretaliation.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionCapability;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.provider.QuestProviderRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class QuestRegistryMetadata {
    public static final Path TOOLING_METADATA_PATH =
            Path.of("tools", "datapack-builder", "quest-registry-metadata.json");
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private QuestRegistryMetadata() {
    }

    public static JsonObject export() {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", 1);
        root.addProperty("mod_id", VillagerRetaliation.MOD_ID);
        root.addProperty("generated_by", "com.jvn.villagerretaliation.quest.QuestRegistryMetadata");

        JsonObject registries = new JsonObject();
        registries.add("conditions", conditionDescriptors());
        registries.add("actions", actionDescriptors());
        registries.add("objectives", objectiveDescriptors());
        registries.add("triggers", triggerDescriptors());
        registries.add("providers", providerDescriptors());
        root.add("registries", registries);

        JsonObject fragments = new JsonObject();
        fragments.add("condition_type", stringEnum(conditionTypeValues()));
        fragments.add("action_type", stringEnum(actionTypeValues()));
        fragments.add("objective_type", stringEnum(objectiveTypeValues()));
        fragments.add("trigger_event", stringEnum(triggerEventValues()));
        fragments.add("provider_type", stringEnum(providerTypeValues()));
        root.add("schema_fragments", fragments);
        return root;
    }

    public static String exportJson() {
        return GSON.toJson(export()) + System.lineSeparator();
    }

    public static void write(Path output) throws IOException {
        Path target = output == null ? TOOLING_METADATA_PATH : output;
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, exportJson(), StandardCharsets.UTF_8);
    }

    private static JsonArray conditionDescriptors() {
        JsonArray conditions = new JsonArray();
        for (DialogueCondition.ConditionTypeDescriptor descriptor : DialogueCondition.descriptors()) {
            JsonObject entry = descriptorBase(descriptor.id(), descriptor.aliases());
            entry.add("capabilities", enumNames(descriptor.capabilities()));
            entry.addProperty("implementation", descriptor.implementationType().getSimpleName());
            conditions.add(entry);
        }
        return conditions;
    }

    private static JsonArray actionDescriptors() {
        JsonArray actions = new JsonArray();
        for (VillagerActionRegistry.ActionTypeDescriptor descriptor : VillagerActionRegistry.descriptors()) {
            JsonObject entry = descriptorBase(descriptor.id(), descriptor.aliases());
            entry.addProperty("kind", descriptor.kind().name().toLowerCase(java.util.Locale.ROOT));
            entry.add("capabilities", enumNames(descriptor.capabilities()));
            actions.add(entry);
        }
        return actions;
    }

    private static JsonArray objectiveDescriptors() {
        JsonArray objectives = new JsonArray();
        for (QuestObjectiveTypeDescriptor<?> descriptor : QuestObjectiveRegistry.descriptors()) {
            JsonObject entry = descriptorBase(descriptor.id(), descriptor.aliases());
            entry.addProperty("type", descriptor.objectiveType().name().toLowerCase(java.util.Locale.ROOT));
            entry.add("requirements", enumNames(descriptor.requirements()));
            entry.addProperty("has_runtime", descriptor.hasImplementation());
            if (descriptor.implementation() != null) {
                entry.addProperty("implementation", descriptor.implementation().getClass().getSimpleName());
            }
            objectives.add(entry);
        }
        return objectives;
    }

    private static JsonArray triggerDescriptors() {
        JsonArray triggers = new JsonArray();
        for (QuestTriggerEventDescriptor descriptor : QuestTriggerRegistry.descriptors()) {
            JsonObject entry = descriptorBase(descriptor.id(), descriptor.aliases());
            entry.addProperty("event", descriptor.event().name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("continuous", descriptor.continuous());
            entry.addProperty("default_cooldown_ticks", descriptor.defaultCooldownTicks());
            triggers.add(entry);
        }
        return triggers;
    }

    private static JsonArray providerDescriptors() {
        JsonArray providers = new JsonArray();
        for (var descriptor : QuestProviderRegistry.descriptors()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", descriptor.id().toString());
            entry.add("capabilities", resourceLocationNames(descriptor.capabilities()));
            providers.add(entry);
        }
        return providers;
    }

    private static JsonObject descriptorBase(String id, Set<String> aliases) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.add("aliases", strings(aliases));
        return entry;
    }

    private static JsonObject stringEnum(Collection<String> values) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.add("enum", strings(values));
        return schema;
    }

    private static Collection<String> conditionTypeValues() {
        return DialogueCondition.descriptors().stream()
                .flatMap(descriptor -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(descriptor.id()),
                        descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> actionTypeValues() {
        return VillagerActionRegistry.descriptors().stream()
                .flatMap(descriptor -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(descriptor.id()),
                        descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> objectiveTypeValues() {
        return QuestObjectiveRegistry.descriptors().stream()
                .flatMap(descriptor -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(descriptor.id()),
                        descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> triggerEventValues() {
        return QuestTriggerRegistry.descriptors().stream()
                .flatMap(descriptor -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(descriptor.id()),
                        descriptor.aliases().stream()))
                .sorted()
                .toList();
    }

    private static Collection<String> providerTypeValues() {
        return QuestProviderRegistry.descriptors().stream()
                .map(descriptor -> descriptor.id().toString())
                .sorted()
                .toList();
    }

    private static JsonArray enumNames(Collection<? extends Enum<?>> values) {
        return strings(values.stream()
                .map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                .sorted()
                .toList());
    }

    private static JsonArray resourceLocationNames(Collection<ResourceLocation> values) {
        return strings(values.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList());
    }

    private static JsonArray strings(Collection<String> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted(Comparator.naturalOrder())
                .forEach(array::add);
        return array;
    }
}
