const DATA = window.VR_WIKI_DATA || { quests: [], reputation: [], gifts: {}, pacification: [], skillTrades: [], advancements: [] };

const PAGES = [
  {
    id: "home",
    title: "Overview",
    group: "Start Here",
    icon: "home",
    description: "What Villager Retaliation changes and how to approach villages without getting chased into a ravine.",
    render: renderHome
  },
  {
    id: "quests",
    title: "Quest Walkthroughs",
    group: "Start Here",
    icon: "map",
    description: "Every built-in quest, offer gate, step, turn-in item, and potential reward.",
    render: renderQuests
  },
  {
    id: "reputation",
    title: "Reputation",
    group: "Core Systems",
    icon: "shield",
    description: "Trust tiers, penalties, pacification, and what low reputation changes for players.",
    render: renderReputation
  },
  {
    id: "dialogue",
    title: "Dialogue And Interaction",
    group: "Core Systems",
    icon: "message-square-text",
    description: "Talking, gifts, recruitment, relationships, stories, and the quest journal.",
    render: renderDialogue
  },
  {
    id: "combat",
    title: "Retaliation And Combat",
    group: "Core Systems",
    icon: "swords",
    description: "How villagers fight, flee, rally, and respond to hostile mobs.",
    render: renderCombat
  },
  {
    id: "player-raids",
    title: "Player Raids",
    group: "Core Systems",
    icon: "flag",
    description: "How to declare a village siege, who joins each side, and how the village defends itself.",
    render: renderPlayerRaids
  },
  {
    id: "gifts",
    title: "Gifts And Keepsakes",
    group: "Player Guides",
    icon: "gift",
    description: "Gift reactions, profession preferences, and high-reputation reward rolls.",
    render: renderGifts
  },
  {
    id: "skill-trades",
    title: "Skill Trades",
    group: "Player Guides",
    icon: "badge-percent",
    description: "Profession skill trades, Special Orders, ranks, and reputation gates.",
    render: renderSkillTrades
  },
  {
    id: "watched-containers",
    title: "Watched Containers",
    group: "Player Guides",
    icon: "package-open",
    description: "What happens when villagers see you opening, breaking, or stealing from protected chests.",
    render: renderContainers
  },
  {
    id: "advancements",
    title: "Advancements",
    group: "Reference",
    icon: "trophy",
    description: "The reputation advancement tab, visible and hidden challenges.",
    render: renderAdvancements
  },
  {
    id: "settings",
    title: "Controls And Settings",
    group: "Reference",
    icon: "settings",
    description: "Quest controls, interaction shortcuts, and ways to change local settings.",
    render: renderSettings
  }
];

const els = {
  nav: document.querySelector("#wiki-nav"),
  content: document.querySelector("#wiki-content"),
  toc: document.querySelector("#page-toc"),
  crumb: document.querySelector("#page-crumb"),
  search: document.querySelector("#wiki-search"),
  palette: document.querySelector("#search-palette"),
  paletteSearch: document.querySelector("#palette-search"),
  paletteResults: document.querySelector("#palette-results"),
  menuToggle: document.querySelector("#menu-toggle"),
  sidebar: document.querySelector(".sidebar")
};

let searchQuery = "";
let paletteQuery = "";
let activeRouteKey = "";
let scrollRestoreFrame = 0;

const NAV_GROUP_STATE_KEY = "villager-retaliation-wiki-nav-groups-v1";
const ROUTE_SCROLL_STATE_KEY = "villager-retaliation-wiki-scroll-v1";

if ("scrollRestoration" in history) history.scrollRestoration = "manual";

function readLocalState(key) {
  try {
    const value = JSON.parse(localStorage.getItem(key) || "{}");
    return value && typeof value === "object" && !Array.isArray(value) ? value : {};
  } catch {
    return {};
  }
}

function writeLocalState(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // The wiki still works when storage is unavailable or full.
  }
}

function routeKey(route) {
  return `${route.type}:${route.id || "home"}`;
}

function saveRouteScroll(key = activeRouteKey) {
  if (!key) return;
  const positions = readLocalState(ROUTE_SCROLL_STATE_KEY);
  positions[key] = Math.max(0, Math.round(window.scrollY));
  writeLocalState(ROUTE_SCROLL_STATE_KEY, positions);
}

function restoreRouteScroll(route) {
  const key = routeKey(route);
  const positions = readLocalState(ROUTE_SCROLL_STATE_KEY);
  const savedPosition = Number(positions[key]);
  activeRouteKey = key;
  if (scrollRestoreFrame) window.cancelAnimationFrame(scrollRestoreFrame);
  scrollRestoreFrame = window.requestAnimationFrame(() => {
    if (activeRouteKey !== key) return;
    window.scrollTo({ top: Number.isFinite(savedPosition) ? savedPosition : 0, left: 0, behavior: "auto" });
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function slugFor(text) {
  return String(text).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "section";
}

function compactId(id) {
  return String(id || "").split(":").pop().replaceAll("_", " ");
}

function titleCase(text) {
  return compactId(text).replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatList(items, empty = "Any") {
  return (items || []).length ? items.join(", ") : empty;
}

function plural(count, singular, pluralWord = `${singular}s`) {
  return count === 1 ? `${count} ${singular}` : `${count} ${pluralWord}`;
}

function capitalizeStatValue(value) {
  const text = String(value ?? "");
  const withLeadingNumber = text.match(/^(\d+\s+)([a-z])/);
  if (withLeadingNumber) {
    const [, prefix, firstChar] = withLeadingNumber;
    return text.replace(prefix + firstChar, `${prefix}${firstChar.toUpperCase()}`);
  }
  return text.length > 0 ? `${text.charAt(0).toUpperCase()}${text.slice(1)}` : text;
}

function countDialogueLines() {
  if (typeof DATA.stats?.dialogueLinesEstimate === "number") {
    return DATA.stats.dialogueLinesEstimate;
  }
  return DATA.quests.reduce((total, quest) => {
    const dialogue = quest.dialogue || {};
    return total + Object.values(dialogue).reduce((questTotal, value) => {
      if (Array.isArray(value)) return questTotal + value.length;
      if (typeof value === "string") return questTotal + (value.trim() ? 1 : 0);
      return questTotal;
    }, 0);
  }, 0);
}

function pageUrl(id) {
  return `#/page/${id}`;
}

function questUrl(slug) {
  return `#/quest/${slug}`;
}

function questlineUrl(id) {
  return `#/questline/${encodeURIComponent(String(id || ""))}`;
}

function advancementUrl(id) {
  return `#/advancement/${encodeURIComponent(String(id || ""))}`;
}

function normalizeAdvancementKey(value) {
  return String(value || "")
    .toLowerCase()
    .trim()
    .replace(/^.*:/, "")
    .replace(/[\s-]+/g, "_")
    .replace(/[^a-z0-9_]/g, "");
}

function findAdvancement(value) {
  const key = normalizeAdvancementKey(value);
  if (!key) return null;
  return (Array.isArray(DATA.advancements) ? DATA.advancements : []).find((advancement) => {
    const idKey = normalizeAdvancementKey(advancement.id);
    const titleKey = normalizeAdvancementKey(advancement.title);
    return idKey === key || titleKey === key;
  }) || null;
}

function advancementRowId(advancementId) {
  return `advancement-${slugFor(advancementId)}`;
}

function focusAdvancementRow(advancementId) {
  if (!advancementId) return;
  const row = document.getElementById(advancementRowId(advancementId));
  if (!row) return;
  row.scrollIntoView({ behavior: "smooth", block: "center" });
}

function currentRoute() {
  const hash = location.hash.replace(/^#\/?/, "");
  if (!hash) return { type: "page", id: "home" };
  const [type, ...rest] = hash.split("/");
  if (type === "quest") return { type: "quest", id: rest.join("/") };
  if (type === "questline") return { type: "questline", id: decodeURIComponent(rest.join("/")) };
  if (type === "advancement") return { type: "advancement", id: decodeURIComponent(rest.join("/")) };
  if (type === "search") return { type: "search", id: "search" };
  if (type === "page") return { type: "page", id: rest[0] || "home" };
  return { type: "page", id: type || "home" };
}

function groupBy(items, key) {
  return items.reduce((groups, item) => {
    const group = item[key] || "Other";
    if (!groups[group]) groups[group] = [];
    groups[group].push(item);
    return groups;
  }, {});
}

function icon(name, className = "inline-icon") {
  return `<i data-lucide="${escapeHtml(name)}" class="${className}" aria-hidden="true"></i>`;
}

function renderIcons() {
  if (!window.lucide) return;
  window.lucide.createIcons({
    attrs: {
      "stroke-width": 1.8
    }
  });
}

function questIcon(quest) {
  const icons = {
    cartographers_atlas: "map",
    exploration: "map",
    dangerous_commissions: "skull",
    lost_civilization: "landmark",
    old_roads: "signpost",
    village_defense: "shield-alert",
    village_supply: "wheat"
  };
  return icons[quest.questline] || icons[quest.group] || "scroll-text";
}

function questlineSummaries() {
  const byId = new Map();
  for (const quest of DATA.quests) {
    if (!quest.questline) continue;
    if (!byId.has(quest.questline)) {
      byId.set(quest.questline, {
        id: quest.questline,
        label: quest.questlineLabel || titleCase(quest.questline),
        quests: []
      });
    }
    byId.get(quest.questline).quests.push(quest);
  }
  return [...byId.values()].map((line) => ({
    ...line,
    quests: line.quests
      .slice()
      .sort((a, b) => (a.questlineOrder ?? 0) - (b.questlineOrder ?? 0) || a.title.localeCompare(b.title))
  })).sort((a, b) => a.label.localeCompare(b.label));
}

function questRewardPreview(quest) {
  const rewards = quest?.rewards || {};
  const parts = [];
  if (typeof rewards.reputation === "number") parts.push(`${rewards.reputation} reputation`);
  if (typeof rewards.experience === "number") parts.push(`${rewards.experience} XP`);
  if (typeof rewards.gossipReputation === "number" && rewards.gossipReputation !== 0) {
    parts.push(`${rewards.gossipReputation} gossip`);
  }
  const loot = Array.isArray(rewards.loot) ? rewards.loot : [];
  if (loot.length > 0) {
    const first = loot[0];
    const label = first?.item ? `${first.count || ""} ${first.item}`.trim() : "loot roll";
    const suffix = loot.length > 1 ? ` +${loot.length - 1} more` : "";
    parts.push(`${label}${suffix}`);
  }
  return parts.length ? `Rewards: ${parts.join(" • ")}` : "Rewards: No listed rewards";
}

function resultIcon(type) {
  if (type === "Advancement") return "trophy";
  return type === "Quest" ? "scroll-text" : "file-text";
}

function renderNav() {
  const route = currentRoute();
  const navGroupState = readLocalState(NAV_GROUP_STATE_KEY);
  const currentQuest = route.type === "quest"
    ? DATA.quests.find((item) => item.slug === route.id)
    : null;
  const navPages = PAGES.filter((page) => page.id !== "quests");
  const byGroup = groupBy(navPages, "group");
  const groups = Object.entries(byGroup).map(([group, pages]) => {
    const groupIsActive = pages.some((page) =>
      (route.type === "page" && route.id === page.id)
      || (route.type === "advancement" && page.id === "advancements")
    );
    const groupIsOpen = groupIsActive || navGroupState[group] === true;
    return `
    <details class="nav-group nav-page-group" data-nav-group="${escapeHtml(group)}" ${groupIsOpen ? "open" : ""}>
      <summary class="nav-heading">${icon("chevron-right", "disclosure-icon")}<span>${escapeHtml(group)}</span></summary>
      <div class="nav-page-links">
        ${pages.map((page) => `
          <a class="nav-link ${(route.type === "page" && route.id === page.id) || (route.type === "advancement" && page.id === "advancements") ? "is-active" : ""}" href="${pageUrl(page.id)}">
            ${icon(page.icon)}
            <span>${escapeHtml(page.title)}</span>
          </a>
        `).join("")}
      </div>
    </details>`;
  }).join("");

  const questLinks = DATA.quests.map((quest) => `
    <a class="nav-link nav-link-small ${route.type === "quest" && route.id === quest.slug ? "is-active" : ""}" href="${questUrl(quest.slug)}">
      ${icon(questIcon(quest))}
      <span>${escapeHtml(quest.title)}</span>
    </a>
  `).join("");
  const questlineLinks = questlineSummaries().map((line) => `
    <a class="nav-link nav-link-small ${route.type === "questline" && route.id === line.id ? "is-active" : ""}" href="${questlineUrl(line.id)}">
      ${icon("map")}
      <span>${escapeHtml(line.label)}</span>
    </a>
  `).join("");
  const questsAreActive = route.type === "quest"
    || route.type === "questline"
    || (route.type === "page" && route.id === "quests");
  const questsAreOpen = questsAreActive || navGroupState.Quests === true;

  els.nav.innerHTML = `${groups}
    <details class="nav-group nav-page-group" data-nav-group="Quests" ${questsAreOpen ? "open" : ""}>
      <summary class="nav-heading">${icon("chevron-right", "disclosure-icon")}<span>Quests</span></summary>
      <div class="nav-page-links">
        <a class="nav-link ${route.type === "page" && route.id === "quests" ? "is-active" : ""}" href="${pageUrl("quests")}">
          ${icon("list")}
          <span>All quests</span>
        </a>
        ${questlineLinks ? `
          <details class="nav-disclosure" ${route.type === "questline" || currentQuest?.questline ? "open" : ""}>
            <summary>${icon("chevron-right", "disclosure-icon")}<span>Questlines</span></summary>
            <div class="nav-disclosure-list">${questlineLinks}</div>
          </details>
        ` : ""}
        <details class="nav-disclosure" ${route.type === "quest" ? "open" : ""}>
          <summary>${icon("chevron-right", "disclosure-icon")}<span>Individual quests</span></summary>
          <div class="nav-disclosure-list">${questLinks}</div>
        </details>
      </div>
    </details>`;

  els.nav.querySelectorAll(".nav-page-group[data-nav-group]").forEach((group) => {
    group.addEventListener("toggle", () => {
      const state = readLocalState(NAV_GROUP_STATE_KEY);
      state[group.dataset.navGroup] = group.open;
      writeLocalState(NAV_GROUP_STATE_KEY, state);
    });
  });
}

function render() {
  const route = currentRoute();
  try {
    renderNav();
    if (route.type === "quest") {
      const quest = DATA.quests.find((item) => item.slug === route.id) || DATA.quests[0];
      if (quest) renderDocument(quest.title, quest.description, renderQuestDetail(quest), {
        icon: questIcon(quest),
        parent: "Quest Walkthroughs",
        section: quest.questlineLabel || quest.groupLabel
      });
      return;
    }
    if (route.type === "questline") {
      const summary = questlineSummaries().find((line) => line.id === route.id) || questlineSummaries()[0];
      if (summary) renderDocument(summary.label, `${summary.quests.length} connected quests in this progression.`, renderQuestlineDetail(summary), {
        icon: "map",
        parent: "Quest Walkthroughs",
        section: "Questlines"
      });
      return;
    }
    if (route.type === "advancement") {
      const advancement = findAdvancement(route.id);
      renderDocument("Advancements", advancement
        ? `Focused on ${advancement.title}.`
        : "The reputation advancement tab, visible and hidden challenges.", renderAdvancements({
        focusedAdvancementId: advancement?.id || ""
      }), {
        icon: "trophy",
        parent: "Reference"
      });
      if (advancement) window.requestAnimationFrame(() => focusAdvancementRow(advancement.id));
      return;
    }
    if (route.type === "search") {
      renderSearch();
      return;
    }
    const page = PAGES.find((item) => item.id === route.id) || PAGES[0];
    renderDocument(page.title, page.description, page.render(), {
      icon: page.icon,
      parent: page.group,
      heroImage: page.id === "home"
        ? "https://cdn.modrinth.com/data/cached_images/16269e99f4ef7ac15b6d24f3b523e5fa5778d5f5.png"
        : null
    });
  } finally {
    restoreRouteScroll(route);
  }
}
function renderDocument(title, description, body, meta = {}) {
  const parent = meta.section ? `${meta.parent} / ${meta.section}` : meta.parent;
  const heroImage = meta.heroImage
    ? `<img src="${escapeHtml(meta.heroImage)}" alt="Villager Retaliation overview" style="display:block;width:100%;height:auto;border-radius:10px;margin:0 0 16px;" />`
    : "";
  els.crumb.innerHTML = `
    ${meta.icon ? icon(meta.icon) : ""}
    <span>${escapeHtml(parent || "Wiki")}</span>
    <strong>${escapeHtml(title)}</strong>
  `;
  els.content.innerHTML = `
    ${heroImage}
    <header class="doc-header">
      <div class="doc-title-row">
        ${meta.icon ? icon(meta.icon, "title-icon") : ""}
        <h1>${escapeHtml(title)}</h1>
      </div>
      <p>${escapeHtml(description)}</p>
    </header>
    ${body}
  `;
  renderToc();
  renderIcons();
  els.content.focus({ preventScroll: true });
}

function renderToc() {
  const headings = [...els.content.querySelectorAll("h2[id], h3[id]")];
  if (!headings.length) {
    els.toc.innerHTML = "";
    return;
  }
  const routeHash = location.hash || "#/home";
  els.toc.innerHTML = `
    <div class="toc-title">On this page</div>
    ${headings.map((heading) => `<a class="toc-link ${heading.tagName === "H3" ? "is-sub" : ""}" href="${routeHash}" data-toc-target="${escapeHtml(heading.id)}">${escapeHtml(heading.textContent)}</a>`).join("")}
  `;
}

function section(title, body, level = 2) {
  const id = slugFor(title);
  return `<section class="doc-section"><h${level} id="${id}">${escapeHtml(title)}</h${level}>${body}</section>`;
}

function countedSection(title, count, body) {
  const id = slugFor(title);
  return `
    <section class="doc-section">
      <div class="section-heading-row">
        <h2 id="${id}">${escapeHtml(title)}</h2>
        <span class="section-count">${escapeHtml(count)}</span>
      </div>
      ${body}
    </section>
  `;
}

function statGrid(stats) {
  return `<div class="stat-grid">${stats.map((stat) => `
    <div class="stat-card">
      ${icon(stat.icon || "sparkles")}
      <div class="card-copy">
        <strong>${escapeHtml(capitalizeStatValue(stat.value))}</strong>
        ${stat.label ? `<span class="stat-label">${escapeHtml(stat.label)}</span>` : ""}
      </div>
    </div>
  `).join("")}</div>`;
}

function simpleList(items) {
  return `<ul class="plain-list">${items.map((item) => `<li>${item}</li>`).join("")}</ul>`;
}

function pillList(items) {
  return `<div class="pill-row">${(items || []).map((item) => `<span class="pill">${escapeHtml(item)}</span>`).join("")}</div>`;
}

function renderHome() {
  const questlines = new Set(DATA.quests.map((quest) => quest.questline).filter(Boolean)).size;
  const questGroups = new Set(DATA.quests.map((quest) => quest.group).filter(Boolean)).size;
  const reputationTiers = Array.isArray(DATA.reputation) ? DATA.reputation.length : 0;
  return `
    ${statGrid([
      { value: plural(DATA.quests.length, "quest"), label: "Built-in walkthroughs", icon: "scroll-text" },
      { value: plural(questlines, "questline"), label: "Questlines", icon: "map" },
      { value: plural(questGroups, "quest group"), label: "Browsable quest tags", icon: "tags" },
      { value: plural(reputationTiers, "reputation tier"), label: "Relationship levels", icon: "shield" },
      { value: plural(DATA.advancements.length, "advancement"), label: "Reputation tab entries", icon: "trophy" },
      { value: "20K+ Dialogue lines", label: "Estimated total", icon: "message-square-text" },
      { value: "11.9 Quadrillion", label: "Villager DNA combinations", icon: "fingerprint" },
      { value: "18 Villager skills", label: "Core progression stats", icon: "brain-circuit" },
      { value: plural(DATA.skillTrades.reduce((sum, group) => sum + group.count, 0), "skill trade"), label: "Skill-generated trade entries", icon: "badge-percent" }
    ])}
    ${section("What The Mod Changes", `
      <p>Villager Retaliation makes villagers remember how each player treats them. Adults can defend themselves, nearby witnesses can react to crimes, and reputation changes how dialogue, trade access, gifts, pacification, and some combat behavior feel.</p>
      <br />
      ${simpleList([
        "Attack a villager and that villager can fight back.",
        "Kill or harm villagers in public and witnesses may rally or remember it.",
        "Build trust through trading, gifts, defending villages, quest completion, and good history.",
        "Talk to villagers through a reputation-aware interaction screen with stories, quests, gifts, recruitment, and relationships.",
        "Follow data-driven quests with tracker text, quest item highlighting, HUD notices, and authored dialogue scenes."
      ].map(escapeHtml))}
    `)}
    ${section("Best First Steps", `
      <ol class="step-list icon-step-list">
        <li>${icon("handshake")}<strong>Start harmless.</strong><span>Trade, talk, and avoid watched containers until you know who trusts you.</span></li>
        <li>${icon("gift")}<strong>Use gifts carefully.</strong><span>Emeralds, diamonds, food, tools, books, and profession items are usually safer than hazards or rotten loot.</span></li>
        <li>${icon("book-open")}<strong>Open the Quest Journal.</strong><span>Default keybinds are <kbd>J</kbd> for the journal and <kbd>K</kbd> for the tracker.</span></li>
        <li>${icon("list-checks")}<strong>Read quest gates.</strong><span>Many quests require a villager profession, trade level, and hidden skill minimum before they appear.</span></li>
      </ol>
    `)}
    ${section("Main Pages", `
      <div class="card-grid two">
        ${PAGES.filter((page) => page.id !== "home").map((page) => `
          <a class="feature-card" href="${pageUrl(page.id)}">
            ${icon(page.icon)}
            <div class="card-copy">
              <strong>${escapeHtml(page.title)}</strong>
              <span>${escapeHtml(page.description)}</span>
            </div>
          </a>
        `).join("")}
      </div>
    `)}
  `;
}

function questCard(quest) {
  const objectives = Array.isArray(quest.objectives) ? quest.objectives : [];
  const requirements = quest.requirements || {};
  const rewards = quest.rewards || {};
  const goal = quest.target?.structure || objectives[0] || quest.description || "No objective summary listed";
  const minLevel = requirements.minLevel || "Any";
  const reputationReward = rewards.reputation ?? "0";
  const experienceReward = rewards.experience ?? "0";
  const rewardPreview = questRewardPreview(quest);
  return `
    <a class="quest-card" href="${questUrl(quest.slug)}" data-questline="${escapeHtml(quest.questline)}" data-group="${escapeHtml(quest.group)}">
      ${icon(questIcon(quest))}
      <span class="card-kicker">${escapeHtml(quest.questlineLabel || quest.groupLabel)}</span>
      <strong>${escapeHtml(quest.title)}</strong>
      <span>${escapeHtml(quest.description)}</span>
      <p>${escapeHtml(goal)}</p>
      <div class="mini-meta">
        <span>${escapeHtml(minLevel)} villager</span>
        <span>${escapeHtml(reputationReward)} reputation</span>
        <span>${escapeHtml(experienceReward)} XP</span>
      </div>
      <p class="quest-reward-preview">${escapeHtml(rewardPreview)}</p>
    </a>
  `;
}

function renderQuests() {
  const byGroup = groupBy(DATA.quests, "groupLabel");
  return `
    <section class="quest-guide" aria-label="How to use quest walkthroughs">
      <div class="quest-guide-card feature-card">
        ${icon("message-square-text")}
        <div class="card-copy">
          <strong>Get the offer</strong>
          <span>Talk to a matching villager when the profession, level, and skill gates line up.</span>
        </div>
      </div>
      <div class="quest-guide-card feature-card">
        ${icon("map-pin")}
        <div class="card-copy">
          <strong>Follow the tracker</strong>
          <span>Location quests point you toward target coordinates and update as you make progress.</span>
        </div>
      </div>
      <div class="quest-guide-card feature-card">
        ${icon("package-check")}
        <div class="card-copy">
          <strong>Return cleanly</strong>
          <span>Bring the proof and any extra items back to the quest giver for rewards.</span>
        </div>
      </div>
    </section>
    ${Object.entries(byGroup).map(([group, quests]) => countedSection(group, plural(quests.length, "quest"), `
      <div class="card-grid two">${quests.map(questCard).join("")}</div>
    `)).join("")}
  `;
}

function questRelationInfo(quest) {
  const relationKey = quest.relationKey || (quest.questline ? `questline:${quest.questline}` : `group:${quest.group}`);
  const related = DATA.quests
    .filter((item) => (item.relationKey || (item.questline ? `questline:${item.questline}` : `group:${item.group}`)) === relationKey)
    .sort((a, b) => (a.questlineOrder ?? 0) - (b.questlineOrder ?? 0) || a.title.localeCompare(b.title));
  const isQuestline = Boolean(quest.questline);
  return {
    related,
    isQuestline,
    label: isQuestline ? "Questline" : "Quest Group"
  };
}

function questlinePanel(quest) {
  const relation = questRelationInfo(quest);
  const related = relation.related;
  if (related.length <= 1) return "";

  const byId = new Map(DATA.quests.map((item) => [item.id, item]));
  const branchGroups = new Set(related.map((item) => item.branchGroup).filter(Boolean));
  const summary = [
    plural(related.length, "quest"),
    branchGroups.size ? plural(branchGroups.size, "branch group") : ""
  ].filter(Boolean).join(" • ");

  return section(relation.label, `
    <div class="questline-panel">
      <div class="questline-summary">
        ${icon(questIcon(quest))}
        <div>
          <strong>${escapeHtml(quest.questlineLabel || quest.groupLabel)}</strong>
          <span>${escapeHtml(summary)}</span>
        </div>
      </div>
      <ol class="questline-list">
        ${related.map((item, index) => {
          const prerequisites = (item.prerequisites || [])
            .map((prerequisite) => byId.get(prerequisite.id))
            .filter(Boolean);
          const requirementText = prerequisites.length
            ? `Requires ${prerequisites.map((prerequisite) => prerequisite.title).join(prerequisites.length > 1 ? " or " : "")}`
            : relation.isQuestline && index === 0 ? "Starts this questline" : "Group entry quest";
          const meta = [
            item.id === quest.id ? "Current quest" : "",
            requirementText,
            item.branchGroup ? "Branch path" : "",
            item.branchChoices?.length ? `Choices: ${item.branchChoices.map((choice) => choice.label).join(", ")}` : ""
          ].filter(Boolean);
          return `
            <li class="${item.id === quest.id ? "is-current" : ""}">
              <span class="questline-index">${index + 1}</span>
              <div class="questline-copy">
                <a href="${questUrl(item.slug)}" ${item.id === quest.id ? `aria-current="page"` : ""}>${escapeHtml(item.title)}</a>
                <span>${escapeHtml(meta.join(" • "))}</span>
              </div>
            </li>
          `;
        }).join("")}
      </ol>
    </div>
  `);
}

function renderQuestlineDetail(summary) {
  const quests = summary.quests || [];
  const byId = new Map(DATA.quests.map((item) => [item.id, item]));
  const branchGroups = new Set(quests.map((quest) => quest.branchGroup).filter(Boolean));
  return `
    ${statGrid([
      { value: plural(quests.length, "quest"), label: "Connected quests", icon: "scroll-text" },
      { value: branchGroups.size ? plural(branchGroups.size, "branch group") : "Linear path", label: "Progression shape", icon: "git-branch" }
    ])}
    ${section("Questline", `
      <div class="questline-panel">
        <div class="questline-summary">
          ${icon("map")}
          <div>
            <strong>${escapeHtml(summary.label)}</strong>
            <span>${escapeHtml(plural(quests.length, "quest"))}</span>
          </div>
        </div>
        <ol class="questline-list">
          ${quests.map((quest, index) => {
            const prerequisites = (quest.prerequisites || [])
              .map((prerequisite) => byId.get(prerequisite.id))
              .filter(Boolean);
            const requirementText = prerequisites.length
              ? `Requires ${prerequisites.map((prerequisite) => prerequisite.title).join(prerequisites.length > 1 ? " or " : "")}`
              : index === 0 ? "Starts this questline" : "No prerequisite quest";
            const meta = [
              requirementText,
              quest.branchGroup ? "Branch path" : "",
              quest.branchChoices?.length ? `Choices: ${quest.branchChoices.map((choice) => choice.label).join(", ")}` : ""
            ].filter(Boolean);
            return `
              <li>
                <span class="questline-index">${index + 1}</span>
                <div class="questline-copy">
                  <a href="${questUrl(quest.slug)}">${escapeHtml(quest.title)}</a>
                  <span>${escapeHtml(meta.join(" • "))}</span>
                </div>
              </li>
            `;
          }).join("")}
        </ol>
      </div>
    `)}
    ${section("Quests", `
      <div class="card-grid two">${quests.map(questCard).join("")}</div>
    `)}
  `;
}

function renderQuestDetail(quest) {
  const relation = questRelationInfo(quest);
  const target = quest.target;
  const totalLootWeight = quest.rewards.loot.reduce((sum, loot) => sum + (Number(loot.weight) || 0), 0);
  const minLootWeight = quest.rewards.loot.reduce((min, loot) => {
    const weight = Number(loot.weight);
    return Number.isFinite(weight) && weight > 0 ? Math.min(min, weight) : min;
  }, Number.POSITIVE_INFINITY);
  const rewardLoot = quest.rewards.loot.length ? quest.rewards.loot.map((loot) => {
    const weight = Number(loot.weight) || 0;
    const chance = totalLootWeight > 0 ? (weight / totalLootWeight) * 100 : 0;
    const chanceText = `${chance.toFixed(1).replace(/\.0$/, "")}%`;
    const detail = loot.note ? loot.note : "Possible reward";
    const isRarest = Number.isFinite(minLootWeight) && weight === minLootWeight;
    return `
    <div class="loot-card${isRarest ? " loot-card-rarest" : ""}">
      ${icon("package")}
      <strong>${escapeHtml(loot.count)} ${escapeHtml(loot.item)}</strong>
      <span class="loot-chance">${escapeHtml(chanceText)}</span>
      <span>${escapeHtml(detail)}</span>
    </div>
  `;
  }).join("") : `<div class="loot-card">${icon("package-x")}<strong>No loot table entries found.</strong><span>This quest only lists fixed rewards.</span></div>`;
  const skillText = quest.requirements.skills.length
    ? quest.requirements.skills.map((skill) => `${escapeHtml(skill.skill)} ${skill.min != null ? `at least ${escapeHtml(skill.min)}` : ""}`).join(", ")
    : "No hidden skill gate listed";
  const locationLabel = target?.structure || "No structure target";
  const rulesText = quest.rules.length ? quest.rules.join(", ") : "No special rules";

  return `
    <div class="back-row"><a href="${pageUrl("quests")}">${icon("arrow-left")}Back to all quests</a></div>

    <section class="quest-summary-panel" aria-label="Quest summary">
      <div class="quest-brief">
        <div>
          ${icon("users")}
          <strong>Quest giver</strong>
          <span>${escapeHtml(formatList(quest.requirements.professions))}</span>
        </div>
        <div>
          ${icon(target ? "map-pin" : "package-check")}
          <strong>Main goal</strong>
          <span>${escapeHtml(locationLabel)}</span>
        </div>
        <div>
          ${icon("package-check")}
          <strong>Turn in</strong>
          <span>${escapeHtml(quest.objectives.join(", "))}</span>
        </div>
      </div>
      <p class="quest-summary-copy">Start with a valid ${escapeHtml(quest.requirements.minLevel.toLowerCase())} villager, follow the tracker to the target, collect the proof, then return to the quest giver for reputation, XP, and a loot roll.</p>
    </section>

    ${statGrid([
      { value: quest.questlineLabel || quest.groupLabel, label: relation.label, icon: questIcon(quest) },
      { value: quest.requirements.minLevel, label: "Minimum villager level", icon: "badge-check" },
      { value: `${quest.rewards.reputation} rep`, label: "Direct reputation reward", icon: "heart-handshake" },
      { value: `${quest.rewards.experience} XP`, label: "Experience reward", icon: "sparkles" }
    ])}

    ${questlinePanel(quest)}

    ${section("Before You Start", `
      <div class="info-grid info-grid-before">
        <div class="info-card">${icon("briefcase-business")}<strong>Profession</strong><span>${escapeHtml(formatList(quest.requirements.professions))}</span></div>
        <div class="info-card">${icon("badge-check")}<strong>Trade level</strong><span>${escapeHtml(quest.requirements.minLevel)} or higher</span></div>
        <div class="info-card">${icon("activity")}<strong>Villager skills</strong><span>${skillText}</span></div>
      </div>
      <div class="info-card info-card-wide">${icon("scroll-text")}<strong>Quest rules</strong><span>${escapeHtml(rulesText)}</span></div>
    `)}

    ${section("Quest Flow", `
      <ol class="quest-flow">
        ${quest.steps.map((step, index) => `
          <li>
            <div class="flow-marker">
              ${icon(questFlowIcon(step.id))}
              <span>${index + 1}</span>
            </div>
            <div>
              <strong>${escapeHtml(step.label)}</strong>
              <p>${escapeHtml(step.text)}${step.hint ? ` Hint: ${escapeHtml(step.hint)}.` : ""}</p>
            </div>
          </li>
        `).join("")}
      </ol>
    `)}

    ${section("Turn In And Rewards", `
      <div class="turnin-layout">
        <div class="turnin-card">
          ${icon("clipboard-check")}
          <strong>Checklist</strong>
          ${pillList(quest.objectives)}
        </div>
        <div class="turnin-card">
          ${icon("heart-handshake")}
          <strong>Reputation</strong>
          <span>${escapeHtml(quest.rewards.reputation)} direct reputation and ${escapeHtml(quest.rewards.gossipReputation)} gossip reputation.</span>
        </div>
      </div>
      <div class="loot-grid">${rewardLoot}</div>
    `)}

    ${target ? section("Target Details", `
      <dl class="fact-list">
        <div><dt>Target</dt><dd>${escapeHtml(target.structure || "No structure target")}</dd></div>
        <div><dt>Proof item</dt><dd>${escapeHtml(target.proofItem || "None")}</dd></div>
        <div><dt>Search radius</dt><dd>${target.searchRadius ? `${escapeHtml(target.searchRadius)} blocks` : "Not used"}</dd></div>
        <div><dt>Discovery radius</dt><dd>${target.discoveryRadius ? `${escapeHtml(target.discoveryRadius)} blocks` : "Not used"}</dd></div>
      </dl>
    `) : ""}

    ${section("Dialogue Reference", renderQuestDialogueReference(quest))}
  `;
}

function renderQuestDialogueReference(quest) {
  const dialogue = quest.dialogue || {};
  const commonStages = (dialogue.commonStages || []).filter(dialogueStageHasContent);
  const branches = dialogue.branches || [];
  if (commonStages.length || branches.length) {
    return `
      <details class="reference-panel">
        <summary>${icon("message-square-text")}Show quest dialogue</summary>
        <div class="dialogue-reference-body">
          ${commonStages.length ? `
            <div class="dialogue-route">
              <div class="dialogue-route-title">
                <strong>Shared path</strong>
                <span>Shown before branch-specific dialogue.</span>
              </div>
              ${renderDialogueStageList(commonStages)}
            </div>
          ` : ""}
          ${branches.map(renderDialogueBranchGroup).join("")}
        </div>
      </details>
    `;
  }
  return `
    <details class="reference-panel">
      <summary>${icon("message-square-text")}Show quest dialogue</summary>
      <div class="quote-stack">
        ${quoteBlock("Offer", dialogue.offer)}
        ${quoteBlock("Started", dialogue.started)}
        ${quoteBlock("Reminder", dialogue.reminder)}
        ${quoteBlock("Completion", dialogue.completed)}
        ${quoteBlock("Missing Items", dialogue.missing)}
      </div>
    </details>
  `;
}

function dialogueStageHasContent(stage) {
  return stage?.slots?.length || stage?.choices?.length || stage?.actions?.length || stage?.scenes?.length;
}

function renderDialogueBranchGroup(branch) {
  return `
    <div class="dialogue-branch-group">
      <div class="dialogue-route-title">
        <strong>Choice at ${escapeHtml(branch.label || titleCase(branch.stageId))}</strong>
        <span>${escapeHtml(branch.stageId || "branch")}</span>
      </div>
      <div class="dialogue-choice-grid">
        ${(branch.choices || []).map((choice) => `
          <div class="dialogue-choice">
            <div class="dialogue-choice-head">
              <strong>${escapeHtml(choice.label || titleCase(choice.id))}</strong>
              ${choice.id ? `<span>${escapeHtml(choice.id)}</span>` : ""}
            </div>
            ${choice.lines?.length ? `<div class="dialogue-lines">${choice.lines.map((line) => `<p>${escapeHtml(line)}</p>`).join("")}</div>` : ""}
            ${choice.destination ? `<p class="dialogue-destination">${escapeHtml(choice.destination)}</p>` : ""}
            ${choice.stages?.length ? renderDialogueStageList(choice.stages) : `<p class="dialogue-empty">No separate branch dialogue is listed for this option.</p>`}
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderDialogueStageList(stages) {
  return `<div class="dialogue-stage-list">${(stages || []).filter(dialogueStageHasContent).map(renderDialogueStage).join("")}</div>`;
}

function renderDialogueStage(stage) {
  return `
    <div class="dialogue-stage">
      <div class="dialogue-stage-head">
        <strong>${escapeHtml(stage.label || titleCase(stage.stageId))}</strong>
        ${stage.stageId ? `<span>${escapeHtml(stage.stageId)}</span>` : ""}
      </div>
      ${stage.trackerText ? `<p class="dialogue-tracker">${escapeHtml(stage.trackerText)}</p>` : ""}
      ${(stage.slots || []).map(renderDialogueSlot).join("")}
      ${stage.choices?.length ? renderDialogueChoices("Branch options", stage.choices) : ""}
      ${(stage.actions || []).map((group) => renderDialogueLineGroup(group.label, group.lines)).join("")}
      ${(stage.scenes || []).map((group) => renderDialogueLineGroup(group.label, group.lines)).join("")}
    </div>
  `;
}

function renderDialogueSlot(slot) {
  return `
    <div class="dialogue-block">
      <div class="dialogue-block-title">
        <strong>${escapeHtml(slot.title || "Dialogue")}</strong>
        ${slot.label && slot.label !== slot.title ? `<span>${escapeHtml(slot.label)}</span>` : ""}
      </div>
      ${slot.lines?.length ? `<div class="dialogue-lines">${slot.lines.map((line) => `<p>${escapeHtml(line)}</p>`).join("")}</div>` : ""}
      ${slot.responses?.length ? renderDialogueChoices("Options", slot.responses) : ""}
    </div>
  `;
}

function renderDialogueChoices(title, choices) {
  return `
    <div class="dialogue-options">
      <strong>${escapeHtml(title)}</strong>
      <ul>
        ${(choices || []).map((choice) => `
          <li>
            <span>${escapeHtml(choice.label || titleCase(choice.id))}</span>
            ${choice.destination ? `<em>${escapeHtml(choice.destination)}</em>` : ""}
            ${choice.lines?.length ? choice.lines.map((line) => `<p>${escapeHtml(line)}</p>`).join("") : ""}
          </li>
        `).join("")}
      </ul>
    </div>
  `;
}

function renderDialogueLineGroup(title, lines) {
  if (!lines?.length) return "";
  return `
    <div class="dialogue-block">
      <div class="dialogue-block-title"><strong>${escapeHtml(title)}</strong></div>
      <div class="dialogue-lines">${lines.map((line) => `<p>${escapeHtml(line)}</p>`).join("")}</div>
    </div>
  `;
}

function questFlowIcon(stepId) {
  if (stepId === "travel") return "map-pin";
  if (stepId === "proof") return "package-search";
  if (stepId === "return") return "undo-2";
  if (stepId.startsWith("bring_")) return "package-check";
  return "circle-dot";
}

function quoteBlock(title, lines) {
  if (!lines || !lines.length) return "";
  return `<blockquote><strong>${escapeHtml(title)}</strong>${lines.map((line) => `<p>${escapeHtml(line)}</p>`).join("")}</blockquote>`;
}

function renderReputation() {
  return `
    ${section("Reputation Is Personal", `
      <p>Reputation is tracked per villager and per player. One villager can trust you while another despises you, and nearby witnesses can carry consequences through gossip.</p>
      <p>Low trust can affect trade prices, pacification, anger duration, fleeing, attack-on-sight behavior, cleric support, dialogue tone, interaction availability, and advancement progress.</p>
    `)}
    ${section("Default Tiers", `
      <div class="table-wrap"><table><thead><tr><th>Tier</th><th>Default threshold</th><th>Effect</th></tr></thead><tbody>
        ${DATA.reputation.map((tier) => `<tr><td>${escapeHtml(tier.level)}</td><td>${escapeHtml(tier.threshold)}</td><td>${escapeHtml(tier.effect)}</td></tr>`).join("")}
      </tbody></table></div>
    `)}
    ${section("Pacification", `
      <p>Hostile villagers can be calmed with datapack-defined payment items unless reputation has fallen too low or the config blocks that outcome.</p>
      <div class="card-grid two">${DATA.pacification.map((payment) => `<div class="feature-card no-icon"><div class="card-copy"><strong>${escapeHtml(payment.item)}</strong><span>${escapeHtml(payment.min)} to ${escapeHtml(payment.max)} ${escapeHtml(payment.name)}</span></div></div>`).join("")}</div>
    `)}
    ${section("Ways To Recover", `
      ${simpleList([
        "Trade with villagers who still allow it.",
        "Give liked or loved gifts, especially profession-specific items.",
        "Defend villagers and villages from hostile mobs.",
        "Complete quests and return proof cleanly.",
        "Avoid watched containers until suspicion cools down."
      ].map(escapeHtml))}
    `)}
  `;
}

function renderDialogue() {
  return `
    ${section("Interaction Screen", `
      <p>The interaction screen lets players talk, give gifts, inspect relationship context, recruit eligible villagers, and follow quest scenes. Dialogue reacts to personal reputation, profession, recent village events, family ties, gear, weather, time, and whether this is the first meeting.</p>
    `)}
    ${section("Stories And Maps", `
      <p>Villagers can share discovered structure and biome stories. Trusted villagers can point players toward map hints, and story sharing feeds advancements such as Once Upon a Time, Story Keeper, Village Chronicler, and Legend Trader.</p>
    `)}
    ${section("Quests In Dialogue", `
      <p>Quest offers, reminders, turn-ins, abandonment prompts, and event follow-ups are authored as dialogue trees. If a quest is available, in progress, or ready to complete, the relevant option appears in the villager conversation menu.</p>
      <p>Default quest keybinds are <kbd>J</kbd> for the Quest Journal and <kbd>K</kbd> for the Quest Tracker.</p>
    `)}
  `;
}

function renderCombat() {
  return `
    ${section("Retaliation", `
      <p>By default, hitting a villager angers that villager. Killing an adult villager can also anger nearby adult witnesses when visibility rules are met. Anger expires after a configurable duration and can spread through witness-based retaliation events.</p>
      <br />
      ${simpleList([
        "Hostile villagers and wandering traders can block trading while hostile.",
        "Hostile villagers can be pacified with datapack-defined item payments unless reputation is too low.",
        "Villagers can use melee weapons, ranged weapons, shields, and armor through combat roles and inventory interaction.",
        "While threatened, villagers and wandering traders can pick up nearby dropped weapons, and temporary retaliation weapons can drop on death at configurable chances.",
        "Clerics can use defensive and splash potions, heal allies, and support trusted-or-better players."
      ].map(escapeHtml))}
    `)}
    ${section("Hostile Mob Defense", `
      <p>When enabled, villagers and wandering traders can target, retaliate against, or stand ground against hostile mobs. This keeps villages more self-protective without replacing vanilla villagers or removing profession identity.</p>
    `)}
    ${section("Environmental Blame", `
      <p>If a player places lava, uses flint and steel, or uses a fire charge, nearby lava or fire damage can be attributed to that player for a short window (2 real-time minutes by default). Village witnesses can connect that damage to the player for retaliation, reputation, and related systems instead of treating it as ordinary environmental harm.</p>
    `)}
  `;
}

function renderPlayerRaids() {
  return `
    ${section("Declaring A Raid", `
      <p>Attach a banner to a helmet, wear it inside a tracked village, and begin using a goat horn. The player and their current party are snapshotted as raiders. The raid cannot overlap a vanilla raid, another Player Raid involving the village or a participant, or a defended-village cooldown.</p>
    `)}
    ${section("When A Party Member Calls It Home", `
      <p>Recruited villagers from the target village permanently leave the party and confront the initiating player in a chained conversation before preparations begin. They call the raiders traitors, rejoin their neighbors, and count among the defenders.</p>
      <p>Every villager recorded as belonging to that village sets every raider player's reputation to at most -250. Reputation already below -250 loses another 250.</p>
    `)}
    ${section("Preparation And Defenses", `
      ${simpleList([
        "The red raid bar fills for 10 seconds by default, then shows the number of snapshotted villagers remaining.",
        "Adult non-nitwits fill empty equipment slots with difficulty-weighted militia weapons and armor.",
        "Babies and nitwits hide while capable villagers engage the raiders.",
        "Aligned iron golems arrive in fixed-budget batches at activation and the 75%, 50%, and 25% defender thresholds.",
        "Raid equipment and surviving summoned golems remain after the outcome."
      ].map(escapeHtml))}
    `)}
    ${section("Winning Or Abandoning", `
      <p>Raiders win once every snapshotted defender is dead or converted. Villagers born later and visitors are not added to the objective, and golems never count as remaining villagers.</p>
      <p>The village wins if no living, non-spectator raider player stays inside the village footprint for 30 seconds by default. A surviving village then receives a three-day cooldown.</p>
    `)}
  `;
}

function renderGifts() {
  const globalPreferredItems = Array.isArray(DATA.gifts.globalPreferredItems) ? DATA.gifts.globalPreferredItems : [];
  const globalDislikedItems = Array.isArray(DATA.gifts.globalDislikedItems) ? DATA.gifts.globalDislikedItems : [];
  const globalNeutralItems = Array.isArray(DATA.gifts.globalNeutralItems) ? DATA.gifts.globalNeutralItems : [];
  const professionPanels = (DATA.gifts.professionPreferences || [])
    .slice()
    .sort((a, b) => String(a.profession || "").localeCompare(String(b.profession || "")))
    .map((group) => {
    const preferred = new Set();
    const disliked = new Set();
    (group.entries || []).forEach((entry) => {
      const key = String(entry.reaction || "").toLowerCase();
      const items = Array.isArray(entry.items) ? entry.items : [];
      if (key === "loved" || key === "liked") items.forEach((item) => preferred.add(item));
      if (key === "disliked" || key === "hated") items.forEach((item) => disliked.add(item));
    });
    const preferredItems = preferred.size ? [...preferred].sort((a, b) => a.localeCompare(b)) : [];
    const dislikedItems = disliked.size ? [...disliked].sort((a, b) => a.localeCompare(b)) : [];
    return `
      <details class="profession-gift-panel">
        <summary>
          <span class="profession-gift-name">${escapeHtml(group.profession)}</span>
          <span class="profession-gift-meta">${preferredItems.length} preferred, ${dislikedItems.length} disliked</span>
          ${icon("chevron-down", "profession-gift-chevron")}
        </summary>
        <div class="profession-gift-body">
          <div class="profession-gift-group profession-gift-group-preferred">
            <strong>Preferred gifts</strong>
            ${preferredItems.length ? pillList(preferredItems) : '<p class="profession-gift-empty">None listed.</p>'}
          </div>
          <div class="profession-gift-group profession-gift-group-disliked">
            <strong>Disliked gifts</strong>
            ${dislikedItems.length ? pillList(dislikedItems) : '<p class="profession-gift-empty">None listed.</p>'}
          </div>
        </div>
      </details>
    `;
  }).join("");

  const globalLikedPanel = `
    <details class="profession-gift-panel profession-gift-panel-global">
      <summary>
        <span class="profession-gift-name">Global gifts</span>
        <span class="profession-gift-meta">${globalPreferredItems.length} preferred, ${globalDislikedItems.length} disliked</span>
        ${icon("chevron-down", "profession-gift-chevron")}
      </summary>
      <div class="profession-gift-body">
        <div class="profession-gift-group profession-gift-group-preferred">
          <strong>Preferred gifts</strong>
          ${globalPreferredItems.length ? pillList(globalPreferredItems) : '<p class="profession-gift-empty">None listed.</p>'}
        </div>
        <div class="profession-gift-group profession-gift-group-disliked">
          <strong>Disliked gifts</strong>
          ${globalDislikedItems.length ? pillList(globalDislikedItems) : '<p class="profession-gift-empty">None listed.</p>'}
        </div>
        <div class="profession-gift-group profession-gift-group-neutral">
          <strong>Neutral</strong>
          ${globalNeutralItems.length ? pillList(globalNeutralItems) : pillList(["Anything not listed"])}
        </div>
      </div>
    </details>
  `;

  const rewardGroups = new Map();
  (DATA.gifts.rewards || []).forEach((reward) => {
    const professions = Array.isArray(reward.professions) && reward.professions.length ? reward.professions : ["Any"];
    professions.forEach((profession) => {
      if (!rewardGroups.has(profession)) rewardGroups.set(profession, []);
      rewardGroups.get(profession).push(reward);
    });
  });
  const rewardPanels = [...rewardGroups.entries()]
    .sort((a, b) => String(a[0]).localeCompare(String(b[0])))
    .map(([profession, rewards]) => `
      <details class="profession-gift-panel reward-panel">
        <summary>
          <span class="profession-gift-name">${escapeHtml(profession)}</span>
          <span class="profession-gift-meta">${rewards.length} reward ${rewards.length === 1 ? "entry" : "entries"}</span>
          ${icon("chevron-down", "profession-gift-chevron")}
        </summary>
        <div class="profession-gift-body reward-panel-body">
          ${rewards.map((reward) => `
            <div class="profession-gift-group reward-gift-group">
              <strong>${escapeHtml(formatList(reward.levels))}</strong>
              ${pillList([`${reward.count} ${reward.item}`])}
            </div>
          `).join("")}
        </div>
      </details>
    `).join("");

  return `
    ${section("Gift Basics", `
      <p>Villagers evaluate gifts by global rules first and then profession rules. Reactions include Loved, Liked, Neutral, Disliked, and Hated. A gifted stack must fit in the villager inventory or it is refused.</p>
      <p>Positive gift reputation is personal to each player-villager relationship. By default, another stack of the same item on the same Minecraft day earns 10% reputation, and positive gift reputation is capped at 120 per relationship per day. Negative gift reactions keep their full penalty.</p>
      ${statGrid([
        { value: DATA.gifts.totals?.preferences || 0, label: "Preference rules", icon: "sparkles" },
        { value: DATA.gifts.totals?.rewards || 0, label: "High-reputation reward rules", icon: "gift" }
      ])}
    `)}
    ${section("Profession Gift Preferences", `
      <div class="profession-gift-list">
        ${globalLikedPanel}
        ${professionPanels}
      </div>
    `)}
    ${section("High-Reputation Rewards", `
      <div class="profession-gift-list reward-panel-list">
        ${rewardPanels}
      </div>
    `)}
  `;
}

function renderSkillTrades() {
  return `
    ${section("How Skill Trades Appear", `
      <p>Skill trades are generated from a villager's profession, hidden skill scores, trade level, reputation gates, and config flags. High-reputation Special Orders can target unlocked definitions directly.</p>
    `)}
    ${DATA.skillTrades.map((group) => section(group.profession, `
      <div class="table-wrap"><table class="skill-trade-table"><colgroup><col class="rank-col"><col class="level-col"><col class="cost-col"><col class="result-col"><col class="chance-col"><col class="order-col"></colgroup><thead><tr><th>Rank</th><th>Level</th><th>Cost</th><th>Result</th><th>Chance</th><th>Special Order</th></tr></thead><tbody>
        ${group.trades.map((trade) => `<tr><td>${escapeHtml(trade.rank)}</td><td>${escapeHtml(trade.level || "")}</td><td>${escapeHtml(trade.cost)}</td><td>${escapeHtml(trade.result)}</td><td>${trade.chance == null ? "" : `${escapeHtml(trade.chance)}%`}</td><td>${trade.requestable ? escapeHtml(trade.minReputation || "Yes") : "No"}</td></tr>`).join("")}
      </tbody></table></div>
    `)).join("")}
  `;
}

function renderContainers() {
  return `
    ${section("Village Property", `
      <p>Villagers can confront players for opening, breaking, or stealing from watched containers, including generated village chests. Reputation and nearby witnesses shape how harsh the response feels.</p>
    `)}
    ${section("Breaking Chests Is Worse", `
      <p>Breaking generated containers unpacks and counts the dropped loot before reputation loss is applied, so smashing a fuller village store is worse than opening an empty one.</p>
    `)}
  `;
}

function normalizedAdvancementParent(parent) {
  if (!parent) return "";
  const slashTail = String(parent).split("/").pop() || "";
  return slashTail.split(":").pop() || "";
}

function advancementFrameWeight(frame) {
  const normalized = String(frame || "").toLowerCase();
  if (normalized === "task") return 0;
  if (normalized === "goal") return 1;
  if (normalized === "challenge") return 2;
  return 3;
}

function advancementDepth(advancementId, byId, visiting = new Set()) {
  if (!advancementId || !byId.has(advancementId)) return 0;
  if (visiting.has(advancementId)) return 0;
  const advancement = byId.get(advancementId);
  const parentId = normalizedAdvancementParent(advancement.parent);
  if (!parentId || !byId.has(parentId)) return 0;
  visiting.add(advancementId);
  const depth = 1 + advancementDepth(parentId, byId, visiting);
  visiting.delete(advancementId);
  return depth;
}

function advancementRows() {
  const advancements = Array.isArray(DATA.advancements) ? DATA.advancements : [];
  const byId = new Map(advancements.map((advancement) => [advancement.id, advancement]));
  return advancements.map((advancement) => {
    const parentId = normalizedAdvancementParent(advancement.parent);
    return {
      ...advancement,
      parentId,
      parentTitle: byId.get(parentId)?.title || "",
      depth: advancementDepth(advancement.id, byId)
    };
  });
}

function groupedAdvancements() {
  const rows = advancementRows();
  const groupedIds = {
    foundation: [
      "root",
      "commonfolk",
      "familiar_face",
      "respect_is_earned",
      "regular_customer",
      "second_chance",
      "changed_my_mind",
      "im_sorry",
      "the_village_remembers"
    ],
    trust: [
      "friend_of_the_village",
      "community_support",
      "price_of_trust",
      "local_legend",
      "crowned_by_the_village",
      "trusted_directions"
    ],
    story: [
      "once_upon_a_time",
      "story_keeper",
      "village_chronicler",
      "legend_trader"
    ],
    retaliation: [
      "bad_first_impression",
      "hands_off",
      "refused_service",
      "marked",
      "the_village_has_eyes",
      "village_enemy",
      "mob_justice",
      "hero_not_menace",
      "an_unwise_decision"
    ],
    hidden: [
      "accidentally_of_course",
      "bait_and_betrayal",
      "no_rest_for_the_wicked",
      "peace_offering",
      "cover_them_in_debris"
    ]
  };

  const labels = {
    foundation: "Trust Foundations",
    trust: "Reputation Growth",
    story: "Story Progression",
    retaliation: "Conflict And Consequences",
    hidden: "Hidden And Mischief Paths",
    other: "Other Advancements"
  };

  const order = ["foundation", "trust", "story", "retaliation", "hidden", "other"];
  const idToGroup = new Map();
  Object.entries(groupedIds).forEach(([group, ids]) => ids.forEach((id) => idToGroup.set(id, group)));

  const groups = {
    foundation: [],
    trust: [],
    story: [],
    retaliation: [],
    hidden: [],
    other: []
  };

  rows.forEach((row) => {
    const group = idToGroup.get(row.id) || "other";
    groups[group].push(row);
  });

  const rowSort = (a, b) => {
    if (a.depth !== b.depth) return a.depth - b.depth;
    if (a.hidden !== b.hidden) return Number(a.hidden) - Number(b.hidden);
    const frameDelta = advancementFrameWeight(a.frame) - advancementFrameWeight(b.frame);
    if (frameDelta !== 0) return frameDelta;
    return String(a.title).localeCompare(String(b.title));
  };

  return order
    .map((key) => ({
      key,
      title: labels[key],
      rows: groups[key].sort(rowSort)
    }))
    .filter((group) => group.rows.length > 0);
}

function renderAdvancements(options = {}) {
  const focusedAdvancementId = options.focusedAdvancementId || "";
  const groups = groupedAdvancements();
  const groupedSections = groups
    .map((group) => section(group.title, `
        <div class="table-wrap"><table class="advancement-flow-table"><colgroup><col class="adv-col-title"><col class="adv-col-parent"><col class="adv-col-type"><col class="adv-col-description"><col class="adv-col-hidden"></colgroup><thead><tr><th>Advancement</th><th>Unlocks After</th><th>Type</th><th>Description</th><th>Hidden</th></tr></thead><tbody>
          ${group.rows.map((advancement) => `<tr id="${escapeHtml(advancementRowId(advancement.id))}" class="${advancement.id === focusedAdvancementId ? "advancement-row-target" : ""}"><td>${escapeHtml(advancement.title)}</td><td>${escapeHtml(advancement.parentTitle || "Root")}</td><td>${escapeHtml(advancement.frame)}</td><td>${escapeHtml(advancement.description)}</td><td>${advancement.hidden ? "Yes" : "No"}</td></tr>`).join("")}
        </tbody></table></div>
      `))
    .join("");

  return `
    ${section("Reputation Tab", `
      <p>The mod includes a full reputation advancement tab. Entries below are grouped by related progression paths, so connected milestones stay together.</p>
    `)}
    ${groupedSections}
  `;
}

function renderSettings() {
  return `
    ${section("Keybinds", `
      <dl class="fact-list">
        <div><dt>Quest Journal</dt><dd><kbd>J</kbd></dd></div>
        <div><dt>Quest Tracker</dt><dd><kbd>K</kbd></dd></div>
      </dl>
    `)}
    ${section("Changing Settings", `
      <p>Open Mod Menu and choose Villager Retaliation to adjust available controls and display preferences. Multiplayer gameplay rules are controlled by the server.</p>
    `)}
  `;
}

function searchIndex() {
  const pageResults = PAGES.map((page) => ({
    type: "Page",
    title: page.title,
    description: page.description,
    url: pageUrl(page.id),
    haystack: `${page.title} ${page.description} ${page.group}`.toLowerCase()
  }));
  const questResults = DATA.quests.map((quest) => ({
    type: "Quest",
    title: quest.title,
    description: `${quest.questlineLabel || quest.groupLabel} - ${quest.description}`,
    url: questUrl(quest.slug),
    haystack: `${quest.title} ${quest.description} ${quest.questlineLabel} ${quest.groupLabel} ${(quest.tags || []).join(" ")} ${quest.objectives.join(" ")} ${quest.requirements.professions.join(" ")} ${quest.requirements.skills.map((skill) => skill.skill).join(" ")}`.toLowerCase()
  }));
  const advancements = Array.isArray(DATA.advancements) ? DATA.advancements : [];
  const advancementResults = advancements.map((advancement) => ({
    type: "Advancement",
    title: advancement.title,
    description: `${advancement.frame}${advancement.hidden ? " hidden" : ""} - ${advancement.description || "Reputation tab advancement."}`,
    url: advancementUrl(advancement.id),
    haystack: `${advancement.title} ${advancement.id} ${compactId(advancement.id)} ${advancement.parent || ""} ${advancement.frame} ${advancement.description || ""} reputation advancement challenge hidden`.toLowerCase()
  }));
  return [...pageResults, ...questResults, ...advancementResults];
}

function renderSearch() {
  const query = searchQuery.trim().toLowerCase();
  const results = query ? searchIndex().filter((item) => item.haystack.includes(query)).slice(0, 40) : [];
  renderDocument("Search", query ? `${results.length} results for ${searchQuery}` : "Search the player wiki.", `
    ${section("Results", `
      ${results.length ? `<div class="search-results">${results.map((result) => `
        <a class="search-result" href="${result.url}">
          ${icon(resultIcon(result.type))}
          <span>${escapeHtml(result.type)}</span>
          <strong>${escapeHtml(result.title)}</strong>
          <p>${escapeHtml(result.description)}</p>
        </a>
      `).join("")}</div>` : `<p>Type in the search box to find quests, advancements, rewards, systems, and walkthrough details.</p>`}
    `)}
  `, {
    icon: "search",
    parent: "Wiki"
  });
}

function paletteResults(query) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return searchIndex().slice(0, 8);
  return searchIndex().filter((item) => item.haystack.includes(normalized)).slice(0, 10);
}

function renderPaletteResults() {
  const results = paletteResults(paletteQuery);
  els.paletteResults.innerHTML = results.length ? results.map((result, index) => `
    <a class="palette-result ${index === 0 ? "is-current" : ""}" href="${result.url}" role="option">
      ${icon(resultIcon(result.type))}
      <span>${escapeHtml(result.type)}</span>
      <strong>${escapeHtml(result.title)}</strong>
      <p>${escapeHtml(result.description)}</p>
    </a>
  `).join("") : `<div class="palette-empty">No matches yet.</div>`;
  renderIcons();
}

function openPalette(seed = "") {
  paletteQuery = seed;
  els.paletteSearch.value = seed;
  renderPaletteResults();
  document.body.classList.add("is-search-open");
  els.palette.classList.add("is-open");
  els.palette.setAttribute("aria-hidden", "false");
  window.requestAnimationFrame(() => {
    els.paletteSearch.focus();
    els.paletteSearch.select();
  });
}

function closePalette() {
  document.body.classList.remove("is-search-open");
  els.palette.classList.remove("is-open");
  els.palette.setAttribute("aria-hidden", "true");
}

function followPaletteResult(result) {
  if (!result) return;
  closePalette();
  location.hash = result.getAttribute("href");
}

els.search.addEventListener("input", () => {
  searchQuery = els.search.value;
  if (searchQuery.trim()) location.hash = "#/search";
  else render();
});

els.search.addEventListener("keydown", (event) => {
  if (event.key !== "Enter") return;
  const first = document.querySelector(".search-result");
  if (first) first.click();
});

document.addEventListener("keydown", (event) => {
  if (event.altKey && event.key.toLowerCase() === "q") {
    event.preventDefault();
    openPalette(els.search.value);
    return;
  }

  if (event.key === "Escape" && els.palette.classList.contains("is-open")) {
    event.preventDefault();
    closePalette();
  }
});

els.paletteSearch.addEventListener("input", () => {
  paletteQuery = els.paletteSearch.value;
  renderPaletteResults();
});

els.paletteSearch.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    followPaletteResult(els.paletteResults.querySelector(".palette-result"));
  }
});

els.paletteResults.addEventListener("click", (event) => {
  const result = event.target.closest(".palette-result");
  if (!result) return;
  event.preventDefault();
  followPaletteResult(result);
});

els.palette.addEventListener("click", (event) => {
  if (event.target === els.palette) closePalette();
});

els.menuToggle.addEventListener("click", () => {
  const open = document.body.classList.toggle("is-menu-open");
  els.menuToggle.setAttribute("aria-expanded", String(open));
});

els.nav.addEventListener("click", () => {
  document.body.classList.remove("is-menu-open");
  els.menuToggle.setAttribute("aria-expanded", "false");
});

els.toc.addEventListener("click", (event) => {
  const link = event.target.closest(".toc-link[data-toc-target]");
  if (!link) return;
  event.preventDefault();
  const targetId = link.getAttribute("data-toc-target");
  if (!targetId) return;
  const heading = document.getElementById(targetId);
  if (!heading) return;
  heading.scrollIntoView({ behavior: "smooth", block: "start" });
});

window.addEventListener("hashchange", () => {
  saveRouteScroll();
  render();
});
window.addEventListener("pagehide", () => saveRouteScroll());

if (!location.hash) location.hash = "#/home";
render();
