import path from "node:path";
import { validateContentSchema } from "./content-schema-validation.mjs";
import { validateCampaign } from "./campaign-validation.mjs";
import { validateStandingReferences } from "./campaign-standing-validation.mjs";
import { validateTransactionReferences } from "./campaign-transaction-validation.mjs";

const normalized = file => path.resolve(file).replaceAll(path.sep, "/");
const values = entries => Array.isArray(entries) ? entries : [];
const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const field = key => key.replaceAll("~", "~0").replaceAll("/", "~1");

export function campaignDataRoot(file) {
  return normalized(file).match(/^(.*\/data)\/[a-z0-9_.-]+\/campaigns\/.+\/campaign\.json$/)?.[1];
}

function localeMessages(root) {
  if (!object(root)) return null;
  const messages = object(root.messages) ? root.messages : root;
  const result = Object.fromEntries(Object.entries(messages).filter(([key]) => messages !== root || key !== "schema"));
  return Object.entries(result).some(([key, value]) => !key.trim() || value === null) ? null : result;
}

export function validateCampaignPack(file, root, resources, schemas) {
  const issues = [];
  const issue = (file, pointer, message) => issues.push({ file, pointer, message });
  for (const problem of validateContentSchema(root, schemas.campaign)) issue(file, problem.pointer, problem.message);
  if (issues.length) return { issues, quests: [], conversations: [] };
  const dataRoot = campaignDataRoot(file);
  if (!dataRoot) {
    issue(file, "", "campaign must be a campaign.json inside data/<namespace>/campaigns/<name>/");
    return { issues, quests: [], conversations: [] };
  }
  const quests = new Map();
  const scenes = new Map();
  const conversations = new Map();
  const campaigns = [];
  const locales = [];
  const namespacePath = resource => normalized(resource.file).slice(dataRoot.length + 1);
  for (const resource of resources) {
    const local = namespacePath(resource);
    const kind = resource.data?.schema;
    if (kind === "villagerretaliation:quest/v2" && /^[a-z0-9_.-]+\/quests\/[a-z0-9_.-]+\/[a-z0-9_.-]+\/quest\.json$/.test(local)) {
      const prior = quests.get(resource.data.id);
      if (prior) issue(resource.file, "/id", "duplicate quest id also defined in " + prior.file);
      else quests.set(resource.data.id, resource);
    } else if (kind === "villagerretaliation:scene/v1") {
      const prior = scenes.get(resource.data.id);
      if (prior) issue(resource.file, "/id", "duplicate scene id also defined in " + prior.file);
      else scenes.set(resource.data.id, resource);
    } else if (kind === "villagerretaliation:conversation/v1") {
      const prior = conversations.get(resource.data.id);
      if (prior) issue(resource.file, "/id", "duplicate conversation id also defined in " + prior.file);
      else conversations.set(resource.data.id, resource);
    }
    if (campaignDataRoot(resource.file)) campaigns.push(resource);
    if (/\/(quests|campaigns)\/.+\/locales\/[^/]+\.json$/.test(local)) locales.push(resource);
  }
  const owner = normalized(path.dirname(file));
  const messages = Object.create(null);
  const questPrefixes = new Set([...quests.values()].map(entry => entry.data.localization_prefix));
  if (questPrefixes.has(root.localization_prefix)) issue(file, "/localization_prefix", "localization prefix belongs to an existing quest bundle");
  for (const resource of campaigns) {
    if (normalized(resource.file) === normalized(file)) continue;
    const problems = validateContentSchema(resource.data, schemas.campaign);
    problems.forEach(problem => issue(resource.file, problem.pointer, problem.message));
    if (problems.length) continue;
    if (resource.data.id === root.id) issue(file, "/id", "campaign id is already defined by " + resource.file);
    if (resource.data.localization_prefix === root.localization_prefix) issue(file, "/localization_prefix", "localization prefix belongs to another campaign");
  }
  const foreignKeys = new Set();
  for (const resource of locales) {
    const directory = normalized(path.dirname(resource.file));
    const owned = directory === owner + "/locales";
    const payload = localeMessages(resource.data);
    if (owned) {
      if (!/^[a-z0-9_-]+\.json$/.test(path.basename(resource.file))) issue(resource.file, "", "locale filename must be lowercase and directly inside locales/");
      if (payload === null) issue(resource.file, "", "locale root and message payloads must be readable");
      else {
        for (const key of Object.keys(payload)) if (!key.startsWith(root.localization_prefix)) issue(resource.file, "/" + field(key), "locale message is owned outside this campaign: " + key);
        if (path.basename(resource.file) === "en_us.json") Object.assign(messages, payload);
      }
    } else if (payload) for (const key of Object.keys(payload)) foreignKeys.add(key);
  }
  for (const resource of locales) {
    if (normalized(path.dirname(resource.file)) !== owner + "/locales") continue;
    for (const key of Object.keys(localeMessages(resource.data) ?? {})) if (foreignKeys.has(key)) issue(resource.file, "/" + field(key), "locale message is also owned outside this campaign: " + key);
  }
  for (const resource of resources) {
    if (normalized(resource.file).startsWith(owner + "/locales/") && normalized(path.dirname(resource.file)) !== owner + "/locales") {
      issue(resource.file, "", "locale filename must be lowercase and directly inside locales/");
    }
  }
  const selectedQuests = root.quest_bundles.map(id => quests.get(id)).filter(Boolean);
  const validQuests = new Map();
  for (const resource of selectedQuests) {
    const problems = validateContentSchema(resource.data, schemas.quest);
    problems.forEach(problem => issue(resource.file, problem.pointer, problem.message));
    if (!problems.length) validQuests.set(resource.data.id, resource.data);
  }
  for (const problem of validateCampaign(root, schemas.campaign, { messages, quests: validQuests })) issue(file, problem.pointer, problem.message);
  for (const [id, resource] of scenes) {
    const problems = validateContentSchema(resource.data, schemas.scene);
    problems.forEach(problem => issue(resource.file, problem.pointer, problem.message));
    if (problems.length) scenes.delete(id);
  }
  const selectedConversations = [...conversations.values()].filter(entry => entry.data.campaign === root.id);
  const characters = new Map((root.characters ?? []).map(value => [value.id, value]));
  const locations = new Set((root.locations ?? []).map(value => value.id));
  const evidence = new Set((root.evidence ?? []).map(value => value.id));
  const milestones = new Map(root.milestones.map(value => [value.id, value]));
  const privateOwner = resource => {
    const owner = normalized(path.dirname(path.dirname(resource.file)));
    if (path.basename(owner) === "_shared") return null;
    return [...quests.values()].find(entry => normalized(path.dirname(entry.file)) === owner)?.data.id ?? "";
  };
  for (const resource of selectedQuests) {
    if (!validQuests.has(resource.data.id)) continue;
    const quest = resource.data;
    const context = { campaign: root, quest: quest.id, quests: new Map([...quests].map(([id, entry]) => [id, entry.data])),
      campaigns: new Map(campaigns.filter(entry => object(entry.data)).map(entry => [entry.data.id, entry.data])) };
    validateStandingReferences(quest, context).forEach(problem => issue(resource.file, problem.pointer, problem.message));
    validateTransactionReferences(quest, context).forEach(problem => issue(resource.file, problem.pointer, problem.message));
    const role = quest.provider?.campaign_role;
    if (role && !characters.has(role)) issue(resource.file, "/provider/campaign_role", "unknown campaign character " + role);
    quest.stages.forEach((stage, at) => {
      values(stage.objectives).forEach((objective, position) => {
        const pointer = "/stages/" + at + "/objectives/" + position;
        if (objective.campaign_location && !locations.has(objective.campaign_location)) issue(resource.file, pointer + "/campaign_location", "unknown campaign location " + objective.campaign_location);
        const story = objective.story;
        if (!object(story)) return;
        const problem = (key, message) => issue(resource.file, pointer + "/story/" + key, message);
        const type = objective.type.replace(/^villagerretaliation:/, "");
        if (type === "campaign_investigate" && !evidence.has(story.evidence)) problem("evidence", "unknown campaign evidence source " + story.evidence);
        if (type === "campaign_outcome") {
          const milestone = milestones.get(story.milestone);
          if (!milestone) problem("milestone", "unknown campaign milestone " + story.milestone);
          else {
            if (!milestone.outcomes.some(value => value.id === story.outcome)) problem("outcome", "unknown milestone outcome " + story.outcome);
            if (milestone.irreversible === false && story.scope !== "completed") problem("scope", "world outcomes, discovery and participation require an irreversible milestone");
          }
        }
        if (type === "campaign_speak" || type === "campaign_follow") {
          if (!characters.has(story.role)) problem("role", "unknown campaign character " + story.role);
          const target = type === "campaign_speak" ? conversations.get(story.conversation) : scenes.get(story.scene);
          const key = type === "campaign_speak" ? "conversation" : "scene";
          if (!target) { problem(key, "authored " + key + " is unavailable"); return; }
          const owner = privateOwner(target);
          if (owner !== null && owner !== quest.id) problem(key, "private " + key + " belongs to another quest bundle");
          if (type === "campaign_speak") {
            if (target.data.campaign !== root.id) problem(key, "conversation belongs to another campaign");
            const node = values(target.data.nodes).find(value => value?.id === story.node);
            if (!node) problem("node", "unknown conversation node " + story.node);
            else if (!values(node.exchanges).some(value => value?.speaker?.type === "role" && value.speaker.role === story.role)) problem("role", "the authored character does not speak at this node");
          } else {
            if (!locations.has(story.location)) problem("location", "unknown campaign destination " + story.location);
            const step = values(target.data.steps).find(value => value.id === story.step);
            if (step?.type !== "villagerretaliation:follow_actor") problem("step", "objective requires an authored follow_actor step");
            else {
              const guide = values(target.data.actors).find(value => value.alias === step.data?.actor);
              const destination = values(target.data.actors).find(value => value.alias === step.data?.target_actor);
              if ((guide?.binding ?? guide?.source) !== story.role) problem("role", "follow step uses another character role");
              if ((destination?.binding ?? destination?.source) !== story.location) problem("location", "follow step uses another destination");
            }
          }
        }
      });
    });
  }
  for (const resource of scenes.values()) {
    validateTransactionReferences(resource.data, {
      quests: new Map([...quests].map(([id, entry]) => [id, entry.data])),
      campaigns: new Map(campaigns.filter(entry => object(entry.data)).map(entry => [entry.data.id, entry.data]))
    }).forEach(problem => issue(resource.file, problem.pointer, problem.message));
    if (!root.quest_bundles.includes(privateOwner(resource))) continue;
    values(resource.data.actors).forEach((actor, at) => {
      const key = actor.binding === undefined ? "source" : "binding";
      const binding = actor[key];
      if (actor.binding_source === "campaign_role" && !characters.has(binding)) issue(resource.file, "/actors/" + at + "/" + key, "unknown campaign character " + binding);
      if (actor.binding_source === "campaign_location" && !locations.has(binding)) issue(resource.file, "/actors/" + at + "/" + key, "unknown campaign location " + binding);
    });
  }
  return { issues, quests: selectedQuests, conversations: selectedConversations };
}
