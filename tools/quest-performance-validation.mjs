export function validateQuestPerformance(quest) {
  const performance = quest?.performance;
  if (!performance || typeof performance !== "object") return [];
  const errors = [];
  const measurements = performance.measurements ?? {};
  const stages = new Set();
  const objectives = new Set();
  const optional = new Set();
  const actors = new Set();
  for (const stage of Array.isArray(quest.stages) ? quest.stages : []) {
    if (!stage || typeof stage !== "object") continue;
    stages.add(stage.id);
    for (const objective of Array.isArray(stage.objectives) ? stage.objectives : []) {
      if (!objective || typeof objective !== "object") continue;
      const id = stage.id + "." + objective.id;
      objectives.add(id);
      if (objective.optional) optional.add(id);
    }
    for (const follower of Array.isArray(stage.followers) ? stage.followers : []) if (follower?.actor?.alias) actors.add(follower.actor.alias);
  }
  function collectFollowers(value) {
    if (!value || typeof value !== "object") return;
    if (value.command && typeof value.actor?.alias === "string") actors.add(value.actor.alias);
    for (const child of Object.values(value)) collectFollowers(child);
  }
  collectFollowers(quest);
  for (const [id, measurement] of Object.entries(measurements)) {
    if (!measurement || typeof measurement !== "object") continue;
    const path = "/performance/measurements/" + id;
    const health = ["final_health_percent", "lowest_health_percent"].includes(measurement.metric);
    if (measurement.stage && measurement.objective) errors.push(path + ": select one scope");
    if (measurement.stage && !stages.has(measurement.stage)) errors.push(path + ": unknown stage");
    if (measurement.objective && !objectives.has(measurement.objective)) errors.push(path + ": unknown objective");
    if (measurement.metric === "optional_objectives" && measurement.objective && !optional.has(measurement.objective))
      errors.push(path + ": objective must be optional");
    if (health !== Boolean(measurement.actor)) errors.push(path + ": only health measurements require an actor");
    if (health && !actors.has(measurement.actor)) errors.push(path + ": unknown follower");
    if (measurement.metric !== "kills" && (measurement.entity_types?.length || measurement.entity_tags?.length))
      errors.push(path + ": only kills accept entity filters");
    if (measurement.metric !== "elapsed_ticks" && (measurement.include_inactive || measurement.include_offline))
      errors.push(path + ": only elapsed time accepts clock options");
  }
  const tiers = new Set();
  for (const [index, tier] of (Array.isArray(performance.tiers) ? performance.tiers : []).entries()) {
    if (!tier || typeof tier !== "object") continue;
    const path = "/performance/tiers/" + index;
    if (tiers.has(tier.id)) errors.push(path + ": duplicate tier");
    tiers.add(tier.id);
    for (const [id, bounds] of Object.entries(tier.requirements ?? {})) {
      if (!bounds || typeof bounds !== "object") continue;
      if (!Object.hasOwn(measurements, id)) errors.push(path + ": unknown measurement " + id);
      if (bounds.min !== undefined && bounds.max !== undefined && bounds.min > bounds.max) errors.push(path + ": minimum exceeds maximum");
      if (["final_health_percent", "lowest_health_percent"].includes(measurements[id]?.metric) && (bounds.min > 100 || bounds.max > 100))
        errors.push(path + ": health percentage exceeds 100");
    }
  }
  return errors;
}
