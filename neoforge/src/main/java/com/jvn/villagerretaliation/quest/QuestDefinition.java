package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.util.ContentTags;
import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;

public record QuestDefinition(
        ResourceLocation id,
        String title,
        String description,
        String titleKey,
        String descriptionKey,
        String questline,
        Set<String> tags,
        ResourceLocation parent,
        List<ResourceLocation> prerequisites,
        boolean showLockedAdventureHint,
        Offer offer,
        Target target,
        List<Objective> objectives,
        Rules rules,
        Tracker tracker,
        String entryStage,
        Map<String, Stage> stages,
        List<Trigger> triggers,
        Rewards rewards,
        Dialogue dialogue,
        DialogueEntryMetadata metadata,
        Links links,
        Revision revision
) {
    public QuestDefinition {
        title = title == null || title.isBlank() ? id.toString() : title;
        description = description == null ? "" : description;
        titleKey = titleKey == null ? "" : titleKey;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        questline = questline == null ? "" : questline;
        tags = ContentTags.normalizeAll(tags);
        prerequisites = prerequisites == null
                ? (parent == null ? List.of() : List.of(parent))
                : prerequisites.stream().filter(java.util.Objects::nonNull).toList();
        offer = offer == null ? Offer.any() : offer;
        target = target == null ? Target.EMPTY : target;
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        rules = rules == null ? Rules.DEFAULT : rules;
        tracker = tracker == null ? Tracker.EMPTY : tracker;
        entryStage = entryStage == null ? "" : entryStage.trim();
        stages = stages == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(stages));
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        rewards = rewards == null ? Rewards.EMPTY : rewards;
        dialogue = dialogue == null ? Dialogue.EMPTY : dialogue;
        metadata = metadata == null ? DialogueEntryMetadata.EMPTY : metadata;
        links = links == null ? Links.EMPTY : links;
        revision = revision == null ? Revision.DEFAULT : revision;
    }

    public QuestDefinition(
            ResourceLocation id,
            String title,
            String description,
            String titleKey,
            String descriptionKey,
            String questline,
            Set<String> tags,
            ResourceLocation parent,
            List<ResourceLocation> prerequisites,
            boolean showLockedAdventureHint,
            Offer offer,
            Target target,
            List<Objective> objectives,
            Rules rules,
            Tracker tracker,
            String entryStage,
            Map<String, Stage> stages,
            List<Trigger> triggers,
            Rewards rewards,
            Dialogue dialogue,
            DialogueEntryMetadata metadata,
            Links links) {
        this(id, title, description, titleKey, descriptionKey, questline, tags, parent, prerequisites,
                showLockedAdventureHint, offer, target, objectives, rules, tracker, entryStage, stages, triggers,
                rewards, dialogue, metadata, links, Revision.DEFAULT);
    }

    public record Revision(
            int number,
            RevisionPolicy activePolicy,
            Map<String, String> stageAliases,
            Map<String, String> objectiveAliases
    ) {
        public static final Revision DEFAULT = new Revision(1, RevisionPolicy.KEEP, Map.of(), Map.of());

        public Revision {
            number = Math.max(1, number);
            activePolicy = activePolicy == null ? RevisionPolicy.KEEP : activePolicy;
            stageAliases = stageAliases == null ? Map.of() : Map.copyOf(stageAliases);
            objectiveAliases = objectiveAliases == null ? Map.of() : Map.copyOf(objectiveAliases);
        }
    }

    public enum RevisionPolicy {
        KEEP,
        RESET_STAGE,
        RESTART,
        FAIL;

        public static RevisionPolicy bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "reset", "reset_stage", "stage_reset" -> RESET_STAGE;
                case "restart", "restart_quest" -> RESTART;
                case "fail", "fail_quest" -> FAIL;
                default -> KEEP;
            };
        }
    }

    public record Links(
            ResourceLocation dialogueTree,
            String offer,
            String reminder,
            String turnIn,
            List<String> forcedDialogue
    ) {
        public static final Links EMPTY = new Links(null, "", "", "", List.of());

        public Links {
            offer = offer == null ? "" : offer;
            reminder = reminder == null ? "" : reminder;
            turnIn = turnIn == null ? "" : turnIn;
            forcedDialogue = forcedDialogue == null ? List.of() : List.copyOf(forcedDialogue);
        }

        public boolean isEmpty() {
            return this.dialogueTree == null
                    && this.offer.isBlank()
                    && this.reminder.isBlank()
                    && this.turnIn.isBlank()
                    && this.forcedDialogue.isEmpty();
        }
    }

    public record Offer(
            Set<VillagerProfession> professions,
            int minVillagerLevel,
            Map<VillagerSkill, Integer> minSkills,
            List<DialogueCondition> conditions,
            int weight
    ) {
        public static Offer any() {
            return new Offer(Set.of(), 1, Map.of(), List.of());
        }

        public Offer(Set<VillagerProfession> professions, int minVillagerLevel,
                     Map<VillagerSkill, Integer> minSkills, List<DialogueCondition> conditions) {
            this(professions, minVillagerLevel, minSkills, conditions, 1);
        }

        public Offer {
            professions = professions == null ? Set.of() : Set.copyOf(professions);
            minVillagerLevel = Math.max(1, Math.min(5, minVillagerLevel));
            minSkills = minSkills == null ? Map.of() : Map.copyOf(minSkills);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            weight = Math.max(0, Math.min(10_000, weight));
        }

        public boolean matches(DialogueContext context) {
            if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
                return false;
            }
            if (context.villager().getVillagerData().getLevel() < this.minVillagerLevel) {
                return false;
            }
            for (Map.Entry<VillagerSkill, Integer> entry : this.minSkills.entrySet()) {
                if (context.skillValue(entry.getKey()) < VillagerSkillSet.clamp(entry.getValue())) {
                    return false;
                }
            }
            return DialogueCondition.matchesAll(context, this.conditions);
        }
    }

    public record Target(
            ResourceLocation structure,
            ResourceKey<Level> dimension,
            List<String> pieces,
            int searchRadius,
            int discoveryRadius,
            ResourceLocation proofItem,
            ItemStackPredicate proofItemPredicate
    ) {
        public static final Target EMPTY =
                new Target(null, null, List.of(), 128, 128, null, ItemStackPredicate.ANY);

        public Target(
                ResourceLocation structure,
                ResourceKey<Level> dimension,
                List<String> pieces,
                int searchRadius,
                int discoveryRadius,
                ResourceLocation proofItem) {
            this(structure, dimension, pieces, searchRadius, discoveryRadius, proofItem, ItemStackPredicate.ANY);
        }

        public Target {
            pieces = pieces == null ? List.of() : List.copyOf(pieces);
            searchRadius = Math.max(1, searchRadius);
            discoveryRadius = Math.max(1, discoveryRadius);
            proofItemPredicate = proofItemPredicate == null ? ItemStackPredicate.ANY : proofItemPredicate;
        }

        public boolean hasStructureTarget() {
            return this.structure != null;
        }

        public boolean hasProofItem() {
            return this.proofItem != null;
        }
    }

    public record Rewards(
            int experience,
            int reputation,
            int gossipReputation,
            ResourceLocation lootTable,
            ResourceLocation memoryEvent,
            com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope memoryScope
    ) {
        public static final Rewards EMPTY = new Rewards(
                0, 0, 0, null, null,
                com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.BOTH);

        public Rewards {
            memoryScope = memoryScope == null
                    ? com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.BOTH
                    : memoryScope;
        }
    }

    public record Objective(
            String id,
            ObjectiveType type,
            boolean optional,
            ResourceLocation structure,
            ResourceKey<Level> dimension,
            BlockPos location,
            int radius,
            List<String> pieces,
            int searchRadius,
            int discoveryRadius,
            ResourceLocation item,
            Set<ResourceLocation> entityTypes,
            Set<ResourceLocation> entityTags,
            Set<ResourceLocation> blockTypes,
            Set<ResourceLocation> blockTags,
            Set<ResourceLocation> memoryTags,
            Set<String> giftReactions,
            Set<VillagerReputationLevel> reputationLevels,
            Integer minReputation,
            Integer maxReputation,
            QuestFactScope factScope,
            ResourceLocation factQuestId,
            Set<ResourceLocation> factTags,
            String factKey,
            Set<String> factValues,
            Integer factMin,
            Integer factMax,
            ResourceLocation criterion,
            Map<String, String> criterionData,
            int count,
            boolean consume,
            ItemRequirements itemRequirements,
            List<DialogueCondition> conditions,
            ObjectiveTracker tracker,
            List<ItemSelectionEntry> itemSelections,
            ItemSelectionMode itemSelectionMode
    ) {
        public Objective {
            id = id == null || id.isBlank() ? "objective" : id;
            type = type == null ? ObjectiveType.CONDITION : type;
            location = location == null ? null : location.immutable();
            radius = Math.max(1, radius);
            pieces = pieces == null ? List.of() : List.copyOf(pieces);
            searchRadius = Math.max(1, searchRadius);
            discoveryRadius = Math.max(1, discoveryRadius);
            entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
            entityTags = entityTags == null ? Set.of() : Set.copyOf(entityTags);
            blockTypes = blockTypes == null ? Set.of() : Set.copyOf(blockTypes);
            blockTags = blockTags == null ? Set.of() : Set.copyOf(blockTags);
            memoryTags = memoryTags == null ? Set.of() : Set.copyOf(memoryTags);
            giftReactions = giftReactions == null ? Set.of() : Set.copyOf(giftReactions);
            reputationLevels = reputationLevels == null ? Set.of() : Set.copyOf(reputationLevels);
            factScope = factScope == null ? QuestFactScope.PLAYER : factScope;
            factTags = factTags == null ? Set.of() : Set.copyOf(factTags);
            factKey = factKey == null ? "" : factKey;
            factValues = factValues == null ? Set.of() : Set.copyOf(factValues);
            criterionData = criterionData == null ? Map.of() : Map.copyOf(criterionData);
            count = Math.max(1, count);
            itemRequirements = itemRequirements == null ? ItemRequirements.EMPTY : itemRequirements;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            tracker = tracker == null ? ObjectiveTracker.EMPTY : tracker;
            itemSelections = itemSelections == null ? List.of() : List.copyOf(itemSelections);
            itemSelectionMode = itemSelectionMode == null ? ItemSelectionMode.FIXED : itemSelectionMode;
        }

        public Objective(
                String id,
                ObjectiveType type,
                boolean optional,
                ResourceLocation structure,
                ResourceKey<Level> dimension,
                BlockPos location,
                int radius,
                List<String> pieces,
                int searchRadius,
                int discoveryRadius,
                ResourceLocation item,
                Set<ResourceLocation> entityTypes,
                Set<ResourceLocation> entityTags,
                Set<ResourceLocation> blockTypes,
                Set<ResourceLocation> blockTags,
                Set<ResourceLocation> memoryTags,
                Set<String> giftReactions,
                Set<VillagerReputationLevel> reputationLevels,
                Integer minReputation,
                Integer maxReputation,
                QuestFactScope factScope,
                ResourceLocation factQuestId,
                Set<ResourceLocation> factTags,
                String factKey,
                Set<String> factValues,
                Integer factMin,
                Integer factMax,
                int count,
                boolean consume,
                ItemRequirements itemRequirements,
                List<DialogueCondition> conditions,
                ObjectiveTracker tracker) {
            this(id, type, optional, structure, dimension, location, radius, pieces, searchRadius, discoveryRadius,
                    item, entityTypes, entityTags, blockTypes, blockTags, memoryTags, giftReactions,
                    reputationLevels, minReputation, maxReputation, factScope, factQuestId, factTags, factKey,
                    factValues, factMin, factMax, null, Map.of(), count, consume, itemRequirements, conditions, tracker,
                    List.of(), ItemSelectionMode.FIXED);
        }

        public Objective(
                String id,
                ObjectiveType type,
                boolean optional,
                ResourceLocation structure,
                ResourceKey<Level> dimension,
                BlockPos location,
                int radius,
                List<String> pieces,
                int searchRadius,
                int discoveryRadius,
                ResourceLocation item,
                Set<ResourceLocation> entityTypes,
                Set<ResourceLocation> entityTags,
                Set<ResourceLocation> blockTypes,
                Set<ResourceLocation> blockTags,
                Set<ResourceLocation> memoryTags,
                Set<String> giftReactions,
                Set<VillagerReputationLevel> reputationLevels,
                Integer minReputation,
                Integer maxReputation,
                QuestFactScope factScope,
                ResourceLocation factQuestId,
                Set<ResourceLocation> factTags,
                String factKey,
                Set<String> factValues,
                Integer factMin,
                Integer factMax,
                ResourceLocation criterion,
                Map<String, String> criterionData,
                int count,
                boolean consume,
                ItemRequirements itemRequirements,
                List<DialogueCondition> conditions,
                ObjectiveTracker tracker) {
            this(id, type, optional, structure, dimension, location, radius, pieces, searchRadius, discoveryRadius,
                    item, entityTypes, entityTags, blockTypes, blockTags, memoryTags, giftReactions,
                    reputationLevels, minReputation, maxReputation, factScope, factQuestId, factTags, factKey,
                    factValues, factMin, factMax, criterion, criterionData, count, consume, itemRequirements,
                    conditions, tracker, List.of(), ItemSelectionMode.FIXED);
        }

        public boolean usesRandomItemSelection() {
            return this.type == ObjectiveType.ITEM_CHECK
                    && this.itemSelectionMode == ItemSelectionMode.RANDOM
                    && !this.itemSelections.isEmpty();
        }
    }

    public record ItemSelectionEntry(
            ResourceLocation item,
            ResourceLocation tag,
            int weight
    ) {
        public ItemSelectionEntry {
            weight = Math.max(1, weight);
        }

        public boolean isTag() {
            return this.tag != null;
        }
    }

    public enum ItemSelectionMode {
        FIXED,
        RANDOM;

        public static ItemSelectionMode bySerializedName(String value) {
            return "random".equalsIgnoreCase(value == null ? "" : value.trim()) ? RANDOM : FIXED;
        }
    }

    public record ItemRequirements(
            List<EnchantmentRequirement> enchantments,
            OptionalInt minDurability,
            OptionalInt maxDurability,
            OptionalInt minDurabilityPercent,
            OptionalInt maxDurabilityPercent,
            CompoundTag customData,
            ItemStackPredicate stackPredicate
    ) {
        public static final ItemRequirements EMPTY = new ItemRequirements(
                List.of(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                null,
                ItemStackPredicate.ANY);

        public ItemRequirements(
                List<EnchantmentRequirement> enchantments,
                OptionalInt minDurability,
                OptionalInt maxDurability,
                OptionalInt minDurabilityPercent,
                OptionalInt maxDurabilityPercent,
                CompoundTag customData) {
            this(
                    enchantments,
                    minDurability,
                    maxDurability,
                    minDurabilityPercent,
                    maxDurabilityPercent,
                    customData,
                    ItemStackPredicate.ANY);
        }

        public ItemRequirements {
            enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
            minDurability = minDurability == null ? OptionalInt.empty() : minDurability;
            maxDurability = maxDurability == null ? OptionalInt.empty() : maxDurability;
            minDurabilityPercent = minDurabilityPercent == null ? OptionalInt.empty() : minDurabilityPercent;
            maxDurabilityPercent = maxDurabilityPercent == null ? OptionalInt.empty() : maxDurabilityPercent;
            customData = customData == null ? null : customData.copy();
            stackPredicate = stackPredicate == null ? ItemStackPredicate.ANY : stackPredicate;
        }

        public boolean hasCustomData() {
            return this.customData != null && !this.customData.isEmpty();
        }
    }

    public record EnchantmentRequirement(
            ResourceLocation id,
            OptionalInt minLevel,
            OptionalInt maxLevel
    ) {
        public EnchantmentRequirement {
            minLevel = minLevel == null ? OptionalInt.empty() : minLevel;
            maxLevel = maxLevel == null ? OptionalInt.empty() : maxLevel;
        }
    }

    public record ObjectiveTracker(
            String text,
            String completeText,
            String textKey,
            String completeTextKey,
            boolean showProgress,
            float progress,
            java.util.Map<String, String> metadata
    ) {
        public static final ObjectiveTracker EMPTY = new ObjectiveTracker("", "", "", "", true, -1.0F, java.util.Map.of());

        public ObjectiveTracker {
            text = text == null ? "" : text;
            completeText = completeText == null ? "" : completeText;
            textKey = textKey == null ? "" : textKey;
            completeTextKey = completeTextKey == null ? "" : completeTextKey;
            progress = Math.max(-1.0F, Math.min(1.0F, progress));
            metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        }

        public boolean hasAnyDisplay() {
            return !this.text.isBlank()
                    || !this.completeText.isBlank()
                    || !this.textKey.isBlank()
                    || !this.completeTextKey.isBlank()
                    || this.progress >= 0.0F
                    || !this.metadata.isEmpty();
        }

        public boolean hasActiveDisplay() {
            return !this.text.isBlank()
                    || !this.textKey.isBlank()
                    || this.progress >= 0.0F
                    || !this.metadata.isEmpty();
        }

        public boolean hasCompletionDisplay() {
            return !this.completeText.isBlank() || !this.completeTextKey.isBlank();
        }

        public SelectedText displayText(boolean complete) {
            return complete && (!this.completeText.isBlank() || !this.completeTextKey.isBlank())
                    ? new SelectedText(this.completeText, this.completeTextKey)
                    : new SelectedText(this.text, this.textKey);
        }
    }

    public enum ObjectiveType {
        STRUCTURE_VISIT,
        LOCATION_VISIT,
        ITEM_CHECK,
        MOB_KILL,
        BLOCK_BREAK,
        BLOCK_PLACE,
        BLOCK_INTERACT,
        MEMORY_EVENT,
        TRADE,
        GIFT,
        REPUTATION,
        CHOICE,
        FACT,
        CRITERION,
        CONDITION;

        public static ObjectiveType bySerializedName(String value) {
            return QuestObjectiveRegistry.objectiveTypeBySerializedName(value);
        }
    }

    public record Rules(
            boolean repeatable,
            boolean lockedToVillager,
            boolean crossVillagerCompatible,
            int maxStarts,
            int maxCompletions,
            CompletionScope completionScope,
            long completionCooldownTicks,
            long prerequisiteCooldownTicks,
            AbandonmentMode abandonment,
            long abandonmentCooldownTicks,
            boolean consumeOnCompletion,
            boolean consumeOnAbandonment,
            ActiveState activeState,
            Expiration expiration,
            Branching branching,
            int maxActiveQuests,
            Map<String, Integer> maxActiveByTag
    ) {
        public static final Rules DEFAULT = new Rules(
                false,
                true,
                false,
                1,
                1,
                CompletionScope.PLAYER,
                0L,
                0L,
                AbandonmentMode.ALLOW_REPICKUP,
                0L,
                false,
                false,
                ActiveState.DEFAULT,
                Expiration.DEFAULT,
                Branching.DEFAULT,
                0,
                Map.of()
        );

        public Rules {
            maxStarts = Math.max(0, maxStarts);
            maxCompletions = Math.max(0, maxCompletions);
            completionScope = completionScope == null ? CompletionScope.PLAYER : completionScope;
            completionCooldownTicks = Math.max(0L, completionCooldownTicks);
            prerequisiteCooldownTicks = Math.max(0L, prerequisiteCooldownTicks);
            abandonment = abandonment == null ? AbandonmentMode.ALLOW_REPICKUP : abandonment;
            abandonmentCooldownTicks = Math.max(0L, abandonmentCooldownTicks);
            activeState = activeState == null ? ActiveState.DEFAULT : activeState;
            expiration = expiration == null ? Expiration.DEFAULT : expiration;
            branching = branching == null ? Branching.DEFAULT : branching;
            maxActiveQuests = Math.max(0, maxActiveQuests);
            maxActiveByTag = ContentTags.normalizeKeys(maxActiveByTag);
        }

        public Rules(
                boolean repeatable, boolean lockedToVillager, boolean crossVillagerCompatible,
                int maxStarts, int maxCompletions, CompletionScope completionScope,
                long completionCooldownTicks, long prerequisiteCooldownTicks, AbandonmentMode abandonment,
                long abandonmentCooldownTicks, boolean consumeOnCompletion, boolean consumeOnAbandonment,
                ActiveState activeState, Expiration expiration, Branching branching) {
            this(repeatable, lockedToVillager, crossVillagerCompatible, maxStarts, maxCompletions,
                    completionScope, completionCooldownTicks, prerequisiteCooldownTicks, abandonment,
                    abandonmentCooldownTicks, consumeOnCompletion, consumeOnAbandonment, activeState, expiration,
                    branching, 0, Map.of());
        }
    }

    public record Branching(
            ResourceLocation exclusiveGroup,
            BranchLockEvent exclusiveOn,
            Set<ResourceLocation> blocksOnStart,
            Set<ResourceLocation> blocksOnCompletion
    ) {
        public static final Branching DEFAULT = new Branching(null, BranchLockEvent.STARTED, Set.of(), Set.of());

        public Branching {
            exclusiveOn = exclusiveOn == null ? BranchLockEvent.STARTED : exclusiveOn;
            blocksOnStart = blocksOnStart == null ? Set.of() : Set.copyOf(blocksOnStart);
            blocksOnCompletion = blocksOnCompletion == null ? Set.of() : Set.copyOf(blocksOnCompletion);
        }

        public Set<ResourceLocation> blocksFor(BranchLockEvent event) {
            return event == BranchLockEvent.STARTED ? this.blocksOnStart : this.blocksOnCompletion;
        }
    }

    public enum BranchLockEvent {
        STARTED("started"),
        COMPLETED("completed");

        private final String serializedName;

        BranchLockEvent(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static BranchLockEvent bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "start", "started", "accepted", "begin", "begun" -> STARTED;
                case "complete", "completed", "turn_in", "turnin", "finish", "finished" -> COMPLETED;
                default -> STARTED;
            };
        }
    }

    public enum CompletionScope {
        PLAYER,
        PLAYER_WORLD,
        WORLD,
        VILLAGE,
        VILLAGER;

        public static CompletionScope bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "player_world", "player_in_world", "per_player_world" -> PLAYER_WORLD;
                case "player", "per_player" -> PLAYER;
                case "world", "global", "server" -> WORLD;
                case "village", "settlement" -> VILLAGE;
                case "villager", "issuer", "quest_giver" -> VILLAGER;
                default -> PLAYER;
            };
        }
    }

    public record ActiveState(
            List<DialogueCondition> conditions,
            boolean hideWhenUnmet,
            boolean pauseProgressWhenUnmet
    ) {
        public static final ActiveState DEFAULT = new ActiveState(List.of(), false, true);

        public ActiveState {
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public boolean hasConditions() {
            return !this.conditions.isEmpty();
        }
    }

    public record Expiration(
            long afterTicks,
            List<DialogueCondition> conditions,
            boolean consume,
            boolean allowRepickup,
            boolean sendNotification,
            String notificationTrigger,
            String notificationText,
            String notificationTextKey
    ) {
        public static final Expiration DEFAULT = new Expiration(
                0L,
                List.of(),
                false,
                true,
                true,
                "quest.expired",
                "Quest expired: {quest}",
                "quest.expired"
        );

        public Expiration {
            afterTicks = Math.max(0L, afterTicks);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            notificationTrigger = notificationTrigger == null || notificationTrigger.isBlank()
                    ? "quest.expired"
                    : notificationTrigger;
            notificationText = notificationText == null || notificationText.isBlank()
                    ? "Quest expired: {quest}"
                    : notificationText;
            notificationTextKey = notificationTextKey == null ? "" : notificationTextKey;
        }

        public boolean enabled() {
            return this.afterTicks > 0L || !this.conditions.isEmpty();
        }
    }

    public enum AbandonmentMode {
        REMOVE_FOREVER,
        ALLOW_REPICKUP,
        COOLDOWN;

        public static AbandonmentMode bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "remove_forever" -> REMOVE_FOREVER;
                case "cooldown" -> COOLDOWN;
                default -> ALLOW_REPICKUP;
            };
        }
    }

    public record Tracker(
            String title,
            String titleKey,
            java.util.Map<String, Step> steps,
            java.util.Map<String, String> metadata,
            ResourceLocation icon,
            String color,
            String outlineColor,
            int priority,
            boolean hidden
    ) {
        public static final Tracker EMPTY = new Tracker("", "", java.util.Map.of(), java.util.Map.of(), null, "", "", 0, false);

        public Tracker(
                String title,
                String titleKey,
                java.util.Map<String, Step> steps,
                java.util.Map<String, String> metadata) {
            this(title, titleKey, steps, metadata, null, "", "", 0, false);
        }

        public Tracker {
            title = title == null ? "" : title;
            titleKey = titleKey == null ? "" : titleKey;
            steps = steps == null ? java.util.Map.of() : java.util.Map.copyOf(steps);
            metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
            color = color == null ? "" : color.trim();
            outlineColor = outlineColor == null ? "" : outlineColor.trim();
        }

        public Step step(String key, Step fallback) {
            if (key == null || key.isBlank()) {
                return fallback;
            }
            return this.steps.getOrDefault(key, fallback);
        }
    }

    public record Step(
            String text,
            String textKey,
            boolean showProgress,
            float progress,
            java.util.Map<String, String> metadata
    ) {
        public static final Step EMPTY = new Step("", "", false, -1.0F, java.util.Map.of());

        public Step {
            text = text == null ? "" : text;
            textKey = textKey == null ? "" : textKey;
            progress = Math.max(-1.0F, Math.min(1.0F, progress));
            metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        }
    }

    public record Stage(
            String id,
            List<String> objectives,
            List<StagePredicate> completeWhen,
            CompletionMode completionMode,
            int completionCount,
            String next,
            List<VillagerActionDefinition> entryActions,
            List<VillagerActionDefinition> exitActions,
            List<StageBranch> branches,
            List<BonusOutcome> bonuses,
            Map<String, String> metadata
    ) {
        public Stage {
            id = id == null ? "" : id.trim();
            objectives = objectives == null ? List.of() : List.copyOf(objectives);
            completeWhen = completeWhen == null ? List.of() : List.copyOf(completeWhen);
            completionMode = completionMode == null ? CompletionMode.ALL : completionMode;
            completionCount = Math.max(1, completionCount);
            next = next == null ? "" : next.trim();
            entryActions = entryActions == null ? List.of() : List.copyOf(entryActions);
            exitActions = exitActions == null ? List.of() : List.copyOf(exitActions);
            branches = branches == null ? List.of() : List.copyOf(branches);
            bonuses = bonuses == null ? List.of() : List.copyOf(bonuses);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        public Stage(
                String id,
                List<String> objectives,
                List<StagePredicate> completeWhen,
                CompletionMode completionMode,
                int completionCount,
                String next,
                List<VillagerActionDefinition> entryActions,
                List<VillagerActionDefinition> exitActions,
                List<StageBranch> branches,
                List<BonusOutcome> bonuses) {
            this(id, objectives, completeWhen, completionMode, completionCount, next,
                    entryActions, exitActions, branches, bonuses, Map.of());
        }

        public Stage(
                String id,
                List<String> objectives,
                List<StagePredicate> completeWhen,
                String next,
                List<VillagerActionDefinition> entryActions,
                List<VillagerActionDefinition> exitActions,
                List<StageBranch> branches) {
            this(id, objectives, completeWhen, CompletionMode.ALL, 1, next,
                    entryActions, exitActions, branches, List.of(), Map.of());
        }

        public boolean hasEntryActions() {
            return !this.entryActions.isEmpty();
        }

        public boolean hasExitActions() {
            return !this.exitActions.isEmpty();
        }
    }

    public enum CompletionMode {
        ALL,
        ANY,
        AT_LEAST;

        public static CompletionMode bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "any", "one", "one_of" -> ANY;
                case "at_least", "atleast", "count", "k_of_n" -> AT_LEAST;
                default -> ALL;
            };
        }
    }

    public record BonusOutcome(
            String id,
            List<StagePredicate> when,
            CompletionMode mode,
            int count,
            List<VillagerActionDefinition> actions
    ) {
        public BonusOutcome {
            id = id == null ? "" : id.trim();
            when = when == null ? List.of() : List.copyOf(when);
            mode = mode == null ? CompletionMode.ALL : mode;
            count = Math.max(1, count);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record StagePredicate(
            String objective,
            List<DialogueCondition> conditions
    ) {
        public StagePredicate {
            objective = objective == null ? "" : objective.trim();
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public boolean isEmpty() {
            return this.objective.isBlank() && this.conditions.isEmpty();
        }
    }

    public record StageBranch(
            String id,
            String label,
            String labelKey,
            List<DialogueCondition> conditions,
            List<VillagerActionDefinition> actions,
            String next,
            List<StageBranchBlocker> blockedBy
    ) {
        public StageBranch {
            id = id == null ? "" : id.trim();
            label = label == null ? "" : label;
            labelKey = labelKey == null ? "" : labelKey;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            actions = actions == null ? List.of() : List.copyOf(actions);
            next = next == null ? "" : next.trim();
            blockedBy = blockedBy == null ? List.of() : List.copyOf(blockedBy);
        }
    }

    public record StageBranchBlocker(
            List<DialogueCondition> conditions,
            String reason,
            String reasonKey
    ) {
        public StageBranchBlocker {
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            reason = reason == null ? "" : reason;
            reasonKey = reasonKey == null ? "" : reasonKey;
        }
    }

    public record Trigger(
            String id,
            TriggerEvent event,
            List<DialogueCondition> conditions,
            List<VillagerActionDefinition> actions,
            Set<String> stages,
            long cooldownTicks,
            double radius,
            boolean repeatable,
            int priority,
            double chance,
            boolean exclusive,
            int weight
    ) {
        private static final double DEFAULT_RADIUS = 10.0D;

        public Trigger(String id, TriggerEvent event, List<DialogueCondition> conditions,
                       List<VillagerActionDefinition> actions, Set<String> stages, long cooldownTicks,
                       double radius, boolean repeatable) {
            this(id, event, conditions, actions, stages, cooldownTicks, radius, repeatable, 0, 1.0D, false, 1);
        }

        public Trigger(String id, TriggerEvent event, List<DialogueCondition> conditions,
                       List<VillagerActionDefinition> actions, Set<String> stages, long cooldownTicks,
                       double radius, boolean repeatable, int priority, double chance, boolean exclusive) {
            this(id, event, conditions, actions, stages, cooldownTicks, radius, repeatable,
                    priority, chance, exclusive, 1);
        }

        public Trigger {
            id = id == null || id.isBlank() ? "trigger" : id;
            event = event == null ? TriggerEvent.PLAYER_TICK : event;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            actions = actions == null ? List.of() : List.copyOf(actions);
            stages = stages == null ? Set.of() : Set.copyOf(stages);
            cooldownTicks = Math.max(0L, cooldownTicks);
            radius = Double.isFinite(radius) && radius > 0.0D ? radius : DEFAULT_RADIUS;
            chance = Double.isFinite(chance) ? Math.max(0.0D, Math.min(1.0D, chance)) : 1.0D;
            weight = Math.max(0, Math.min(10_000, weight));
        }
    }

    public enum TriggerEvent {
        PLAYER_TICK,
        PROXIMITY,
        STARTED,
        PROGRESS,
        MOB_KILL,
        BLOCK_BREAK,
        BLOCK_PLACE,
        BLOCK_INTERACT,
        MEMORY_EVENT,
        GIFT,
        TRADE,
        REPUTATION,
        CRITERION,
        STAGE_CHANGED,
        COMPLETED,
        FAILED,
        ABANDONED,
        EXPIRED;

        public static TriggerEvent bySerializedName(String value) {
            return QuestTriggerRegistry.eventBySerializedName(value);
        }

        public boolean isContinuous() {
            return QuestTriggerRegistry.isContinuous(this);
        }
    }

    public record Dialogue(
            List<String> start,
            List<String> startKeys,
            List<String> reminder,
            List<String> reminderKeys,
            List<String> turnIn,
            List<String> turnInKeys,
            List<String> alreadyCompleted,
            List<String> alreadyCompletedKeys,
            List<String> unavailable,
            List<String> unavailableKeys,
            List<String> inactive,
            List<String> inactiveKeys,
            List<String> missingTarget,
            List<String> missingTargetKeys,
            List<String> missingProof,
            List<String> missingProofKeys,
            List<String> locateFailed,
            List<String> locateFailedKeys
    ) {
        public static final Dialogue EMPTY = new Dialogue(
                List.of("I do not have the details for that quest."),
                List.of("quest.dialogue.start"),
                List.of("I do not have the details for that quest."),
                List.of("quest.dialogue.reminder"),
                List.of("Thank you."),
                List.of("quest.dialogue.turn_in"),
                List.of("That matter is already settled."),
                List.of("quest.dialogue.already_completed"),
                List.of("This is not the right moment."),
                List.of("quest.dialogue.unavailable"),
                List.of("This is not the right moment."),
                List.of("quest.dialogue.inactive"),
                List.of("You have not reached the place I marked yet."),
                List.of("quest.dialogue.missing_target"),
                List.of("Bring back proof that you found it."),
                List.of("quest.dialogue.missing_proof"),
                List.of("I cannot get a clear reading on that place."),
                List.of("quest.dialogue.locate_failed")
        );

        public Dialogue {
            start = normalize(start, List.of("I do not have the details for that quest."));
            startKeys = normalizeKeys(startKeys);
            reminder = normalize(reminder, List.of("I do not have the details for that quest."));
            reminderKeys = normalizeKeys(reminderKeys);
            turnIn = normalize(turnIn, List.of("Thank you."));
            turnInKeys = normalizeKeys(turnInKeys);
            alreadyCompleted = normalize(alreadyCompleted, List.of("That matter is already settled."));
            alreadyCompletedKeys = normalizeKeys(alreadyCompletedKeys);
            unavailable = normalize(unavailable, List.of("This is not the right moment."));
            unavailableKeys = normalizeKeys(unavailableKeys);
            inactive = normalize(inactive, List.of("This is not the right moment."));
            inactiveKeys = normalizeKeys(inactiveKeys);
            missingTarget = normalize(missingTarget, List.of("You have not reached the place I marked yet."));
            missingTargetKeys = normalizeKeys(missingTargetKeys);
            missingProof = normalize(missingProof, List.of("Bring back proof that you found it."));
            missingProofKeys = normalizeKeys(missingProofKeys);
            locateFailed = normalize(locateFailed, List.of("I cannot get a clear reading on that place."));
            locateFailedKeys = normalizeKeys(locateFailedKeys);
        }

        public String selectStart(RandomSource random) {
            return selectStartText(random).text();
        }

        public SelectedText selectStartText(RandomSource random) {
            return select(this.start, this.startKeys, random);
        }

        public String selectReminder(RandomSource random) {
            return selectReminderText(random).text();
        }

        public SelectedText selectReminderText(RandomSource random) {
            return select(this.reminder, this.reminderKeys, random);
        }

        public String selectTurnIn(RandomSource random) {
            return selectTurnInText(random).text();
        }

        public SelectedText selectTurnInText(RandomSource random) {
            return select(this.turnIn, this.turnInKeys, random);
        }

        public String selectAlreadyCompleted(RandomSource random) {
            return selectAlreadyCompletedText(random).text();
        }

        public SelectedText selectAlreadyCompletedText(RandomSource random) {
            return select(this.alreadyCompleted, this.alreadyCompletedKeys, random);
        }

        public String selectUnavailable(RandomSource random) {
            return selectUnavailableText(random).text();
        }

        public SelectedText selectUnavailableText(RandomSource random) {
            return select(this.unavailable, this.unavailableKeys, random);
        }

        public String selectInactive(RandomSource random) {
            return selectInactiveText(random).text();
        }

        public SelectedText selectInactiveText(RandomSource random) {
            return select(this.inactive, this.inactiveKeys, random);
        }

        public String selectMissingTarget(RandomSource random) {
            return selectMissingTargetText(random).text();
        }

        public SelectedText selectMissingTargetText(RandomSource random) {
            return select(this.missingTarget, this.missingTargetKeys, random);
        }

        public String selectMissingProof(RandomSource random) {
            return selectMissingProofText(random).text();
        }

        public SelectedText selectMissingProofText(RandomSource random) {
            return select(this.missingProof, this.missingProofKeys, random);
        }

        public String selectLocateFailed(RandomSource random) {
            return selectLocateFailedText(random).text();
        }

        public SelectedText selectLocateFailedText(RandomSource random) {
            return select(this.locateFailed, this.locateFailedKeys, random);
        }

        private static List<String> normalize(List<String> lines, List<String> fallback) {
            if (lines == null || lines.isEmpty()) {
                return fallback;
            }
            List<String> normalized = lines.stream()
                    .filter(line -> line != null && !line.isBlank())
                    .toList();
            return normalized.isEmpty() ? fallback : List.copyOf(normalized);
        }

        private static List<String> normalizeKeys(List<String> keys) {
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            return keys.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .toList();
        }

        private static SelectedText select(List<String> lines, List<String> keys, RandomSource random) {
            String text = selectInline(lines, random);
            if (keys != null && !keys.isEmpty()) {
                return new SelectedText(text, selectInline(keys, random));
            }
            return new SelectedText(text, "");
        }

        private static String selectInline(List<String> lines, RandomSource random) {
            if (lines == null || lines.isEmpty()) {
                return "";
            }
            return lines.get(random.nextInt(lines.size()));
        }
    }

    public record SelectedText(String text, String key) {
        public SelectedText {
            text = text == null ? "" : text;
            key = key == null ? "" : key;
        }
    }
}
