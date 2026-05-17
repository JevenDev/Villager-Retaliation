package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerDialogueService {
    private static final List<DialogueLine> LINES = createLines();

    private VillagerDialogueService() {
    }

    public static DialogueResult select(DialogueContext context, DialogueRequestType requestType, List<String> recentDialogueIds) {
        DialogueDisposition disposition = dispositionFor(context.reputationLevel());
        List<DialogueLine> candidates = LINES.stream()
                .filter(line -> line.matches(context, requestType, disposition))
                .sorted(Comparator.comparingInt(line -> recentDialogueIds.contains(line.id()) ? 1 : 0))
                .toList();
        if (candidates.isEmpty()) {
            candidates = LINES.stream()
                    .filter(line -> line.matches(context, requestType, DialogueDisposition.NEUTRAL))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return new DialogueResult("fallback", "They stare at you, unsure what to say.");
        }

        List<DialogueLine> freshCandidates = candidates.stream()
                .filter(line -> !recentDialogueIds.contains(line.id()))
                .toList();
        if (!freshCandidates.isEmpty()) {
            candidates = freshCandidates;
        }

        int totalWeight = candidates.stream().mapToInt(DialogueLine::weight).sum();
        int selected = context.random().nextInt(Math.max(1, totalWeight));
        for (DialogueLine candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) {
                return new DialogueResult(candidate.id(), candidate.text());
            }
        }

        DialogueLine fallback = candidates.getLast();
        return new DialogueResult(fallback.id(), fallback.text());
    }

    public static DialogueDisposition dispositionFor(VillagerReputationLevel reputationLevel) {
        return switch (reputationLevel) {
            case ROYALTY, REVERED -> DialogueDisposition.RESPECTFUL;
            case RESPECTED, TRUSTED -> DialogueDisposition.FRIENDLY;
            case NEUTRAL -> DialogueDisposition.NEUTRAL;
            case SUSPICIOUS -> DialogueDisposition.CAUTIOUS;
            case HOSTILE -> DialogueDisposition.RUDE;
            case DESPISED -> DialogueDisposition.HOSTILE;
            case FEARED -> DialogueDisposition.FEARFUL;
        };
    }

    private static List<DialogueLine> createLines() {
        List<DialogueLine> lines = new ArrayList<>();

        add(lines, "first_high", DialogueRequestType.GREETING, "So you're the one everyone keeps praising. I'm glad we finally met.")
                .firstConversationOnly().dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY).weight(40).build();
        add(lines, "first_neutral", DialogueRequestType.GREETING, "I don't think we've spoken before. Are you new around here?")
                .firstConversationOnly().dispositions(DialogueDisposition.NEUTRAL, DialogueDisposition.CAUTIOUS).weight(40).build();
        add(lines, "first_bad", DialogueRequestType.GREETING, "First time speaking, and already I know to keep my distance.")
                .firstConversationOnly().dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL).weight(40).build();

        add(lines, "greeting_high_safe", DialogueRequestType.GREETING, "Ah, there you are. The village feels safer when you're nearby.")
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY).build();
        add(lines, "greeting_high_welcome", DialogueRequestType.GREETING, "We've heard good things about you. Please, stay as long as you like.")
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY).build();
        add(lines, "greeting_neutral_need", DialogueRequestType.GREETING, "Need something?")
                .dispositions(DialogueDisposition.NEUTRAL, DialogueDisposition.CAUTIOUS).build();
        add(lines, "greeting_neutral_passing", DialogueRequestType.GREETING, "Passing through?")
                .dispositions(DialogueDisposition.NEUTRAL, DialogueDisposition.CAUTIOUS).build();
        add(lines, "greeting_low_nerve", DialogueRequestType.GREETING, "You have nerve showing your face here.")
                .dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE).build();
        add(lines, "greeting_low_quick", DialogueRequestType.GREETING, "Make it quick. I don't trust you.")
                .dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL).build();

        add(lines, "farmer_question_soil", DialogueRequestType.QUESTION, "The soil's been kind lately. That's more than I can say for some visitors.")
                .professions(VillagerProfession.FARMER).build();
        add(lines, "farmer_story_harvest", DialogueRequestType.STORY, "A good harvest keeps a village standing.")
                .professions(VillagerProfession.FARMER).build();
        add(lines, "weaponsmith_story_golem", DialogueRequestType.STORY, "The golem did good work last night. Still, I keep the blades sharp.")
                .professions(VillagerProfession.WEAPONSMITH).build();
        add(lines, "weaponsmith_question_trouble", DialogueRequestType.QUESTION, "Trouble always finds villages. Best be ready.")
                .professions(VillagerProfession.WEAPONSMITH).build();
        add(lines, "cleric_question_wounds", DialogueRequestType.QUESTION, "There are wounds that potions cannot mend.")
                .professions(VillagerProfession.CLERIC).build();
        add(lines, "cleric_story_omens", DialogueRequestType.STORY, "Bad omens linger longer than most people think.")
                .professions(VillagerProfession.CLERIC).build();
        add(lines, "librarian_question_memory", DialogueRequestType.QUESTION, "Villages remember more than people expect.")
                .professions(VillagerProfession.LIBRARIAN).build();
        add(lines, "librarian_story_records", DialogueRequestType.STORY, "Every trade, every kindness, every cruelty. Someone writes it down.")
                .professions(VillagerProfession.LIBRARIAN).build();
        add(lines, "fletcher_question_patrols", DialogueRequestType.QUESTION, "A good bow hears trouble before the bell does.")
                .professions(VillagerProfession.FLETCHER).build();
        add(lines, "butcher_story_supplies", DialogueRequestType.STORY, "Food decides whether a scare becomes a disaster.")
                .professions(VillagerProfession.BUTCHER).build();
        add(lines, "nitwit_question_thinking", DialogueRequestType.QUESTION, "I had a thought once. Left it somewhere safe, probably.")
                .professions(VillagerProfession.NITWIT).build();
        add(lines, "unemployed_question_general", DialogueRequestType.QUESTION, "Work is work. Finding the right work is the tricky part.")
                .professions(VillagerProfession.NONE).build();

        add(lines, "story_baby_born", DialogueRequestType.STORY, "There was a child born recently. A small bit of hope, that.")
                .eventTags(VillageEventMemory.EventTag.BABY_BORN).weight(35).build();
        add(lines, "story_golem_defense", DialogueRequestType.STORY, "Our golem crushed a monster near the edge of town. Good iron, that one.")
                .eventTags(VillageEventMemory.EventTag.IRON_GOLEM_DEFEATED_MOB, VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE).weight(35).build();
        add(lines, "story_villager_death", DialogueRequestType.STORY, "We lost someone recently. The village is quieter now.")
                .eventTags(VillageEventMemory.EventTag.VILLAGER_DEATH).weight(40).build();
        add(lines, "story_raid", DialogueRequestType.STORY, "The bells haven't sounded like that in a long time.")
                .eventTags(VillageEventMemory.EventTag.RAID).weight(40).build();
        add(lines, "question_attack", DialogueRequestType.QUESTION, "People remember raised hands. They remember lowered ones too.")
                .eventTags(VillageEventMemory.EventTag.VILLAGER_ATTACKED, VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER).weight(35).build();

        add(lines, "joke_farmer_carrot", DialogueRequestType.JOKE, "Why did the carrot blush? Too many eyes on it.")
                .professions(VillagerProfession.FARMER).build();
        add(lines, "joke_librarian_book", DialogueRequestType.JOKE, "I told a book a joke once. It was well-read, but not amused.")
                .professions(VillagerProfession.LIBRARIAN).build();
        add(lines, "joke_nitwit_thinking", DialogueRequestType.JOKE, "I tried thinking once. Terrible idea.")
                .professions(VillagerProfession.NITWIT).build();
        add(lines, "joke_general_bell", DialogueRequestType.JOKE, "The bell tells better jokes than I do. It always gets a ringing laugh.")
                .build();

        add(lines, "insult_low_zombies", DialogueRequestType.INSULT, "I've seen zombies with better manners.")
                .dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL).build();
        add(lines, "insult_low_trouble", DialogueRequestType.INSULT, "If trouble had a face, it would look familiar.")
                .dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL).build();
        add(lines, "insult_high_hero", DialogueRequestType.INSULT, "Careful, hero. Praise goes stale if you let it.")
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY).build();
        add(lines, "insult_high_watching", DialogueRequestType.INSULT, "You walk like someone who knows people are watching.")
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY).build();
        add(lines, "insult_neutral_hat", DialogueRequestType.INSULT, "I've traded with fence posts that listened better.")
                .dispositions(DialogueDisposition.NEUTRAL, DialogueDisposition.CAUTIOUS).build();

        add(lines, "question_general", DialogueRequestType.QUESTION, "A village is mostly small tasks, done before they become large problems.")
                .build();
        add(lines, "story_general", DialogueRequestType.STORY, "Once, the well ran dry for three days. Everyone learned how much a bucket matters.")
                .build();

        return List.copyOf(lines);
    }

    private static DialogueLine.Builder add(List<DialogueLine> lines, String id, DialogueRequestType requestType, String text) {
        return new AutoAddingBuilder(lines, id, requestType, text);
    }

    public record DialogueResult(String lineId, String text) {
    }

    private static final class AutoAddingBuilder extends DialogueLine.Builder {
        private final List<DialogueLine> lines;

        private AutoAddingBuilder(List<DialogueLine> lines, String id, DialogueRequestType requestType, String text) {
            super(id, requestType, text);
            this.lines = lines;
        }

        @Override
        public DialogueLine build() {
            DialogueLine line = super.build();
            this.lines.add(line);
            return line;
        }
    }
}
