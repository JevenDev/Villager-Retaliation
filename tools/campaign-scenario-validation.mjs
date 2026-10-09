import path from "node:path";
import { validateContentSchema } from "./content-schema-validation.mjs";

const values = entries => Array.isArray(entries) ? entries : [];
const named = entries => new Map(values(entries).map(entry => [entry.id, entry]));
const requests = new Set(["quest_response", "quest_select", "quest_confirmation", "conversation_start", "conversation_intent", "fixture_entry"]);
const ordinaryRequests = new Set(["quest_response", "quest_select", "quest_confirmation"]);
const playerSteps = new Set(["player", "quest_prompt", "quest_response", "quest_confirmation", "conversation_start", "conversation_intent", "interrupt", "fixture_entry", "assert_player", "assert_quest", "assert_standing"]);

export function scenarioPath(file) {
  const normalized = path.resolve(file).replaceAll(path.sep, "/");
  const match = /^(.*\/data)\/([a-z0-9_.-]+)\/campaign_scenarios\/([a-z0-9_./-]+)\.json$/.exec(normalized);
  return match ? { dataRoot: match[1], id: match[2] + ":" + match[3] } : null;
}

export function validateCampaignScenario(root, schema, context) {
  const issues = validateContentSchema(root, schema);
  if (issues.length) return issues;
  const issue = (pointer, message) => issues.push({ pointer, message });
  const unique = (entries, key, pointer, label, optional = false) => {
    const found = new Set();
    entries.forEach((entry, index) => {
      const value = key ? entry[key] : entry;
      if (optional && !value) return;
      if (found.has(value)) issue(pointer + "/" + index + (key ? "/" + key : ""), "duplicate scenario " + label);
      found.add(value);
    });
    return found;
  };
  const players = unique(root.players, null, "/players", "player");
  const actors = unique(root.actors ?? [], "id", "/actors", "actor");
  unique(root.actors ?? [], "role", "/actors", "character role", true);
  unique(root.locations ?? [], "alias", "/locations", "location");
  const prior = new Map();
  const sessions = new Set();
  let ticks = 0;
  root.steps.forEach((step, index) => {
    const at = "/steps/" + index;
    const refs = playerSteps.has(step.type) ? ["player"] : [];
    if (step.type === "party" || step.type === "privilege") refs.push("leader", "member");
    if (step.type === "conversation_intent" && step.intent === "hand_over") refs.push("argument");
    if (step.type === "assert_conversation" && step.controller) refs.push("controller");
    for (const key of refs) if (!players.has(step[key])) issue(at + "/" + key, "unknown scenario player " + (step[key] ?? ""));
    if (["quest_response", "quest_prompt", "interrupt"].includes(step.type) && !actors.has(step.actor)) issue(at + "/actor", "unknown scenario actor " + step.actor);
    if (["conversation_intent", "assert_conversation", "assert_check"].includes(step.type) && !sessions.has(step.session)) issue(at + "/session", "session must be started earlier in the scenario");
    if (step.type === "conversation_start") {
      if (sessions.has(step.session)) issue(at + "/session", "duplicate scenario session");
      sessions.add(step.session);
    }
    if (step.type === "quest_select" && prior.get(step.prompt)?.type !== "quest_prompt") issue(at + "/prompt", "quest selection requires an earlier prompt");
    if (step.type === "replay") {
      const request = prior.get(step.request);
      if (!requests.has(request?.type)) issue(at + "/request", "replay requires an earlier submitted request");
      else if (step.accepted === false && ordinaryRequests.has(request.type)) issue(at + "/accepted", "ordinary quest requests have no acceptance response, assert their resulting state instead");
    }
    if (step.type === "assert_same" && prior.get(step.checkpoint)?.type !== "checkpoint") issue(at + "/checkpoint", "assert_same requires an earlier checkpoint");
    if (step.type === "checkpoint") {
      const valid = step.scope === "campaign" ? !step.target : step.scope === "player" ? players.has(step.target) : sessions.has(step.target);
      if (!valid) issue(at + "/target", "checkpoint target does not match its scope");
    }
    if (step.type === "conversation_intent") {
      const checkpoint = prior.get(step.revision_from);
      if (step.revision_from && (checkpoint?.type !== "checkpoint" || checkpoint.scope !== "conversation" || checkpoint.target !== step.session)) issue(at + "/revision_from", "revision checkpoint must name this conversation");
      if (step.seed !== undefined && step.intent !== "roll") issue(at + "/seed", "random seed is only valid for a roll request");
      if (["choose", "hand_over"].includes(step.intent) !== Boolean(step.argument)) issue(at + "/argument", "intent argument is missing or unsupported");
    }
    if (step.type === "assert_campaign" && !["outcomes", "audits", "rewards"].some(key => step[key] !== undefined)) issue(at, "campaign assertion must check at least one count");
    if (step.type === "wait") ticks += step.ticks;
    if (prior.has(step.id)) issue(at + "/id", "duplicate scenario step " + step.id);
    else prior.set(step.id, step);
  });
  if (ticks > 6000) issue("/steps", "scenario waits exceed 6000 ticks");
  if (issues.length || !context) return issues;
  validateReferences(root, context, issue);
  return issues;
}

function validateReferences(root, context, issue) {
  const campaign = context.campaigns.get(root.campaign);
  if (!campaign) { issue("/campaign", "scenario campaign is unavailable"); return; }
  const roles = named(campaign.characters);
  const locations = named(campaign.locations);
  const milestones = named(campaign.milestones);
  const fixtureEntries = named(campaign.fixture_entries);
  const owned = new Set(campaign.quest_bundles);
  values(root.actors).forEach((actor, index) => {
    if (actor.role && roles.get(actor.role)?.source !== "existing_villager") issue("/actors/" + index + "/role", "scenario actor requires an existing-villager campaign role");
  });
  values(root.locations).forEach((location, index) => {
    const definition = locations.get(location.alias);
    if (!definition || definition.dimension !== "minecraft:overworld") issue("/locations/" + index + "/alias", "scenario location must name an overworld campaign location");
  });
  const sessions = new Map();
  const prompts = new Map();
  root.steps.forEach((step, index) => {
    const at = "/steps/" + index;
    let response = null;
    if (step.type === "quest_prompt") { prompts.set(step.id, step); response = step; }
    else if (step.type === "quest_response") response = step;
    else if (step.type === "quest_select") response = { ...prompts.get(step.prompt), response: step.response };
    if (response) {
      const quest = context.quests.get(response.quest);
      if (!quest || !owned.has(response.quest)) { issue(at + "/quest", "quest does not belong to this campaign"); return; }
      const stage = values(quest.stages).find(value => value.id === response.stage);
      const slot = stage?.dialogue?.[response.slot];
      const ref = slot?.scene ?? slot?.scene_ref;
      const node = ref ? values(stage.scenes).find(value => value.id === ref) : slot;
      if (!node) issue(at + "/slot", "quest dialogue binding is unavailable");
      else if (response.response && !values(node.responses).some(value => value.id === response.response)) issue(at + "/response", "unknown authored quest response");
    } else if (step.type === "assert_quest") {
      const quest = context.quests.get(step.quest);
      if (!quest || !owned.has(step.quest)) issue(at + "/quest", "quest does not belong to this campaign");
      else if (step.stage && !values(quest.stages).some(value => value.id === step.stage)) issue(at + "/stage", "unknown quest stage");
    } else if (step.type === "assert_standing") {
      const standing = values(step.scope === "character" ? campaign.relationships : campaign.factions).find(value => value.id === step.subject);
      if (!standing) issue(at + "/subject", "unknown campaign standing " + step.subject);
      else {
        if (step.value < standing.minimum || step.value > standing.maximum) issue(at + "/value", "standing assertion is outside its authored range");
        if (step.reaction && !values(standing.reactions).some(value => value.id === step.reaction)) issue(at + "/reaction", "unknown standing reaction " + step.reaction);
      }
    } else if (step.type === "conversation_start") {
      const conversation = context.conversations.get(step.conversation);
      if (!conversation || conversation.campaign !== campaign.id) issue(at + "/conversation", "conversation does not belong to this campaign");
      else {
        sessions.set(step.session, conversation);
        if (!values(conversation.topics).some(value => value.id === step.topic)) issue(at + "/topic", "unknown conversation topic");
      }
    } else if (step.type === "fixture_entry" && !fixtureEntries.has(step.entry)) issue(at + "/entry", "unknown campaign fixture entry");
    else if (step.type === "assert_outcome") {
      const milestone = milestones.get(step.milestone);
      if (!milestone?.irreversible) issue(at + "/milestone", "unknown irreversible campaign milestone");
      else if (!values(milestone.outcomes).some(value => value.id === step.outcome)) issue(at + "/outcome", "unknown milestone outcome");
    } else if (step.type === "assert_player" && step.participation) {
      const found = new Set();
      step.participation.forEach((id, position) => {
        if (!milestones.get(id)?.irreversible || found.has(id)) issue(at + "/participation/" + position, "participation must name distinct irreversible milestones");
        found.add(id);
      });
    } else if (step.type === "world_event" && !values(campaign.milestones).some(value => value.observe?.event === step.event)) issue(at + "/event", "campaign does not observe this world event");
    const conversation = sessions.get(step.session);
    if (!conversation) return;
    if (step.type === "conversation_intent" && step.intent === "choose" && !values(conversation.nodes).some(node => values(node.responses).some(value => value.id === step.argument))) issue(at + "/argument", "unknown conversation response");
    else if (step.type === "assert_check" && !values(conversation.checks).some(value => value.id === step.check)) issue(at + "/check", "unknown conversation check");
    else if (step.type === "assert_conversation") {
      if (step.node && !values(conversation.nodes).some(value => value.id === step.node)) issue(at + "/node", "unknown conversation node");
      if (step.outcome && !values(conversation.outcomes).some(value => value.id === step.outcome)) issue(at + "/outcome", "unknown conversation outcome");
    }
  });
}

export function scenarioContext(resources) {
  const context = { campaigns: new Map(), quests: new Map(), conversations: new Map() };
  const fields = { "villagerretaliation:campaign/v1": "campaigns", "villagerretaliation:quest/v2": "quests", "villagerretaliation:conversation/v1": "conversations" };
  for (const resource of resources) {
    const field = fields[resource.data?.schema];
    if (field) context[field].set(resource.data.id, resource.data);
  }
  return context;
}
