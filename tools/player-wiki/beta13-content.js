(() => {
  "use strict";

  const beta13ExistingPages = new Map(PAGES.map((page) => [page.id, page]));
  const beta13OriginalQuestRender = beta13ExistingPages.get("quests")?.render || renderQuests;

  const beta13DespisedTier = Array.isArray(DATA.reputation)
    ? DATA.reputation.find((tier) => tier.level === "Despised")
    : null;
  if (beta13DespisedTier) {
    beta13DespisedTier.threshold = "-400 or below";
    beta13DespisedTier.effect = "Most peaceful options are unavailable. Villagers may refuse service or attack on sight when that behavior is enabled.";
  }

  function beta13ExistingPage(id, patch = {}) {
    const page = beta13ExistingPages.get(id);
    if (!page) throw new Error(`Missing existing wiki page: ${id}`);
    return Object.assign(page, patch);
  }

  function beta13NewPage(id, title, group, iconName, description, renderPage) {
    return { id, title, group, icon: iconName, description, render: renderPage };
  }

  function beta13Table(headers, rows, className = "") {
    return `
      <div class="table-wrap">
        <table${className ? ` class="${escapeHtml(className)}"` : ""}>
          <thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead>
          <tbody>${rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join("")}</tr>`).join("")}</tbody>
        </table>
      </div>
    `;
  }

  function beta13FeatureCards(cards) {
    return `<div class="card-grid two">${cards.map((card) => `
      <div class="feature-card">
        ${icon(card.icon || "circle-check")}
        <div class="card-copy">
          <strong>${escapeHtml(card.title)}</strong>
          <span>${escapeHtml(card.text)}</span>
        </div>
      </div>
    `).join("")}</div>`;
  }

  function beta13FactList(rows) {
    return `<dl class="fact-list">${rows.map(([term, detail]) => `
      <div><dt>${escapeHtml(term)}</dt><dd>${escapeHtml(detail)}</dd></div>
    `).join("")}</dl>`;
  }

  function beta13RenderHome() {
    const questlines = new Set(DATA.quests.map((quest) => quest.questline).filter(Boolean)).size;
    const reputationTiers = Array.isArray(DATA.reputation) ? DATA.reputation.length : 0;
    const skillTradeCount = DATA.skillTrades.reduce((sum, group) => sum + group.count, 0);
    return `
      ${statGrid([
        { value: plural(DATA.quests.length, "quest"), label: "Built-in walkthroughs", icon: "scroll-text" },
        { value: plural(questlines, "questline"), label: "Connected stories", icon: "map" },
        { value: plural(reputationTiers, "reputation tier"), label: "Relationship levels", icon: "shield" },
        { value: plural(DATA.advancements.length, "advancement"), label: "Optional milestones", icon: "trophy" },
        { value: plural(skillTradeCount, "profession trade"), label: "Skill-based offers", icon: "badge-percent" },
        { value: "18 Villager skills", label: "Individual strengths", icon: "brain-circuit" },
        { value: "13 Worker roles", label: "Optional hired work", icon: "briefcase-business" },
        { value: "5 Social attributes", label: "Persistent personalities", icon: "sparkles" }
      ])}
      ${section("Villagers Who Remember", `
        <p>Villager Retaliation turns villagers into persistent characters who remember how each player treats them. Conversation, trade, gifts, quests, witnessed crimes, village events, combat, and time all shape individual relationships.</p>
        <br />
        ${simpleList([
          "Build trust through fair trade, thoughtful gifts, healing, village defense, quests, and helpful choices.",
          "Lose trust through attacks, theft, harmful gifts, broken expectations, and violence witnessed by others.",
          "Meet villagers with different skills, personalities, moods, memories, families, professions, and village loyalties.",
          "Live with consequences that can affect dialogue, prices, services, quest access, pacification, fleeing, and retaliation."
        ])}
      `)}
      ${section("Talk, Trade, And Take On Quests", `
        ${beta13FeatureCards([
          { icon: "message-square-text", title: "Conversation", text: "Use the interaction screen for greetings, questions, stories, relationships, family, local events, and responses shaped by the villager's current view of you." },
          { icon: "gift", title: "Gifts and keepsakes", text: "Learn profession preferences, give items that matter, and receive keepsakes or high-trust rewards from villagers who know you well." },
          { icon: "scroll-text", title: "Quests", text: "Discover profession- and skill-gated stories with choices, tracked objectives, authored scenes, turn-ins, rewards, and lasting outcomes." },
          { icon: "badge-percent", title: "Skills and trade growth", text: "Villager skills influence special trades, quest eligibility, work aptitude, and the ways each villager develops over time." }
        ])}
      `)}
      ${section("Conflict Has Consequences", `
        <p>Adults can defend themselves and their communities. Nearby witnesses may react to public harm, gossip can spread what happened, and personal reputation can outlast the immediate fight.</p>
        ${simpleList([
          "Villagers can use profession-aware weapons, armor, shields, ranged attacks, potions, healing, and retreat behavior.",
          "Opening, breaking, or stealing from watched containers can create suspicion and damage nearby relationships.",
          "Hostile villagers may refuse service, demand pacification, flee, or fight depending on their standing and circumstances.",
          "Player Raids turn an attack on a tracked village into a community defense with a fixed raiding party, defenders, reinforcements, and consequences."
        ])}
      `)}
      ${section("Work, Parties, And Village Life", `
        ${beta13FeatureCards([
          { icon: "hand-coins", title: "Hire villagers", text: "Offer qualified adults paid contracts for farming, mining, logging, fishing, brewing, building, transport, combat, and other practical work." },
          { icon: "users", title: "Travel as a party", text: "Recruit villagers, share supplies, issue movement and combat orders, form alliances, and assign mounts for longer journeys." },
          { icon: "landmark", title: "Belong to a village", text: "Villagers remember their home community, defend fellow residents, settle as Wanderers, and can help trusted players shape village identity." },
          { icon: "heart-pulse", title: "Care and recovery", text: "Food, mood, sleep, healing, danger, and protected downed recovery affect villagers in daily life, at work, and in combat." }
        ])}
      `)}
      ${section("Getting Started", `
        <ol class="step-list icon-step-list">
          <li>${icon("message-square-text")}<strong>Meet a villager.</strong><span>Use an empty hand on an adult villager to open the interaction screen. Shift-right-click can bypass it for vanilla interaction when enabled.</span></li>
          <li>${icon("handshake")}<strong>Build a good history.</strong><span>Talk, trade, help, and avoid harming villagers or taking from watched containers while trust is low.</span></li>
          <li>${icon("gift")}<strong>Learn what they value.</strong><span>Gift reactions depend on the item, profession, repetition, personality, and the relationship you already have.</span></li>
          <li>${icon("book-open")}<strong>Follow a quest.</strong><span>Use <kbd>J</kbd> for the Quest Journal and <kbd>K</kbd> for the Quest Tracker. Offers can depend on profession, trade level, skill, reputation, and earlier choices.</span></li>
          <li>${icon("briefcase-business")}<strong>Explore advanced roles when ready.</strong><span>Job Stats explains hiring qualifications, while party and village pages cover recruitment, allegiance, mounts, and community defense.</span></li>
        </ol>
      `)}
      ${section("How The Systems Connect", `
        ${beta13FactList([
          ["Reputation", "Changes dialogue, trade pressure, gifts, services, quest access, pacification, fleeing, and aggression"],
          ["Skills and profession", "Shape special trades, quest gates, job qualification, aptitude, work speed, and transfer capacity"],
          ["Memories, gossip, and mood", "Connect personal history, witnessed events, short-term emotion, dialogue, and work efficiency"],
          ["Party and village allegiance", "Determine leadership, allies, protected targets, community defense, and combat consequences"],
          ["Food, health, and recovery", "Affect natural healing, sleep recovery, urgent care, work readiness, and survival in combat"]
        ])}
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
  function beta13RenderHiring() {
    return `
      ${section("Who Can Be Hired", `
        <p>Hiring is available through an adult villager's interaction screen. A villager cannot be hired while recruited into a party, and starting a hired contract ends ordinary recruitment or follow behavior.</p>
        ${beta13FeatureCards([
          { icon: "badge-check", title: "Profession qualification", text: "A matching vanilla profession automatically qualifies for its role, even when the contributing skills are low." },
          { icon: "brain-circuit", title: "Skill qualification", text: "Otherwise, the role's primary and support skills must total at least 61. Job Stats shows the exact progress." },
          { icon: "route", title: "Universal and restricted roles", text: "Courier is available to every adult. Nitwit work is available only to nitwits. Builder uses a separate construction order." },
          { icon: "lock-keyhole", title: "One active controller", text: "Only the hirer manages the contract, work setup, storage, assigned mount, and ordinary job inventory." }
        ])}
      `)}
      ${section("Price And Duration", `
        <p>Ordinary work is prepaid for 1 to 30 Minecraft days. Extensions cannot push the remaining time above 30 days. The daily wage is based on the server's base price, the villager's relevant skill, and your reputation with that villager, then clamped to the configured minimum and maximum.</p>
        ${beta13FactList([
          ["Default base wage", "12 emeralds per day before skill and reputation adjustments"],
          ["Default allowed range", "4 to 128 emeralds per day"],
          ["Payment timing", "The full selected duration is charged before work begins"],
          ["Role changes", "Allowed during an active ordinary contract without buying a new contract"],
          ["Builder", "Quoted and paid per construction order instead of per day"]
        ])}
        <p>Server settings can change every displayed value. Use the amount shown in the confirmation screen rather than relying on a memorized price.</p>
      `)}
      ${section("Wages, Renewal, And Payment Boxes", `
        <p>Paid time is converted into the villager's wage wallet as the contract runs. Automatic renewal is off until you assign a Payment Box and enable recurring payment.</p>
        <ol class="step-list icon-step-list">
          <li>${icon("box")}<strong>Place and fund a Payment Box.</strong><span>It has 27 slots and accepts the configured contract currency. It is a wage source, not a secure personal lockbox.</span></li>
          <li>${icon("clipboard-list")}<strong>Assign it as Payment storage.</strong><span>Use the Clipboard's Payment mode on the box, then on the hired villager.</span></li>
          <li>${icon("repeat-2")}<strong>Leave recurring payment enabled.</strong><span>When paid time expires, the worker attempts to buy one more day.</span></li>
          <li>${icon("pause")}<strong>Resolve an unpaid warning.</strong><span>If renewal fails, work pauses for a one-day grace period. Fund or restore the assigned box before that grace ends.</span></li>
        </ol>
        <p>Payment boxes are for ordinary hired contracts. Party villager contracts do not use Payment Box renewal.</p>
      `)}
      ${section("Ending A Contract", `
        ${beta13FeatureCards([
          { icon: "coins", title: "Early cancellation", text: "Unused prepaid value is refunded at the server's configured rate. The default is 50 percent." },
          { icon: "package-check", title: "Supplies and output", text: "Removable job items are returned to assigned storage when possible. Villager-owned or protected property stays with the villager." },
          { icon: "clock", title: "Overflow claim", text: "If items cannot be returned cleanly, the former controller has a three-Minecraft-day claim window." },
          { icon: "skull", title: "Death", text: "A villager's death ends the contract and drops the job inventory. A hired contract alone does not grant downed-state protection." }
        ])}
      `)}
      ${section("Things To Know", `
        ${simpleList([
          "Workers wait rather than simulating work while their hirer is offline.",
          "A contract does not keep arbitrary work chunks loaded. The job, stations, targets, and storage must be available and reachable.",
          "Recurring payment buys one day at a time and requires the assigned Payment Box to remain valid.",
          "A Builder order cannot be changed to another role after construction has started.",
          "Hired and recruited-party states are mutually exclusive."
        ])}
      `)}
    `;
  }

  const beta13JobRows = [
    ["Combat", "Weaponsmith, or Guarding + Archery", "Work area or route, usable weapon, and ammunition for ranged weapons", "Guard protects against attacks. Roaming searches natural hostiles. It does not independently hunt players, villagers, golems, or tame animals."],
    ["Hunting", "Fletcher, or Archery + Survival", "Work area or route, bow, crossbow, axe, or sword, plus ranged ammunition", "Can target selected animals, hostiles, or players and collect drops. Enabling player targets creates a real PvP risk."],
    ["Mining", "Toolsmith, or Mining + Masonry", "Work area and pickaxe", "Exposed Ore, Horizontal Excavation, or Vertical Excavation. Excavation can need ladders or floor-patching blocks."],
    ["Logging", "Gathering + Crafting", "Work area and axe", "Fells connected natural trees. Optional replanting, bonemeal, and leaf-clearing behavior depend on supplies and settings."],
    ["Farming", "Farmer, or Farming + Gathering", "Farmer profession, hoe, seeds, and either an area or claimed farmer job site", "Harvests mature crops, replants, and can plant farmland. Tilling is an explicit-area option. Immature crops produce a waiting state."],
    ["Fishing", "Fisherman, or Fishing + Survival", "Work area, fishing rod, accessible open water", "Performs actual casts and retrieves catches. No usable water or rod blocks the job."],
    ["Brewing", "Cleric, or Medicine + Scholarship", "Work area, brewing stand, bottles, ingredients, and blaze powder", "Runs supported potion orders. It will not overwrite incompatible stand contents or brew arbitrary unsupported combinations."],
    ["Builder", "Mason, or Masonry + Crafting", "One-off blueprint order, valid site, quoted payment, and all required materials", "Builds structures from the provided catalog. It is not a daily role and does not capture arbitrary player schematics."],
    ["Animal Handling", "Shepherd or Leatherworker, or Animal Handling + Farming", "Work area, enabled animal types, breeding food, and output space", "Breeds animals and collects products. An optional population cap may cull excess animals and needs a weapon."],
    ["Cook", "Butcher, or Cooking + Gathering", "Work area, furnace or smoker, crafting table for supported foods, fuel, and ingredients", "Prepares supported food outputs and follows the item filter. It is not general-purpose recipe automation."],
    ["Smelter", "Armorer, or Smithing + Mining", "Work area, furnace or blast furnace, fuel, and raw ore materials", "Processes supported ore and raw-material outputs. It does not run every possible furnace recipe."],
    ["Courier", "Every adult", "Usable route plus assigned Input and Output storage", "Patrols the route, collects cargo, delivers it, and returns. Empty starting storage does not stop later route pickups."],
    ["Nitwit", "Nitwit only. Diplomacy + Survival is shown in Job Stats", "No work area", "Produces occasional novelty work reports rather than practical resources."]
  ];

  function beta13RenderJobs() {
    return `
      ${section("Qualification Rules", `
        <p>Each ordinary role has a primary and support skill. A matching profession qualifies automatically. Otherwise, the two scores must total at least 61. Role aptitude weights the primary skill at 70 percent and the support skill at 30 percent.</p>
        ${beta13FeatureCards([
          { icon: "gauge", title: "Work speed", text: "Aptitude scales skill-controlled work speed from 75 to 125 percent." },
          { icon: "package", title: "Transfer capacity", text: "Aptitude scales supported transfer trips from 50 to 150 percent." },
          { icon: "timer", title: "Shared station timers", text: "Skill does not speed the built-in processing timer of furnaces, smokers, blast furnaces, or brewing stands." },
          { icon: "graduation-cap", title: "Practice", text: "Successful measurable work can train the role's two skills at the same 70/30 split when growth is enabled." }
        ])}
      `)}
      ${section("Roles", beta13Table(
        ["Role", "Qualification", "Required setup", "What it does and important rules"],
        beta13JobRows
      ))}
      ${section("Choosing The Right Role", `
        ${simpleList([
          "Use Job Stats before paying: it explains profession overrides, contributing skills, aptitude, work speed, and transfer capacity.",
          "Choose Courier when you need movement between assigned storage rather than resource production.",
          "Use Combat for local defense and Hunting for configured target seeking and loot collection.",
          "Treat Builder as a paid project with a start and finish, not as a permanent workforce slot.",
          "A selectable role may still need more setup. Warnings identify missing areas, stations, tools, fuel, materials, or storage."
        ])}
      `)}
    `;
  }

  const beta13StateRows = [
    ["Working / Moving", "The villager is selecting, approaching, validating, or acting on a target."],
    ["Collecting / Depositing", "Output is being moved through the job inventory or into assigned storage."],
    ["Waiting for materials", "A required input, fuel, crop, animal, recipe item, or valid target is not currently available."],
    ["No work area", "The role needs an assigned area or route and does not have one."],
    ["Missing tool", "The required pickaxe, axe, hoe, fishing rod, weapon, ammunition, ladder, or other role supply is unavailable."],
    ["No storage / Storage full", "The required purpose is unassigned, unreachable, incompatible, or unable to accept more output."],
    ["Inventory full", "The worker cannot safely hold another result or displaced item."],
    ["Failed cooldown", "The last target, route, site, or storage path failed. The worker pauses briefly before trying again."],
    ["Unpaid", "Automatic renewal failed. Work is paused during the one-day payment grace period."],
    ["Idle / Awaiting instruction", "Work is disabled, setup is incomplete, the role has nothing valid to do, or a higher-priority villager behavior has control."]
  ];

  function beta13RenderWorkManagement() {
    return `
      ${section("When Work Runs", `
        <p>Hired work yields to the villager's safety and ordinary control state. A valid contract alone is not enough: Work Enabled must be on, the hirer must be online, and the villager must be able to reach a valid target.</p>
        ${beta13FeatureCards([
          { icon: "moon", title: "Rest and sleep", text: "Workers stop ordinary job navigation while resting or sleeping." },
          { icon: "shield-alert", title: "Threats and combat", text: "Threat memories, active combat, recovery behavior, and the downed state take priority over ordinary work." },
          { icon: "user-round", title: "Follow and party orders", text: "Accepted Follow Me, Stay Here, regroup, movement, gathering, and similar party orders suspend hired work until released." },
          { icon: "wifi-off", title: "Hirer unavailable", text: "Workers wait while the hirer is offline instead of processing jobs in the background." }
        ])}
      `)}
      ${section("Work Areas And Routes", `
        <p>Most roles use a three-dimensional work area. Combat, Hunting, and Courier can use routes. Builder uses its blueprint site, and Nitwit needs no area. Farming can use a claimed farmer job site when no explicit area is set.</p>
        ${beta13FactList([
          ["Minimum horizontal radius", "4 blocks"],
          ["Normal maximum", "Up to 32 blocks, with the usable size affected by the role and worker"],
          ["Hunting", "Uses a larger 64-block search range"],
          ["Route roles", "Combat, Hunting, and Courier"],
          ["New contract", "Starts with an uncommitted draft area, so the worker reports No work area until you assign one"]
        ])}
        <p>Work does not bypass unloaded chunks, solid obstacles, world borders, claim or protection rules, or an unreachable storage face.</p>
      `)}
      ${section("Efficiency", `
        <p>The displayed efficiency starts from the configured base, applies skill work speed, then applies current mood and missing-tool penalties. It is clamped to the server's configured minimum and maximum. The defaults are 25 to 175 percent.</p>
        ${beta13Table(
          ["Condition", "Default effect on displayed efficiency"],
          [
            ["Content, Grateful, Proud, or Hopeful", "+8 percentage points"],
            ["Suspicious or Lonely", "-8 percentage points"],
            ["Angry, Afraid, Stressed, or Grieving", "-15 percentage points"],
            ["Required tool missing", "-20 percentage points"],
            ["Other moods", "No direct mood adjustment"]
          ]
        )}
        <p>Efficiency changes skill-controlled cadence, movement between actions, block work, fishing waits, combat recovery, courier cargo, and supported transfer trips. It does not shorten the vanilla processing time of a furnace or brewing stand.</p>
      `)}
      ${section("Job States And Warnings", beta13Table(["Status", "What it means"], beta13StateRows))}
      ${section("Troubleshooting Order", `
        <ol class="step-list icon-step-list">
          <li>${icon("toggle-right")}<strong>Check Work Enabled and payment.</strong><span>An unpaid or manually paused worker will not start a target.</span></li>
          <li>${icon("map-pin")}<strong>Check the area or route.</strong><span>Make sure it is committed, in the same dimension, loaded, and physically reachable.</span></li>
          <li>${icon("hammer")}<strong>Check the role tool or station.</strong><span>Use the warning and current target readout to identify the exact missing piece.</span></li>
          <li>${icon("package-open")}<strong>Check assigned purposes.</strong><span>Supplies, Output, General, and Payment storage are not interchangeable for every job.</span></li>
          <li>${icon("boxes")}<strong>Check capacity and filters.</strong><span>Full inventories, a restrictive Item Filter, or incompatible station contents can pause otherwise valid work.</span></li>
        </ol>
      `)}
    `;
  }

  function beta13RenderWorkforce() {
    return `
      ${section("Opening The Workforce Screen", `
        <p>Use a Clipboard in the air to open the workforce screen. It summarizes hired villagers, current jobs, work states, warnings, daily wages, days remaining, assigned storage and payment counts, recurring-payment status, work areas, routes, targets, and recent diagnostics.</p>
        <p>Shift-use in the air clears the current Clipboard selection.</p>
      `)}
      ${section("Clipboard Modes", `
        ${beta13Table(
          ["Mode", "Use"],
          [
            ["Assign Storage", "Select ordinary containers, then use the Clipboard on the hired villager."],
            ["Tool", "Assign storage intended to supply tools and equipment."],
            ["Input", "Assign source storage for ingredients, fuel, seeds, breeding food, or courier pickup."],
            ["Output", "Assign destinations for produced items and courier delivery."],
            ["Payment", "Select a Payment Box for ordinary contract renewal."],
            ["Work Area Preview", "Inspect the current area without committing a replacement."],
            ["Set Work Area", "Edit and commit a three-dimensional area."],
            ["Route", "Create an ordered route for Combat, Hunting, or Courier work."]
          ]
        )}
        <p>Ctrl+mouse-wheel cycles storage-purpose modes. A Clipboard can hold up to eight selected container positions at once.</p>
      `)}
      ${section("Work Area Controls", `
        ${beta13Table(
          ["Input", "Action while editing"],
          [
            ["Right-click a block", "Place or recenter the area draft."],
            ["Mouse wheel", "Move the draft."],
            ["Shift + wheel", "Move vertically."],
            ["Ctrl + wheel", "Resize horizontally."],
            ["Alt + wheel", "Strafe the draft sideways."],
            ["Ctrl + Alt + wheel", "Resize vertically."],
            ["Shift + left-click", "Clear the draft."]
          ]
        )}
      `)}
      ${section("Route Controls", `
        ${simpleList([
          "Right-click blocks to add route nodes in order.",
          "Use the first node again to toggle whether the route loops.",
          "Shift-right-click a node to remove it.",
          "Shift-right-click the air to clear the route draft.",
          "Courier requires both a usable route and assigned Input and Output storage."
        ])}
      `)}
      ${section("Reading Warnings", `
        <p>Warnings are grouped by the action needed. Common groups include no work area, no targets, missing tools, missing materials, no storage, storage full, inventory full, unpaid, too far away, unreachable storage, and unreachable building sites.</p>
        ${beta13FeatureCards([
          { icon: "map-pin-off", title: "No work area", text: "Commit an area or a route supported by the selected role." },
          { icon: "package-x", title: "No storage", text: "Assign the required purpose and ensure the worker can stand close enough to interact with it." },
          { icon: "wrench", title: "Missing tools or materials", text: "Supply the role's tool, ammunition, fuel, ingredient, ladder, seed, breeding item, or build block." },
          { icon: "route-off", title: "Unreachable", text: "Clear obstacles, load the area, choose a different container face, or move the site closer." }
        ])}
      `)}
    `;
  }

  function beta13RenderStorageInventory() {
    return `
      ${section("Storage Purposes", `
        ${beta13Table(
          ["Purpose", "How it works"],
          [
            ["General", "Fallback supplies and returned contract items where a more specific purpose is not required."],
            ["Supplies", "Tools, weapons, ammunition, ladders, job equipment, ingredients, fuel, seeds, breeding food, raw ore, and courier pickup sources."],
            ["Output", "Produced items, catches, drops, gathered products, and courier delivery destinations."],
            ["Payment", "Payment Box only. Funds automatic renewal of an ordinary hired contract."]
          ]
        )}
        <p>Assignments are dimension-specific. Workers navigate to containers and may remember a full or failed destination before trying another. Assigning a chest does not make it reachable through walls or unloaded terrain.</p>
        ${beta13FactList([
          ["Use assigned storage for supplies", "On by default. Lets the worker pull tools, fuel, ingredients, and role supplies from assigned purposes"],
          ["Auto-deposit outputs", "On by default. Sends completed output to an available assigned destination"],
          ["When either toggle is off", "The worker relies more heavily on carried job inventory and can stop sooner when inputs or space run out"]
        ])}
      `)}
      ${section("Worker Inventories", `
        <p>Adult villagers can expose separate Personal, Job, and Party inventory views when the relevant relationship allows access. The job inventory includes armor, main hand, off hand, a 27-slot main grid, a 9-slot hotbar, and an Item Filter slot.</p>
        ${beta13FeatureCards([
          { icon: "shield", title: "Live equipment", text: "Assigned armor and held items synchronize with what the villager actually wears and uses." },
          { icon: "backpack", title: "Job supplies", text: "Contract tools, ammunition, fuel, ingredients, and outputs are tracked separately from ordinary villager property." },
          { icon: "lock-keyhole", title: "Protected items", text: "Keepsakes, villager-owned property, and other protected stacks cannot be removed merely because you hired the villager." },
          { icon: "boxes", title: "Party inventory", text: "Recruited villagers use party access and shared-inventory rules instead of ordinary hired ownership." }
        ])}
      `)}
      ${section("Equipment Behavior", `
        ${simpleList([
          "Workers use role-appropriate tools and weapons from their assigned slots or job inventory.",
          "Villagers can pick up and equip stronger dropped weapons or armor when the relevant behavior is enabled, preserving displaced gear when space permits.",
          "Adult villagers automatically ready a carried Totem of Undying in the off hand unless that slot was explicitly assigned.",
          "Party villagers ordered to Raise Shields can keep a totem available while temporarily using a stored shield.",
          "Full inventories can prevent pickup, output creation, or safe equipment replacement and produce a warning."
        ])}
      `)}
      ${section("Item Filters", `
        <p>Place an Item Filter in the filter slot and choose one of two modes through the villager interaction.</p>
        ${beta13FeatureCards([
          { icon: "list-checks", title: "Allowlist", text: "Only listed items are withdrawn. Cooks prepare only listed supported foods." },
          { icon: "list-x", title: "Denylist", text: "Listed items are skipped. Cooks choose other supported foods." }
        ])}
        <p>A filter narrows what a job handles. It does not add new recipes, potion combinations, targets, or storage behavior.</p>
      `)}
      ${section("Contract End And Death", `
        ${simpleList([
          "At contract end, removable job items are deposited into assigned storage when possible.",
          "Undeposited overflow can be claimed by the former controller for three Minecraft days.",
          "Protected villager property remains with the villager.",
          "If the villager dies, the job inventory is dropped and the contract ends.",
          "Payment Boxes are wage containers, not ownership-protected vaults."
        ])}
      `)}
    `;
  }

  function beta13RenderNeeds() {
    return `
      ${section("Food And Natural Regeneration", `
        <p>Villagers track food from 0 to 20 plus saturation. Injured villagers can consume accessible food, and low-health villagers may use golden apples or healing and regeneration potions when available.</p>
        ${beta13FeatureCards([
          { icon: "heart-pulse", title: "Regeneration requires food", text: "With the naturalRegeneration gamerule enabled, food allows gradual health recovery. Saturated villagers recover faster than unsaturated villagers." },
          { icon: "utensils", title: "Food is consumed", text: "Healing creates exhaustion, which drains saturation and then food. A hungry injured villager may warn nearby players." },
          { icon: "swords", title: "Combat changes priorities", text: "Villagers avoid ordinary eating during active combat. They can still use urgent healing items when survival requires them." },
          { icon: "bed", title: "Rest helps", text: "Sleep healing is configurable and can restore health up to its configured cap." }
        ])}
        <p>Food supports regeneration. It is not a separate hired-work efficiency multiplier.</p>
      `)}
      ${section("Recovery Behavior", `
        <p>An injured villager below roughly half health can disengage and focus on recovery until safer. Active danger, dialogue, work, party orders, and navigation can be interrupted while recovery has priority.</p>
        <p>The protected downed state is different: a downed villager is incapacitated and does not use ordinary food or natural recovery until the downed system releases them.</p>
      `)}
      ${section("Mood", `
        <p>Mood reacts to gifts, conversation, attacks, witnessed deaths, raids, danger, weather, fire, successful defense, fleeing, and survival. It gradually settles toward neutral, while persistent social attributes can change the strength or duration of reactions.</p>
        ${beta13Table(
          ["Mood group", "Hired-work effect"],
          [
            ["Content, Grateful, Proud, Hopeful", "Small efficiency bonus"],
            ["Suspicious, Lonely", "Moderate efficiency penalty"],
            ["Angry, Afraid, Stressed, Grieving", "Larger efficiency penalty"],
            ["Other moods", "No direct efficiency change"]
          ]
        )}
      `)}
      ${section("Practical Care", `
        ${simpleList([
          "Keep suitable food and urgent healing items in an inventory the villager can access.",
          "Do not place work areas where the villager remains under constant threat.",
          "Treat repeated negative dialogue, theft, attacks, and village disasters as productivity problems as well as reputation problems.",
          "Check whether a low-efficiency worker is unhappy, missing a required tool, or both.",
          "Remember that ordinary hired status does not protect a villager from lethal damage."
        ])}
      `)}
    `;
  }

  function beta13RenderSkills() {
    return `
      ${section("Reading A Villager", `
        <p>Open an adult villager's interaction screen and use the page switcher to cycle through Skills, Profile, and Job Stats. Hover a Profile point or Job Stats role for a quick readout, then select the role for its full capability details.</p>
        ${beta13FactList([
          ["Skills", "Learned capability that affects trades, quest gates, job qualification, and hired-work performance"],
          ["Profile", "Persistent personality attributes that describe how a villager tends to think and react"],
          ["Job Stats", "The hiring view for role readiness, skill pairings, aptitude, speed, and transfer capacity"]
        ])}
      `)}
      ${section("Profile", `
        <p>Each villager has five persistent Social Attributes from 1 to 100. They appear as a five-point profile chart: the farther a point reaches, the stronger that trait is. The values describe personality rather than a job level or a player-controlled build.</p>
        ${beta13Table(
          ["Attribute", "What it describes", "Examples of behavior it can shape"],
          [
            ["Knowledge", "Understanding, memory, and social awareness", "Suspicion, dialogue, and social awareness"],
            ["Guts", "Courage under pressure and appetite for risk", "Fear, anger, protective behavior, and standing ground"],
            ["Proficiency", "Practical capability, precision, and tool handling", "Stress, pride, combat recovery, and ranged accuracy"],
            ["Kindness", "Patience, generosity, and care for others", "Gratitude, contentment, grief, and social reactions"],
            ["Charm", "Warmth, persuasion, and social grace", "Dialogue and grateful social moments"]
          ]
        )}
        ${beta13FactList([
          ["Poor", "1 to 19"],
          ["Modest", "20 to 39"],
          ["Average", "40 to 59"],
          ["Strong", "60 to 79"],
          ["Exceptional", "80 to 100"]
        ])}
        <p>Profile effects are configurable. When Social Attribute behavior or one of its sub-options is disabled, the profile can still be viewed but does not apply that behavioral effect.</p>
      `)}
      ${section("Skills", `
        <p>Every villager has persistent skill scores from 1 to 100. Profession influences the starting profile, but the values remain individual and can grow through configured trade and hired-work systems.</p>
        ${pillList([
          "Farming", "Fishing", "Smithing", "Crafting", "Trading", "Medicine", "Archery", "Guarding", "Cooking",
          "Animal Handling", "Cartography", "Scholarship", "Gathering", "Masonry", "Mining", "Leatherworking", "Diplomacy", "Survival"
        ])}
        ${beta13FactList([
          ["Novice", "1 to 19"],
          ["Apprentice", "20 to 39"],
          ["Skilled", "40 to 59"],
          ["Expert", "60 to 79"],
          ["Master", "80 to 100"]
        ])}
      `)}
      ${section("Job Stats", `
        <p>Job Stats is the practical hiring view. Each role shows a ready or locked status and a bar for its two relevant skills. The bar is the unweighted skill total toward 61; a matching profession can make a role ready before that total is reached.</p>
        ${beta13FactList([
          ["Ordinary qualification", "Primary skill + support skill must total at least 61; exactly 60 remains locked"],
          ["Profession qualification", "A matching vanilla profession automatically qualifies for its canonical role"],
          ["Universal and restricted roles", "Courier is ready for every adult; Nitwit work is available only to nitwits"],
          ["Aptitude", "Rounded 70 percent primary skill + 30 percent support skill"],
          ["Skill work speed", "75 to 125 percent from aptitude; it changes task cadence, not furnace, smoker, blast-furnace, or brewing timers"],
          ["Transfer capacity", "50 to 150 percent from aptitude for supported collection trips; Couriers carry 64 to 128 items per assigned supply container"]
        ])}
      `)}
      ${section("Role Skill Pairs", beta13Table(
        ["Role", "Primary skill", "Support skill"],
        [
          ["Combat", "Guarding", "Archery"],
          ["Hunting", "Archery", "Survival"],
          ["Mining", "Mining", "Masonry"],
          ["Logging", "Gathering", "Crafting"],
          ["Farming", "Farming", "Gathering"],
          ["Fishing", "Fishing", "Survival"],
          ["Brewing", "Medicine", "Scholarship"],
          ["Craftsman", "Crafting", "Gathering"],
          ["Builder", "Masonry", "Crafting"],
          ["Animal Handling", "Animal Handling", "Farming"],
          ["Cook", "Cooking", "Gathering"],
          ["Smelter", "Smithing", "Mining"],
          ["Courier", "Gathering", "Survival"],
          ["Nitwit", "Diplomacy", "Survival"]
        ]
      ))}
      ${section("Growth", `
        ${simpleList([
          "Successful hired actions can train the role's primary and support skills at a 70/30 split when hired-work growth is enabled.",
          "Larger measurable jobs train more than trivial actions, while repeated equivalent work has diminishing returns.",
          "Trading and villager trade-level progression can also grow relevant skills when the server enables those options.",
          "Skill affects job throughput and skill-generated trades, and some quests use hidden skill minimums.",
          "Station processing timers remain unchanged even when the worker's transfer and action cadence improve."
        ])}
      `)}
    `;
  }

  function beta13RenderBuilding() {
    return `
      ${section("Builder Is Not A Daily Job", `
        <p>Builder appears with the worker roles because it uses Masonry and Crafting, but it is purchased as a single construction project. The villager must qualify, the structure must be available in the build catalog, and the proposed site must pass the server's checks.</p>
      `)}
      ${section("Construction Flow", `
        <ol class="step-list icon-step-list">
          <li>${icon("landmark")}<strong>Choose a supported structure.</strong><span>The interaction screen creates a pending Construction Blueprint with a size, material summary, and quoted emerald cost.</span></li>
          <li>${icon("move-3d")}<strong>Place and rotate the preview.</strong><span>Move the blueprint to a valid nearby site. The optional Placement Lock key starts unbound.</span></li>
          <li>${icon("package-search")}<strong>Supply every required block.</strong><span>The builder uses carried or assigned materials and reports exactly what is missing.</span></li>
          <li>${icon("hammer")}<strong>Let the builder travel and place.</strong><span>The villager validates each target and respects world borders, obstacles, protection rules, and reachable paths.</span></li>
          <li>${icon("badge-check")}<strong>Finish the project.</strong><span>The order ends when the planned blocks are complete or when the player cancels under the applicable refund rules.</span></li>
        </ol>
      `)}
      ${section("Default Limits And Price", `
        ${beta13FactList([
          ["Maximum structure size", "4096 planned blocks"],
          ["Maximum site distance", "28 blocks from the builder"],
          ["Material-storage search", "32 blocks"],
          ["Base builder fee", "8 emeralds"],
          ["Additional fee", "3 emeralds per 64 planned blocks"],
          ["Soft-block replacement", "Enabled by default"]
        ])}
        <p>These are server defaults. The final quote shown in game is authoritative.</p>
      `)}
      ${section("Escrow And Cancellation", `
        <p>The fee is held while the project is pending. Cancelling before any block is placed returns the full builder payment. Once construction has placed blocks, the payment is released and the villager cannot be switched to another role until the active build is resolved.</p>
      `)}
      ${section("Things To Know", `
        ${simpleList([
          "Only structures present in the supported catalog can be ordered.",
          "The blueprint does not capture arbitrary player builds or import general schematics.",
          "All required blocks must be supplied. The builder does not create free materials.",
          "Protected blocks, world borders, obstructing entities, invalid foundations, and unreachable positions can block placement.",
          "The project needs the builder and site to remain loaded and navigable."
        ])}
      `)}
    `;
  }

  function beta13RenderParties() {
    return `
      ${section("Recruitment Contracts", `
        <p>A recruited villager belongs to a party rather than to the hired-work system. A villager cannot be recruited and hired at the same time.</p>
        ${beta13FactList([
          ["Party player limit", "4 players"],
          ["Recruited villager limit", "4 villagers"],
          ["Villager contract", "32 emeralds for one Minecraft day"],
          ["Renewal", "Manual extension. Payment Boxes do not renew party contracts"],
          ["Party invitation", "Player invitations expire after 60 seconds"]
        ])}
        <p>Only eligible adult villagers can be recruited. Active party control suppresses the normal trading screen until the controlled state ends.</p>
      `)}
      ${section("Movement And Quick Commands", `
        <p>The party leader opens the quick-command wheel with <kbd>Left Alt</kbd> by default. Each recruited villager can be included in or excluded from quick commands.</p>
        ${pillList([
          "Attack", "Move To", "Stay Here", "Regroup", "Stand Guard", "Range", "Melee", "Heal",
          "Pick Up Drops", "Loot Containers", "Ride Mount", "Dismount Mount"
        ])}
        <p>Drop gathering searches nearby ground items, while Loot Containers uses the selected nearby area. Movement, regrouping, gathering, and looting orders can suppress ordinary combat targeting until the order is complete or replaced.</p>
      `)}
      ${section("Combat Policies", `
        ${beta13Table(
          ["Setting", "Choices"],
          [
            ["Combat behavior", "Kill On Sight, Attack With Party, or Self Defense"],
            ["Allowed targets", "Animals, Hostiles, Players, Villagers and golems, Parties, All, or None"],
            ["Drop collection", "Off, Slain Entities, or All Drops"],
            ["Weapon preference", "Ranged or Melee quick order"],
            ["Guarding", "Raise or lower shields through Stand Guard"]
          ]
        )}
        <p>Global party settings can be overridden for individual villagers. Manual Attack orders still respect allegiance, party alliances, and other friendly-target protections.</p>
      `)}
      ${section("Inventory, Alliances, And Leadership", `
        ${simpleList([
          "Party members can share authorized villager inventories when the party's shared-inventory option is enabled.",
          "The leader controls recruitment, villager settings, quick-command participation, and party mount mode.",
          "Players can be invited, leadership can change, and two parties can form a mutual alliance.",
          "Allied players and recruited villagers do not intentionally target or retaliate against one another.",
          "A recruited villager from a village being attacked by a Player Raid leaves the raiding party and rejoins that village's defense."
        ])}
      `)}
      ${section("Things To Know", `
        ${simpleList([
          "Party villager contracts last one day at a time and have no Payment Box auto-renewal.",
          "Quick commands are leader-only and affect only villagers that have participation enabled.",
          "Gather and loot orders need loaded, reachable targets and available inventory space.",
          "A party contract does not turn the villager into a hired resource worker.",
          "Downed protection applies to active party villagers only when the server enables that protection rule."
        ])}
      `)}
    `;
  }

  function beta13RenderMounts() {
    return `
      ${section("Assigning A Mount", `
        <p>An active hirer or party leader can assign an eligible adult horse, donkey, mule, llama, or camel to an adult controlled villager. Start Assign Mount from the villager screen, then select the mount within 30 seconds. An eligible leashed mount can also be assigned directly, returning the lead.</p>
        ${beta13FeatureCards([
          { icon: "badge-check", title: "Authority", text: "The active hirer manages a worker's mount. The party leader manages a recruited villager's mount." },
          { icon: "link", title: "Persistent pairing", text: "A villager keeps one durable mount assignment until it is cleared or either entity is permanently removed." },
          { icon: "users", title: "Seat availability", text: "The mount must have room and cannot contain unrelated passengers when it is assigned." },
          { icon: "shield-check", title: "Authorized riding", text: "The hirer or a valid party member can take the driver seat. Unrelated players cannot bypass the assignment." }
        ])}
      `)}
      ${section("Travel Behavior", `
        ${simpleList([
          "Hired workers have a Mounted Travel toggle for long job journeys.",
          "Parties have a shared mount mode plus Ride Mount and Dismount Mount quick commands.",
          "Villagers dismount near precise work, construction, storage, and other actions that need block-level movement.",
          "An assigned villager retries when the mount is temporarily unloaded or occupied and parks an idle mount near its last anchor.",
          "Close to a destination, the mount travels normally. Farther away, it uses catch-up movement."
        ])}
      `)}
      ${section("Ride On Compatibility", `
        <p>Ride On is optional. Villager Retaliation provides mount assignment, single-villager riding, mounted travel, parking, retries, and ordinary player takeover without it.</p>
        ${beta13FactList([
          ["Without Ride On", "One assigned villager rides and controls the mount through Minecraft's normal passenger behavior"],
          ["With Ride On 1.0.0-pre-release.3 or newer", "A supported assigned horse-family mount can carry two villagers, or an authorized player and villager can share the mount"],
          ["Ride On is required for", "The second seat, rear-passenger behavior, and coordinated driver-seat transitions"],
          ["Villager Retaliation still controls", "Mount assignment, riding permission, travel orders, combat allegiance, parking, and automatic remount attempts"]
        ])}
      `)}
      ${section("Things To Know", `
        ${simpleList([
          "Mount assignment is for controlled adult villagers, not every villager in the world.",
          "A mount must be structurally eligible, available, and have an open supported seat.",
          "Precise jobs still dismount. The mount speeds travel rather than performing the block interaction.",
          "An unloaded or occupied mount cannot move until it becomes available. The villager keeps retrying.",
          "Two-villager or player-and-villager seating on supported horse-family mounts requires Ride On 1.0.0-pre-release.3 or newer."
        ])}
      `)}
    `;
  }

  function beta13RenderVillages() {
    return `
      ${section("Tracked Villages", `
        <p>Villages have durable identities, generated names, resident rosters, and footprints built from village points of interest, structure pieces, and connected village terrain. A villager's home is not simply whichever village is closest at this instant.</p>
        ${beta13FeatureCards([
          { icon: "house", title: "Residents", text: "Villagers born or created inside a tracked footprint join that village." },
          { icon: "map-pin", title: "Wanderers", text: "Villagers created outside a tracked village begin without a home." },
          { icon: "clock", title: "Settlement", text: "A Wanderer who remains continuously in a village for one Minecraft day settles there. Active party villagers do not settle automatically." },
          { icon: "shield", title: "Allegiance", text: "Same-party and same-village relationships prevent friendly targeting and shape who rallies during combat." }
        ])}
      `)}
      ${section("Asking About Home", `
        <p>The interaction screen's Home topic lets you ask where a villager belongs and whether the current village is their home. A player who is Revered or Royalty with that villager can ask them to adopt the current active village. The same request must be confirmed a second time.</p>
        <p>A recruited party villager accepts that order only from a qualifying player in the same party.</p>
      `)}
      ${section("Naming A Village", `
        <ol class="step-list icon-step-list">
          <li>${icon("flag")}<strong>Hold a banner.</strong><span>The banner is used as the naming interaction item and is not consumed.</span></li>
          <li>${icon("bell")}<strong>Right-click a bell inside the tracked village.</strong><span>The naming screen shows whether the trust requirement is met.</span></li>
          <li>${icon("heart-handshake")}<strong>Earn community trust.</strong><span>You need Revered or Royalty standing with at least half of the village's tracked living adult residents.</span></li>
          <li>${icon("pencil")}<strong>Choose a unique name.</strong><span>Names must be 1-32 characters and cannot contain formatting or control codes.</span></li>
        </ol>
      `)}
      ${section("Consequences And Limits", `
        ${simpleList([
          "A villager keeps their established home while traveling unless an allowed reassignment or lifecycle rule changes it.",
          "Village footprints can merge or change as the tracked village evidence changes. This is not a player-painted claim system.",
          "Outside villagers are neutral Wanderers until they settle or receive a trusted reassignment.",
          "Community combat uses allegiance in addition to personal reputation, party membership, and direct retaliation history.",
          "The optional village-bounds overlay is an administrator display and is not required for ordinary play."
        ])}
      `)}
    `;
  }

  function beta13RenderDowned() {
    return `
      ${section("Who Can Be Downed", `
        <p>The downed state is a special death-protection rule, not a universal villager rule. A villager is protected only when at least one active rule applies.</p>
        ${beta13FeatureCards([
          { icon: "users", title: "Active party villagers", text: "Recruited villagers can use the downed state when the server's party protection setting is enabled." },
          { icon: "scroll-text", title: "Quest or scene protection", text: "A current quest giver or protected scripted-scene villager can receive non-lethal handling from that content." },
          { icon: "shield-check", title: "Essential villagers", text: "Villagers intentionally marked essential are protected." },
          { icon: "briefcase-business", title: "Hired workers", text: "Hiring by itself does not grant protection. A hired villager still needs another qualifying protection rule." }
        ])}
      `)}
      ${section("What Happens", `
        <p>Ordinary lethal damage leaves a protected villager at 1 health and incapacitates them. They dismount, stop AI, navigation, loot pickup, work, combat, and normal interaction, and use a downed pose until recovery.</p>
        ${beta13FactList([
          ["Default minimum downed time", "160 ticks, or 8 seconds"],
          ["Default threat radius", "16 blocks"],
          ["Default quiet period", "60 ticks, or 3 seconds without a qualifying nearby threat"],
          ["Default recovery health", "25 percent of maximum health"],
          ["Normal food recovery", "Suspended while downed"]
        ])}
      `)}
      ${section("Recovery", `
        <p>Both conditions must be satisfied: the minimum timer must finish, and the area must remain quiet for the configured period. Continued danger resets the quiet countdown. After recovery, the villager resumes ordinary behavior with the configured health amount.</p>
      `)}
      ${section("Second Wind Compatibility", `
        <p>Second Wind is optional. Villager Retaliation provides the protected downed state and automatic recovery without it.</p>
        ${beta13FactList([
          ["Without Second Wind", "Protected villagers remain downed until Villager Retaliation's recovery timer and safety conditions are satisfied"],
          ["With Second Wind", "A player can channel an early revive, and downed villagers can use Second Wind's crawl presentation"],
          ["Second Wind is required for", "Player-channeled early revival and the crawl presentation"],
          ["Villager Retaliation still controls", "Who is protected, when a villager becomes downed, automatic recovery, recovery health, and lethal bypass damage"]
        ])}
      `)}
      ${section("Damage That Still Kills", `
        <p>Void or out-of-world damage, generic kill-style damage, and other damage that bypasses invulnerability can bypass the downed protection. Removing the active protection before a later lethal hit also removes the safety net.</p>
        ${simpleList([
          "Do not assume every village resident is immortal.",
          "Do not assume an ordinary hired contract is protection.",
          "Downed villagers cannot be traded with, managed, ordered, or used as active quest actors until they recover.",
          "Recovery can take longer than the minimum when enemies remain nearby.",
          "Server owners can disable the system or change all timing and health defaults."
        ])}
      `)}
    `;
  }

  function beta13RenderReputation() {
    return `
      ${section("Reputation Is Personal", `
        <p>Reputation is stored for each player-villager relationship. Two villagers in the same village can feel very differently about you. Direct events, witnesses, gossip, persistent history, and configurable decay all contribute, but a village does not replace every personal score with one shared number.</p>
      `)}
      ${section("Default Reputation Tiers", `
        <p>Servers can change these thresholds. The values below are the defaults.</p>
        ${beta13Table(
          ["Tier", "Default threshold", "Effect"],
          DATA.reputation.map((tier) => [tier.level, String(tier.threshold), tier.effect])
        )}
      `)}
      ${section("Memories, Gossip, And Mood", `
        ${beta13FeatureCards([
          { icon: "history", title: "Personal history", text: "Trades, gifts, attacks, healing, quests, dialogue, theft, and village events can affect the relationship that experienced them." },
          { icon: "messages-square", title: "Witnesses and gossip", text: "Nearby villagers can learn about public harm or help, with line of sight and spread controlled separately by server settings." },
          { icon: "cloud-sun", title: "Mood", text: "Mood is a shorter-term emotional state. It can change dialogue and work efficiency without replacing persistent reputation." },
          { icon: "landmark", title: "Village allegiance", text: "Home-village loyalty determines community defense and friendly protections. It is separate from how much one villager trusts one player." }
        ])}
      `)}
      ${section("Consequences", `
        ${simpleList([
          "Positive standing can improve dialogue, pricing, gifts, quest access, home reassignment, village naming eligibility, and high-reputation interactions.",
          "Suspicious or worse standing can cool dialogue, worsen trade pressure, block service, make pacification harder, and contribute to aggression.",
          "Despised begins at -400 by default. Attack-on-sight behavior can also interrupt hired work when enabled.",
          "Feared defaults to -1000 and is the lowest tier.",
          "Reputation can slowly drift toward neutral when server decay is enabled, but severe consequences are not erased by the short combat-anger timer."
        ])}
      `)}
      ${section("Pacification And Recovery", `
        <p>Hostile villagers can accept datapack-defined pacification payments only while their tier and server rules still allow it.</p>
        <div class="card-grid two">${DATA.pacification.map((payment) => `<div class="feature-card no-icon"><div class="card-copy"><strong>${escapeHtml(payment.item)}</strong><span>${escapeHtml(payment.min)} to ${escapeHtml(payment.max)} ${escapeHtml(payment.name)}</span></div></div>`).join("")}</div>
        ${simpleList([
          "Trade with villagers who still permit it.",
          "Give liked or loved gifts without repeatedly farming the same stack.",
          "Heal or save villagers and defend the village from real threats.",
          "Complete quests and honor their turn-in requirements.",
          "Stop opening watched property and allow suspicion and configured decay to cool."
        ])}
      `)}
    `;
  }

  function beta13RenderDialogue() {
    return `
      ${section("Opening The Interaction Screen", `
        <p>Use an empty main hand on a nearby villager. By default, shift-right-click bypasses the custom screen for a normal vanilla interaction. The server can disable the screen, change its range, or change the bypass behavior.</p>
      `)}
      ${section("What The Menu Can Contain", `
        ${beta13FeatureCards([
          { icon: "message-square-text", title: "Conversation", text: "Greetings, questions, jokes, stories, relationships, family, local events, home, and reputation-aware responses." },
          { icon: "gift", title: "Gifts and trade", text: "Give evaluated gifts, request eligible Special Orders, or open trading when the villager's current state allows it." },
          { icon: "scroll-text", title: "Quests", text: "Offers, reminders, choices, turn-ins, and story scenes appear when they are relevant to your current quest." },
          { icon: "briefcase-business", title: "Work and party actions", text: "Hiring, job management, inventories, construction, recruitment, party settings, mounts, and home choices appear when you lead or employ the villager." }
        ])}
      `)}
      ${section("Why An Option May Be Missing", `
        ${simpleList([
          "The villager has the wrong profession or trade level.",
          "Your reputation, an earlier quest choice, a cooldown, or one of the villager's skills does not yet meet the requirement.",
          "The villager is hired, recruited, sleeping, trading, in combat, recovering, downed, too far away, or controlled by another player.",
          "The server has disabled that feature or uses different custom content.",
          "You recently asked the same question and must wait before asking again."
        ])}
      `)}
      ${section("Quests And Tracking", `
        <p>Press <kbd>J</kbd> to review accepted quests and <kbd>K</kbd> to open the Tracker. Active quests can show objectives, coordinates, HUD notices, highlighted items, and story scenes. What appears depends on the villager, your progress, and earlier choices.</p>
      `)}
      ${section("Memory And Consequences", `
        <p>Dialogue can react to personal reputation, profession, family, social attributes, mood, recent village events, first meetings, time since the last meeting, weather, equipment, nearby danger, stories, and quest history. These systems do not all mean the same thing: reputation is persistent standing, gossip spreads witnessed information, mood is temporary, and village allegiance identifies community membership.</p>
      `)}
      ${section("Things To Know", `
        ${simpleList([
          "The screen is proximity-based and does not control unloaded villagers.",
          "A despised or actively hostile villager can refuse conversation or trade.",
          "Controlled states can intentionally suppress the vanilla trading menu.",
          "A downed villager is incapacitated and cannot open normal dialogue.",
        ])}
      `)}
    `;
  }

  function beta13RenderQuests() {
    return `
      ${section("Finding A Quest Giver", `
        <p>Each quest has its own giver requirements. A quest may need a particular profession, trade level, villager skill, reputation level, completed quest, earlier choice, or cooldown. If an offer is missing, compare the villager with the requirements on that quest's page.</p>
        ${beta13FeatureCards([
          { icon: "user-round-check", title: "Match the giver", text: "Check the required profession, trade level, and skill. A villager in combat, asleep, or recovering may not offer quests." },
          { icon: "list-checks", title: "Check your progress", text: "Some quests require enough reputation, an earlier quest, a specific story choice, or time for a cooldown to end." },
          { icon: "map-pin", title: "Track the objectives", text: "Press J for the Journal and K for the Tracker. Live progress appears after you accept the quest." },
          { icon: "package-check", title: "Return prepared", text: "Finish every objective and bring any requested proof or consumable items back to the required quest giver." }
        ])}
      `)}
      ${section("What Can Change A Quest", `
        ${simpleList([
          "A quest can appear in this guide before you have met a villager who can offer it.",
          "Story choices can close one route and open another for that playthrough.",
          "Some objectives require a particular structure, dimension, character, or active encounter.",
          "Multiplayer servers can add or replace quests, so their available stories may differ from this built-in guide.",
        ])}
      `)}
      ${beta13OriginalQuestRender()}
    `;
  }

  function beta13RenderCombat() {
    return `
      ${section("Natural Retaliation", `
        <p>Hitting a villager can anger the victim. Killing an adult villager in public can also rally nearby adult witnesses when the configured visibility rules are met. The short combat anger timer can expire while the longer personal reputation damage remains.</p>
        ${simpleList([
          "Hostile villagers and wandering traders can refuse service while hostile.",
          "Pacification is available only when the current reputation tier and server rules allow it.",
          "Placed lava, flint and steel, and fire charges can attribute nearby fire or lava harm to the responsible player during the configured window.",
          "Same-party, allied-party, and same-village protections prevent many friendly combat mistakes.",
          "Babies remain noncombatants. Nitwits generally flee unless they have a usable combat reason and equipment."
        ])}
      `)}
      ${section("Profession Combat And Equipment", `
        <p>Adult villagers can use melee weapons, bows, crossbows, tridents, shields, armor, potions, and role-specific behavior. Fletchers favor ranged attacks, armorers and weaponsmiths are stronger defenders, and clerics can heal or support allies when configured.</p>
        ${simpleList([
          "Villagers can scavenge stronger dropped weapons and armor while threatened when that behavior is enabled.",
          "Displaced equipment is kept when inventory space allows instead of being silently duplicated.",
          "Adult villagers automatically prepare carried Totems of Undying unless the off hand was explicitly assigned.",
          "Ranged combat needs usable ammunition and can pause or change weapon preference when none is available.",
          "Villagers can defend against hostile mobs according to the server's targeting, retaliation, and stand-ground settings."
        ])}
      `)}
      ${section("Hired Combat And Hunting", `
        ${beta13FeatureCards([
          { icon: "shield", title: "Combat: Guard", text: "Protects the work area or route and answers attacks. It does not turn the villager into an unrestricted player hunter." },
          { icon: "route", title: "Combat: Roaming", text: "Patrols and scans for natural hostile targets within the assigned setup." },
          { icon: "crosshair", title: "Hunting", text: "Can seek enabled animals, hostiles, or players, then collect drops and deposit output." },
          { icon: "triangle-alert", title: "Player target warning", text: "Enabling players for a hunter can target non-hirer players. Treat it as a server PvP setting, not a harmless filter." }
        ])}
      `)}
      ${section("Party Combat", `
        <p>Recruited villagers use party combat behavior, allowed-target filters, per-villager overrides, weapon preferences, quick Attack orders, Stand Guard, and Heal. Party alliances and allegiance rules still limit targets, and movement or loot-gathering orders can temporarily suppress ordinary target acquisition.</p>
      `)}
      ${section("Recovery And Lethality", `
        <p>Food, sleep healing, retreat behavior, and urgent healing items help living villagers recover. Protected villagers can enter the downed state, but ordinary villagers and hired workers without a protection rule can still die. Void and kill-style damage can bypass protection.</p>
      `)}
    `;
  }

  function beta13RenderPlayerRaids() {
    return `
      ${section("Declaring A Player Raid", `
        <p>Wear an ominous banner on your helmet inside a tracked village and begin using a goat horn. The initiating player and their current party are snapshotted as the raiders. A Player Raid cannot overlap a vanilla raid, another conflicting Player Raid, or the village's active cooldown.</p>
      `)}
      ${section("Party Members From The Target Village", `
        <p>Recruited villagers whose home is the target village permanently leave the raiding party and confront the initiator before the defense begins. They rejoin their neighbors and count among the defenders. Target-village residents also receive severe reputation consequences toward every snapshotted raider player.</p>
      `)}
      ${section("Preparation And Defense", `
        ${simpleList([
          "The default preparation phase lasts 10 seconds.",
          "Adult non-nitwit defenders fill empty equipment slots with difficulty-weighted militia gear.",
          "Babies and nitwits hide while capable adults engage the raiders.",
          "Aligned iron golems arrive in budgeted reinforcement waves as defender thresholds are crossed.",
          "Raid equipment and surviving summoned golems remain after the outcome."
        ])}
      `)}
      ${section("Finding Remaining Defenders", `
        <p>During an active raid, a banner-helmet raider can reuse a goat horn to reveal nearby tracked defenders. The reveal is for the current raid roster. Visitors and villagers born after the snapshot do not become new objective members.</p>
      `)}
      ${section("Mercy Stage", `
        <p>After the armed defenders are resolved, surviving snapshotted babies and nitwits enter a mercy stage. Raiders can right-click each survivor to spare them, leave them for a manual kill, or leave the choice unresolved. Spared villagers survive with exactly -1000 reputation toward every raider player.</p>
        <p>Nearby unresolved survivors may plead for their lives. The raid is not cleanly settled until its required remaining outcomes are resolved.</p>
      `)}
      ${section("Winning, Losing, And Cooldown", `
        <p>Raiders win after the snapshotted defender objective and mercy outcomes are resolved. The village wins when no living, non-spectator raider remains inside the village footprint for the configured abandonment time. The default is 30 seconds.</p>
        <p>A completed raid leaves the village on the configured cooldown after either outcome. The default is three Minecraft days. Recruited party villagers can react to victory or loss after the result.</p>
      `)}
      ${section("Things To Know", `
        ${simpleList([
          "Only the start snapshot counts. Later births and ordinary visitors do not join the objective.",
          "Iron golems defend but do not count as remaining villagers.",
          "The raid cannot start where another raid or participant conflict already owns the village.",
          "The defender reveal requires the active raider banner-and-horn interaction.",
        ])}
      `)}
    `;
  }

  function beta13RenderSettings() {
    return `
      ${section("Default Player Controls", `
        <div class="table-wrap"><table><thead><tr><th>Action</th><th>Default</th><th>Notes</th></tr></thead><tbody>
          <tr><td>Quest Journal</td><td><kbd>J</kbd></td><td>Browse active and known quest information.</td></tr>
          <tr><td>Quest Tracker</td><td><kbd>K</kbd></td><td>Show active objective progress and navigation.</td></tr>
          <tr><td>Party Quick Commands</td><td><kbd>Left Alt</kbd></td><td>Leader command wheel for participating recruited villagers.</td></tr>
          <tr><td>Blueprint Placement Lock</td><td>Unbound</td><td>Optional construction-preview control.</td></tr>
          <tr><td>Toggle Villager Name Tags</td><td>Unbound</td><td>Optional client display toggle.</td></tr>
          <tr><td>Bypass interaction screen</td><td><kbd>Shift</kbd> + right-click</td><td>Enabled by default and configurable.</td></tr>
        </tbody></table></div>
      `)}
      ${section("Config Access", `
        <p>Open Mod Menu and choose Villager Retaliation to adjust available settings. In multiplayer, the server controls gameplay rules while client display and keybind preferences remain local.</p>
        ${beta13FeatureCards([
          { icon: "server", title: "Server-controlled gameplay", text: "Contracts, wages, work limits, reputation thresholds, combat, raids, downed recovery, gifts, quests, and social rules follow the server in multiplayer." },
          { icon: "monitor", title: "Client display and controls", text: "Keybinds, name tags, HUD placement, text effects, camera presentation, and similar visual preferences are local controls where supported." },
          { icon: "sliders-horizontal", title: "Defaults can differ", text: "A server can change costs, durations, radii, efficiency bounds, thresholds, recovery timing, and feature toggles. In-game values are authoritative." },
          { icon: "puzzle", title: "Datapacks can differ", text: "Quests, dialogue, gifts, pacification, notifications, structures, recipes, and other server-provided data may be replaced or extended by the server." }
        ])}
      `)}
      ${section("Optional Mod Compatibility", `
        <p>Second Wind and Ride On are optional companion mods. Villager Retaliation runs without them, but the features listed below need the matching mod. In multiplayer, use the same companion-mod setup on the server and participating clients.</p>
        ${beta13Table(
          ["Companion mod", "Available without it", "Features that require it"],
          [
            ["Second Wind", "Protected downed villagers and automatic recovery", "Player-channeled early revival and the crawl downed presentation"],
            ["Ride On 1.0.0-pre-release.3 or newer", "Assigned mounts, one villager per mount, mounted travel, parking, retries, and ordinary player takeover", "Two riders on a supported horse-family mount, rear-passenger behavior, and coordinated seat transitions"]
          ]
        )}
      `)}
      ${section("Important World Rule", `
        <p>The vanilla <code>naturalRegeneration</code> gamerule also controls villager food-based natural healing. Disabling it prevents that normal regeneration path even when a villager has food.</p>
      `)}
      ${section("Settings Categories", `
        ${pillList([
          "General", "Dialogue", "Notifications", "Gifts", "Social", "Balance", "Retaliation", "Reputation",
          "Player Raids", "Trade", "Combat", "Wandering Trader", "Quest"
        ])}
      `)}
    `;
  }

  const beta13Pages = [
    beta13ExistingPage("home", {
      title: "Overview",
      group: "Start Here",
      description: "How relationships, dialogue, quests, gifts, village life, retaliation, skills, work, parties, and recovery fit together.",
      render: beta13RenderHome
    }),
    beta13NewPage(
      "hiring",
      "Hiring And Contracts",
      "Hired Workers",
      "hand-coins",
      "Hire villagers, understand daily wages and 1-30 day contracts, use Payment Boxes, renew work, cancel, and recover items.",
      beta13RenderHiring
    ),
    beta13NewPage(
      "workforce",
      "Clipboard And Workforce",
      "Hired Workers",
      "clipboard-list",
      "Use the Clipboard dashboard, assign storage purposes, edit work areas and routes, and resolve worker warnings.",
      beta13RenderWorkforce
    ),
    beta13ExistingPage("quests", {
      title: "Quest Walkthroughs",
      group: "Start Here",
      description: "Find eligible quest givers, follow objectives and story choices, prepare turn-ins, and understand what each route unlocks.",
      render: beta13RenderQuests
    }),
    beta13NewPage(
      "jobs",
      "Jobs And Professions",
      "Hired Workers",
      "briefcase-business",
      "All thirteen worker roles, qualification rules, required tools and stations, outputs, modes, and important rules.",
      beta13RenderJobs
    ),
    beta13NewPage(
      "work-management",
      "Schedules, States, And Efficiency",
      "Hired Workers",
      "gauge",
      "When work runs or pauses, work areas and routes, job states, idle and blocked warnings, mood, skills, and efficiency.",
      beta13RenderWorkManagement
    ),
    beta13NewPage(
      "storage-inventory",
      "Storage, Inventory, And Equipment",
      "Hired Workers",
      "package-open",
      "Assigned Supplies, Output, General, and Payment storage, plus job inventories, protected items, filters, supplies, and equipment.",
      beta13RenderStorageInventory
    ),
    beta13NewPage(
      "villager-needs",
      "Food, Mood, And Recovery",
      "Villager Life",
      "heart-pulse",
      "Villager food, natural regeneration, sleep healing, urgent recovery, mood effects, and care during work and combat.",
      beta13RenderNeeds
    ),
    beta13NewPage(
      "villager-skills",
      "Skills, Attributes, And Job Stats",
      "Villager Life",
      "brain-circuit",
      "All 18 skills, five social attributes, role qualification, aptitude, work speed, transfer capacity, trades, quests, and growth.",
      beta13RenderSkills
    ),
    beta13NewPage(
      "building",
      "Builder And Blueprints",
      "Hired Workers",
      "hammer",
      "Order one-off construction, place a blueprint, supply materials, understand escrow, defaults, site checks, and project rules.",
      beta13RenderBuilding
    ),
    beta13NewPage(
      "parties",
      "Parties And Recruits",
      "Parties",
      "users",
      "Recruit paid party villagers, use follow and quick commands, configure combat and drop collection, share inventory, and form alliances.",
      beta13RenderParties
    ),
    beta13NewPage(
      "mounts",
      "Assigned Mounts",
      "Parties",
      "route",
      "Assign mounts, control mounted travel, parking, and retries, and understand which two-seat features require Ride On.",
      beta13RenderMounts
    ),
    beta13NewPage(
      "villages",
      "Villages And Allegiance",
      "Villager Life",
      "landmark",
      "Tracked village identity, residents, Wanderers, home reassignment, banner-and-bell naming, and allegiance combat consequences.",
      beta13RenderVillages
    ),
    beta13ExistingPage("combat", {
      title: "Retaliation And Combat",
      group: "Combat And Consequences",
      description: "Natural retaliation, profession equipment, hostile-mob defense, hired guards and hunters, party targeting, recovery, and lethal limits.",
      render: beta13RenderCombat
    }),
    beta13NewPage(
      "downed",
      "Downed Villagers",
      "Combat And Consequences",
      "shield-check",
      "Protected downed villagers, automatic recovery, lethal bypasses, and the early-revive features that require Second Wind.",
      beta13RenderDowned
    ),
    beta13ExistingPage("player-raids", {
      title: "Player Raids",
      group: "Combat And Consequences",
      description: "Declare a village raid, understand the party snapshot, defenders, horn reveal, mercy stage, outcomes, and cooldown.",
      render: beta13RenderPlayerRaids
    }),
    beta13ExistingPage("reputation", {
      title: "Reputation",
      group: "Start Here",
      description: "Trust thresholds, personal standing, memories, gossip, mood, pacification, decay, and consequences.",
      render: beta13RenderReputation
    }),
    beta13ExistingPage("dialogue", {
      title: "Dialogue And Interaction",
      group: "Start Here",
      description: "Open and bypass the interaction screen, understand when menu options appear, quests, hiring, parties, memories, and refusal conditions.",
      render: beta13RenderDialogue
    }),
    beta13ExistingPage("gifts", { group: "Social Systems" }),
    beta13ExistingPage("skill-trades", { group: "Social Systems" }),
    beta13ExistingPage("watched-containers", { group: "Social Systems" }),
    beta13ExistingPage("advancements", { group: "Reference" }),
    beta13ExistingPage("settings", {
      title: "Controls And Configuration",
      group: "Reference",
      description: "Player controls, Mod Menu settings, gamerules, datapack differences, and optional Second Wind and Ride On compatibility.",
      render: beta13RenderSettings
    })
  ];

  const beta13DuplicateIds = beta13Pages
    .map((page) => page.id)
    .filter((id, index, ids) => ids.indexOf(id) !== index);
  if (beta13DuplicateIds.length) {
    throw new Error(`Duplicate wiki page ids: ${beta13DuplicateIds.join(", ")}`);
  }

  PAGES.splice(0, PAGES.length, ...beta13Pages);
  renderNav();
  render();
})();
