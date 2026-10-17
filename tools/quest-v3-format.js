(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.QuestV3Format = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";
  const V2 = "villagerretaliation:quest/v2";
  const V3 = "villagerretaliation:quest/v3";
  const clone = value => JSON.parse(JSON.stringify(value));
  const equal = (a, b) => JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));
  function canonical(value) {
    if (Array.isArray(value)) return value.map(canonical);
    if (value && typeof value === "object") return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
    return value;
  }
  function move(from, key, to, target = key) {
    if (!(key in from)) return;
    if (target in to && !equal(from[key], to[target])) throw new Error(`Conflicting fields: ${key} and ${target}`);
    to[target] = from[key];
    delete from[key];
  }
  function display(owner, metadata, prefix) {
    const result = owner.display || {};
    for (const [key, value] of Object.entries(owner.ui || {})) {
      if (key in result && !equal(result[key], value)) throw new Error(`Conflicting display.${key}`);
      result[key] = value;
    }
    delete owner.ui;
    if ("title" in result) move(result, "title", result, "tracker_title");
    if ("title_key" in result) move(result, "title_key", result, "tracker_title_key");
    for (const source of [owner, metadata].filter(Boolean)) {
      for (const key of ["title", "title_key", "description", "description_key", "show_locked_adventure_hint"]) move(source, key, result);
    }
    for (const key of Object.keys(result)) {
      if (!key.endsWith("_key")) continue;
      const field = key.slice(0, -4);
      const reference = { key: result[key] };
      const currentKey = result[field]?.key;
      const expanded = currentKey?.startsWith("#") ? prefix + "." + currentKey.slice(1) : currentKey;
      if (field in result && !equal(result[field], reference) && expanded !== reference.key) throw new Error(`Conflicting display.${field} and ${key}`);
      if (!(field in result)) result[field] = reference;
      delete result[key];
    }
    if (Object.keys(result).length) owner.display = result;
  }
  function transition(owner, kind) {
    let target = owner.transition ? clone(owner.transition) : {};
    if ("next" in owner) {
      const next = typeof owner.next === "string" ? { stage: owner.next } : owner.next;
      for (const [key, value] of Object.entries(next)) {
        if (key in target && !equal(target[key], value)) throw new Error(`Conflicting transition.${key}`);
        target[key] = value;
      }
      delete owner.next;
    }
    if (kind === "response") for (const key of ["stage", "scene", "response", "complete", "abandon", "fail"]) move(owner, key, target);
    if (Object.keys(target).length) owner.transition = target;
  }
  function dialogue(owner, kind) {
    if (kind === "scene" || kind === "response" || kind === "event") transition(owner, kind);
    if (kind === "event") {
      move(owner, "trigger", owner, "event");
      move(owner, "type", owner, "event");
    }
    for (const response of owner.responses || []) dialogue(response, "response");
    for (const scene of owner.scenes || []) dialogue(scene, "scene");
    for (const slot of Object.values(owner.dialogue || {})) if (slot && typeof slot === "object") dialogue(slot, "slot");
    for (const event of owner.events || []) dialogue(event, "event");
  }
  function qualifyRewards(owner) {
    if (typeof owner.rewards?.memory_event === "string" && !owner.rewards.memory_event.includes(":")) {
      owner.rewards.memory_event = "villagerretaliation:" + owner.rewards.memory_event.toLowerCase();
    }
  }
  function migrate(source) {
    if (source.schema !== V2) throw new Error(`Expected ${V2}`);
    const quest = clone(source);
    quest.schema = V3;
    const metadata = quest.metadata || {};
    move(metadata, "campaign", quest, "story_arc");
    for (const key of ["revision", "migration"]) move(metadata, key, quest);
    if (quest.migration) move(quest.migration, "on_active_change", quest.migration, "active_policy");
    display(quest, metadata, quest.localization_prefix);
    if (quest.provider?.capabilities) {
      quest.provider.required_capabilities = [...new Set([...(quest.provider.required_capabilities || []), ...quest.provider.capabilities])];
      delete quest.provider.capabilities;
    }
    qualifyRewards(quest);
    dialogue(quest, "quest");
    if (Array.isArray(quest.lifecycle?.dialogue)) for (const scene of quest.lifecycle.dialogue) dialogue(scene, "scene");
    for (const stage of quest.stages || []) {
      qualifyRewards(stage);
      display(stage, null, quest.localization_prefix);
      move(stage, "entry_actions", stage, "on_enter");
      move(stage, "exit_actions", stage, "on_exit");
      if ("completion_mode" in stage || "completion_count" in stage) {
        stage.completion ||= {};
        move(stage, "completion_mode", stage.completion, "mode");
        move(stage, "completion_count", stage.completion, "count");
      }
      transition(stage, "stage");
      for (const objective of stage.objectives || []) display(objective, null, quest.localization_prefix);
      dialogue(stage, "stage");
    }
    return quest;
  }
  function runtime(source) {
    if (source.schema !== V3) throw new Error(`Expected ${V3}`);
    const quest = clone(source);
    delete quest.world_campaign;
    const metadata = quest.metadata ||= {};
    move(quest, "story_arc", metadata, "campaign");
    for (const key of ["revision", "migration"]) move(quest, key, metadata);
    function lower(owner, destination) {
      if (!owner.display) return;
      owner.ui = owner.display;
      delete owner.display;
      if (destination) for (const key of ["title", "title_key", "description", "description_key", "show_locked_adventure_hint"]) move(owner.ui, key, destination);
    }
    lower(quest, metadata);
    if (quest.ui) {
      move(quest.ui, "tracker_title", quest.ui, "title");
      move(quest.ui, "tracker_title_key", quest.ui, "title_key");
    }
    for (const stage of quest.stages || []) {
      lower(stage, stage);
      if (stage.ui) {
        move(stage.ui, "tracker_title", stage.ui, "title");
        move(stage.ui, "tracker_title_key", stage.ui, "title_key");
      }
      move(stage, "transition", stage, "next");
      if (stage.next && Object.keys(stage.next).length === 1 && typeof stage.next.stage === "string") stage.next = stage.next.stage;
      for (const objective of stage.objectives || []) {
        lower(objective);
        if (objective.ui) {
          move(objective.ui, "tracker_title", objective.ui, "title");
          move(objective.ui, "tracker_title_key", objective.ui, "title_key");
        }
      }
    }
    return quest;
  }
  return { V2, V3, migrate, runtime };
});
