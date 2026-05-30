package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;

public record QuestDefinition(
        ResourceLocation id,
        String title,
        String description,
        String questline,
        ResourceLocation parent,
        Offer offer,
        Target target,
        List<Objective> objectives,
        Rules rules,
        Tracker tracker,
        List<Trigger> triggers,
        Rewards rewards,
        Dialogue dialogue
) {
    public QuestDefinition {
        title = title == null || title.isBlank() ? id.toString() : title;
        description = description == null ? "" : description;
        questline = questline == null ? "" : questline;
        offer = offer == null ? Offer.any() : offer;
        target = target == null ? Target.EMPTY : target;
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        rules = rules == null ? Rules.DEFAULT : rules;
        tracker = tracker == null ? Tracker.EMPTY : tracker;
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        rewards = rewards == null ? Rewards.EMPTY : rewards;
        dialogue = dialogue == null ? Dialogue.EMPTY : dialogue;
    }

    public record Offer(
            Set<VillagerProfession> professions,
            int minVillagerLevel,
            Map<VillagerSkill, Integer> minSkills
    ) {
        public static Offer any() {
            return new Offer(Set.of(), 1, Map.of());
        }

        public Offer {
            professions = professions == null ? Set.of() : Set.copyOf(professions);
            minVillagerLevel = Math.max(1, Math.min(5, minVillagerLevel));
            minSkills = minSkills == null ? Map.of() : Map.copyOf(minSkills);
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
            return true;
        }
    }

    public record Target(
            ResourceLocation structure,
            List<String> pieces,
            int searchRadius,
            int discoveryRadius,
            ResourceLocation proofItem
    ) {
        public static final Target EMPTY = new Target(null, List.of(), 128, 128, null);

        public Target {
            pieces = pieces == null ? List.of() : List.copyOf(pieces);
            searchRadius = Math.max(1, searchRadius);
            discoveryRadius = Math.max(1, discoveryRadius);
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
            ResourceLocation memoryEvent
    ) {
        public static final Rewards EMPTY = new Rewards(0, 0, 0, null, null);
    }

    public record Objective(
            String id,
            ObjectiveType type,
            boolean optional,
            ResourceLocation structure,
            List<String> pieces,
            int searchRadius,
            int discoveryRadius,
            ResourceLocation item,
            int count,
            List<DialogueCondition> conditions,
            ObjectiveTracker tracker
    ) {
        public Objective {
            id = id == null || id.isBlank() ? "objective" : id;
            type = type == null ? ObjectiveType.CONDITION : type;
            pieces = pieces == null ? List.of() : List.copyOf(pieces);
            searchRadius = Math.max(1, searchRadius);
            discoveryRadius = Math.max(1, discoveryRadius);
            count = Math.max(1, count);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            tracker = tracker == null ? ObjectiveTracker.EMPTY : tracker;
        }
    }

    public record ObjectiveTracker(
            String text,
            String completeText,
            boolean showProgress,
            float progress,
            java.util.Map<String, String> metadata
    ) {
        public static final ObjectiveTracker EMPTY = new ObjectiveTracker("", "", true, -1.0F, java.util.Map.of());

        public ObjectiveTracker {
            text = text == null ? "" : text;
            completeText = completeText == null ? "" : completeText;
            progress = Math.max(-1.0F, Math.min(1.0F, progress));
            metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        }

        public boolean hasAnyDisplay() {
            return !this.text.isBlank()
                    || !this.completeText.isBlank()
                    || this.progress >= 0.0F
                    || !this.metadata.isEmpty();
        }

        public boolean hasActiveDisplay() {
            return !this.text.isBlank()
                    || this.progress >= 0.0F
                    || !this.metadata.isEmpty();
        }
    }

    public enum ObjectiveType {
        STRUCTURE_VISIT,
        ITEM_CHECK,
        CONDITION;

        public static ObjectiveType bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "structure_visit" -> STRUCTURE_VISIT;
                case "item_check" -> ITEM_CHECK;
                case "condition" -> CONDITION;
                default -> null;
            };
        }
    }

    public record Rules(
            boolean repeatable,
            boolean lockedToVillager,
            boolean crossVillagerCompatible,
            int maxStarts,
            int maxCompletions,
            long completionCooldownTicks,
            AbandonmentMode abandonment,
            long abandonmentCooldownTicks,
            boolean consumeOnCompletion,
            boolean consumeOnAbandonment,
            ActiveState activeState,
            Expiration expiration
    ) {
        public static final Rules DEFAULT = new Rules(
                false,
                true,
                false,
                1,
                1,
                0L,
                AbandonmentMode.ALLOW_REPICKUP,
                0L,
                false,
                false,
                ActiveState.DEFAULT,
                Expiration.DEFAULT
        );

        public Rules {
            maxStarts = Math.max(0, maxStarts);
            maxCompletions = Math.max(0, maxCompletions);
            completionCooldownTicks = Math.max(0L, completionCooldownTicks);
            abandonment = abandonment == null ? AbandonmentMode.ALLOW_REPICKUP : abandonment;
            abandonmentCooldownTicks = Math.max(0L, abandonmentCooldownTicks);
            activeState = activeState == null ? ActiveState.DEFAULT : activeState;
            expiration = expiration == null ? Expiration.DEFAULT : expiration;
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
            String notificationText
    ) {
        public static final Expiration DEFAULT = new Expiration(
                0L,
                List.of(),
                false,
                true,
                true,
                "quest.expired",
                "Quest expired: {quest}"
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
            java.util.Map<String, Step> steps,
            java.util.Map<String, String> metadata
    ) {
        public static final Tracker EMPTY = new Tracker("", java.util.Map.of(), java.util.Map.of());

        public Tracker {
            title = title == null ? "" : title;
            steps = steps == null ? java.util.Map.of() : java.util.Map.copyOf(steps);
            metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
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
            boolean showProgress,
            float progress,
            java.util.Map<String, String> metadata
    ) {
        public static final Step EMPTY = new Step("", false, 0.0F, java.util.Map.of());

        public Step {
            text = text == null ? "" : text;
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        }
    }

    public record Trigger(
            String id,
            TriggerEvent event,
            List<DialogueCondition> conditions,
            List<VillagerActionDefinition> actions,
            long cooldownTicks,
            double radius,
            boolean repeatable
    ) {
        private static final double DEFAULT_RADIUS = 10.0D;

        public Trigger {
            id = id == null || id.isBlank() ? "trigger" : id;
            event = event == null ? TriggerEvent.PLAYER_TICK : event;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            actions = actions == null ? List.of() : List.copyOf(actions);
            cooldownTicks = Math.max(0L, cooldownTicks);
            radius = Double.isFinite(radius) && radius > 0.0D ? radius : DEFAULT_RADIUS;
        }
    }

    public enum TriggerEvent {
        PLAYER_TICK,
        PROXIMITY,
        STARTED,
        PROGRESS,
        COMPLETED,
        ABANDONED,
        EXPIRED;

        public static TriggerEvent bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "player_tick" -> PLAYER_TICK;
                case "proximity" -> PROXIMITY;
                case "started" -> STARTED;
                case "progress" -> PROGRESS;
                case "completed" -> COMPLETED;
                case "abandoned" -> ABANDONED;
                case "expired" -> EXPIRED;
                default -> null;
            };
        }

        public boolean isContinuous() {
            return this == PLAYER_TICK || this == PROXIMITY;
        }
    }

    public record Dialogue(
            List<String> start,
            List<String> reminder,
            List<String> turnIn,
            List<String> alreadyCompleted,
            List<String> unavailable,
            List<String> inactive,
            List<String> missingTarget,
            List<String> missingProof,
            List<String> locateFailed
    ) {
        public static final Dialogue EMPTY = new Dialogue(
                List.of("I do not have the details for that quest."),
                List.of("I do not have the details for that quest."),
                List.of("Thank you."),
                List.of("That matter is already settled."),
                List.of("This is not the right moment."),
                List.of("This is not the right moment."),
                List.of("You have not reached the place I marked yet."),
                List.of("Bring back proof that you found it."),
                List.of("I cannot get a clear reading on that place.")
        );

        public Dialogue {
            start = normalize(start, List.of("I do not have the details for that quest."));
            reminder = normalize(reminder, List.of("I do not have the details for that quest."));
            turnIn = normalize(turnIn, List.of("Thank you."));
            alreadyCompleted = normalize(alreadyCompleted, List.of("That matter is already settled."));
            unavailable = normalize(unavailable, List.of("This is not the right moment."));
            inactive = normalize(inactive, List.of("This is not the right moment."));
            missingTarget = normalize(missingTarget, List.of("You have not reached the place I marked yet."));
            missingProof = normalize(missingProof, List.of("Bring back proof that you found it."));
            locateFailed = normalize(locateFailed, List.of("I cannot get a clear reading on that place."));
        }

        public String selectStart(RandomSource random) {
            return select(this.start, random);
        }

        public String selectReminder(RandomSource random) {
            return select(this.reminder, random);
        }

        public String selectTurnIn(RandomSource random) {
            return select(this.turnIn, random);
        }

        public String selectAlreadyCompleted(RandomSource random) {
            return select(this.alreadyCompleted, random);
        }

        public String selectUnavailable(RandomSource random) {
            return select(this.unavailable, random);
        }

        public String selectInactive(RandomSource random) {
            return select(this.inactive, random);
        }

        public String selectMissingTarget(RandomSource random) {
            return select(this.missingTarget, random);
        }

        public String selectMissingProof(RandomSource random) {
            return select(this.missingProof, random);
        }

        public String selectLocateFailed(RandomSource random) {
            return select(this.locateFailed, random);
        }

        private static List<String> normalize(List<String> lines, List<String> fallback) {
            if (lines == null || lines.isEmpty()) {
                return fallback;
            }
            return List.copyOf(lines.stream().filter(line -> line != null && !line.isBlank()).toList());
        }

        private static String select(List<String> lines, RandomSource random) {
            if (lines == null || lines.isEmpty()) {
                return "";
            }
            return lines.get(random.nextInt(lines.size()));
        }
    }
}
