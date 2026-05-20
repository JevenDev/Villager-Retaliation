package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.toucanlib.util.ToucanRandom;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerDialogueService {
    private static final List<DialogueLine> LINES = createLines();

    private VillagerDialogueService() {
    }

    public static DialogueResult select(DialogueContext context, DialogueRequestType requestType, List<String> recentDialogueIds) {
        if (context.villager().isBaby()) {
            return selectBabyLine(context, requestType);
        }
        if (requestType == DialogueRequestType.STORY) {
            Optional<DialogueResult> storyHint = VillagerStoryHintService.select(context);
            if (storyHint.isPresent()) {
                return storyHint.get();
            }
        }
        Optional<DialogueResult> giftMemory = selectGiftMemoryLine(context, requestType, recentDialogueIds);
        if (giftMemory.isPresent()) {
            return giftMemory.get();
        }

        DialogueDisposition disposition = moodFor(context);
        List<DialogueLine> candidates = LINES.stream()
                .filter(line -> line.matches(context, requestType, disposition))
                .sorted(Comparator.comparingInt(line -> recentDialogueIds.contains(line.id()) ? 1 : 0))
                .toList();
        candidates = preferDirectHitMemoryCandidates(context, requestType, candidates);
        candidates = preferBrokenBedMemoryCandidates(context, requestType, candidates);
        if (candidates.isEmpty()) {
            candidates = LINES.stream()
                    .filter(line -> line.matches(context, requestType, DialogueDisposition.NEUTRAL))
                    .toList();
            candidates = preferDirectHitMemoryCandidates(context, requestType, candidates);
            candidates = preferBrokenBedMemoryCandidates(context, requestType, candidates);
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

        int totalWeight = candidates.stream().mapToInt(VillagerDialogueService::effectiveWeight).sum();
        int selected = context.random().nextInt(Math.max(1, totalWeight));
        for (DialogueLine candidate : candidates) {
            selected -= effectiveWeight(candidate);
            if (selected < 0) {
                return new DialogueResult(candidate.id(), resolveText(candidate.text(), context));
            }
        }

        DialogueLine fallback = candidates.getLast();
        return new DialogueResult(fallback.id(), resolveText(fallback.text(), context));
    }

    public static String selectOpeningGreeting(DialogueContext context) {
        if (context.villager().isBaby()) {
            return selectBabyOpening(context);
        }
        Optional<String> giftMemory = selectOpeningGiftMemoryLine(context);
        if (giftMemory.isPresent()) {
            return giftMemory.get();
        }
        return selectConversationLine(context, "hello", globalHelloLines(context), professionHelloLines(context.profession()));
    }

    public static String selectClosingGoodbye(DialogueContext context) {
        if (context.villager().isBaby()) {
            return selectBabyGoodbye(context);
        }
        return selectConversationLine(context, "goodbye", globalGoodbyeLines(context), professionGoodbyeLines(context.profession()));
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

    public static DialogueDisposition moodFor(DialogueContext context) {
        DialogueDisposition baseline = dispositionFor(context.reputationLevel());
        if (context.hasRecentDirectHitMemory()) {
            return context.reputationLevel() == VillagerReputationLevel.FEARED
                    ? DialogueDisposition.FEARFUL
                    : lowerMood(baseline, DialogueDisposition.RUDE);
        }
        if (context.hasRecentBrokenBedMemory()) {
            return lowerMood(baseline, DialogueDisposition.RUDE);
        }
        if (context.hasRecentNegativeDialogueMoodMemory()) {
            return lowerMood(baseline, DialogueDisposition.CAUTIOUS);
        }
        if (context.badFirstImpression()) {
            return lowerMood(baseline, DialogueDisposition.CAUTIOUS);
        }
        if (context.hasRecentPositiveDialogueMoodMemory()) {
            return liftMood(baseline);
        }
        return baseline;
    }

    private static DialogueDisposition lowerMood(DialogueDisposition baseline, DialogueDisposition limit) {
        return moodRank(baseline) < moodRank(limit) ? baseline : limit;
    }

    private static DialogueDisposition liftMood(DialogueDisposition baseline) {
        return switch (baseline) {
            case RESPECTFUL, FRIENDLY -> baseline;
            case NEUTRAL, CAUTIOUS -> DialogueDisposition.FRIENDLY;
            case RUDE, HOSTILE -> DialogueDisposition.CAUTIOUS;
            case FEARFUL -> DialogueDisposition.FEARFUL;
        };
    }

    private static int moodRank(DialogueDisposition disposition) {
        return switch (disposition) {
            case RESPECTFUL -> 3;
            case FRIENDLY -> 2;
            case NEUTRAL -> 1;
            case CAUTIOUS -> 0;
            case RUDE -> -1;
            case HOSTILE -> -2;
            case FEARFUL -> -3;
        };
    }

    private static int effectiveWeight(DialogueLine line) {
        return line.weight() + line.specificityScore() * 8;
    }

    private static Optional<DialogueResult> selectGiftMemoryLine(
            DialogueContext context,
            DialogueRequestType requestType,
            List<String> recentDialogueIds) {
        if (requestType != DialogueRequestType.CHAT && requestType != DialogueRequestType.GREETING) {
            return Optional.empty();
        }
        int chance = requestType == DialogueRequestType.CHAT ? 45 : 35;
        if (context.random().nextInt(100) >= chance) {
            return Optional.empty();
        }

        Optional<VillageEventMemory.MemoryEvent> directGift = context.recentGiftToThisVillager();
        if (directGift.isPresent()) {
            String id = "gift_memory_direct_" + directGift.get().gift().reaction().name().toLowerCase();
            if (!recentDialogueIds.contains(id)) {
                return Optional.of(new DialogueResult(id, directGiftLine(directGift.get().gift(), context)));
            }
        }

        Optional<VillageEventMemory.MemoryEvent> villageGift = context.recentGiftToAnotherVillager();
        if (villageGift.isPresent()) {
            String id = "gift_memory_village_" + villageGift.get().gift().reaction().name().toLowerCase();
            if (!recentDialogueIds.contains(id)) {
                return Optional.of(new DialogueResult(id, villageGiftLine(villageGift.get().gift(), context)));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> selectOpeningGiftMemoryLine(DialogueContext context) {
        if (context.random().nextInt(100) >= 30) {
            return Optional.empty();
        }
        Optional<VillageEventMemory.MemoryEvent> directGift = context.recentGiftToThisVillager();
        if (directGift.isPresent()) {
            return Optional.of(directGiftLine(directGift.get().gift(), context));
        }
        Optional<VillageEventMemory.MemoryEvent> villageGift = context.recentGiftToAnotherVillager();
        return villageGift.map(memoryEvent -> villageGiftLine(memoryEvent.gift(), context));
    }

    private static String directGiftLine(VillageEventMemory.GiftMemory gift, DialogueContext context) {
        int variant = context.random().nextInt(4);
        return switch (gift.reaction()) {
            case LOVED -> switch (variant) {
                case 0 -> "Still thinking about the " + gift.itemName() + " you gave me. That was a real kindness.";
                case 1 -> "You came back. I was just telling someone about that " + gift.itemName() + ".";
                case 2 -> "A gift like that " + gift.itemName() + " is hard to forget.";
                default -> "After the " + gift.itemName() + " you brought me, you are welcome here.";
            };
            case LIKED -> switch (variant) {
                case 0 -> "That " + gift.itemName() + " you gave me was useful. Thank you again.";
                case 1 -> "I put your " + gift.itemName() + " to good use.";
                case 2 -> "You chose well with that " + gift.itemName() + ".";
                default -> "I remember the gift. Practical kindness still counts.";
            };
            case NEUTRAL -> switch (variant) {
                case 0 -> "I am still deciding what to do with that " + gift.itemName() + " you gave me.";
                case 1 -> "About that " + gift.itemName() + "... an unusual gift, but not unwelcome.";
                case 2 -> "You do give memorable gifts. Confusing, but memorable.";
                default -> "I kept the " + gift.itemName() + ". Maybe it will find a purpose.";
            };
            case DISLIKED -> switch (variant) {
                case 0 -> "I remember that " + gift.itemName() + ". Not fondly.";
                case 1 -> "If this is about another gift like that " + gift.itemName() + ", spare me.";
                case 2 -> "You gave me " + gift.itemName() + " and called it a gift. I am still sorting that out.";
                default -> "A poor gift says more than people think.";
            };
            case HATED -> switch (variant) {
                case 0 -> "I have not forgotten the " + gift.itemName() + " you handed me.";
                case 1 -> "After that " + gift.itemName() + ", casual chat feels generous of me.";
                case 2 -> "You called " + gift.itemName() + " a gift. I called it a warning.";
                default -> "Bring better offerings if you want better words.";
            };
        };
    }

    private static String villageGiftLine(VillageEventMemory.GiftMemory gift, DialogueContext context) {
        int variant = context.random().nextInt(4);
        String villagerName = gift.villagerName() == null || gift.villagerName().isBlank() ? "someone here" : gift.villagerName();
        return switch (gift.reaction()) {
            case LOVED -> switch (variant) {
                case 0 -> "I heard you gave " + villagerName + " " + gift.itemName() + ". That was kind of you.";
                case 1 -> villagerName + " has been talking about your " + gift.itemName() + ". In a good way.";
                case 2 -> "Word travels when someone brings a gift like " + gift.itemName() + ".";
                default -> "A generous gift to " + villagerName + " makes the whole village warmer to you.";
            };
            case LIKED -> switch (variant) {
                case 0 -> "I heard you gave " + villagerName + " " + gift.itemName() + ". Thoughtful enough.";
                case 1 -> villagerName + " seemed pleased with that " + gift.itemName() + ".";
                case 2 -> "A useful gift gets noticed around here.";
                default -> "People remember who brings helpful things.";
            };
            case NEUTRAL -> switch (variant) {
                case 0 -> "I heard about the " + gift.itemName() + " you gave " + villagerName + ". Interesting choice.";
                case 1 -> villagerName + " is still wondering what to do with your " + gift.itemName() + ".";
                case 2 -> "You have a strange sense for gifts, but no harm done.";
                default -> "The village has heard of odder gifts than that " + gift.itemName() + ".";
            };
            case DISLIKED -> switch (variant) {
                case 0 -> "I heard you gave " + villagerName + " " + gift.itemName() + ". That was not your finest moment.";
                case 1 -> villagerName + " did not sound pleased about that " + gift.itemName() + ".";
                case 2 -> "Bad gifts travel as gossip faster than good ones.";
                default -> "Careful what you offer people. We talk.";
            };
            case HATED -> switch (variant) {
                case 0 -> "I heard what you gave " + villagerName + ". Do not bring that sort of thing here.";
                case 1 -> "That " + gift.itemName() + " gift made people nervous.";
                case 2 -> "If you meant to scare " + villagerName + ", the village noticed.";
                default -> "Some gifts sound a lot like threats.";
            };
        };
    }

    private static List<DialogueLine> preferDirectHitMemoryCandidates(
            DialogueContext context,
            DialogueRequestType requestType,
            List<DialogueLine> candidates) {
        List<DialogueLine> directHitCandidates = memoryCandidates(
                candidates,
                context.hasRecentDirectHitMemory(),
                DialogueLine::requiresRecentDirectHitMemory
        );

        return switch (requestType) {
            case GREETING, QUESTION, INSULT -> directHitCandidates;
            case CHAT -> context.random().nextInt(100) < 45 ? directHitCandidates : candidates;
            default -> candidates;
        };
    }

    private static List<DialogueLine> preferBrokenBedMemoryCandidates(
            DialogueContext context,
            DialogueRequestType requestType,
            List<DialogueLine> candidates) {
        List<DialogueLine> brokenBedCandidates = memoryCandidates(
                candidates,
                context.hasRecentBrokenBedMemory(),
                DialogueLine::requiresRecentBrokenBedMemory
        );

        return switch (requestType) {
            case GREETING, QUESTION, CHAT, INSULT -> brokenBedCandidates;
            default -> candidates;
        };
    }

    private static List<DialogueLine> memoryCandidates(
            List<DialogueLine> candidates,
            boolean hasMemory,
            Predicate<DialogueLine> requirement) {
        if (!hasMemory || candidates.isEmpty()) {
            return candidates;
        }

        List<DialogueLine> memoryCandidates = candidates.stream()
                .filter(requirement)
                .toList();
        return memoryCandidates.isEmpty() ? candidates : memoryCandidates;
    }

    private static String resolveText(String text, DialogueContext context) {
        return text.replace("{attack_weapon}", context.rememberedAttackWeapon());
    }

    private static String selectConversationLine(
            DialogueContext context,
            String fallback,
            List<String> globalLines,
            List<String> professionLines) {
        List<String> candidates = new ArrayList<>(globalLines);
        candidates.addAll(professionLines);
        if (candidates.isEmpty()) {
            return fallback;
        }
        return ToucanRandom.choose(context.random(), candidates);
    }

    private static DialogueResult selectBabyLine(DialogueContext context, DialogueRequestType requestType) {
        String[] lines = babyLines(context, requestType);
        int index = ToucanRandom.index(context.random(), lines.length);
        return new DialogueResult("baby_" + requestType.name().toLowerCase() + "_" + index, lines[index]);
    }

    private static String selectBabyOpening(DialogueContext context) {
        String[] lines = switch (babyDisposition(context)) {
            case FRIENDLY -> new String[] {
                    "Hi! Did you come to visit?",
                    "Oh, it's you! I was just running around.",
                    "Hello! The grown-ups said you are nice."
            };
            case CAUTIOUS -> new String[] {
                    "Um. Are you here to talk?",
                    "I can talk, but I am staying close to home.",
                    "Hello. Please do not be scary."
            };
            case AFRAID -> new String[] {
                    "Please do not yell.",
                    "I am only little. What do you want?",
                    "I can listen, but then I am going back inside."
            };
        };
        return ToucanRandom.choose(context.random(), lines);
    }

    private static String selectBabyGoodbye(DialogueContext context) {
        String[] lines = switch (babyDisposition(context)) {
            case FRIENDLY -> new String[] {
                    "Bye! Come back later.",
                    "I have to go practice running now.",
                    "See you! I am going to tell someone I talked to you."
            };
            case CAUTIOUS -> new String[] {
                    "Okay. Goodbye.",
                    "I am going back near the grown-ups now.",
                    "Bye. Please be nice to everyone."
            };
            case AFRAID -> new String[] {
                    "Goodbye. I am leaving now.",
                    "Please let me go.",
                    "I am going to find someone older."
            };
        };
        return ToucanRandom.choose(context.random(), lines);
    }

    private static String[] babyLines(DialogueContext context, DialogueRequestType requestType) {
        BabyDisposition disposition = babyDisposition(context);
        return switch (requestType) {
            case CHAT -> babyChatLines(disposition);
            case GREETING -> babyGreetingLines(disposition);
            case QUESTION -> babyQuestionLines(context, disposition);
            case STORY -> babyStoryLines(context, disposition);
            case JOKE -> babyJokeLines(disposition);
            case INSULT -> babyInsultLines(disposition);
        };
    }

    private static BabyDisposition babyDisposition(DialogueContext context) {
        if (context.reputationLevel().trustRank() >= VillagerReputationLevel.TRUSTED.trustRank()) {
            return BabyDisposition.FRIENDLY;
        }
        if (context.reputationLevel().trustRank() <= VillagerReputationLevel.HOSTILE.trustRank()) {
            return BabyDisposition.AFRAID;
        }
        return BabyDisposition.CAUTIOUS;
    }

    private static String[] babyChatLines(BabyDisposition disposition) {
        return switch (disposition) {
            case FRIENDLY -> new String[] {
                    "I found a really good hiding place, but I cannot tell you or it stops being good.",
                    "The bell is loud up close. I learned that by mistake.",
                    "When I grow up, I might have the best job site. Maybe two.",
                    "I can run from one path to the other without touching the garden.",
                    "The grown-ups worry a lot. I try to help by not worrying."
            };
            case CAUTIOUS -> new String[] {
                    "I am not supposed to wander too far.",
                    "The paths are safer when everyone is watching.",
                    "I know where the doors are. That is important.",
                    "Sometimes I listen from behind the corner.",
                    "I like quiet visitors best."
            };
            case AFRAID -> new String[] {
                    "I do not want trouble.",
                    "The grown-ups told me to stay away from you.",
                    "I am going to stand where someone can see me.",
                    "Please do not make the bell ring.",
                    "I remember scary things, even when I try not to."
            };
        };
    }

    private static String[] babyGreetingLines(BabyDisposition disposition) {
        return switch (disposition) {
            case FRIENDLY -> new String[] {
                    "Hi again!",
                    "You came back!",
                    "Hello! I am faster today.",
                    "Good to see you. I think.",
                    "Want to hear what I learned?"
            };
            case CAUTIOUS -> new String[] {
                    "Hello.",
                    "Are you staying long?",
                    "I can say hello from here.",
                    "The grown-ups are nearby.",
                    "You look busy. Or maybe sneaky."
            };
            case AFRAID -> new String[] {
                    "Please stay there.",
                    "I see you.",
                    "I am not coming closer.",
                    "Do you need something?",
                    "I should probably go."
            };
        };
    }

    private static String[] babyQuestionLines(DialogueContext context, BabyDisposition disposition) {
        return switch (disposition) {
            case FRIENDLY -> new String[] {
                    "A question? I know three things: doors, bells, and which path has puddles.",
                    "The best place in the village is wherever nobody has chores for you.",
                    "If you ask a grown-up, they use more words. I use better ones.",
                    "I saw someone hide bread once. I am not saying who.",
                    timeAnswer(context)
            };
            case CAUTIOUS -> new String[] {
                    "I do not know much. I know enough to stay near home.",
                    "Ask a grown-up. They like sounding certain.",
                    "The village is safe when people are careful.",
                    "I know where to run if things get loud.",
                    weatherAnswer(context)
            };
            case AFRAID -> new String[] {
                    "I should not answer you.",
                    "Please ask someone else.",
                    "I know you have made people nervous.",
                    "The safest answer is no answer.",
                    "I am little, but I still notice who scares people."
            };
        };
    }

    private static String[] babyStoryLines(DialogueContext context, BabyDisposition disposition) {
        return switch (disposition) {
            case FRIENDLY -> new String[] {
                    "Once I ran all the way around the well and did not fall in. That is the whole story, but it was very exciting.",
                    "I saw a grown-up lose their work hat. They pretended they meant to put it there.",
                    "One time the bell rang and everyone moved at once. It looked like a dance, except worried.",
                    "I had a dream I was tall enough to see over every fence.",
                    recentBabyStory(context)
            };
            case CAUTIOUS -> new String[] {
                    "There is a story about staying close when strangers arrive. I like that one.",
                    "I once heard footsteps outside at night. In the morning everyone said it was nothing, but nobody sounded sure.",
                    "A path can feel very long when you are small.",
                    "The grown-ups tell stories after scary days. They make the endings softer.",
                    recentBabyStory(context)
            };
            case AFRAID -> new String[] {
                    "I do not want to tell stories right now.",
                    "The scary stories sound too much like real ones lately.",
                    "Maybe another day, when everyone feels safer.",
                    "I know a story about hiding, but I am using it.",
                    "Some stories are for people who have been kinder."
            };
        };
    }

    private static String[] babyJokeLines(BabyDisposition disposition) {
        return switch (disposition) {
            case FRIENDLY -> new String[] {
                    "Why did the bell ring? Because it had something important to say!",
                    "I made up a joke, but I forgot the middle. The ending is me laughing.",
                    "A grown-up told me a work joke. I did not understand it, so it must be very advanced.",
                    "If I stand very still, chores cannot see me.",
                    "That was funny. Or I decided it was."
            };
            case CAUTIOUS -> new String[] {
                    "That is a joke? I think I need practice.",
                    "I might laugh if it is not mean.",
                    "Grown-up jokes have too many job sites in them.",
                    "I know one joke, but it only works if you are short.",
                    "Was that the funny part?"
            };
            case AFRAID -> new String[] {
                    "I do not feel like laughing.",
                    "Please do not make the joke scary.",
                    "That sounded like a trick.",
                    "Maybe tell that to someone older.",
                    "I can smile a little. That is all."
            };
        };
    }

    private static String[] babyInsultLines(BabyDisposition disposition) {
        return switch (disposition) {
            case FRIENDLY -> new String[] {
                    "That was not nice.",
                    "Hey. I thought we were talking kindly.",
                    "I am telling a grown-up you said that.",
                    "You should use better words.",
                    "I do not like that game."
            };
            case CAUTIOUS -> new String[] {
                    "That is mean.",
                    "Please stop.",
                    "I knew I should have stayed farther away.",
                    "Why would you say that?",
                    "I am leaving if you keep doing that."
            };
            case AFRAID -> new String[] {
                    "Please do not.",
                    "I want to go now.",
                    "You are being scary.",
                    "I did not do anything to you.",
                    "The village will hear about this."
            };
        };
    }

    private static String timeAnswer(DialogueContext context) {
        return switch (context.timeOfDay()) {
            case MORNING -> "Morning is best because nobody has remembered all their chores yet.";
            case AFTERNOON -> "Afternoon is when everyone walks faster.";
            case EVENING -> "Evening means doors, supper smells, and grown-ups counting heads.";
            case NIGHT -> "Night is when I am supposed to be inside. Very inside.";
        };
    }

    private static String weatherAnswer(DialogueContext context) {
        return switch (context.weather()) {
            case CLEAR -> "The sky is behaving today. That helps.";
            case RAIN -> "Rain makes puddles, and puddles are important if nobody is watching.";
            case THUNDER -> "Thunder is too loud. I do not like when the sky shouts.";
        };
    }

    private static String recentBabyStory(DialogueContext context) {
        if (context.hasRecentEvent(VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE)) {
            return "Everyone talked about the fighting. I liked the part where the village was still here after.";
        }
        if (context.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH)) {
            return "People got quiet recently. I do not like that kind of story.";
        }
        if (context.hasRecentEvent(VillageEventMemory.EventTag.BABY_BORN)) {
            return "There is another little one around. Everyone pretends not to smile too much.";
        }
        return "Yesterday I learned that grown-ups say 'soon' when they do not know.";
    }

    private static List<String> globalHelloLines(DialogueContext context) {
        return switch (moodFor(context)) {
            case RESPECTFUL, FRIENDLY -> List.of(
                    "Good to see you. What can I do for you?",
                    "Welcome back. The village has room for a friendly face."
            );
            case CAUTIOUS, RUDE -> List.of(
                    "Say what you came to say.",
                    "Keep this brief, and keep it civil."
            );
            case HOSTILE -> List.of(
                    "You should make this quick.",
                    "I am listening, against my better judgment."
            );
            case FEARFUL -> List.of(
                    "Please, just say what you need.",
                    "I am listening. Carefully."
            );
            default -> List.of(
                    "Need something?",
                    "Hello. What brings you here?"
            );
        };
    }

    private static List<String> globalGoodbyeLines(DialogueContext context) {
        return switch (moodFor(context)) {
            case RESPECTFUL, FRIENDLY -> List.of(
                    "Safe travels. Come by again.",
                    "Take care out there."
            );
            case CAUTIOUS, RUDE -> List.of(
                    "Good. That is enough for now.",
                    "We're done here."
            );
            case HOSTILE -> List.of(
                    "Leave, then.",
                    "Try not to make the village regret this conversation."
            );
            case FEARFUL -> List.of(
                    "Goodbye. Please let that be all.",
                    "Go safely. Away from here, preferably."
            );
            default -> List.of(
                    "Goodbye.",
                    "Until next time."
            );
        };
    }

    private static List<String> professionHelloLines(VillagerProfession profession) {
        ProfessionDialogue profile = profileFor(profession);
        if (profile == null) {
            return List.of();
        }
        return List.of(
                "Mind the " + profile.workplace() + ". What do you need?",
                "I was just working on " + profile.craft() + ". You have a moment?"
        );
    }

    private static List<String> professionGoodbyeLines(VillagerProfession profession) {
        ProfessionDialogue profile = profileFor(profession);
        if (profile == null) {
            return List.of();
        }
        return List.of(
                "Back to " + profile.craft() + ", then.",
                "If you need the " + profile.role() + ", you know where to find me."
        );
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
        add(lines, "greeting_hit_apology", DialogueRequestType.GREETING, "Here to apologize for attacking me?")
                .requiresRecentDirectHitMemory()
                .dispositions(DialogueDisposition.CAUTIOUS, DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(44)
                .build();
        add(lines, "greeting_bed_break_1", DialogueRequestType.GREETING, "You smashed my bed. Why would I welcome you after that?")
                .requiresRecentBrokenBedMemory()
                .dispositions(DialogueDisposition.CAUTIOUS, DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(46)
                .build();
        add(lines, "greeting_bed_break_2", DialogueRequestType.GREETING, "A broken bed makes for a poor night's sleep. I have not forgotten.")
                .requiresRecentBrokenBedMemory()
                .dispositions(DialogueDisposition.CAUTIOUS, DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(46)
                .build();
        add(lines, "greeting_hit_bruise", DialogueRequestType.GREETING, "Still have a bruise from when you hit me with that {attack_weapon}.")
                .requiresRecentDirectHitMemory()
                .dispositions(DialogueDisposition.CAUTIOUS, DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(44)
                .build();

        add(lines, "chat_respectful_welcome", DialogueRequestType.CHAT, "Stay a while. It's nice having someone around who doesn't make the village tense.")
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY).weight(34).build();
        add(lines, "chat_respectful_daily_life", DialogueRequestType.CHAT, "Most days are chores, bells, and neighbors. Good company improves all three.")
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY).weight(34).build();
        add(lines, "chat_neutral_routine", DialogueRequestType.CHAT, "Nothing dramatic today. Just work, weather, and trying to stay ahead of both.")
                .dispositions(DialogueDisposition.NEUTRAL, DialogueDisposition.CAUTIOUS).weight(34).build();
        add(lines, "chat_neutral_street", DialogueRequestType.CHAT, "You hear a lot just standing in the street for a few minutes.")
                .dispositions(DialogueDisposition.NEUTRAL, DialogueDisposition.CAUTIOUS).weight(34).build();
        add(lines, "chat_rude_brisk", DialogueRequestType.CHAT, "If this is casual conversation, make it unusually good.")
                .dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE).weight(34).build();
        add(lines, "chat_rude_short", DialogueRequestType.CHAT, "I've got work. Talk fast or talk elsewhere.")
                .dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE).weight(34).build();
        add(lines, "chat_fearful_quiet", DialogueRequestType.CHAT, "If we're just talking, keep it calm. I prefer calm.")
                .dispositions(DialogueDisposition.FEARFUL).weight(34).build();
        add(lines, "chat_fearful_smalltalk", DialogueRequestType.CHAT, "Small talk is easier when nobody is shouting.")
                .dispositions(DialogueDisposition.FEARFUL).weight(34).build();
        add(lines, "chat_hit_memory", DialogueRequestType.CHAT, "Casual conversation is harder when I remember getting struck with that {attack_weapon}.")
                .requiresRecentDirectHitMemory()
                .weight(52)
                .build();
        add(lines, "chat_hit_memory_2", DialogueRequestType.CHAT, "You hit me once already. Makes friendly chatter feel a bit thin.")
                .requiresRecentDirectHitMemory()
                .weight(52)
                .build();
        add(lines, "chat_hit_memory_3", DialogueRequestType.CHAT, "We can call this a chat, but I still remember that {attack_weapon} connecting.")
                .requiresRecentDirectHitMemory()
                .weight(52)
                .build();
        add(lines, "chat_hit_memory_4", DialogueRequestType.CHAT, "Hard to make small talk when I'm still thinking about being hit with your {attack_weapon}.")
                .requiresRecentDirectHitMemory()
                .weight(34)
                .build();
        add(lines, "chat_bed_break_1", DialogueRequestType.CHAT, "It's difficult to relax around someone who tears apart a sleeping villager's bed.")
                .requiresRecentBrokenBedMemory()
                .weight(42)
                .build();
        add(lines, "chat_bed_break_2", DialogueRequestType.CHAT, "I slept badly after you broke my bed. That sort of thing lingers.")
                .requiresRecentBrokenBedMemory()
                .weight(42)
                .build();
        add(lines, "chat_general_well", DialogueRequestType.CHAT, "The well, the paths, the crops, the gossip. That's a village, more or less.")
                .weight(28).build();
        add(lines, "chat_general_day", DialogueRequestType.CHAT, "Some days all anyone wants is a quiet street and a door that stays shut at night.")
                .weight(28).build();

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
        add(lines, "greeting_player_defended_village_1", DialogueRequestType.GREETING, "Thanks for fending off the mobs. The village is still standing because of it.")
                .playerEventTags(VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE)
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY, DialogueDisposition.NEUTRAL)
                .weight(42)
                .build();
        add(lines, "greeting_player_defended_village_2", DialogueRequestType.GREETING, "That zombie had it coming. You saved our skin.")
                .playerEventTags(VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE)
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY, DialogueDisposition.NEUTRAL)
                .weight(42)
                .build();
        add(lines, "story_player_defended_village_1", DialogueRequestType.STORY, "People are still talking about how you fought for us when the mobs closed in.")
                .playerEventTags(VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE)
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY, DialogueDisposition.NEUTRAL)
                .weight(40)
                .build();
        add(lines, "story_player_defended_village_2", DialogueRequestType.STORY, "We remember who stood between this village and the dark. That was you.")
                .playerEventTags(VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE)
                .dispositions(DialogueDisposition.RESPECTFUL, DialogueDisposition.FRIENDLY)
                .weight(40)
                .build();
        add(lines, "story_villager_death", DialogueRequestType.STORY, "We lost someone recently. The village is quieter now.")
                .eventTags(VillageEventMemory.EventTag.VILLAGER_DEATH).weight(40).build();
        add(lines, "story_raid", DialogueRequestType.STORY, "The bells haven't sounded like that in a long time.")
                .eventTags(VillageEventMemory.EventTag.RAID).weight(40).build();
        add(lines, "question_attack", DialogueRequestType.QUESTION, "People remember raised hands. They remember lowered ones too.")
                .eventTags(VillageEventMemory.EventTag.VILLAGER_ATTACKED, VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER).weight(35).build();
        add(lines, "question_attack_weapon", DialogueRequestType.QUESTION, "Why should I answer calmly when the last thing you brought me was a {attack_weapon}?")
                .requiresRecentDirectHitMemory()
                .dispositions(DialogueDisposition.CAUTIOUS, DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(40)
                .build();
        add(lines, "question_bed_break_1", DialogueRequestType.QUESTION, "You broke the bed I was sleeping in. Start there if you want answers.")
                .requiresRecentBrokenBedMemory()
                .dispositions(DialogueDisposition.CAUTIOUS, DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(42)
                .build();
        add(lines, "question_bed_break_2", DialogueRequestType.QUESTION, "Ask me something useful, like whether smashing my bed was worth it.")
                .requiresRecentBrokenBedMemory()
                .dispositions(DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(42)
                .build();

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
        add(lines, "insult_after_hit", DialogueRequestType.INSULT, "You've already made your point with that {attack_weapon}. Use words for once.")
                .requiresRecentDirectHitMemory()
                .dispositions(DialogueDisposition.CAUTIOUS, DialogueDisposition.RUDE, DialogueDisposition.HOSTILE, DialogueDisposition.FEARFUL)
                .weight(40)
                .build();

        add(lines, "question_general", DialogueRequestType.QUESTION, "A village is mostly small tasks, done before they become large problems.")
                .build();
        add(lines, "story_general", DialogueRequestType.STORY, "Once, the well ran dry for three days. Everyone learned how much a bucket matters.")
                .build();

        addProfessionChatLines(lines);
        addVillageLifeLines(lines);
        addGlobalMoodLines(lines);
        addProfessionMoodLines(lines);
        addWeatherLines(lines);
        addTimeOfDayLines(lines);

        return List.copyOf(lines);
    }

    private static void addProfessionChatLines(List<DialogueLine> lines) {
        for (ProfessionDialogue profile : professionProfiles()) {
            add(lines, profile.key() + "_chat_work", DialogueRequestType.CHAT,
                    "Most of my day is " + profile.craft() + ". It sounds simple until everything depends on it.")
                    .professions(profile.profession())
                    .weight(30)
                    .build();
            add(lines, profile.key() + "_chat_pride", DialogueRequestType.CHAT,
                    "A good " + profile.role() + " thinks about " + profile.pride() + " even when nobody notices.")
                    .professions(profile.profession())
                    .weight(30)
                    .build();
        }
    }

    private static void addVillageLifeLines(List<DialogueLine> lines) {
        for (DialogueDisposition disposition : DialogueDisposition.values()) {
            for (int index = 0; index < 12; index++) {
                add(lines, "village_life_" + disposition.name().toLowerCase() + "_" + index, requestForVillageLifeIndex(index), villageLifeText(disposition, index))
                        .dispositions(disposition)
                        .weight(20)
                        .build();
            }
        }
    }

    private static DialogueRequestType requestForVillageLifeIndex(int index) {
        return switch (index % 6) {
            case 0 -> DialogueRequestType.CHAT;
            case 1 -> DialogueRequestType.GREETING;
            case 2 -> DialogueRequestType.QUESTION;
            case 3 -> DialogueRequestType.STORY;
            case 4 -> DialogueRequestType.JOKE;
            default -> DialogueRequestType.INSULT;
        };
    }

    private static String villageLifeText(DialogueDisposition disposition, int index) {
        return switch (disposition) {
            case RESPECTFUL -> switch (index) {
                case 0 -> "When you visit, even ordinary work feels less fragile.";
                case 1 -> "There you are. The square brightens when trusted feet cross it.";
                case 2 -> "Ask what you like. A good neighbor earns plain answers.";
                case 3 -> "The best days here are quiet because someone kept them that way.";
                case 4 -> "A village joke from you usually lands softer than a bell and better than a tax.";
                case 5 -> "Even admired friends can step in the wrong furrow.";
                case 6 -> "People notice who checks the roads before checking the prices.";
                case 7 -> "Welcome. We have work, gossip, and fewer worries when you are near.";
                case 8 -> "You want the truth? Villages survive on habit, help, and a little stubbornness.";
                case 9 -> "I remember a season when kindness arrived late. Yours comes earlier.";
                case 10 -> "If this is another joke, I am already giving it generous odds.";
                default -> "Careful with that tongue. Respect is warm, not fireproof.";
            };
            case FRIENDLY -> switch (index) {
                case 0 -> "Good company makes even sweeping the path feel like progress.";
                case 1 -> "Hello again. You are becoming part of the village noise.";
                case 2 -> "Ask away. I have a minute before the next chore finds me.";
                case 3 -> "Once the well rope snapped at dawn. Everyone became an expert at once.";
                case 4 -> "If the punchline has emeralds, I am listening with professional interest.";
                case 5 -> "That was sharper than friendly talk needs to be.";
                case 6 -> "A familiar face saves everyone the trouble of worrying first.";
                case 7 -> "You arrive often enough that people have started guessing your errands.";
                case 8 -> "Most answers here begin with 'it depends who saw it.'";
                case 9 -> "One good favor can echo through three houses by supper.";
                case 10 -> "Tell it, then. I have heard worse from tired traders.";
                default -> "Mind yourself. Liking you is not the same as ignoring everything.";
            };
            case NEUTRAL -> switch (index) {
                case 0 -> "The village keeps moving. People eat, work, argue, sleep, repeat.";
                case 1 -> "Hello. Keep to the paths and we will get along.";
                case 2 -> "Questions are welcome when they come with patience.";
                case 3 -> "A missing bucket once caused more debate here than a raid warning.";
                case 4 -> "Go on. I can spare one laugh if it is sturdy.";
                case 5 -> "That sounded like practice for a worse insult.";
                case 6 -> "Most days are ordinary. Ordinary is underrated.";
                case 7 -> "Passing through? Then pass through politely.";
                case 8 -> "A village is smaller than it looks and louder than it sounds.";
                case 9 -> "Everyone has a story here. Most are about weather, work, or doors.";
                case 10 -> "If I laugh, it counts as luck, not permission.";
                default -> "Careful. Neutral ground can tilt.";
            };
            case CAUTIOUS -> switch (index) {
                case 0 -> "People have started watching where you stand.";
                case 1 -> "Say hello if you must. Keep your hands easy to see.";
                case 2 -> "Why ask me? There are safer questions and safer questioners.";
                case 3 -> "The village has learned to turn small warnings into habits.";
                case 4 -> "A joke might help. A cruel one will not.";
                case 5 -> "That is the sort of remark that gets repeated indoors.";
                case 6 -> "Trust is not gone, but it is checking the exits.";
                case 7 -> "You are welcome to speak. That is not the same as being welcome everywhere.";
                case 8 -> "We keep lists in our heads, even when nobody writes them down.";
                case 9 -> "Once a stranger smiled through every lie. People remember that.";
                case 10 -> "Make it kind. I am measuring more than the joke.";
                default -> "That did not help your case.";
            };
            case RUDE -> switch (index) {
                case 0 -> "I have had friendlier conversations with locked doors.";
                case 1 -> "You again. Make the interruption worthwhile.";
                case 2 -> "Answers cost trust, and you are short on coin.";
                case 3 -> "There is a story here about people who wore out their welcome. Guess the ending.";
                case 4 -> "A joke from you? Fine. Surprise me by not making it worse.";
                case 5 -> "You mistake patience for softness.";
                case 6 -> "The village works harder when you are around. Not in the good way.";
                case 7 -> "Speak, then. I was almost enjoying the quiet.";
                case 8 -> "Every house here has a door. You are why people remember that.";
                case 9 -> "Once we ignored a warning sign. We are less sentimental now.";
                case 10 -> "That joke limped in and blamed the road.";
                default -> "Careful. Even words can start bells ringing.";
            };
            case HOSTILE -> switch (index) {
                case 0 -> "The village would breathe easier if you left.";
                case 1 -> "Do not confuse my answer with welcome.";
                case 2 -> "Why would anyone here help you understand us?";
                case 3 -> "Our latest story is about keeping trouble at the edge of town.";
                case 4 -> "I am sure you find yourself hilarious. That makes one of us.";
                case 5 -> "You are standing on borrowed patience.";
                case 6 -> "People lower their voices when you pass. That is not respect.";
                case 7 -> "Make it fast. The village has endured enough of you.";
                case 8 -> "The answer is no, unless the question is whether we remember.";
                case 9 -> "There are names we speak as warnings. Yours is getting close.";
                case 10 -> "Nothing you say sounds harmless anymore.";
                default -> "Say less. Leave more.";
            };
            case FEARFUL -> switch (index) {
                case 0 -> "I am answering because refusing feels worse.";
                case 1 -> "Hello. Please keep your distance.";
                case 2 -> "I do not know what answer will keep things calm.";
                case 3 -> "Fear makes every ordinary sound seem planned.";
                case 4 -> "I can try to laugh if that is what you want.";
                case 5 -> "Please do not aim your cruelty here.";
                case 6 -> "When you arrive, people count who is nearby.";
                case 7 -> "I am listening. Carefully. Too carefully.";
                case 8 -> "The safest answer is short.";
                case 9 -> "Some stories get quiet in the telling. Yours does that.";
                case 10 -> "Was that a joke? I cannot always tell with you.";
                default -> "Please stop before someone gets hurt.";
            };
        };
    }

    private static void addTimeOfDayLines(List<DialogueLine> lines) {
        addGlobalTimeOfDayLines(lines);
        addProfessionTimeOfDayLines(lines);
    }

    private static void addGlobalTimeOfDayLines(List<DialogueLine> lines) {
        for (DialogueContext.TimeOfDay timeOfDay : DialogueContext.TimeOfDay.values()) {
            for (int index = 0; index < 5; index++) {
                add(lines, "time_" + timeOfDay.name().toLowerCase() + "_global_" + index, requestForIndex(index), globalTimeOfDayText(timeOfDay, index))
                        .timeOfDays(timeOfDay)
                        .weight(24)
                        .build();
            }
        }
    }

    private static void addProfessionTimeOfDayLines(List<DialogueLine> lines) {
        for (ProfessionDialogue profile : professionProfiles()) {
            for (DialogueContext.TimeOfDay timeOfDay : DialogueContext.TimeOfDay.values()) {
                for (int index = 0; index < 5; index++) {
                    add(lines, profile.key() + "_" + timeOfDay.name().toLowerCase() + "_" + index, requestForIndex(index), professionTimeOfDayText(profile, timeOfDay, index))
                            .professions(profile.profession())
                            .timeOfDays(timeOfDay)
                            .weight(30)
                            .build();
                }
            }
        }
    }

    private static String globalTimeOfDayText(DialogueContext.TimeOfDay timeOfDay, int index) {
        return switch (timeOfDay) {
            case MORNING -> switch (index) {
                case 0 -> "Morning. The village is still deciding what kind of day this will be.";
                case 1 -> "Early questions get better answers than late ones.";
                case 2 -> "Mornings make every chore look possible for about ten minutes.";
                case 3 -> "A morning joke? Bold. Some of us are still waking up.";
                default -> "Careful with sharp words before breakfast.";
            };
            case AFTERNOON -> switch (index) {
                case 0 -> "Afternoon already. The day's work has teeth now.";
                case 1 -> "Ask quickly. This is when tasks start chasing each other.";
                case 2 -> "By afternoon, every village knows what it forgot in the morning.";
                case 3 -> "If your joke is good, it might carry me to supper.";
                default -> "That's an afternoon sort of insult: tired, but still pointed.";
            };
            case EVENING -> switch (index) {
                case 0 -> "Evening's coming. People start counting doors and beds.";
                case 1 -> "Ask now if you must. Soon everyone will be heading in.";
                case 2 -> "Evening makes the village honest. You can hear who is worried.";
                case 3 -> "A joke before dusk? Fine, but keep it friendly.";
                default -> "Not every thought needs to follow someone into the evening.";
            };
            case NIGHT -> switch (index) {
                case 0 -> "Keep your voice down. Night belongs to sleepers and things we avoid.";
                case 1 -> "Questions at night feel heavier.";
                case 2 -> "At night, every sound outside the door becomes a story.";
                case 3 -> "A joke after dark? It had better be quiet.";
                default -> "Insults travel farther at night. So do consequences.";
            };
        };
    }

    private static String professionTimeOfDayText(ProfessionDialogue profile, DialogueContext.TimeOfDay timeOfDay, int index) {
        return switch (timeOfDay) {
            case MORNING -> switch (index) {
                case 0 -> "Morning at the " + profile.workplace() + ". Best time to start " + profile.craft() + ".";
                case 1 -> "Ask now. A " + profile.role() + "'s patience is freshest before the work piles up.";
                case 2 -> "Every morning, " + profile.pride() + " starts as a plan and becomes a negotiation.";
                case 3 -> profile.joke() + " That one is easier before I'm tired.";
                default -> "Mock a " + profile.role() + " before breakfast and see how short the day gets.";
            };
            case AFTERNOON -> switch (index) {
                case 0 -> "Afternoon at the " + profile.workplace() + " is when mistakes get expensive.";
                case 1 -> "If this is about " + profile.craft() + ", ask before my hands are full again.";
                case 2 -> "By now, " + profile.concern() + " has either appeared or is waiting until I relax.";
                case 3 -> "A joke now? Good. The " + profile.workplace() + " could use one.";
                default -> "I've spent all day on " + profile.pride() + ". Choose your words well.";
            };
            case EVENING -> switch (index) {
                case 0 -> "Evening means cleaning the " + profile.workplace() + " and hoping nothing was missed.";
                case 1 -> "Questions about " + profile.warning() + " feel different near dusk.";
                case 2 -> "A " + profile.role() + " measures the day by what can safely wait until morning.";
                case 3 -> "If your joke involves " + profile.gift() + ", tell it before everyone goes inside.";
                default -> "Evening is a poor time to insult the work that got us through the day.";
            };
            case NIGHT -> switch (index) {
                case 0 -> "Night is no hour for the " + profile.workplace() + ". Speak softly.";
                case 1 -> "Ask tomorrow about " + profile.craft() + ". Night makes bad answers.";
                case 2 -> "When it gets dark, " + profile.warning() + " starts sounding too close.";
                case 3 -> "A quiet joke, then. The sleeping folk outrank both of us.";
                default -> "Do not bring insults to a " + profile.role() + " after dark.";
            };
        };
    }

    private static void addWeatherLines(List<DialogueLine> lines) {
        addGlobalWeatherLines(lines);
        addProfessionWeatherLines(lines);
    }

    private static void addGlobalWeatherLines(List<DialogueLine> lines) {
        String[] rainLines = {
                "It's raining today. The roads complain, but the gardens look pleased.",
                "Rain makes everyone speak softer. Even the village bell sounds damp.",
                "A wet day keeps tempers indoors, mostly.",
                "If you're tracking mud through the village, at least bring news with it.",
                "The rain is good for the wells, less good for anyone with leaky boots.",
                "Listen to that roof. Sounds like the whole village is thinking.",
                "Rain has a way of showing which roofs need mending.",
                "No shame in staying under an awning today.",
                "The village smells cleaner after rain. For a little while, anyway.",
                "Careful on the paths. Wet stone has no manners."
        };
        String[] thunderLines = {
                "Storm's close. Even the bravest folk count the seconds after lightning.",
                "Thunder makes the whole village remember how small it is.",
                "If the sky keeps shouting, I may let it handle the talking.",
                "Keep an eye out. Bad things like noisy weather.",
                "The bell and thunder are arguing over who gets to be louder.",
                "That storm has teeth. Best not wander far.",
                "Lightning makes every shadow look suspicious.",
                "A storm like this sends everyone checking doors twice.",
                "If you hear thunder, the roofs heard it first.",
                "Some days the sky sounds angrier than the village."
        };
        for (int index = 0; index < rainLines.length; index++) {
            add(lines, "weather_rain_global_" + index, requestForIndex(index), rainLines[index])
                    .weatherStates(DialogueContext.WeatherState.RAIN)
                    .weight(28)
                    .build();
        }
        for (int index = 0; index < thunderLines.length; index++) {
            add(lines, "weather_thunder_global_" + index, requestForIndex(index), thunderLines[index])
                    .weatherStates(DialogueContext.WeatherState.THUNDER)
                    .weight(32)
                    .build();
        }
    }

    private static void addProfessionWeatherLines(List<DialogueLine> lines) {
        for (ProfessionDialogue profile : professionProfiles()) {
            for (int index = 0; index < 10; index++) {
                add(lines, profile.key() + "_rain_" + index, requestForIndex(index), professionRainText(profile, index))
                        .professions(profile.profession())
                        .weatherStates(DialogueContext.WeatherState.RAIN)
                        .weight(34)
                        .build();
                add(lines, profile.key() + "_thunder_" + index, requestForIndex(index), professionThunderText(profile, index))
                        .professions(profile.profession())
                        .weatherStates(DialogueContext.WeatherState.THUNDER)
                        .weight(38)
                        .build();
            }
        }
    }

    private static String professionRainText(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Rain on the " + profile.workplace() + " changes the whole rhythm of my day.";
            case 1 -> "It's raining today. Good for some work, miserable for " + profile.craft() + ".";
            case 2 -> "Weather like this makes " + profile.concern() + " harder to ignore.";
            case 3 -> "Rain jokes? Mine all involve " + profile.gift() + " getting soaked.";
            case 4 -> "If you came to mock the rain, take it up with the clouds.";
            case 5 -> "The rain keeps most folk close to home. That can be a blessing.";
            case 6 -> "Ask me after the rain stops; half my thoughts are on " + profile.warning() + ".";
            case 7 -> "A wet day tests " + profile.pride() + " more than people think.";
            case 8 -> profile.joke() + " Rain makes even that sound soggy.";
            default -> "Careful with wet boots near the " + profile.workplace() + ".";
        };
    }

    private static String professionThunderText(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Thunder over the " + profile.workplace() + " makes every tool sound guilty.";
            case 1 -> "Storms are bad for " + profile.craft() + ". Worse for steady hands.";
            case 2 -> "When thunder rolls, " + profile.concern() + " starts feeling less ordinary.";
            case 3 -> "I had a storm joke about " + profile.gift() + ", but the sky interrupted.";
            case 4 -> "Insults sound smaller when thunder is doing the shouting.";
            case 5 -> "Stay near shelter. A " + profile.role() + " knows when work can wait.";
            case 6 -> "If you're asking about " + profile.warning() + ", thunder is already an answer.";
            case 7 -> "Storms make me double-check " + profile.pride() + ".";
            case 8 -> profile.joke() + " The thunder gave it better timing.";
            default -> "Not today. The storm has made everyone sharp enough already.";
        };
    }

    private static void addGlobalMoodLines(List<DialogueLine> lines) {
        for (DialogueDisposition disposition : DialogueDisposition.values()) {
            for (int index = 0; index < 10; index++) {
                add(lines, "global_" + disposition.name().toLowerCase() + "_" + index, requestForIndex(index), globalMoodText(disposition, index))
                        .dispositions(disposition)
                        .weight(18)
                        .build();
            }
        }
    }

    private static void addProfessionMoodLines(List<DialogueLine> lines) {
        for (ProfessionDialogue profile : professionProfiles()) {
            for (DialogueDisposition disposition : DialogueDisposition.values()) {
                for (int index = 0; index < 10; index++) {
                    add(lines, profile.key() + "_" + disposition.name().toLowerCase() + "_" + index, requestForIndex(index), professionMoodText(profile, disposition, index))
                            .professions(profile.profession())
                            .dispositions(disposition)
                            .weight(24)
                            .build();
                }
            }
        }
    }

    private static DialogueRequestType requestForIndex(int index) {
        return switch (index % 5) {
            case 0 -> DialogueRequestType.GREETING;
            case 1 -> DialogueRequestType.QUESTION;
            case 2 -> DialogueRequestType.STORY;
            case 3 -> DialogueRequestType.JOKE;
            default -> DialogueRequestType.INSULT;
        };
    }

    private static String globalMoodText(DialogueDisposition disposition, int index) {
        return switch (disposition) {
            case RESPECTFUL -> switch (index) {
                case 0 -> "You're welcome here. People stand a little taller when you visit.";
                case 1 -> "If you're asking after the village, we're doing better because of you.";
                case 2 -> "There are names folk say softly, with gratitude. Yours is one of them.";
                case 3 -> "I could make a joke, but the bell already rings whenever you arrive.";
                case 4 -> "Even heroes get told when they're blocking the path.";
                case 5 -> "Good to see you again. The village remembers steady hands.";
                case 6 -> "Need news? The best news is that you're not trouble.";
                case 7 -> "Once trust takes root, it feeds more than one house.";
                case 8 -> "If reputation bought bread, you'd own the bakery.";
                default -> "Careful, friend. Admiration can sour if you step on it.";
            };
            case FRIENDLY -> switch (index) {
                case 0 -> "There you are. I was hoping the day would bring a familiar face.";
                case 1 -> "Ask away. I have a moment, and you usually listen.";
                case 2 -> "A kind visitor changes the sound of a street.";
                case 3 -> "Tell me your joke, but I reserve the right to groan loudly.";
                case 4 -> "That's a bold thing to say to someone who still likes you.";
                case 5 -> "Stay awhile if you can. The village feels less tense with you around.";
                case 6 -> "Questions are easier from someone who has earned answers.";
                case 7 -> "Small kindnesses stack higher than most walls.";
                case 8 -> "If that joke has emeralds in it, I already approve.";
                default -> "Mind your tongue. Goodwill is sturdy, not unbreakable.";
            };
            case NEUTRAL -> switch (index) {
                case 0 -> "Morning. Or evening. Hard to tell when the work piles up.";
                case 1 -> "What do you need to know?";
                case 2 -> "Every village has two histories: what happened, and what people repeat.";
                case 3 -> "A joke? Fine, but make it quick.";
                case 4 -> "I've heard worse. Usually from traders.";
                case 5 -> "Passing through, or making this our problem?";
                case 6 -> "Questions are cheap. Useful answers cost attention.";
                case 7 -> "Most days are ordinary until someone careless arrives.";
                case 8 -> "If this is funny, I'll pretend I meant to laugh.";
                default -> "That sounded sharper than it needed to.";
            };
            case CAUTIOUS -> switch (index) {
                case 0 -> "Keep your hands where I can see them.";
                case 1 -> "Why are you asking?";
                case 2 -> "Trust leaves faster than it arrives. Villages learn that early.";
                case 3 -> "A joke from you? This should be interesting.";
                case 4 -> "You're not helping your case.";
                case 5 -> "Say what you came to say.";
                case 6 -> "I answer carefully around people I'm still measuring.";
                case 7 -> "We notice patterns. Yours is not settled yet.";
                case 8 -> "If I laugh, it is because I'm nervous.";
                default -> "That is exactly the sort of thing people remember.";
            };
            case RUDE -> switch (index) {
                case 0 -> "Make it quick. I have better uses for daylight.";
                case 1 -> "You want answers after all that? Hm.";
                case 2 -> "Some visitors leave footprints. Some leave warnings.";
                case 3 -> "Go on then. Astonish me.";
                case 4 -> "I've heard kinder sounds from a stuck door.";
                case 5 -> "You're still here. Brave, or just stubborn.";
                case 6 -> "Ask, but don't expect warmth.";
                case 7 -> "A village can forgive. It does not forget for free.";
                case 8 -> "That joke had the shape of humor and none of the taste.";
                default -> "Careful. My patience is thinner than you think.";
            };
            case HOSTILE -> switch (index) {
                case 0 -> "You should not be here.";
                case 1 -> "Why would I tell you anything useful?";
                case 2 -> "When people ask how trouble sounds, I describe your footsteps.";
                case 3 -> "If this is a joke, it already failed.";
                case 4 -> "You mistake fear for permission.";
                case 5 -> "Leave before the village decides together.";
                case 6 -> "No answer I give you will be for your benefit.";
                case 7 -> "There are doors here that close because of you.";
                case 8 -> "Funny thing: nobody laughs when you arrive.";
                default -> "Say another word like that and see how many windows open.";
            };
            case FEARFUL -> switch (index) {
                case 0 -> "Please. Just say what you need and go.";
                case 1 -> "I don't know. Or I do, and I shouldn't say.";
                case 2 -> "Fear makes every street feel narrower.";
                case 3 -> "A joke? Now?";
                case 4 -> "I won't answer that. I won't.";
                case 5 -> "I am listening. Please don't make me regret it.";
                case 6 -> "Questions from you feel like tests.";
                case 7 -> "Some nights the village goes quiet all at once.";
                case 8 -> "I might laugh if I knew it was safe.";
                default -> "Please stop. That is enough.";
            };
        };
    }

    private static String professionMoodText(ProfessionDialogue profile, DialogueDisposition disposition, int index) {
        return switch (disposition) {
            case RESPECTFUL -> professionRespectful(profile, index);
            case FRIENDLY -> professionFriendly(profile, index);
            case NEUTRAL -> professionNeutral(profile, index);
            case CAUTIOUS -> professionCautious(profile, index);
            case RUDE -> professionRude(profile, index);
            case HOSTILE -> professionHostile(profile, index);
            case FEARFUL -> professionFearful(profile, index);
        };
    }

    private static String professionRespectful(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Welcome back. Even the " + profile.workplace() + " feels steadier when you're nearby.";
            case 1 -> "You want to know about " + profile.craft() + "? For you, I'll answer plainly.";
            case 2 -> "I once thought " + profile.concern() + " was our biggest worry. Then you proved people can help too.";
            case 3 -> "My best joke involves " + profile.gift() + ", but you have already earned the good version.";
            case 4 -> "If I tease you, it is only because you've earned familiar words.";
            case 5 -> "A good day to you. The " + profile.role() + " notices who protects the village.";
            case 6 -> "Ask about " + profile.warning() + " if you like. I trust you with the answer.";
            case 7 -> "There is pride in " + profile.pride() + ", and some in knowing decent visitors still exist.";
            case 8 -> profile.joke() + " That one is better with an audience I trust.";
            default -> "Careful now. Even respected friends should not mock a " + profile.role() + "'s work.";
        };
    }

    private static String professionFriendly(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Good to see you. I was just thinking about " + profile.craft() + ".";
            case 1 -> "Questions about " + profile.workplace() + "? I can spare a minute.";
            case 2 -> "The trick to " + profile.pride() + " is patience, mostly. And not panicking.";
            case 3 -> "I have a " + profile.role() + "'s joke about " + profile.gift() + ". It is only mostly terrible.";
            case 4 -> "That jab had some bite. Lucky for you, I like you.";
            case 5 -> "Pull up a patch of shade. The " + profile.workplace() + " can wait briefly.";
            case 6 -> "If you're asking about " + profile.concern() + ", the answer changes by the hour.";
            case 7 -> "People think " + profile.craft() + " is simple until they try it tired.";
            case 8 -> profile.joke() + " See? Professionally funny.";
            default -> "Mock the work if you must, but the village still needs it.";
        };
    }

    private static String professionNeutral(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Need the " + profile.role() + "? Say what you need.";
            case 1 -> "Ask about " + profile.craft() + ", but keep it sensible.";
            case 2 -> "Most days, " + profile.workplace() + " teaches the same lesson twice.";
            case 3 -> "A joke about " + profile.gift() + "? I've heard worse starts.";
            case 4 -> "If that was an insult, it needs more work.";
            case 5 -> "I'm between tasks. Briefly.";
            case 6 -> profile.concern() + " is the thing people ignore until it costs them.";
            case 7 -> "You learn a lot as a " + profile.role() + ". Mostly who is patient.";
            case 8 -> profile.joke() + " No refunds on laughter.";
            default -> "Careful. The " + profile.workplace() + " has heard better complaints.";
        };
    }

    private static String professionCautious(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Keep back from the " + profile.workplace() + ". I mean it.";
            case 1 -> "Why do you want to know about " + profile.craft() + "?";
            case 2 -> "A " + profile.role() + " learns to spot trouble before it speaks.";
            case 3 -> "If this joke involves " + profile.gift() + ", choose the gentle version.";
            case 4 -> "That sounded like a warning wearing a smile.";
            case 5 -> "I can talk, but I am watching your hands.";
            case 6 -> profile.warning() + " is not something I discuss carelessly.";
            case 7 -> "The village depends on " + profile.pride() + ". Don't make that harder.";
            case 8 -> profile.joke() + " I laugh quieter around uncertain company.";
            default -> "Insult me if you want, but leave the " + profile.workplace() + " out of it.";
        };
    }

    private static String professionRude(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "The " + profile.role() + " is busy. You are not the reason I stop.";
            case 1 -> "You want " + profile.craft() + " explained? Try earning a lesson.";
            case 2 -> "I have seen " + profile.concern() + " handled with more grace than you manage walking.";
            case 3 -> "Tell the joke. The " + profile.workplace() + " could use a warning sound.";
            case 4 -> "That insult was duller than bad " + profile.gift() + ".";
            case 5 -> "Make it quick. I have " + profile.pride() + " to keep alive.";
            case 6 -> "My advice about " + profile.warning() + "? Don't be the cause of it.";
            case 7 -> "A " + profile.role() + " remembers who makes work harder.";
            case 8 -> profile.joke() + " Still better than talking to you.";
            default -> "If you came to sneer at my work, stand where I can ignore you.";
        };
    }

    private static String professionHostile(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Step away from the " + profile.workplace() + ". Now.";
            case 1 -> "I will not teach you anything about " + profile.craft() + ".";
            case 2 -> "Every " + profile.role() + " knows what damage looks like. You qualify.";
            case 3 -> "No joke from you belongs near my " + profile.gift() + ".";
            case 4 -> "Threats sound cheap from someone already unwelcome.";
            case 5 -> "Leave before " + profile.warning() + " becomes the day's work.";
            case 6 -> "You ask like a thief measuring a door.";
            case 7 -> "The village needs " + profile.pride() + ", not whatever you bring.";
            case 8 -> profile.joke() + " There. More warmth than you deserve.";
            default -> "Insult a " + profile.role() + " again and listen for the bell.";
        };
    }

    private static String professionFearful(ProfessionDialogue profile, int index) {
        return switch (index) {
            case 0 -> "Please don't come closer to the " + profile.workplace() + ".";
            case 1 -> "I can answer about " + profile.craft() + ", but please don't be angry.";
            case 2 -> "When " + profile.concern() + " happens, I know what to do. With you, I am less sure.";
            case 3 -> "A joke about " + profile.gift() + "? I can try to laugh.";
            case 4 -> "Please don't mock the work. It is all I have steady.";
            case 5 -> "I am only a " + profile.role() + ". Say what you need.";
            case 6 -> profile.warning() + " already keeps me awake. Don't add to it.";
            case 7 -> "I focus on " + profile.pride() + " so my hands stop shaking.";
            case 8 -> profile.joke() + " Was that alright?";
            default -> "Please. Not the " + profile.workplace() + ". Not today.";
        };
    }

    private static List<ProfessionDialogue> professionProfiles() {
        return List.of(
                new ProfessionDialogue(VillagerProfession.ARMORER, "armorer", "armorer", "forge", "fitting mail and shields", "cracked plates after a raid", "iron plates", "broken armor", "keeping people standing", "Armor jokes are hard to land; they usually need padding."),
                new ProfessionDialogue(VillagerProfession.BUTCHER, "butcher", "butcher", "smokehouse", "keeping meat salted and safe", "empty hooks before supper", "smoked cuts", "spoiled stores", "feeding hungry neighbors", "A butcher's joke has good timing, or it gets carved up."),
                new ProfessionDialogue(VillagerProfession.CARTOGRAPHER, "cartographer", "cartographer", "map table", "reading roads and borders", "blank edges on a map", "fresh parchment", "lost paths", "helping people find their way", "Map jokes go nowhere unless you fold them right."),
                new ProfessionDialogue(VillagerProfession.CLERIC, "cleric", "cleric", "brewing stand", "mixing remedies and prayers", "wounds that won't close", "glass bottles", "bad omens", "keeping hope from curdling", "A cleric's joke is medicinal: bitter first, useful later."),
                new ProfessionDialogue(VillagerProfession.FARMER, "farmer", "farmer", "fields", "coaxing food from stubborn soil", "dry rows and trampled crops", "fresh bread", "ruined harvests", "making tomorrow edible", "A farmer's joke grows on you, if watered properly."),
                new ProfessionDialogue(VillagerProfession.FISHERMAN, "fisherman", "fisherman", "dock", "reading water and weather", "empty nets at dusk", "good twine", "snapped lines", "bringing in enough for stew", "Fisher jokes are all about the delivery, and the line."),
                new ProfessionDialogue(VillagerProfession.FLETCHER, "fletcher", "fletcher", "fletching table", "balancing arrows true", "warped shafts", "clean feathers", "missed shots", "making trouble keep its distance", "A fletcher's joke flies better with proper feathers."),
                new ProfessionDialogue(VillagerProfession.LEATHERWORKER, "leatherworker", "leatherworker", "tannery", "curing leather without wasting hide", "bad stitching in the rain", "soft leather", "split seams", "making gear that lasts", "Leather jokes need a thick skin."),
                new ProfessionDialogue(VillagerProfession.LIBRARIAN, "librarian", "librarian", "lectern", "keeping records and stories straight", "missing pages", "ink and paper", "forgotten warnings", "remembering what others rush past", "A librarian's joke has footnotes, but I will spare you."),
                new ProfessionDialogue(VillagerProfession.MASON, "mason", "mason", "stonecutter", "setting stone that stays set", "walls with weak corners", "smooth stone", "falling masonry", "building what outlives panic", "Mason jokes are solid, if a little dense."),
                new ProfessionDialogue(VillagerProfession.NITWIT, "nitwit", "nitwit", "sunny corner", "finding odd jobs and stranger thoughts", "forgetting why I came outside", "a nice stick", "serious faces", "noticing what others miss", "My joke wandered off. It may return with snacks."),
                new ProfessionDialogue(VillagerProfession.NONE, "unemployed", "unemployed villager", "job site I haven't found yet", "looking useful before anyone notices", "not knowing where I fit", "a fair chance", "being dismissed", "finding the right work", "Unemployed jokes are flexible. So am I, apparently."),
                new ProfessionDialogue(VillagerProfession.SHEPHERD, "shepherd", "shepherd", "loom", "turning wool into warmth", "scattered sheep", "clean wool", "wolves near the pens", "keeping the flock calm", "Shepherd jokes are best when nobody pulls the wool over them."),
                new ProfessionDialogue(VillagerProfession.TOOLSMITH, "toolsmith", "toolsmith", "smithing table", "making tools that don't fail", "cracked handles", "good handles", "dull edges", "putting strength in careful hands", "Tool jokes work better when they have a point."),
                new ProfessionDialogue(VillagerProfession.WEAPONSMITH, "weaponsmith", "weaponsmith", "grindstone", "keeping blades honest", "rust on a sword", "tempered steel", "unready guards", "making danger think twice", "Weapon jokes are sharp; I keep the dull ones for strangers.")
        );
    }

    private static ProfessionDialogue profileFor(VillagerProfession profession) {
        for (ProfessionDialogue profile : professionProfiles()) {
            if (profile.profession() == profession) {
                return profile;
            }
        }
        return null;
    }

    private static DialogueLine.Builder add(List<DialogueLine> lines, String id, DialogueRequestType requestType, String text) {
        return new AutoAddingBuilder(lines, id, requestType, text);
    }

    public record DialogueResult(String lineId, String text) {
    }

    private record ProfessionDialogue(
            VillagerProfession profession,
            String key,
            String role,
            String workplace,
            String craft,
            String concern,
            String gift,
            String warning,
            String pride,
            String joke
    ) {
    }

    private enum BabyDisposition {
        FRIENDLY,
        CAUTIOUS,
        AFRAID
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
