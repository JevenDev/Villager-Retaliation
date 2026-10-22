export function validateQuestSiteAccess(quest) {
  const errors = [];
  const site = quest?.target?.site;
  const access = site?.access;
  const routes = new Set();
  const approaches = new Set();
  if (access) {
    for (const [index, route] of (Array.isArray(access.routes) ? access.routes : []).entries()) {
      if (routes.has(route.id)) errors.push(`/target/site/access/routes/${index}: duplicate route ${route.id}`);
      routes.add(route.id);
    }
    for (const [index, approach] of (Array.isArray(access.approaches) ? access.approaches : []).entries()) {
      const pointer = `/target/site/access/approaches/${index}`;
      if (approaches.has(approach.id)) errors.push(`${pointer}: duplicate approach ${approach.id}`);
      approaches.add(approach.id);
      if (approach.route && !routes.has(approach.route)) errors.push(`${pointer}/route: unknown route ${approach.route}`);
      if (!approach.route && approach.guards_defeated !== true && !approach.conditions?.length)
        errors.push(`${pointer}: an approach requires a route, defeated guards, or conditions`);
    }
    if (Array.isArray(access.guard_positions) && access.guard_positions.length !== (site.guards ?? 2))
      errors.push("/target/site/access/guard_positions: provide exactly one position per guard");
  }
  function walk(value, pointer) {
    if (!value || typeof value !== "object") return;
    if (value.type === "quest_site" && value.quest === quest.id) {
      if (!site) errors.push(`${pointer}: this quest has no site`);
      if (value.route && !routes.has(value.route)) errors.push(`${pointer}/route: unknown route ${value.route}`);
      if (value.approach && !approaches.has(value.approach)) errors.push(`${pointer}/approach: unknown approach ${value.approach}`);
    }
    for (const [key, child] of Object.entries(value)) walk(child, pointer + "/" + key);
  }
  walk(quest, "");
  return errors;
}
