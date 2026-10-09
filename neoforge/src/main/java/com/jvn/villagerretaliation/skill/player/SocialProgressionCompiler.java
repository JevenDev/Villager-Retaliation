package com.jvn.villagerretaliation.skill.player;

import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class SocialProgressionCompiler {
    private SocialProgressionCompiler() { }

    public static Result compile(QuestBundleTransactions.EffectiveBundle bundle, ResourceLocation id) {
        var source = bundle.source(QuestBundlePath.Kind.SOCIAL_PROGRESSION, id);
        var root = bundle.definitions().getOrDefault(QuestBundlePath.Kind.SOCIAL_PROGRESSION, java.util.Map.of()).get(id);
        var issues = new ArrayList<QuestDiagnostic>();
        for (var issue : SocialProgressionSchema.validate(root)) issues.add(error(source, issue.pointer(), issue.message()));
        if (!issues.isEmpty()) return new Result(null, issues);
        if (!bundle.owner().shared()) issues.add(error(source, "", "social progression belongs to quests/_shared/social_progression"));
        if (!id.toString().equals(root.get("id").getAsString())) issues.add(error(source, "/id", "social progression id does not match its bundle identity"));
        var skills = new LinkedHashMap<PlayerSocialSkill, List<SocialProgressionDefinition.Level>>();
        var entries = root.getAsJsonArray("skills");
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index).getAsJsonObject();
            var skill = PlayerSocialSkill.parse(entry.get("id").getAsString());
            String at = "/skills/" + index;
            var levels = new ArrayList<SocialProgressionDefinition.Level>();
            var values = entry.getAsJsonArray("levels");
            for (int level = 0; level < values.size(); level++) {
                var value = values.get(level).getAsJsonObject();
                int experience = value.get("experience").getAsInt(), bonus = value.get("bonus").getAsInt();
                if (level == 0 && experience != 0) issues.add(error(source, at + "/levels/0/experience", "the first social level must start at zero experience"));
                if (!levels.isEmpty() && experience <= levels.getLast().experience())
                    issues.add(error(source, at + "/levels/" + level + "/experience", "social experience thresholds must increase in authored order"));
                if (!levels.isEmpty() && bonus < levels.getLast().bonus())
                    issues.add(error(source, at + "/levels/" + level + "/bonus", "social bonuses must not decrease at later levels"));
                levels.add(new SocialProgressionDefinition.Level(experience, bonus));
            }
            if (skills.putIfAbsent(skill, levels) != null) issues.add(error(source, at + "/id", "duplicate social skill " + skill.id()));
        }
        if (!issues.isEmpty()) return new Result(null, issues);
        return new Result(new SocialProgressionDefinition(id, root.get("revision").getAsInt(), QuestBundleFingerprints.definition(root),
                skills, bundle.owner(), source), List.of());
    }

    static QuestDiagnostic error(QuestBundleTransactions.DefinitionSource source, String pointer, String message) {
        return new QuestDiagnostic(QuestDiagnostic.Severity.ERROR, "social_progression.invalid", source.resource(), source.pack(),
                source.resource() == null ? "" : source.resource().toString(), pointer, message,
                "Correct this shared social progression definition and reload datapacks.", Set.of());
    }

    public record Result(SocialProgressionDefinition definition, List<QuestDiagnostic> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return definition != null && diagnostics.isEmpty(); }
    }
}
