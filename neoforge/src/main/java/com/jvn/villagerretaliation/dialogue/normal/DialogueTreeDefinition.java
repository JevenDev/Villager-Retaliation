package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
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
            String labelKey,
            DialogueEntryMetadata metadata,
            String start,
            DialogueRequestType requestType,
            boolean showForAdults,
            boolean showForBabies,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            List<DialogueCondition> conditions,
            boolean forceCameraTowardsVillager,
            int order,
            int priority
    ) {
        public Entry {
            id = id == null || id.isBlank() ? "default" : id;
            label = label == null ? "" : label;
            labelKey = labelKey == null ? "" : labelKey;
            metadata = metadata == null ? DialogueEntryMetadata.EMPTY : metadata;
            start = start == null || start.isBlank() ? "start" : start;
            requestType = requestType == null ? DialogueRequestType.STORY : requestType;
            professions = professions == null ? Set.of() : Set.copyOf(professions);
            dispositions = dispositions == null ? Set.of() : Set.copyOf(dispositions);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public Entry(
                String id, String label, DialogueEntryMetadata metadata, String start, DialogueRequestType requestType,
                boolean showForAdults, boolean showForBabies, Set<VillagerProfession> professions,
                Set<DialogueDisposition> dispositions, List<DialogueCondition> conditions,
                boolean forceCameraTowardsVillager, int order) {
            this(id, label, "", metadata, start, requestType, showForAdults, showForBabies, professions, dispositions,
                    conditions, forceCameraTowardsVillager, order, 0);
        }

        public boolean matches(DialogueContext context, DialogueDisposition disposition) {
            if (this.label.isBlank() && this.labelKey.isBlank()) {
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
            return toOption(treeId, DialogueEntryMetadata.EMPTY);
        }

        public DialogueOptionDefinition toOption(ResourceLocation treeId, DialogueEntryMetadata inheritedMetadata) {
            return toOption(null, treeId, inheritedMetadata);
        }

        public DialogueOptionDefinition toOption(
                DialogueContext context,
                ResourceLocation treeId,
                DialogueEntryMetadata inheritedMetadata) {
            return option(
                    DialogueTreeService.entryOptionId(treeId, this.id),
                    DialogueTreeReference.entry(treeId, this.id),
                    treeId,
                    inheritedMetadata == null ? this.metadata : inheritedMetadata.merge(this.metadata),
                    resolveLabel(context, this.label, this.labelKey),
                    this.requestType,
                    this.forceCameraTowardsVillager,
                    this.priority,
                    this.order
            );
        }
    }

    public record Node(
            String id,
            List<String> lines,
            List<DialogueTextVariant> textVariants,
            List<VillagerActionDefinition> actions,
            List<DialogueCondition> conditions,
            List<Response> responses,
            boolean end,
            List<com.jvn.villagerretaliation.dialogue.DialogueExchange> exchanges,
            com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera
    ) {
        public Node(String id, List<String> lines, List<DialogueTextVariant> textVariants,
                List<VillagerActionDefinition> actions, List<DialogueCondition> conditions, List<Response> responses,
                boolean end, List<com.jvn.villagerretaliation.dialogue.DialogueExchange> exchanges) {
            this(id, lines, textVariants, actions, conditions, responses, end, exchanges, null);
        }

        public Node(String id, List<String> lines, List<DialogueTextVariant> textVariants,
                List<VillagerActionDefinition> actions, List<DialogueCondition> conditions, List<Response> responses, boolean end) {
            this(id, lines, textVariants, actions, conditions, responses, end, List.of());
        }

        public Node {
            exchanges = List.copyOf(exchanges);
            id = id == null || id.isBlank() ? "start" : id;
            lines = lines == null ? List.of() : List.copyOf(lines);
            textVariants = textVariants == null || textVariants.isEmpty()
                    ? DialogueTextVariant.legacy(id, lines, "", DialogueEntryMetadata.EMPTY, DialogueUsagePolicy.DEFAULT)
                    : List.copyOf(textVariants);
            actions = actions == null ? List.of() : List.copyOf(actions);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            responses = responses == null ? List.of() : List.copyOf(responses);
        }

        public Node(String id, List<String> lines, List<VillagerActionDefinition> actions,
                List<DialogueCondition> conditions, List<Response> responses, boolean end) {
            this(id, lines, List.of(), actions, conditions, responses, end);
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

        public String selectLine(DialogueContext context) { return selectText(context).text(); }

        public DialogueLine.SelectedText selectText(DialogueContext context) {
            return selectedText(this.textVariants, context, this.camera);
        }
    }

    public record Response(
            String id,
            String label,
            String labelKey,
            DialogueEntryMetadata metadata,
            String next,
            DialogueRequestType requestType,
            List<String> lines,
            List<DialogueTextVariant> textVariants,
            List<VillagerActionDefinition> actions,
            List<DialogueCondition> conditions,
            boolean end,
            int order,
            int priority,
            List<com.jvn.villagerretaliation.dialogue.DialogueExchange> exchanges,
            com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera,
        com.jvn.villagerretaliation.dialogue.DialogueChoice choice
) {
    public Response(String id,
            String label,
            String labelKey,
            DialogueEntryMetadata metadata,
            String next,
            DialogueRequestType requestType,
            List<String> lines,
            List<DialogueTextVariant> textVariants,
            List<VillagerActionDefinition> actions,
            List<DialogueCondition> conditions,
            boolean end,
            int order,
            int priority,
            List<com.jvn.villagerretaliation.dialogue.DialogueExchange> exchanges,
            com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera) {
        this(id, label, labelKey, metadata, next, requestType, lines, textVariants, actions, conditions, end, order, priority, exchanges, camera, com.jvn.villagerretaliation.dialogue.DialogueChoice.HIDDEN);
    }

        public Response(String id, String label, String labelKey, DialogueEntryMetadata metadata, String next,
                DialogueRequestType requestType, List<String> lines, List<DialogueTextVariant> textVariants,
                List<VillagerActionDefinition> actions, List<DialogueCondition> conditions, boolean end, int order,
                int priority, List<com.jvn.villagerretaliation.dialogue.DialogueExchange> exchanges) {
            this(id, label, labelKey, metadata, next, requestType, lines, textVariants, actions, conditions, end,
                    order, priority, exchanges, null);
        }

        public Response(String id, String label, String labelKey, DialogueEntryMetadata metadata, String next,
                DialogueRequestType requestType, List<String> lines, List<DialogueTextVariant> textVariants,
                List<VillagerActionDefinition> actions, List<DialogueCondition> conditions, boolean end, int order, int priority) {
            this(id, label, labelKey, metadata, next, requestType, lines, textVariants, actions, conditions, end, order, priority, List.of());
        }

        public Response {
            exchanges = List.copyOf(exchanges);
            id = id == null || id.isBlank() ? "response" : id;
            label = label == null ? "" : label;
            labelKey = labelKey == null ? "" : labelKey;
            metadata = metadata == null ? DialogueEntryMetadata.EMPTY : metadata;
            next = next == null ? "" : next;
            requestType = requestType == null ? DialogueRequestType.STORY : requestType;
            lines = lines == null ? List.of() : List.copyOf(lines);
            textVariants = textVariants == null || textVariants.isEmpty()
                    ? DialogueTextVariant.legacy(id, lines, "", metadata, DialogueUsagePolicy.DEFAULT)
                    : List.copyOf(textVariants);
            actions = actions == null ? List.of() : List.copyOf(actions);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public Response(
                String id, String label, DialogueEntryMetadata metadata, String next, DialogueRequestType requestType,
                List<String> lines, List<VillagerActionDefinition> actions, List<DialogueCondition> conditions,
                boolean end, int order) {
            this(id, label, "", metadata, next, requestType, lines, List.of(), actions, conditions, end, order, 0);
        }

        public Response(
                String id, String label, DialogueEntryMetadata metadata, String next, DialogueRequestType requestType,
                List<String> lines, List<VillagerActionDefinition> actions, List<DialogueCondition> conditions,
                boolean end, int order, int priority) {
            this(id, label, "", metadata, next, requestType, lines, List.of(), actions, conditions, end, order, priority);
        }

        public boolean matches(DialogueContext context) {
            return (!this.label.isBlank() || !this.labelKey.isBlank())
                    && DialogueCondition.matchesAll(context, this.conditions);
        }

        public DialogueOptionDefinition toOption(ResourceLocation treeId) {
            return toOption(treeId, DialogueEntryMetadata.EMPTY);
        }

        public DialogueOptionDefinition toOption(ResourceLocation treeId, DialogueEntryMetadata inheritedMetadata) {
            return toOption(null, treeId, inheritedMetadata);
        }

        public DialogueOptionDefinition toOption(
                DialogueContext context,
                ResourceLocation treeId,
                DialogueEntryMetadata inheritedMetadata) {
            return option(
                    DialogueTreeService.responseOptionId(treeId, this.id),
                    DialogueTreeReference.response(treeId, this.id),
                    treeId,
                    inheritedMetadata == null ? this.metadata : inheritedMetadata.merge(this.metadata),
                    resolveLabel(context, this.label, this.labelKey),
                    this.requestType,
                    false,
                    this.priority,
                    this.order
            ).withChoice(this.choice, context == null || matches(context),
                    context == null || matches(context) ? "" : this.choice.reason(context));
        }

        public String selectLine(net.minecraft.util.RandomSource random) {
            if (this.lines.isEmpty()) {
                return "";
            }
            return this.lines.get(random.nextInt(this.lines.size()));
        }

        public String selectLine(DialogueContext context) { return selectText(context).text(); }

        public DialogueLine.SelectedText selectText(DialogueContext context) {
            return selectedText(this.textVariants, context, this.camera);
        }
    }

    private static DialogueLine.SelectedText selectedText(List<DialogueTextVariant> variants, DialogueContext context,
            com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera) {
        return DialogueTextVariant.selectAndRecord(variants, context, List.of())
                .map(variant -> new DialogueLine.SelectedText(variant.id(), variant.textKey().isBlank() ? variant.text()
                        : VillagerDialogueResources.message(context, variant.textKey()).orElse(variant.text()), variant.textKey(),
                        variant.camera() == null ? camera : variant.camera()))
                .orElse(new DialogueLine.SelectedText("", "", "", camera));
    }

    private static String resolveLabel(DialogueContext context, String fallback, String key) {
        if (context == null || key == null || key.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return DialogueOptionDefinition.networkSafeLabel(
                VillagerDialogueResources.message(context, key).orElse(fallback == null ? "" : fallback));
    }

    private static DialogueOptionDefinition option(
            String id,
            DialogueTreeReference treeReference,
            ResourceLocation source,
            DialogueEntryMetadata metadata,
            String label,
            DialogueRequestType requestType,
            boolean forceCameraTowardsVillager,
            int priority,
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
                false,
                List.of(),
                false,
                priority,
                order
        );
    }
}
