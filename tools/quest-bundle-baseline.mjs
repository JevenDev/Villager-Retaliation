import { createHash } from "node:crypto";
import { readFile, readdir, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dataRoot = path.join(root, "neoforge", "src", "main", "resources", "data", "villagerretaliation");
const fixtureRoot = path.join(root, "tools", "quest-bundle-baseline");
const sourceCommit = "6e5913766bc93eede34185e0b87eaa428e53b0e6";
const roots = {
  quests: path.join(dataRoot, "quests"),
  scenes: path.join(dataRoot, "quest_scenes"),
  encounters: path.join(dataRoot, "quest_encounters"),
  pools: path.join(dataRoot, "quest_pools"),
  rewards: path.join(dataRoot, "loot_table", "quest"),
  messages: path.join(dataRoot, "dialogue", "en_us", "quests", "messages")
};

const textFields = new Set(["title", "description", "label", "text", "lines", "tracker_text", "complete_text", "custom_name", "trophy_name"]);
const stableArrays = new Set(["stages", "objectives", "events", "triggers", "scenes", "responses", "actors", "steps", "actions", "waves", "variants", "members", "phases"]);

const [quests, scenes, encounters, pools, rewards, messageFiles] = await Promise.all([
  loadIdentified(roots.quests),
  loadIdentified(roots.scenes),
  loadIdentified(roots.encounters),
  loadIdentified(roots.pools),
  loadRewards(roots.rewards),
  loadJsonFiles(roots.messages)
]);
const messages = loadMessages(messageFiles);
const ownership = inferOwnership();
const localeKeys = collectLocaleKeys();
function manifest() {
  const ids = {
    quests: sorted(quests.keys()), stages: [], objectives: [], triggers: [],
    scenes: sorted(scenes.keys()), steps: [], operations: [],
    encounters: sorted(encounters.keys()), rewards: sorted(rewards.keys()),
    pools: sorted(pools.keys()), messages: sorted(messages.keys()),
    prefixes: [], aliases: []
  };
  const slugs = {};
  const owners = [];
  for (const [questId, entry] of sortedEntries(quests)) {
    const slug = path.basename(entry.file, ".json");
    slugs[questId] = slug;
    ids.prefixes.push(questPrefix(entry));
    owners.push({ id: questId, kind: "quest", owner: questId });
    for (const stage of entry.data.stages ?? []) {
      ids.stages.push(questId + "#" + stage.id);
      for (const objective of stage.objectives ?? []) ids.objectives.push(questId + "#" + stage.id + "/" + objective.id);
      collectOperations(stage, questId + "#" + stage.id, ids.operations);
    }
    for (const trigger of [...(entry.data.events ?? []), ...(entry.data.triggers ?? [])]) {
      if (trigger?.id) ids.triggers.push(questId + "#" + trigger.id);
    }
    collectOperations(entry.data.events ?? [], questId + "#event", ids.operations);
    const revision = entry.data.metadata?.revision ?? entry.data.revision ?? {};
    for (const [from, to] of Object.entries(revision.stage_aliases ?? {})) ids.aliases.push(questId + "#stage:" + from + "->" + to);
    for (const [from, to] of Object.entries(revision.objective_aliases ?? {})) ids.aliases.push(questId + "#objective:" + from + "->" + to);
  }
  for (const [sceneId, entry] of sortedEntries(scenes)) {
    owners.push({ id: sceneId, kind: "scene", owner: ownership.scenes.get(sceneId) });
    for (const actor of entry.data.actors ?? []) if (actor?.alias) ids.aliases.push(sceneId + "#actor:" + actor.alias);
    for (const step of entry.data.steps ?? []) {
      ids.steps.push(sceneId + "#" + step.id);
      collectOperations(step, sceneId + "#" + step.id, ids.operations);
    }
  }
  for (const id of encounters.keys()) owners.push({ id, kind: "encounter", owner: ownership.encounters.get(id) });
  for (const id of rewards.keys()) owners.push({ id, kind: "reward", owner: ownership.rewards.get(id) });
  for (const id of pools.keys()) owners.push({ id, kind: "pool", owner: "_shared" });
  for (const id of messages.keys()) owners.push({ id, kind: "message", owner: ownership.messages.get(id) });
  for (const key of Object.keys(ids)) ids[key] = sorted(new Set(ids[key]));
  return canonical({
    schema: "villagerretaliation:quest_bundle_compatibility/v1",
    source_commit: sourceCommit,
    ids,
    frozen_builtin_slugs: slugs,
    canonical_owners: owners.sort((a, b) => a.id.localeCompare(b.id) || a.kind.localeCompare(b.kind)),
    locale_keys: [...localeKeys.values()].sort((a, b) => a.key.localeCompare(b.key))
  });
}

function equivalence() {
  const payload = {
    structures: mapData(quests),
    effective_en_us: Object.fromEntries(sortedEntries(localeKeys).map(([key, value]) => [key, value.payload])),
    scenes: mapData(scenes), encounters: mapData(encounters),
    rewards: mapData(rewards), pools: mapData(pools)
  };
  return canonical({
    schema: "villagerretaliation:quest_bundle_compiled_equivalence/v1",
    source_commit: sourceCommit,
    ...payload,
    fingerprints: {
      persistent_structure: fingerprint({
        structures: payload.structures, scenes: payload.scenes,
        encounters: payload.encounters, pools: payload.pools
      }),
      migration_equivalence: fingerprint(payload)
    }
  });
}

function behavioral() {
  const seeds = [0, 1, 42, 3733, 16411];
  const messageSelections = [];
  for (const id of [
    "quest.offer_hint.trust",
    "quest.offer_hint.level",
    "quest.village_supply.bread_delivery.objective.bring_bread.text",
    "quest.cartographers_atlas.choose_the_horizon.title"
  ]) {
    if (!localeKeys.has(id)) continue;
    const variants = variantsOf(localeKeys.get(id).payload);
    for (const seed of seeds) messageSelections.push({ id, seed, selected: selectMessage(id, variants, seed) });
  }
  const rewardSelections = [];
  for (const id of [
    "villagerretaliation:quest/bread_delivery",
    "villagerretaliation:quest/egg_baskets",
    "villagerretaliation:quest/map_paper"
  ]) {
    if (!rewards.has(id)) continue;
    for (const seed of seeds) rewardSelections.push({ id, seed, items: simulateReward(rewards.get(id).data, seed) });
  }
  const poolSelections = [];
  for (const [id, entry] of sortedEntries(pools)) {
    for (const [scope, epoch] of [["baseline-village", 0], ["baseline-village", 1], ["other-village", 17]]) {
      poolSelections.push({ id, scope, epoch, quests: selectPool(entry.data, scope, epoch) });
    }
  }
  return canonical({
    schema: "villagerretaliation:quest_bundle_behavioral_baseline/v1",
    source_commit: sourceCommit,
    rng_contract: "net.minecraft.util.RandomSource.create(seed); semantically ordered arrays preserved",
    messages: messageSelections, pools: poolSelections, rewards: rewardSelections
  });
}

function saveFixtures() {
  return canonical({
    schema: "villagerretaliation:quest_bundle_save_fixtures/v1",
    quest_data_version: 4, scene_data_version: 4,
    player: "00000000-0000-4000-8000-000000000001",
    provider: "00000000-0000-4000-8000-000000000002",
    fixtures: [
      { id: "active", quest: "villagerretaliation:bread_delivery", state: "ACTIVE", stage: "work", progress: { bring_bread: 9 } },
      { id: "completed", quest: "villagerretaliation:blank_map_promise", state: "COMPLETED", stage: "return", completion_count: 1 },
      { id: "abandoned", quest: "villagerretaliation:night_run", state: "ABANDONED", stage: "escort", abandonment_time: 72000 },
      { id: "branching", quest: "villagerretaliation:choose_the_horizon", state: "ACTIVE", stage: "coast_final", variables: { choice: "coast" }, choice_history: ["choice=coast"] },
      { id: "randomized", quest: "villagerretaliation:market_day", state: "ACTIVE", stage: "work", selected_items: { bring_market_goods: "minecraft:apple" } },
      { id: "party", quest: "villagerretaliation:standing_watch", state: "ACTIVE", stage: "watch", party_scope: "00000000-0000-4000-8000-000000000003", pending_party_reward: true },
      { id: "tracked", quest: "villagerretaliation:bread_delivery", state: "ACTIVE", stage: "work", tracked: true },
      { id: "pending", quest: "villagerretaliation:tales_of_a_lost_civilization", state: "ACTIVE", stage: "return", pending_lifecycle_events: ["completed"], pending_trigger_events: ["lost_city_told"] },
      { id: "scene", quest: "villagerretaliation:choose_the_horizon", state: "ACTIVE", stage: "coast_final", scene: { id: "villagerretaliation:atlas_horizon_choice", step: "ink_the_choice", operation: "atlas_horizon_choice_v1" } },
      { id: "encounter", quest: "villagerretaliation:standing_watch", state: "ACTIVE", stage: "watch", encounter: { id: "villagerretaliation:standing_watch", wave: "captain_assault", definition_version: 1 } },
      { id: "missing-definition", quest: "missingpack:removed_quest", state: "ACTIVE", stage: "stable_stage", progress: { stable_objective: 7 }, unresolved: true }
    ]
  });
}

function inferOwnership() {
  const sceneOwners = new Map([...scenes].map(([id, entry]) => [id, entry.data.metadata?.quest ?? "_shared"]));
  const encounterClaims = new Map();
  for (const [sceneId, entry] of scenes) walk(entry.data, object => claim(object.encounter, sceneOwners.get(sceneId), encounterClaims));
  let changed = true;
  while (changed) {
    changed = false;
    for (const [id, entry] of encounters) {
      const owners = encounterClaims.get(id) ?? new Set();
      for (const variant of entry.data.variants ?? []) {
        const before = encounterClaims.get(variant.template)?.size ?? 0;
        for (const owner of owners) claim(variant.template, owner, encounterClaims);
        if ((encounterClaims.get(variant.template)?.size ?? 0) !== before) changed = true;
      }
    }
  }
  const encounterOwners = new Map([...encounters.keys()].map(id => [id, singleOwner(encounterClaims.get(id))]));
  const rewardClaims = new Map();
  for (const [id, entry] of quests) walk(entry.data, object => claim(object.loot_table, id, rewardClaims));
  for (const [id, entry] of encounters) walk(entry.data, object => claim(object.loot_table, encounterOwners.get(id), rewardClaims));
  const rewardOwners = new Map([...rewards.keys()].map(id => [id, singleOwner(rewardClaims.get(id))]));
  const prefixOwners = sortedEntries(quests).map(([id, entry]) => [questPrefix(entry), id])
    .sort((a, b) => b[0].length - a[0].length);
  const messageOwners = new Map([...messages.keys()].map(id => {
    const match = prefixOwners.find(([prefix]) => id === prefix || id.startsWith(prefix + "."));
    return [id, match?.[1] ?? "_shared"];
  }));
  return { scenes: sceneOwners, encounters: encounterOwners, rewards: rewardOwners, messages: messageOwners };
}

function collectLocaleKeys() {
  const claims = new Map();
  for (const [key, payload] of messages) claims.set(key, {
    key, owner: ownership.messages.get(key), generated: false,
    sources: ["message:" + key], payload
  });
  for (const [id, entry] of quests) collectText(entry.data, {
    owner: id, prefix: questPrefix(entry), source: "quest:" + id, segments: []
  }, claims);
  for (const [id, entry] of scenes) collectText(entry.data, {
    owner: ownership.scenes.get(id), prefix: ownerPrefix(ownership.scenes.get(id)),
    source: "scene:" + id, segments: ["scene", stablePath(id)]
  }, claims);
  for (const [id, entry] of encounters) collectText(entry.data, {
    owner: ownership.encounters.get(id), prefix: ownerPrefix(ownership.encounters.get(id)),
    source: "encounter:" + id, segments: ["encounter", stablePath(id)]
  }, claims);
  return new Map(sortedEntries(claims));
}

function collectText(value, context, claims) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return;
  for (const [field, child] of Object.entries(value)) {
    if (textFields.has(field) && hasText(child)) {
      if (field === "lines" && child && typeof child === "object" && !Array.isArray(child)) {
        for (const [status, payload] of Object.entries(child)) addClaim(value, status, payload, {
          ...context, segments: [...context.segments, "lines"]
        }, claims);
      } else addClaim(value, field, child, context, claims);
      continue;
    }
    if (Array.isArray(child)) {
      for (const element of child) {
        if (!element || typeof element !== "object" || Array.isArray(element)) continue;
        collectText(element, {
          ...context, segments: [...context.segments, singular(field), stableToken(element, field)]
        }, claims);
      }
    } else if (child && typeof child === "object") {
      collectText(child, { ...context, segments: [...context.segments, field] }, claims);
    }
  }
}

function addClaim(parent, field, payload, context, claims) {
  const explicit = explicitKey(parent, field, payload);
  const segments = ((field === "title" || field === "description")
    && context.segments.length === 1 && ["metadata", "ui"].includes(context.segments[0]))
    ? [] : context.segments;
  const key = explicit || [context.prefix, ...segments, field].filter(Boolean).join(".");
  const source = [context.source, ...context.segments, field].join("/");
  const previous = claims.get(key);
  if (previous) {
    if (!explicit && previous.generated
      && JSON.stringify(previous.payload) !== JSON.stringify(canonical(payload))) {
      throw new Error("Generated locale key collision for " + key + ": "
        + [...previous.sources, source].join(", "));
    }
    previous.sources = sorted(new Set([...previous.sources, source]));
    previous.generated &&= !explicit;
    return;
  }
  claims.set(key, {
    key, owner: context.owner, generated: !explicit,
    sources: [source], payload: canonical(payload)
  });
}

function explicitKey(parent, field, payload) {
  for (const candidate of [
    field + "_key", field === "text" ? "line_key" : "",
    field === "lines" ? "key" : ""
  ]) {
    if (candidate && typeof parent[candidate] === "string" && parent[candidate]) return parent[candidate];
  }
  return payload && !Array.isArray(payload) && typeof payload === "object"
    && typeof payload.key === "string" ? payload.key : "";
}

function stableToken(object, field) {
  for (const key of [
    "id", "alias", "operation_id", "action", "type", "event",
    "trigger", "key", "name", "template", "entity"
  ]) {
    if (typeof object[key] === "string" && object[key]) return stablePath(object[key]);
  }
  if (stableArrays.has(field) && containsText(object)) {
    throw new Error("Player-facing " + field + " entry lacks stable semantic identifier: "
      + JSON.stringify(object).slice(0, 180));
  }
  return singular(field);
}

function selectMessage(id, variants, seed) {
  if (!variants.length) return "";
  const ordered = variants.map((value, index) => ({
    id: variants.length === 1 ? id : id + "#line_" + index, value
  })).sort((a, b) => a.id.localeCompare(b.id));
  return ordered[new JavaRandom(seed).nextInt(ordered.length)].value;
}

function simulateReward(table, seed) {
  const random = new JavaRandom(seed);
  const result = [];
  for (const pool of table.pools ?? []) {
    const entries = (pool.entries ?? []).filter(entry => entry.type === "minecraft:item");
    const total = entries.reduce((sum, entry) => sum + Number(entry.weight ?? 1), 0);
    if (!entries.length || total <= 0) continue;
    let ticket = random.nextInt(total);
    let selected = entries.at(-1);
    for (const entry of entries) {
      ticket -= Number(entry.weight ?? 1);
      if (ticket < 0) { selected = entry; break; }
    }
    let count = 1;
    for (const fn of selected.functions ?? []) {
      if (fn.function !== "minecraft:set_count") continue;
      if (typeof fn.count === "number") count = fn.count;
      else if (fn.count?.type === "minecraft:uniform") {
        count = Number(fn.count.min)
          + random.nextInt(Number(fn.count.max) - Number(fn.count.min) + 1);
      }
    }
    result.push({ item: selected.name, count });
  }
  return result;
}

function selectPool(pool, scope, epoch) {
  const candidates = sortedEntries(quests)
    .map(([id, entry]) => ({ id, data: entry.data }))
    .filter(candidate => poolClaims(pool, candidate.id,
      new Set(candidate.data.metadata?.tags ?? [])))
    .map(candidate => ({
      ...candidate,
      weight: poolWeight(pool, candidate.id,
        new Set(candidate.data.metadata?.tags ?? []),
        candidate.data.provider?.weight ?? 1)
    })).filter(candidate => candidate.weight > 0);
  const recent = new Set();
  for (let offset = 1; offset <= Number(pool.anti_repeat_rotations ?? 1); offset++) {
    for (const id of poolDraw(pool, candidates, scope, epoch - offset,
      new Set(), Number(pool.max_offers ?? 3), [])) recent.add(id);
  }
  const selected = new Set(poolDraw(pool, candidates, scope, epoch, recent,
    Number(pool.max_offers ?? 3), []));
  if (selected.size < Math.min(Number(pool.max_offers ?? 3), candidates.length)) {
    const selectedData = candidates.filter(candidate => selected.has(candidate.id))
      .map(candidate => candidate.data);
    for (const id of poolDraw(pool, candidates, scope + "\0backfill", epoch,
      selected, Number(pool.max_offers ?? 3) - selected.size, selectedData)) {
      selected.add(id);
    }
  }
  return sorted(selected);
}

function poolDraw(pool, candidates, scope, epoch, excluded, limit, initial) {
  const remaining = candidates.filter(candidate => !excluded.has(candidate.id));
  const selected = [];
  const selectedData = [...initial];
  let state = poolSeed(pool, scope, epoch);
  while (remaining.length && selected.length < limit) {
    for (let i = remaining.length - 1; i >= 0; i--) {
      if (!quotaAllows(pool, remaining[i].data, selectedData)) remaining.splice(i, 1);
    }
    if (!remaining.length) break;
    const total = remaining.reduce((sum, candidate) => sum + BigInt(candidate.weight), 0n);
    state = mix64(state + 0x9e3779b97f4a7c15n);
    let ticket = unsigned(state) % total;
    let chosen = 0;
    for (let i = 0; i < remaining.length; i++) {
      ticket -= BigInt(remaining[i].weight);
      if (ticket < 0n) { chosen = i; break; }
    }
    const [choice] = remaining.splice(chosen, 1);
    selected.push(choice.id);
    selectedData.push(choice.data);
  }
  return selected;
}

function poolClaims(pool, id, tags) {
  if (pool.enabled === false || (pool.exclude_quests ?? []).includes(id)
    || (pool.exclude_tags ?? []).some(tag => tags.has(tag))) return false;
  const explicit = !(pool.quests ?? []).length || pool.quests.includes(id);
  const all = !(pool.all_tags ?? []).length || pool.all_tags.every(tag => tags.has(tag));
  const any = !(pool.any_tags ?? []).length || pool.any_tags.some(tag => tags.has(tag));
  if (pool.match === "all") {
    return explicit && all && any
      && ((pool.quests ?? []).length + (pool.all_tags ?? []).length
        + (pool.any_tags ?? []).length > 0);
  }
  return ((pool.quests ?? []).length > 0 && explicit)
    || ((pool.all_tags ?? []).length > 0 && all)
    || ((pool.any_tags ?? []).length > 0 && any);
}

function poolWeight(pool, id, tags, offerWeight) {
  let value = Object.hasOwn(pool.weights ?? {}, id)
    ? Math.max(0, pool.weights[id])
    : Number(pool.default_weight ?? 1) * Number(offerWeight ?? 1);
  for (const rule of pool.weight_rules ?? []) {
    if ((rule.any_tags ?? []).length
      && !(rule.any_tags ?? []).some(tag => tags.has(tag))) continue;
    if (!(rule.all_tags ?? []).every(tag => tags.has(tag))) continue;
    if ((rule.exclude_tags ?? []).some(tag => tags.has(tag))) continue;
    value *= Number(rule.multiplier ?? 1);
  }
  return Math.max(0, Math.min(10000, Math.round(value)));
}

function quotaAllows(pool, candidate, selected) {
  const tags = new Set(candidate.metadata?.tags ?? []);
  for (const [tag, limit] of Object.entries(pool.tag_quotas ?? {})) {
    if (!tags.has(tag)) continue;
    const used = selected.filter(quest =>
      new Set(quest.metadata?.tags ?? []).has(tag)).length;
    if (used >= Math.max(0, Number(limit))) return false;
  }
  return true;
}

function poolSeed(pool, scope, epoch) {
  let value = 0xcbf29ce484222325n;
  for (const byte of new TextEncoder().encode(pool.id + "\0" + scope)) {
    value = signed((value ^ BigInt(byte)) * 0x100000001b3n);
  }
  return mix64(value ^ BigInt(pool.seed_salt ?? 0) ^ BigInt(epoch));
}

function mix64(input) {
  let value = signed(input);
  value = signed((value ^ (unsigned(value) >> 30n)) * 0xbf58476d1ce4e5b9n);
  value = signed((value ^ (unsigned(value) >> 27n)) * 0x94d049bb133111ebn);
  return signed(value ^ (unsigned(value) >> 31n));
}

class JavaRandom {
  constructor(seed) {
    this.state = (BigInt(seed) ^ 0x5deece66dn) & ((1n << 48n) - 1n);
  }
  next(bits) {
    this.state = (this.state * 0x5deece66dn + 0xbn) & ((1n << 48n) - 1n);
    return Number(this.state >> BigInt(48 - bits));
  }
  nextInt(bound) {
    if (bound <= 0) throw new Error("Invalid random bound " + bound);
    if ((bound & -bound) === bound) {
      return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    }
    let bits;
    let value;
    do {
      bits = this.next(31);
      value = bits % bound;
    } while (bits - value + bound - 1 > 0x7fffffff);
    return value;
  }
}

async function loadIdentified(directory) {
  const result = new Map();
  for (const entry of await loadJsonFiles(directory)) {
    const id = entry.data?.id;
    if (!id) throw new Error(relative(entry.file) + " has no stable id");
    if (result.has(id)) throw new Error("Duplicate stable id " + id);
    result.set(id, entry);
  }
  return new Map(sortedEntries(result));
}

async function loadRewards(directory) {
  const result = new Map();
  for (const entry of await loadJsonFiles(directory)) {
    const local = path.relative(directory, entry.file)
      .replace(/\\/g, "/").replace(/\.json$/, "");
    result.set("villagerretaliation:quest/" + local, entry);
  }
  return new Map(sortedEntries(result));
}

function loadMessages(files) {
  const result = new Map();
  for (const entry of files) {
    for (const message of entry.data?.messages ?? []) {
      const key = message.key ?? message.id;
      if (!key) throw new Error(relative(entry.file) + " has message without stable key");
      if (result.has(key)) throw new Error("Duplicate English message owner " + key);
      const payload = { ...message };
      delete payload.id;
      delete payload.key;
      result.set(key, canonical(payload));
    }
  }
  return new Map(sortedEntries(result));
}

async function loadJsonFiles(directory) {
  const files = [];
  for (const dirent of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, dirent.name);
    if (dirent.isDirectory()) files.push(...await loadJsonFiles(file));
    else if (dirent.isFile() && dirent.name.endsWith(".json")) {
      files.push({ file, data: JSON.parse(await readFile(file, "utf8")) });
    }
  }
  return files.sort((a, b) => a.file.localeCompare(b.file));
}

function questPrefix(entry) {
  return "quest." + (entry.data.metadata?.questline ?? "")
    + "." + path.basename(entry.file, ".json");
}
function ownerPrefix(owner) {
  const entry = quests.get(owner);
  return entry ? questPrefix(entry) : "villagerretaliation.shared";
}
function mapData(entries) {
  return Object.fromEntries(sortedEntries(entries)
    .map(([id, entry]) => [id, canonical(entry.data)]));
}
function variantsOf(payload) {
  const value = payload?.lines ?? payload?.variants ?? payload;
  if (Array.isArray(value)) {
    return value.map(entry => typeof entry === "string"
      ? entry : entry.text ?? entry.line ?? "");
  }
  return typeof value === "string" ? [value] : [];
}
function hasText(value) {
  if (typeof value === "string") return value.length > 0;
  if (Array.isArray(value)) {
    return value.some(entry => typeof entry === "string"
      || entry?.text || entry?.line || entry?.key);
  }
  return value && typeof value === "object"
    && (value.key || Object.values(value).some(hasText));
}
function containsText(value) {
  let found = false;
  walk(value, object => {
    if (Object.keys(object).some(key => textFields.has(key))) found = true;
  });
  return found;
}
function collectOperations(value, prefix, target) {
  walk(value, object => {
    if (object.operation_id) target.push(prefix + "/" + object.operation_id);
    for (const action of object.actions ?? []) {
      if (action?.id) target.push(prefix + "/" + action.id);
    }
  });
}
function claim(value, owner, claims) {
  if (typeof value !== "string" || !value.includes(":") || !owner) return;
  if (!claims.has(value)) claims.set(value, new Set());
  claims.get(value).add(owner);
}
function singleOwner(values) {
  return values?.size === 1 ? [...values][0] : "_shared";
}
function singular(value) {
  return value.endsWith("ies") ? value.slice(0, -3) + "y"
    : value.endsWith("s") ? value.slice(0, -1) : value;
}
function stablePath(value) {
  return String(value).replace(/^[^:]+:/, "")
    .replace(/[^a-zA-Z0-9_.-]+/g, ".");
}
function fingerprint(value) {
  return createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex");
}
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(Object.keys(value).sort()
    .map(key => [key, canonical(value[key])]));
}
function walk(value, visitor) {
  if (!value || typeof value !== "object") return;
  if (!Array.isArray(value)) visitor(value);
  for (const child of Array.isArray(value) ? value : Object.values(value)) {
    walk(child, visitor);
  }
}
function sorted(values) {
  return [...values].sort((a, b) => String(a).localeCompare(String(b)));
}
function sortedEntries(map) {
  return [...map.entries()].sort(([a], [b]) =>
    String(a).localeCompare(String(b)));
}
function relative(file) {
  return path.relative(root, file).replace(/\\/g, "/");
}
function signed(value) {
  return BigInt.asIntN(64, value);
}
function unsigned(value) {
  return BigInt.asUintN(64, value);
}

const generated = {
  "compatibility-manifest.json": manifest(),
  "compiled-equivalence.json": equivalence(),
  "behavioral-baselines.json": behavioral(),
  "save-fixtures.json": saveFixtures()
};

if (process.argv.includes("--write")) {
  await mkdir(fixtureRoot, { recursive: true });
  await Promise.all(Object.entries(generated).map(([name, value]) =>
    writeFile(path.join(fixtureRoot, name), JSON.stringify(value, null, 2) + "\n", "utf8")));
  console.log("Wrote beta.13 quest compatibility baseline.");
} else {
  const failures = [];
  for (const [name, actual] of Object.entries(generated)) {
    try {
      const expected = JSON.parse(await readFile(path.join(fixtureRoot, name), "utf8"));
      if (JSON.stringify(expected) !== JSON.stringify(actual)) failures.push(name + " differs");
    } catch (error) {
      failures.push(name + ": " + error.message);
    }
  }
  if (failures.length) throw new Error("Quest bundle baseline verification failed:\n- " + failures.join("\n- "));
  console.log("Quest bundle baseline passed: " + quests.size + " quests, " + scenes.size + " scenes, "
    + encounters.size + " encounters, " + rewards.size + " rewards, " + messages.size
    + " authored messages, " + localeKeys.size + " effective English keys.");
}
