import { validateContentSchema } from "./content-schema-validation.mjs";

const entries = value => Array.isArray(value) ? value : [];
const plainText = value => typeof value === "string" ? value : value?.text;
const irreversible = milestone => milestone.irreversible !== false;

export function validateCampaign(root, schema, context = {}) {
  const issues = validateContentSchema(root, schema);
  if (issues.length) return issues;
  const issue = (pointer, message) => issues.push({ pointer, message });
  const unique = (values, pointer) => {
    const seen = new Set();
    values.forEach((value, index) => {
      if (seen.has(value)) issue(pointer + "/" + index, "duplicate reference " + value);
      seen.add(value);
    });
  };
  const index = (values, pointer) => {
    const result = new Map();
    values.forEach((value, at) => {
      if (result.has(value.id)) issue(pointer + "/" + at + "/id", "duplicate id " + value.id);
      else result.set(value.id, { ...value, pointer: pointer + "/" + at });
    });
    return result;
  };
  const localized = (key, pointer) => {
    if (!key.startsWith(root.localization_prefix)) issue(pointer, "localization key must use the campaign prefix " + root.localization_prefix);
    if (context.messages !== undefined && (typeof plainText(context.messages?.[key]) !== "string" || !plainText(context.messages[key]).trim())) {
      issue(pointer, "English localization is missing or not plain text: " + key);
    }
  };
  const bundles = new Set(root.quest_bundles);
  const chapters = index(root.chapters, "/chapters");
  const milestones = index(root.milestones, "/milestones");
  const characters = index(root.characters ?? [], "/characters");
  const locations = index(root.locations ?? [], "/locations");
  const evidence = index(root.evidence ?? [], "/evidence");
  const variants = index(root.location_variants ?? [], "/location_variants");
  const facts = index(root.facts ?? [], "/facts");
  const rewards = index(root.rewards ?? [], "/rewards");
  const outcomes = new Map();
  for (const milestone of milestones.values()) outcomes.set(milestone.id, index(milestone.outcomes, milestone.pointer + "/outcomes"));
  const bundle = (id, pointer, requireOwner = false) => {
    if (!bundles.has(id)) issue(pointer, "quest is not declared in quest_bundles: " + id);
    if (!context.quests) return;
    const quest = context.quests.get(id);
    if (!quest) issue(pointer, "quest bundle is unavailable: " + id);
    else if (requireOwner && quest.metadata?.campaign !== root.id) issue(pointer, "quest metadata.campaign must identify the owning campaign " + root.id + ": " + id);
  };
  unique(root.quest_bundles, "/quest_bundles");
  root.quest_bundles.forEach((id, at) => bundle(id, "/quest_bundles/" + at));
  localized(root.title_key, "/title_key");
  for (const location of locations.values()) {
    const pointer = location.pointer;
    localized(location.title_key, pointer + "/title_key");
    if (location.source === "marker" && (!location.origin || location.search_radius !== undefined)) issue(pointer, "marker locations require an origin and cannot search");
    if ((location.source === "structure") !== (location.structure !== undefined)) issue(pointer + "/structure", "only structure locations require a structure id");
  }
  for (const character of characters.values()) {
    const pointer = character.pointer;
    localized(character.title_key, pointer + "/title_key");
    if (!locations.has(character.location)) issue(pointer + "/location", "unknown campaign location " + character.location);
    if (character.loss_policy === "alternate_path" && ![...milestones.values()].some(milestone =>
      milestone.observe?.event === "villagerretaliation:campaign_character_lost" && milestone.observe.character === character.id)) {
      issue(pointer + "/loss_policy", "alternate_path requires a milestone observing this character's permanent loss");
    }
    const visited = new Set();
    let current = character;
    while (current?.loss_policy === "replacement") {
      if (visited.has(current.id)) { issue(pointer + "/replacement_role", "character replacements contain a cycle"); break; }
      visited.add(current.id);
      const replacement = characters.get(current.replacement_role);
      if (!replacement) issue(current.pointer + "/replacement_role", "unknown replacement character " + current.replacement_role);
      current = replacement;
    }
  }
  for (const section of ["relationships", "factions"]) {
    for (const standing of index(root[section] ?? [], "/" + section).values()) {
      const pointer = standing.pointer;
      localized(standing.title_key, pointer + "/title_key");
      if (section === "relationships" && !characters.has(standing.id)) issue(pointer + "/id", "relationship must name an authored campaign character " + standing.id);
      if (standing.maximum < standing.minimum) issue(pointer + "/maximum", "standing maximum must not be below its minimum");
      if (standing.initial < standing.minimum || standing.initial > standing.maximum) issue(pointer + "/initial", "initial standing must be inside its authored range");
      const reactions = index(standing.reactions, pointer + "/reactions");
      for (const reaction of reactions.values()) localized(reaction.title_key, reaction.pointer + "/title_key");
      standing.reactions.forEach((reaction, at) => {
        const location = pointer + "/reactions/" + at + "/minimum";
        if (at === 0 && reaction.minimum !== standing.minimum) issue(location, "the first reaction must start at the standing minimum");
        if (at > 0 && reaction.minimum <= standing.reactions[at - 1].minimum) issue(location, "reaction thresholds must increase in authored order");
        if (reaction.minimum < standing.minimum || reaction.minimum > standing.maximum) issue(location, "reaction threshold must be inside the standing range");
      });
    }
  }
  for (const item of evidence.values()) {
    localized(item.title_key, item.pointer + "/title_key");
    localized(item.text_key, item.pointer + "/text_key");
    if (!locations.has(item.location)) issue(item.pointer + "/location", "unknown campaign location " + item.location);
  }
  const dependencies = new Map();
  for (const chapter of chapters.values()) {
    const pointer = chapter.pointer;
    localized(chapter.title_key, pointer + "/title_key");
    unique(chapter.quests, pointer + "/quests");
    chapter.quests.forEach((id, at) => bundle(id, pointer + "/quests/" + at, true));
    const targets = new Set();
    const required = new Set();
    for (const [at, requirement] of (chapter.prerequisites ?? []).entries()) {
      const where = pointer + "/prerequisites/" + at;
      if (Boolean(requirement.chapter) === Boolean(requirement.milestone)) { issue(where, "prerequisite must reference exactly one chapter or milestone"); continue; }
      const target = requirement.chapter ? "chapter/" + requirement.chapter : "milestone/" + requirement.milestone;
      if (targets.has(target)) issue(where, "duplicate prerequisite " + target);
      targets.add(target);
      if (requirement.chapter) {
        if (!chapters.has(requirement.chapter)) issue(where + "/chapter", "unknown chapter " + requirement.chapter);
        else required.add(requirement.chapter);
        if (requirement.outcome) issue(where + "/outcome", "chapter prerequisites cannot name a milestone outcome");
      } else {
        const milestone = milestones.get(requirement.milestone);
        if (!milestone) issue(where + "/milestone", "unknown milestone " + requirement.milestone);
        else {
          required.add(milestone.chapter);
          if (requirement.outcome && !outcomes.get(milestone.id).has(requirement.outcome)) issue(where + "/outcome", "unknown milestone outcome " + requirement.outcome);
        }
      }
    }
    dependencies.set(chapter.id, required);
  }
  const resolved = new Set();
  for (let previous = -1; previous !== resolved.size;) {
    previous = resolved.size;
    for (const [id, required] of dependencies) if ([...required].every(id => resolved.has(id))) resolved.add(id);
  }
  for (const chapter of chapters.values()) if (!resolved.has(chapter.id)) issue(chapter.pointer + "/prerequisites", "chapter prerequisites contain a cycle or an unresolved dependency");
  const stageOwners = new Set();
  for (const milestone of milestones.values()) {
    const pointer = milestone.pointer;
    const chapter = chapters.get(milestone.chapter);
    if (!chapter) issue(pointer + "/chapter", "unknown chapter " + milestone.chapter);
    else if (!chapter.quests.includes(milestone.quest)) issue(pointer + "/quest", "milestone quest must belong to its chapter");
    bundle(milestone.quest, pointer + "/quest");
    for (const outcome of outcomes.get(milestone.id).values()) {
      localized(outcome.title_key, outcome.pointer + "/title_key");
      const selected = outcome.location_variants ?? [];
      unique(selected, outcome.pointer + "/location_variants");
      selected.forEach((id, at) => {
        const where = outcome.pointer + "/location_variants/" + at;
        if (!irreversible(milestone)) issue(where, "location variants require an irreversible world outcome");
        if (!variants.has(id)) issue(where, "unknown location variant " + id);
      });
    }
    if (irreversible(milestone) && !milestone.catch_up) issue(pointer + "/catch_up", "irreversible milestone requires an authored catch-up entry");
    const original = context.quests?.get(milestone.quest);
    const stage = milestone.stage ?? original?.entry_stage;
    if (original && !entries(original.stages).some(value => value.id === stage)) issue(pointer + "/stage", "milestone stage does not exist: " + stage);
    if (stage !== undefined) {
      const identity = milestone.quest + "/" + stage;
      if (stageOwners.has(identity)) issue(pointer + "/stage", "another milestone already owns this quest stage");
      stageOwners.add(identity);
    }
    if (milestone.catch_up) {
      const catchUp = milestone.catch_up;
      const where = pointer + "/catch_up";
      bundle(catchUp.quest, where + "/quest", true);
      if (catchUp.quest === milestone.quest && catchUp.stage === stage) issue(where + "/stage", "catch-up must use an alternate entry stage");
      const alternate = context.quests?.get(catchUp.quest);
      if (alternate && !entries(alternate.stages).some(value => value.id === catchUp.stage)) issue(where + "/stage", "catch-up stage does not exist in " + catchUp.quest + ": " + catchUp.stage);
      if (alternate && milestone.observe?.event === "villagerretaliation:campaign_character_lost" && alternate.provider?.campaign_role === milestone.observe.character) {
        issue(where + "/quest", "character-loss catch-up cannot require the lost character as its quest provider");
      }
    }
    const observation = milestone.observe;
    if (observation) {
      if (!irreversible(milestone)) issue(pointer + "/observe", "external world observations require an irreversible milestone");
      if (!outcomes.get(milestone.id).has(observation.outcome)) issue(pointer + "/observe/outcome", "observed outcome does not exist");
      if (observation.event === "villagerretaliation:campaign_character_lost" && !characters.has(observation.character)) issue(pointer + "/observe/character", "unknown campaign character " + observation.character);
    }
  }
  const fixtures = index(root.fixture_entries ?? [], "/fixture_entries");
  for (const fixture of fixtures.values()) {
    const chapter = chapters.get(fixture.chapter);
    if (!chapter) issue(fixture.pointer + "/chapter", "unknown fixture chapter " + fixture.chapter);
    const ids = new Set();
    const world = new Map();
    const completedMilestones = new Map();
    const completedQuests = new Set();
    const changedFacts = new Set();
    const outcome = effect => {
      const milestone = milestones.get(effect.milestone);
      if (!milestone) issue(effect.pointer + "/milestone", "unknown fixture milestone " + effect.milestone);
      else if (!outcomes.get(effect.milestone).has(effect.outcome)) issue(effect.pointer + "/outcome", "unknown fixture outcome " + effect.outcome);
      return milestone;
    };
    for (const [at, raw] of fixture.effects.entries()) {
      const effect = { ...raw, pointer: fixture.pointer + "/effects/" + at };
      if (ids.has(effect.id)) issue(effect.pointer + "/id", "duplicate fixture effect " + effect.id);
      ids.add(effect.id);
      if (effect.type === "world_outcome") {
        const milestone = outcome(effect);
        if (milestone && !irreversible(milestone)) issue(effect.pointer + "/milestone", "fixture world outcome requires an irreversible milestone");
        if (world.has(effect.milestone)) issue(effect.pointer + "/milestone", "duplicate fixture world milestone " + effect.milestone);
        else world.set(effect.milestone, effect.outcome);
      } else if (effect.type === "complete_milestone") {
        const milestone = outcome(effect);
        if (milestone && irreversible(milestone) && world.get(effect.milestone) !== effect.outcome) {
          issue(effect.pointer + "/outcome", "fixture personal completion requires an earlier matching world outcome effect");
        }
        if (completedMilestones.has(effect.milestone)) issue(effect.pointer + "/milestone", "duplicate fixture personal milestone " + effect.milestone);
        else completedMilestones.set(effect.milestone, effect.outcome);
      } else if (effect.type === "complete_quest") {
        bundle(effect.quest, effect.pointer + "/quest", true);
        if (completedQuests.has(effect.quest)) issue(effect.pointer + "/quest", "duplicate fixture quest " + effect.quest);
        completedQuests.add(effect.quest);
        if ([...milestones.values()].some(milestone => milestone.quest === effect.quest && !completedMilestones.has(milestone.id))) {
          issue(effect.pointer + "/quest", "complete the quest's personal milestones earlier in the fixture");
        }
      } else if (effect.type === "set_fact") {
        const fact = facts.get(effect.fact);
        if (!fact) issue(effect.pointer + "/fact", "unknown fixture fact " + effect.fact);
        else if (fact.kind === "tag" && !["true", "false"].includes(effect.value)) issue(effect.pointer + "/value", "Campaign tags require true or false.");
        else if (fact.kind === "counter") {
          const canonical = /^(?:0|[1-9][0-9]*|-[1-9][0-9]*)$(?![\s\S])/.test(effect.value);
          if (!canonical || BigInt(effect.value) < -9223372036854775808n || BigInt(effect.value) > 9223372036854775807n) {
            issue(effect.pointer + "/value", "Campaign counters require a signed 64-bit integer.");
          }
        }
        if (changedFacts.has(effect.fact)) issue(effect.pointer + "/fact", "duplicate fixture fact " + effect.fact);
        changedFacts.add(effect.fact);
      }
    }
    for (const [at, prerequisite] of (chapter?.prerequisites ?? []).entries()) {
      let satisfied;
      if (prerequisite.chapter) {
        const prior = chapters.get(prerequisite.chapter);
        satisfied = prior && prior.quests.every(quest => completedQuests.has(quest))
          && [...milestones.values()].filter(milestone => milestone.chapter === prior.id).every(milestone => completedMilestones.has(milestone.id));
      } else satisfied = completedMilestones.has(prerequisite.milestone)
        && (!prerequisite.outcome || prerequisite.outcome === completedMilestones.get(prerequisite.milestone));
      if (!satisfied) issue(fixture.pointer + "/effects", "fixture effects do not satisfy chapter prerequisite at " + chapter.pointer + "/prerequisites/" + at);
    }
  }
  for (const reward of rewards.values()) {
    const pointer = reward.pointer;
    bundle(reward.quest, pointer + "/quest", true);
    if (!(reward.experience > 0) && !reward.loot_table) issue(pointer, "reward needs experience or a loot table");
    if (reward.scope === "personal") {
      if (reward.milestone || reward.outcome) issue(pointer, "personal rewards cannot require shared milestone credit");
    } else {
      const milestone = milestones.get(reward.milestone);
      if (!milestone || !irreversible(milestone) || milestone.quest !== reward.quest) issue(pointer + "/milestone", "shared and participant rewards require an irreversible milestone owned by their quest");
      else if (reward.outcome && !outcomes.get(milestone.id).has(reward.outcome)) issue(pointer + "/outcome", "unknown reward outcome " + reward.outcome);
    }
  }
  for (const variant of variants.values()) {
    if (!locations.has(variant.location)) issue(variant.pointer + "/location", "unknown campaign location " + variant.location);
    index(variant.cells, variant.pointer + "/cells");
  }
  return issues;
}
