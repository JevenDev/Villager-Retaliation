package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record ConversationDefinition(ResourceLocation id, int revision, String fingerprint,
        ResourceLocation campaign, String character, ResourceLocation quest, String titleKey,
        Map<String, Topic> topics, Map<String, Node> nodes, Map<String, Check> checks,
        Map<String, Outcome> outcomes, QuestBundlePath.Owner owner, QuestBundleTransactions.DefinitionSource source) {
    public ConversationDefinition {
        topics = ordered(topics);
        nodes = ordered(nodes);
        checks = ordered(checks);
        outcomes = ordered(outcomes);
    }

    private static <T> Map<String, T> ordered(Map<String, T> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public record Topic(String id, String labelKey, String start, List<DialogueCondition> conditions, String pointer) {
        public Topic { conditions = List.copyOf(conditions); }
    }

    public record Node(String id, List<Exchange> exchanges, List<Effect> effects,
            Map<String, Response> responses, String next, String outcome, String pointer) {
        public Node {
            exchanges = List.copyOf(exchanges);
            effects = List.copyOf(effects);
            responses = ordered(responses);
        }
    }

    public record Exchange(String id, Speaker speaker, String textKey, String pointer) {
    }

    public record Speaker(SpeakerKind kind, String role) {
    }

    public enum SpeakerKind { ROLE, PLAYER, NARRATOR }

    public record Response(String id, String labelKey, List<DialogueCondition> conditions, List<Effect> effects,
            String next, String check, String pointer) {
        public Response {
            conditions = List.copyOf(conditions);
            effects = List.copyOf(effects);
        }
    }

    public record Effect(String id, VillagerActionDefinition action, String pointer) {
    }

    public record Check(String id, int difficulty, String success, String failure, Social social, List<Modifier> modifiers, String pointer) {
        public Check {
            modifiers = List.copyOf(modifiers);
            if (modifiers.size() > ConversationCheckModifier.MAX_COUNT
                    || modifiers.stream().map(Modifier::id).distinct().count() != modifiers.size())
                throw new IllegalArgumentException("Invalid authored conversation modifiers");
        }

        public Check(String id, int difficulty, String success, String failure, Social social, String pointer) {
            this(id, difficulty, success, failure, social, List.of(), pointer);
        }

        public Check(String id, int difficulty, String success, String failure, String pointer) {
            this(id, difficulty, success, failure, null, List.of(), pointer);
        }
    }

    public record Modifier(String id, String labelKey, int amount, List<DialogueCondition> conditions, String pointer) {
        public Modifier {
            conditions = List.copyOf(conditions);
            if (id == null || id.length() > 256 || !id.matches("[a-z0-9][a-z0-9_./-]*")
                    || labelKey == null || labelKey.isBlank()
                    || amount < -ConversationCheckModifier.MAX_AMOUNT || amount > ConversationCheckModifier.MAX_AMOUNT)
                throw new IllegalArgumentException("Invalid authored conversation modifier");
        }
    }

    public record Social(ResourceLocation progression, PlayerSocialSkill skill, int experience) {
        public Social {
            if (progression == null || skill == null || experience < 0) throw new IllegalArgumentException("Invalid authored social check");
        }
    }

    public record Outcome(String id, String labelKey, String pointer) {
    }
}
