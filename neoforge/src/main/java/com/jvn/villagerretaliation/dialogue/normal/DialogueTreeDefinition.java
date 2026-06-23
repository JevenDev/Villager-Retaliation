package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

public record DialogueTreeDefinition(
        ResourceLocation id,
        String title,
        String description,
        DialogueEntryMetadata metadata,
        List<DialogueCondition> conditions,
        List<Entry> entries,
        Map<String, Node> nodes
) {
    public DialogueTreeDefinition {
        title = title == null || title.isBlank() ? id.toString() : title;
        description = description == null ? "" : description;
        metadata = metadata == null ? DialogueEntryMetadata.EMPTY : metadata;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        entries = entries == null ? List.of() : List.copyOf(entries);
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }

    public boolean matches(DialogueContext context) {
        return DialogueCondition.matchesAll(context, this.conditions);
    }

    public Optional<Node> node(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.nodes.get(id));
    }

    public Optional<Entry> entry(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return this.entries.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public record Entry(
            String id,
            String label,
            DialogueEntryMetadata metadata,
            String start,
            DialogueRequestType requestType,
            boolean showForAdults,
            boolean showForBabies,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            List<DialogueCondition> conditions,
            boolean forceCameraTowardsVillager,
            int order
    ) {
        public Entry {
            id = id == null || id.isBlank() ? "default" : id;
            label = label == null ? "" : label;
            metadata = metadata == null ? DialogueEntryMetadata.EMPTY : metadata;
            start = start == null || start.isBlank() ? "start" : start;
            requestType = requestType == null ? DialogueRequestType.STORY : requestType;
            professions = professions == null ? Set.of() : Set.copyOf(professions);
            dispositions = dispositions == null ? Set.of() : Set.copyOf(dispositions);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public boolean matches(DialogueContext context, DialogueDisposition disposition) {
            if (this.label.isBlank()) {
                return false;
            }
            if (context.villager().isBaby()) {
                if (!this.showForBabies) {
                    return false;
                }
            } else if (!this.showForAdults) {
                return false;
            }
            if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
                return false;
            }
            if (!DialogueCondition.matchesAll(context, this.conditions)) {
                return false;
            }
            return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
        }

        public DialogueOptionDefinition toOption(ResourceLocation treeId) {
            return option(
                    DialogueTreeService.entryOptionId(treeId, this.id),
                    DialogueTreeReference.entry(treeId, this.id),
                    treeId,
                    this.metadata,
                    this.label,
                    this.requestType,
                    this.forceCameraTowardsVillager,
                    this.order
            );
        }
    }

    public record Node(
            String id,
            List<String> lines,
            List<VillagerActionDefinition> actions,
            List<DialogueCondition> conditions,
            List<Response> responses,
            boolean end
    ) {
        public Node {
            id = id == null || id.isBlank() ? "start" : id;
            lines = lines == null ? List.of() : List.copyOf(lines);
            actions = actions == null ? List.of() : List.copyOf(actions);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            responses = responses == null ? List.of() : List.copyOf(responses);
        }

        public boolean matches(DialogueContext context) {
            return DialogueCondition.matchesAll(context, this.conditions);
        }

        public String selectLine(net.minecraft.util.RandomSource random) {
            if (this.lines.isEmpty()) {
                return "";
            }
            return this.lines.get(random.nextInt(this.lines.size()));
        }
    }

    public record Response(
            String id,
            String label,
            DialogueEntryMetadata metadata,
            String next,
            DialogueRequestType requestType,
            List<String> lines,
            List<VillagerActionDefinition> actions,
            List<DialogueCondition> conditions,
            boolean end,
            int order
    ) {
        public Response {
            id = id == null || id.isBlank() ? "response" : id;
            label = label == null ? "" : label;
            metadata = metadata == null ? DialogueEntryMetadata.EMPTY : metadata;
            next = next == null ? "" : next;
            requestType = requestType == null ? DialogueRequestType.STORY : requestType;
            lines = lines == null ? List.of() : List.copyOf(lines);
            actions = actions == null ? List.of() : List.copyOf(actions);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public boolean matches(DialogueContext context) {
            return !this.label.isBlank() && DialogueCondition.matchesAll(context, this.conditions);
        }

        public DialogueOptionDefinition toOption(ResourceLocation treeId) {
            return option(
                    DialogueTreeService.responseOptionId(treeId, this.id),
                    DialogueTreeReference.response(treeId, this.id),
                    treeId,
                    this.metadata,
                    this.label,
                    this.requestType,
                    false,
                    this.order
            );
        }

        public String selectLine(net.minecraft.util.RandomSource random) {
            if (this.lines.isEmpty()) {
                return "";
            }
            return this.lines.get(random.nextInt(this.lines.size()));
        }
    }

    private static DialogueOptionDefinition option(
            String id,
            DialogueTreeReference treeReference,
            ResourceLocation source,
            DialogueEntryMetadata metadata,
            String label,
            DialogueRequestType requestType,
            boolean forceCameraTowardsVillager,
            int order) {
        return new DialogueOptionDefinition(
                id,
                source,
                metadata,
                DialogueQuestAction.EMPTY,
                treeReference,
                label,
                requestType,
                true,
                true,
                Set.of(),
                Set.of(),
                VillagerEquipmentCondition.empty(),
                VillagerPlayerItemCondition.empty(),
                VillagerReputationCondition.empty(),
                DialogueItemPayment.empty(),
                forceCameraTowardsVillager,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                List.of(),
                false,
                order
        );
    }
}
