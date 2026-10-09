import path from "node:path";
import { validateContentSchema } from "./content-schema-validation.mjs";

export function socialProgressionPath(file) {
  const match = path.resolve(file).replaceAll(path.sep, "/").match(/^(.*\/data)\/([a-z0-9_.-]+)\/quests\/_shared\/social_progression\/[a-z0-9_.-]+\.json$/);
  return match ? { dataRoot: match[1], namespace: match[2] } : null;
}

export function validateSocialProgression(root, schema) {
  const issues = validateContentSchema(root, schema);
  if (issues.length) return issues;
  const skills = new Set();
  root.skills.forEach((skill, index) => {
    const at = "/skills/" + index;
    if (skills.has(skill.id)) issues.push({ pointer: at + "/id", message: "duplicate social skill " + skill.id });
    skills.add(skill.id);
    skill.levels.forEach((level, position) => {
      const pointer = at + "/levels/" + position;
      if (position === 0 && level.experience !== 0) issues.push({ pointer: pointer + "/experience", message: "the first social level must start at zero experience" });
      if (position > 0 && level.experience <= skill.levels[position - 1].experience)
        issues.push({ pointer: pointer + "/experience", message: "social experience thresholds must increase in authored order" });
      if (position > 0 && level.bonus < skill.levels[position - 1].bonus)
        issues.push({ pointer: pointer + "/bonus", message: "social bonuses must not decrease at later levels" });
    });
  });
  return issues;
}

export function collectSocialProgressions(resources, schema) {
  const progressions = new Map();
  const declared = new Map();
  const issues = [];
  for (const { file, data } of resources) {
    const location = socialProgressionPath(file);
    if (!location && data?.schema !== "villagerretaliation:social_progression/v1") continue;
    const problems = validateSocialProgression(data, schema);
    if (!location) problems.push({ pointer: "", message: "social progression belongs to quests/_shared/social_progression" });
    else if (typeof data?.id === "string" && !data.id.startsWith(location.namespace + ":"))
      problems.push({ pointer: "/id", message: "social progression id must use the bundle namespace" });
    if (typeof data?.id === "string") {
      if (declared.has(data.id)) {
        problems.push({ pointer: "/id", message: "duplicate social progression id also defined in " + declared.get(data.id) });
        progressions.delete(data.id);
      } else declared.set(data.id, file);
    }
    if (problems.length) issues.push(...problems.map(problem => ({ file, ...problem })));
    else progressions.set(data.id, data);
  }
  return { progressions, issues };
}
