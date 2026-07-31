package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.profile.VillagerProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Owns every conversion from normalized practice into permanent skill points. */
public final class VillagerSkillProgressionService {
    public static final double DAILY_FULL_RATE_XP = 6.0D;
    public static final double DAILY_SOFT_CAP_MULTIPLIER = 0.20D;
    public static final int FULL_RATE_REPETITIONS = 8;
    public static final double REPETITION_MULTIPLIER = 0.35D;
    public static final int MAX_REPETITION_KEYS_PER_SKILL = 64;
    private static final double EPSILON = 0.000_001D;

    private VillagerSkillProgressionService() {
    }

    /** XP needed for the next point: 2 + (current skill / 20)^1.35. */
    public static double requiredXp(int currentSkill) {
        int clamped = VillagerSkillSet.clamp(currentSkill);
        return 2.0D + Math.pow(clamped / 20.0D, 1.35D);
    }

    public static VillagerSkillProgressionResult apply(
            VillagerProfile profile,
            List<VillagerSkillPractice> practice,
            long overworldDayIndex,
            long gameTime,
            double xpPerUnit) {
        if (profile == null || practice == null || practice.isEmpty()) {
            return VillagerSkillProgressionResult.NONE;
        }
        if (!Double.isFinite(xpPerUnit) || xpPerUnit <= 0.0D) {
            throw new IllegalArgumentException("XP per practice unit must be finite and positive");
        }

        EnumMap<VillagerSkill, Integer> oldValues = new EnumMap<>(VillagerSkill.class);
        double requestedXp = 0.0D;
        double grantedXp = 0.0D;
        boolean changed = false;

        for (VillagerSkillPractice event : practice) {
            if (event == null) {
                continue;
            }
            VillagerSkill skill = event.skill();
            int currentSkill = profile.skills().get(skill);
            if (currentSkill >= VillagerSkillSet.MAX_VALUE) {
                changed |= profile.setSkillPracticeXp(skill, 0.0D, gameTime);
                continue;
            }

            double baseXpPerRepetition = event.units() * xpPerUnit;
            double baseXp = baseXpPerRepetition * event.repetitions();
            if (!Double.isFinite(baseXpPerRepetition)
                    || baseXpPerRepetition <= 0.0D
                    || !Double.isFinite(baseXp)
                    || baseXp <= 0.0D) {
                throw new IllegalArgumentException("Calculated practice XP must be finite and positive");
            }
            requestedXp += baseXp;

            long repetitionIdentity = event.repetitionIdentity();
            int repeated = profile.repetitionCount(skill, repetitionIdentity, overworldDayIndex);
            int fullRateRepetitions = Math.min(
                    event.repetitions(),
                    Math.max(0, FULL_RATE_REPETITIONS - repeated));
            int reducedRepetitions = event.repetitions() - fullRateRepetitions;
            double repetitionAdjusted = baseXpPerRepetition * fullRateRepetitions
                    + baseXpPerRepetition * reducedRepetitions * REPETITION_MULTIPLIER;
            double earnedToday = profile.practiceEarnedToday(skill, overworldDayIndex);
            double fullRateRemaining = Math.max(0.0D, DAILY_FULL_RATE_XP - earnedToday);
            double fullRatePart = Math.min(repetitionAdjusted, fullRateRemaining);
            double dailyAdjusted = fullRatePart
                    + Math.max(0.0D, repetitionAdjusted - fullRatePart) * DAILY_SOFT_CAP_MULTIPLIER;
            if (!Double.isFinite(dailyAdjusted) || dailyAdjusted <= 0.0D) {
                continue;
            }

            changed |= profile.recordPracticeEvent(
                    skill,
                    repetitionIdentity,
                    overworldDayIndex,
                    dailyAdjusted,
                    event.repetitions(),
                    MAX_REPETITION_KEYS_PER_SKILL,
                    gameTime);
            grantedXp += dailyAdjusted;

            oldValues.putIfAbsent(skill, currentSkill);
            double storedXp = profile.skillPracticeXp(skill) + dailyAdjusted;
            while (currentSkill < VillagerSkillSet.MAX_VALUE) {
                double requirement = requiredXp(currentSkill);
                if (storedXp + EPSILON < requirement) {
                    break;
                }
                storedXp = Math.max(0.0D, storedXp - requirement);
                currentSkill++;
            }
            if (currentSkill >= VillagerSkillSet.MAX_VALUE) {
                storedXp = 0.0D;
            }
            changed |= profile.setSkillPracticeXp(skill, storedXp, gameTime);
            changed |= profile.setSkill(skill, currentSkill, gameTime);
        }

        List<VillagerSkillProgressionResult.SkillIncrease> increases = new ArrayList<>();
        for (var entry : oldValues.entrySet()) {
            int newValue = profile.skills().get(entry.getKey());
            if (newValue > entry.getValue()) {
                increases.add(new VillagerSkillProgressionResult.SkillIncrease(
                        entry.getKey(), newValue - entry.getValue(), newValue));
            }
        }
        return new VillagerSkillProgressionResult(increases, requestedXp, grantedXp, changed);
    }
}
