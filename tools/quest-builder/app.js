(function questBuilderApp() {
  "use strict";

  const model = window.QuestBuilderModel;
  const zipUtils = window.QuestBuilderZip;
  const STORAGE_KEY = "villager-retaliation.quest-builder.project.v1";
  const BACKUP_KEY = `${STORAGE_KEY}.backup`;
  const MAX_HISTORY = 80;
  const SLOT_LABELS = { offer: "Quest offer", reminder: "Reminder", turn_in: "Turn-in" };
  const OBJECTIVE_TYPES = Object.keys(model.OBJECTIVE_LABELS);

  const els = {
    editor: document.querySelector("#editor"),
    projectName: document.querySelector("#project-name"),
    questList: document.querySelector("#quest-list"),
    stageShortcuts: document.querySelector("#stage-shortcuts"),
    workflowNav: document.querySelector(".workflow-nav"),
    checksPanel: document.querySelector(".checks-panel"),
    checksList: document.querySelector("#checks-list"),
    checkSummary: document.querySelector("#check-summary"),
    checksToggle: document.querySelector("#checks-toggle"),
    undoButton: document.querySelector("#undo-button"),
    redoButton: document.querySelector("#redo-button"),
    saveState: document.querySelector("#save-state"),
    importButton: document.querySelector("#import-button"),
    importInput: document.querySelector("#import-input"),
    newProjectButton: document.querySelector("#new-project-button"),
    addQuestButton: document.querySelector("#add-quest-button"),
    exportButton: document.querySelector("#export-button"),
    templateDialog: document.querySelector("#template-dialog"),
    confirmDialog: document.querySelector("#confirm-dialog"),
    confirmTitle: document.querySelector("#confirm-title"),
    confirmMessage: document.querySelector("#confirm-message"),
    confirmAction: document.querySelector("#confirm-action"),
    exportDialog: document.querySelector("#export-dialog"),
    exportDialogContent: document.querySelector("#export-dialog-content"),
    toast: document.querySelector("#toast")
  };

  let project;
  let activeView = "setup";
  let selectedStageId = "";
  let selectedSlot = "offer";
  let selectedSceneId = "";
  let registryMetadata = { registries: {} };
  let metadataStatus = "loading";
  let undoStack = [];
  let redoStack = [];
  let saveTimer = 0;
  let toastTimer = 0;
  let pendingConfirmation = null;
  let templateMode = "add";
  let rawJsonDraft = "";
  let rawJsonError = "";
  let startupMessage = "";

  function loadProject() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      project = stored ? model.normalizeProject(JSON.parse(stored)) : model.createProject("linear", "my_pack");
      if (stored) startupMessage = "Recovered your last local draft.";
    } catch (error) {
      const damaged = localStorage.getItem(STORAGE_KEY);
      if (damaged) localStorage.setItem(BACKUP_KEY, damaged);
      localStorage.removeItem(STORAGE_KEY);
      project = model.createProject("linear", "my_pack");
      startupMessage = "The saved draft was damaged. A backup was preserved and a clean project was opened.";
      console.error(error);
    }
    ensureSelection();
  }

  async function loadMetadata() {
    try {
      const response = await fetch("../datapack-builder/quest-registry-metadata.json", { cache: "no-store" });
      if (!response.ok) throw new Error(`Registry request failed with ${response.status}`);
      registryMetadata = await response.json();
      metadataStatus = "ready";
    } catch (error) {
      metadataStatus = "error";
      registryMetadata = { registries: {} };
      console.error(error);
    }
    render();
  }

  function currentQuest() {
    return project.quests.find((quest) => quest.id === project.selectedQuestId) || project.quests[0];
  }

  function currentStage() {
    const quest = currentQuest();
    return quest?.stages?.find((stage) => stage.id === selectedStageId) || quest?.stages?.[0] || null;
  }

  function currentScene() {
    return project.scenes?.find((scene) => scene.id === selectedSceneId) || project.scenes?.[0] || null;
  }

  function issuesForQuest(quest) {
    const questIndex = project.quests.indexOf(quest);
    return model.validateProject(project, registryMetadata)
      .filter((issue) => issue.questIndex === questIndex)
      .map(({ questIndex: ignoredIndex, questId: ignoredId, ...issue }) => issue);
  }

  function ensureSelection() {
    project = model.normalizeProject(project);
    const quest = currentQuest();
    if (!quest) return;
    if (!quest.stages?.some((stage) => stage.id === selectedStageId)) selectedStageId = quest.entry_stage || quest.stages?.[0]?.id || "";
    if (!project.scenes?.some((scene) => scene.id === selectedSceneId)) selectedSceneId = project.scenes?.[0]?.id || "";
  }

  function projectSnapshot() {
    return JSON.stringify(project);
  }

  function commitMutation(mutator, options = {}) {
    const before = projectSnapshot();
    mutator();
    project.updatedAt = new Date().toISOString();
    const after = projectSnapshot();
    if (before === after) return false;
    undoStack.push(before);
    if (undoStack.length > MAX_HISTORY) undoStack.shift();
    redoStack = [];
    ensureSelection();
    scheduleSave();
    rawJsonDraft = "";
    rawJsonError = "";
    if (options.render !== false) render();
    return true;
  }

  function undo() {
    if (!undoStack.length) return;
    redoStack.push(projectSnapshot());
    project = model.normalizeProject(JSON.parse(undoStack.pop()));
    ensureSelection();
    rawJsonDraft = "";
    scheduleSave();
    render();
    showToast("Undid the last change.");
  }

  function redo() {
    if (!redoStack.length) return;
    undoStack.push(projectSnapshot());
    project = model.normalizeProject(JSON.parse(redoStack.pop()));
    ensureSelection();
    rawJsonDraft = "";
    scheduleSave();
    render();
    showToast("Redid the change.");
  }

  function scheduleSave() {
    window.clearTimeout(saveTimer);
    els.saveState.textContent = "Saving locally…";
    els.saveState.className = "save-state is-saving";
    saveTimer = window.setTimeout(saveProject, 180);
  }

  function saveProject() {
    try {
      const serialized = projectSnapshot();
      const previous = localStorage.getItem(STORAGE_KEY);
      if (previous) localStorage.setItem(BACKUP_KEY, previous);
      localStorage.setItem(STORAGE_KEY, serialized);
      els.saveState.textContent = `Saved locally · ${new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}`;
      els.saveState.className = "save-state";
    } catch (error) {
      els.saveState.textContent = "Could not save locally";
      els.saveState.className = "save-state is-error";
      showToast("Local saving failed. Export the project to avoid losing work.", true);
      console.error(error);
    }
  }

  function showToast(message, isError = false) {
    window.clearTimeout(toastTimer);
    els.toast.textContent = message;
    els.toast.className = `toast is-visible${isError ? " is-error" : ""}`;
    toastTimer = window.setTimeout(() => { els.toast.className = "toast"; }, 3200);
  }

  function render() {
    ensureSelection();
    els.projectName.value = project.name;
    renderQuestList();
    renderNavigation();
    renderStageShortcuts();
    const quest = currentQuest();
    const issues = activeView === "scenes" ? (currentScene() ? model.validateScene(currentScene(), registryMetadata) : []) : issuesForQuest(quest);
    if (activeView === "setup") renderSetup(quest, issues);
    else if (activeView === "stages") renderStages(quest, issues);
    else if (activeView === "dialogue") renderDialogue(quest, issues);
    else if (activeView === "rewards") renderRewards(quest, issues);
    else if (activeView === "scenes") renderScenes(issues);
    else renderReview(quest, issues);
    renderChecks(issues);
    els.undoButton.disabled = undoStack.length === 0;
    els.redoButton.disabled = redoStack.length === 0;
    renderIcons();
    if (activeView === "stages") window.requestAnimationFrame(drawGraphLines);
  }

  function renderIcons() {
    try { window.lucide?.createIcons({ attrs: { "stroke-width": 1.9 } }); } catch { /* Labels keep the app usable. */ }
  }

  function renderQuestList() {
    els.questList.innerHTML = project.quests.map((quest) => {
      const issues = issuesForQuest(quest);
      const summary = model.summarizeIssues(issues);
      const health = summary.error ? `<span class="quest-health has-errors" title="${summary.error} error${summary.error === 1 ? "" : "s"}">${summary.error}</span>` : `<span class="quest-health is-valid" title="No errors">✓</span>`;
      return `<button type="button" data-select-quest="${escapeHtml(quest.id)}" class="${quest === currentQuest() ? "is-active" : ""}">
        <span><strong>${escapeHtml(quest.metadata?.title || quest.ui?.title || model.titleFromId(quest.id))}</strong><small>${escapeHtml(quest.id || "Missing quest id")}</small></span>${health}
      </button>`;
    }).join("");
  }

  function renderNavigation() {
    for (const button of els.workflowNav.querySelectorAll("[data-view]")) button.classList.toggle("is-active", button.dataset.view === activeView);
  }

  function renderStageShortcuts() {
    const quest = currentQuest();
    if (!["stages", "dialogue"].includes(activeView) || !quest?.stages?.length) {
      els.stageShortcuts.innerHTML = "";
      return;
    }
    els.stageShortcuts.innerHTML = `<h3>Stages</h3>${quest.stages.map((stage) => `<button type="button" data-select-stage="${escapeHtml(stage.id)}" class="${stage.id === selectedStageId ? "is-active" : ""} ${stage.id === quest.entry_stage ? "is-entry" : ""}">${escapeHtml(stage.title || model.titleFromId(stage.id))}</button>`).join("")}`;
  }

  function pageHeader(title, description, actions = "") {
    return `<div class="editor-header"><div><h1>${escapeHtml(title)}</h1><p>${escapeHtml(description)}</p></div>${actions ? `<div class="section-actions">${actions}</div>` : ""}</div>`;
  }

  function renderSetup(quest, issues) {
    const metadata = quest.metadata || {};
    const availability = quest.availability || {};
    const provider = quest.provider || {};
    const professions = provider.filters?.professions || [];
    const stageOptions = (quest.stages || []).map((stage) => ({ value: stage.id, label: stage.title || model.titleFromId(stage.id) }));
    els.editor.innerHTML = `<div class="editor-content">
      ${pageHeader("Quest setup", "Name the quest, choose who offers it, and control when it can be played.", `
        <button class="button button-secondary" type="button" data-action="duplicate-quest"><i data-lucide="copy"></i>Duplicate</button>
        <button class="button button-plain" type="button" data-action="delete-quest"><i data-lucide="trash-2"></i>Delete</button>`)}
      <section class="section-card">
        <h2>Identity</h2>
        <div class="form-grid">
          ${field({ label: "Quest title", value: metadata.title || "", path: "/metadata/title", field: "metadata.title", placeholder: "A Helping Hand", issues })}
          ${field({ label: "Quest id", value: quest.id || "", path: "/id", field: "id", placeholder: "my_pack:a_helping_hand", help: "Lowercase namespace:path. This also controls the exported file path.", issues })}
          ${textareaField({ label: "Description", value: metadata.description || "", field: "metadata.description", className: "full", placeholder: "What the player will do and why." })}
          ${field({ label: "Questline", value: metadata.questline || "", field: "metadata.questline", placeholder: "village_errands" })}
          ${field({ label: "Parent quest", value: metadata.parent || "", path: "/metadata/parent", field: "metadata.parent", placeholder: "my_pack:previous_quest", help: "Optional. Used to organize related quests.", issues })}
          ${field({ label: "Tags", value: (metadata.tags || []).join(", "), field: "metadata.tags", dataType: "list", className: "span-8", placeholder: "story, village, group.errands" })}
          ${field({ label: "Author", value: metadata.author || "", field: "metadata.author", className: "span-4", placeholder: "Pack author" })}
        </div>
      </section>

      <section class="section-card">
        <h2>Provider</h2>
        <div class="form-grid">
          ${selectField({ label: "Provider type", value: provider.type || "villagerretaliation:villager", field: "provider.type", options: [{ value: "villagerretaliation:villager", label: "Villager" }], className: "span-4" })}
          ${field({ label: "Professions", value: professions.join(", "), field: "provider.filters.professions", dataType: "list", className: "span-8", placeholder: "minecraft:farmer, minecraft:librarian", help: "Leave blank to allow any villager profession." })}
        </div>
      </section>

      <section class="section-card">
        <h2>Availability</h2>
        <div class="form-grid">
          ${selectField({ label: "Entry stage", value: quest.entry_stage || "", field: "entry_stage", options: stageOptions, path: "/entry_stage", issues })}
          ${field({ label: "Prerequisite quests", value: (availability.prerequisites || []).join(", "), field: "availability.prerequisites", dataType: "list", className: "span-8", placeholder: "my_pack:first_quest, my_pack:second_quest", help: "All listed quests must be completed. Order is preserved in the journal." })}
          ${numberField({ label: "Maximum completions", value: availability.max_completions ?? 1, field: "availability.max_completions", min: 0, help: "0 means unlimited when the quest is repeatable." })}
          ${checkboxField({ label: "Repeatable", detail: "Allow this quest to be completed more than once.", checked: Boolean(availability.repeatable), field: "availability.repeatable" })}
          ${checkboxField({ label: "Lock to villager", detail: "The same villager must receive the turn-in.", checked: availability.locked_to_villager !== false, field: "availability.locked_to_villager" })}
          ${checkboxField({ label: "Consume on completion", detail: "Remove required items when the quest is turned in.", checked: Boolean(availability.consume_on_completion), field: "availability.consume_on_completion" })}
          ${checkboxField({ label: "Cross-villager compatible", detail: "Allow another compatible villager to continue the quest.", checked: Boolean(availability.cross_villager_compatible), field: "availability.cross_villager_compatible" })}
        </div>
      </section>

      <section class="section-card">
        <h2>Quest tracker</h2>
        <div class="form-grid">
          ${field({ label: "Tracker title", value: quest.ui?.title || metadata.title || "", field: "ui.title" })}
          ${field({ label: "Icon item", value: quest.ui?.icon || "", field: "ui.icon", path: "/ui/icon", placeholder: "minecraft:book", issues })}
          ${field({ label: "Tracker text", value: quest.ui?.tracker_text || "", field: "ui.tracker_text", className: "span-8", placeholder: "Bring the supplies to the villager." })}
          ${field({ label: "Color", value: quest.ui?.color || "", field: "ui.color", className: "span-4", placeholder: "#6f9e45" })}
        </div>
      </section>
    </div>`;
  }

  function renderStages(quest, issues) {
    const stage = currentStage();
    const actions = `<button class="button button-secondary" type="button" data-action="add-stage"><i data-lucide="plus"></i>Add stage</button>`;
    els.editor.innerHTML = `<div class="editor-content">
      ${pageHeader("Stages", "Build the quest flow, then define what the player must do at each step.", actions)}
      <section class="section-card graph-card">
        <div class="graph-toolbar"><div><h2>Quest flow</h2><span>Solid lines continue automatically. Dashed lines are player choices.</span></div><button class="button button-secondary" type="button" data-action="center-graph"><i data-lucide="scan"></i>Fit flow</button></div>
        <div class="graph-scroll"><div class="quest-graph" id="quest-graph"><svg class="graph-lines" aria-hidden="true"></svg>${renderGraphNodes(quest, issues)}</div></div>
      </section>
      ${stage ? renderStageEditor(quest, stage, issues) : `<div class="empty-state"><p>This quest has no stages.</p><button class="button button-primary" type="button" data-action="add-stage">Add the first stage</button></div>`}
    </div>`;
  }

  function renderGraphNodes(quest, issues) {
    return (quest.stages || []).map((stage, index) => {
      const stageIssues = issues.filter((issue) => issue.path.startsWith(`/stages/${index}`));
      const objectiveCount = stage.objectives?.length || 0;
      const status = stageIssues.some((issue) => issue.severity === "error") ? `${stageIssues.filter((issue) => issue.severity === "error").length} errors` : `${objectiveCount} objective${objectiveCount === 1 ? "" : "s"}`;
      return `<button type="button" class="graph-node ${stage.id === selectedStageId ? "is-active" : ""} ${stage.id === quest.entry_stage ? "is-entry" : ""}" data-select-stage="${escapeHtml(stage.id)}" data-node-id="${escapeHtml(stage.id)}"><strong>${escapeHtml(stage.title || model.titleFromId(stage.id))}</strong><span>${escapeHtml(stage.id)}</span><span class="node-status">${escapeHtml(status)}</span></button>`;
    }).join("");
  }

  function renderStageEditor(quest, stage, issues) {
    const stageIndex = quest.stages.indexOf(stage);
    const nextOptions = [{ value: "", label: "End here / use dialogue choices" }, ...quest.stages.filter((item) => item !== stage).map((item) => ({ value: item.id, label: item.title || model.titleFromId(item.id) }))];
    return `<section class="section-card" data-stage-editor>
      <div class="section-card-header"><div><h2>${escapeHtml(stage.title || model.titleFromId(stage.id))}</h2><p>${escapeHtml(stage.id)}</p></div><div class="section-actions">
        <button class="button button-secondary" type="button" data-action="duplicate-stage"><i data-lucide="copy"></i>Duplicate</button>
        <button class="button button-plain" type="button" data-action="delete-stage"><i data-lucide="trash-2"></i>Delete</button>
      </div></div>
      <div class="form-grid">
        ${field({ label: "Stage title", value: stage.title || "", stageField: "title", placeholder: "Gather Supplies" })}
        ${field({ label: "Stage id", value: stage.id || "", stageField: "id", path: `/stages/${stageIndex}/id`, placeholder: "gather_supplies", issues })}
        ${textareaField({ label: "Description", value: stage.description || "", stageField: "description", className: "full", placeholder: "What happens during this part of the quest." })}
        ${selectField({ label: "Next stage", value: typeof stage.next === "string" ? stage.next : stage.next?.stage || "", stageField: "next", options: nextOptions, path: `/stages/${stageIndex}`, issues })}
        ${field({ label: "Tracker text", value: stage.ui?.tracker_text || "", stageField: "ui.tracker_text", placeholder: "Bring the requested supplies." })}
      </div>
      <div class="subsection">
        <div class="section-card-header"><div><h3>Objectives</h3><p>Every required objective must be complete before this stage advances.</p></div><button class="button button-secondary" type="button" data-action="add-objective"><i data-lucide="plus"></i>Add objective</button></div>
        <div class="objective-list">${(stage.objectives || []).length ? stage.objectives.map((objective, index) => renderObjective(stage, objective, index, stageIndex, issues)).join("") : `<div class="empty-state"><p>This is a return-only stage. Add an objective if the player must complete another task.</p><button class="button button-secondary" type="button" data-action="add-objective">Add objective</button></div>`}</div>
      </div>
    </section>`;
  }

  function renderObjective(stage, objective, index, stageIndex, issues) {
    const path = `/stages/${stageIndex}/objectives/${index}`;
    const typeOptions = OBJECTIVE_TYPES.map((type) => ({ value: type, label: model.OBJECTIVE_LABELS[type] }));
    const target = objectiveTargetField(objective, index, path, issues);
    const needsCount = ["item_check", "gift", "mob_kill", "block_break", "block_place", "trade", "memory_event"].includes(objective.type);
    return `<article class="objective-card">
      <div class="objective-header"><div><strong>${escapeHtml(model.OBJECTIVE_LABELS[objective.type] || model.titleFromId(objective.type))}</strong><span class="objective-number">Objective ${index + 1}</span></div><button class="icon-button remove-button" type="button" data-action="remove-objective" data-objective-index="${index}" aria-label="Remove objective"><i data-lucide="trash-2"></i></button></div>
      <div class="form-grid">
        ${selectField({ label: "Objective type", value: objective.type || "", objectiveField: "type", objectiveIndex: index, options: typeOptions })}
        ${field({ label: "Objective id", value: objective.id || "", objectiveField: "id", objectiveIndex: index, path: `${path}/id`, issues })}
        ${target}
        ${needsCount ? numberField({ label: "Required count", value: objective.count ?? 1, objectiveField: "count", objectiveIndex: index, min: 1, path: `${path}/count`, issues }) : ""}
        ${field({ label: "Tracker instruction", value: objective.tracker?.text || "", objectiveField: "tracker.text", objectiveIndex: index, className: needsCount ? "full" : "", placeholder: "Tell the player exactly what to do." })}
        ${field({ label: "Completion text", value: objective.tracker?.complete_text || "", objectiveField: "tracker.complete_text", objectiveIndex: index, className: needsCount ? "full" : "", placeholder: "Shown when the objective is complete." })}
        ${checkboxField({ label: "Optional objective", detail: "Do not require this objective to advance the stage.", checked: Boolean(objective.optional), objectiveField: "optional", objectiveIndex: index, className: "full" })}
      </div>
    </article>`;
  }

  function objectiveTargetField(objective, index, path, issues) {
    const shared = { objectiveIndex: index, issues };
    if (["item_check", "gift"].includes(objective.type)) return field({ ...shared, label: "Item id", value: objective.item || "", objectiveField: "item", path: `${path}/item`, placeholder: "minecraft:bread" });
    if (objective.type === "mob_kill") return field({ ...shared, label: "Entity id", value: objective.entity || "", objectiveField: "entity", path: `${path}/entity`, placeholder: "minecraft:zombie" });
    if (["block_break", "block_place", "block_interact"].includes(objective.type)) return field({ ...shared, label: "Block id", value: objective.block || "", objectiveField: "block", path: `${path}/block`, placeholder: "minecraft:stone" });
    if (objective.type === "structure_visit") return field({ ...shared, label: "Structure id", value: objective.structure || "", objectiveField: "structure", path: `${path}/structure`, placeholder: "minecraft:village_plains" });
    if (objective.type === "memory_event") return field({ ...shared, label: "Memory event", value: objective.event || "", objectiveField: "event", placeholder: "player_helped_villager" });
    if (objective.type === "reputation") return numberField({ ...shared, label: "Minimum reputation", value: objective.min ?? 0, objectiveField: "min" });
    if (objective.type === "choice") return `${field({ ...shared, label: "Choice fact key", value: objective.key || "", objectiveField: "key", placeholder: "help_path" })}${field({ ...shared, label: "Allowed values", value: (objective.values || []).join(", "), objectiveField: "values", dataType: "list", placeholder: "supplies, defense" })}`;
    if (objective.type === "fact") return `${field({ ...shared, label: "Fact key", value: objective.key || "", objectiveField: "key", placeholder: "progress" })}${field({ ...shared, label: "Expected value", value: objective.value ?? "", objectiveField: "value", placeholder: "1" })}`;
    if (objective.type === "location_visit") return `${numberField({ ...shared, label: "X", value: objective.x ?? 0, objectiveField: "x", className: "span-3" })}${numberField({ ...shared, label: "Y", value: objective.y ?? 64, objectiveField: "y", className: "span-3" })}${numberField({ ...shared, label: "Z", value: objective.z ?? 0, objectiveField: "z", className: "span-3" })}${numberField({ ...shared, label: "Radius", value: objective.radius ?? 8, objectiveField: "radius", className: "span-3", min: 1 })}`;
    if (objective.type === "condition") return textareaField({ ...shared, label: "Conditions JSON", value: JSON.stringify(objective.conditions || [], null, 2), objectiveField: "conditions", dataType: "json", className: "full" });
    return "";
  }

  function renderDialogue(quest, issues) {
    const stage = currentStage();
    if (!stage) {
      els.editor.innerHTML = `<div class="editor-content">${pageHeader("Dialogue", "Write quest offers, reminders, turn-ins, and player choices.")}<div class="empty-state"><p>Add a stage before writing dialogue.</p><button class="button button-primary" type="button" data-view="stages">Open stages</button></div></div>`;
      return;
    }
    const stageIndex = quest.stages.indexOf(stage);
    if (!SLOT_LABELS[selectedSlot]) selectedSlot = "offer";
    const slot = stage.dialogue?.[selectedSlot];
    els.editor.innerHTML = `<div class="editor-content">
      ${pageHeader("Dialogue", "Write what the villager says and connect player responses to scenes or stages.", selectField({ label: "Stage", value: stage.id, selectStage: true, options: quest.stages.map((item) => ({ value: item.id, label: item.title || model.titleFromId(item.id) })), bare: true }))}
      <section class="section-card">
        <div class="dialogue-layout">
          <nav class="slot-list" aria-label="Dialogue slots">${Object.entries(SLOT_LABELS).map(([name, label]) => `<button type="button" data-select-slot="${name}" class="${selectedSlot === name ? "is-active" : ""} ${stage.dialogue?.[name] ? "is-configured" : ""}">${label}</button>`).join("")}</nav>
          <div class="dialogue-editor">${slot ? renderDialogueSlot(quest, stage, stageIndex, selectedSlot, slot, issues) : renderDisabledSlot(selectedSlot)}</div>
        </div>
      </section>
    </div>`;
  }

  function renderDisabledSlot(slotName) {
    return `<div class="slot-enable"><h2>${escapeHtml(SLOT_LABELS[slotName])}</h2><p class="field-help">This stage does not currently use this dialogue slot.</p><button class="button button-primary" type="button" data-action="enable-slot" data-slot="${slotName}" style="margin-top: 14px"><i data-lucide="plus"></i>Add ${escapeHtml(SLOT_LABELS[slotName].toLowerCase())}</button></div>`;
  }

  function renderDialogueSlot(quest, stage, stageIndex, slotName, slot, issues) {
    return `<div class="section-card-header"><div><h2>${escapeHtml(SLOT_LABELS[slotName])}</h2><p>${slotName === "offer" ? "Shown before the quest starts." : slotName === "reminder" ? "Shown while the quest is active." : "Shown when the player returns to finish."}</p></div><button class="button button-plain" type="button" data-action="disable-slot" data-slot="${slotName}"><i data-lucide="trash-2"></i>Remove</button></div>
      <div class="form-grid">
        ${field({ label: "Option label", value: slot.label || "", dialogueField: "label", placeholder: quest.metadata?.title || "About the quest" })}
        ${field({ label: "Request", value: slot.request || "question", dialogueField: "request", placeholder: "question" })}
        ${textareaField({ label: "Villager lines", value: (slot.lines || (slot.text ? [slot.text] : [])).join("\n"), dialogueField: "lines", dataType: "lines", className: "full", placeholder: "One possible line per row." })}
      </div>
      <div class="subsection">
        <div class="section-card-header"><div><h3>Player responses</h3><p>Stage destinations create branches in the quest flow.</p></div><button class="button button-secondary" type="button" data-action="add-response"><i data-lucide="plus"></i>Add response</button></div>
        <div class="response-list">${(slot.responses || []).length ? slot.responses.map((response, index) => renderResponse(quest, stageIndex, slotName, response, index, issues)).join("") : `<div class="empty-state"><p>No player responses yet.</p><button class="button button-secondary" type="button" data-action="add-response">Add response</button></div>`}</div>
      </div>`;
  }

  function renderResponse(quest, stageIndex, slotName, response, index, issues) {
    const type = response.stage || response.next ? "stage" : response.scene ? "scene" : response.complete ? "complete" : response.fail ? "fail" : response.abandon ? "abandon" : "none";
    const destination = response.stage || response.next || response.scene || "";
    const destinationControl = type === "stage"
      ? selectField({ label: "Destination", value: destination, responseField: "destination", responseIndex: index, options: [{ value: "", label: "Choose a stage" }, ...quest.stages.map((stage) => ({ value: stage.id, label: stage.title || model.titleFromId(stage.id) }))], bare: true })
      : type === "scene"
        ? field({ label: "Scene id", value: destination, responseField: "destination", responseIndex: index, bare: true, placeholder: "start_quest" })
        : `<div></div>`;
    return `<div class="response-row">
      ${field({ label: "Response id", value: response.id || "", responseField: "id", responseIndex: index, bare: true, path: `/stages/${stageIndex}/dialogue/${slotName}/responses/${index}/id`, issues })}
      ${field({ label: "Player text", value: response.label || response.text || "", responseField: "label", responseIndex: index, bare: true, placeholder: "I can help." })}
      ${selectField({ label: "Outcome", value: type, responseField: "outcome", responseIndex: index, options: [
        { value: "none", label: "No transition" }, { value: "stage", label: "Go to stage" }, { value: "scene", label: "Run scene" }, { value: "complete", label: "Complete quest" }, { value: "fail", label: "Fail quest" }, { value: "abandon", label: "Abandon quest" }
      ], bare: true })}
      ${destinationControl}
      <button class="icon-button remove-button" type="button" data-action="remove-response" data-response-index="${index}" aria-label="Remove response"><i data-lucide="trash-2"></i></button>
    </div>`;
  }

  function renderRewards(quest, issues) {
    const rewards = quest.rewards || {};
    const availability = quest.availability || {};
    els.editor.innerHTML = `<div class="editor-content">
      ${pageHeader("Rewards", "Choose what the player receives and how the quest behaves after completion.")}
      <section class="section-card">
        <h2>Completion rewards</h2>
        <div class="form-grid">
          ${numberField({ label: "Experience", value: rewards.experience ?? 0, field: "rewards.experience", min: 0 })}
          ${numberField({ label: "Reputation", value: rewards.reputation ?? 0, field: "rewards.reputation" })}
          ${numberField({ label: "Gossip reputation", value: rewards.gossip_reputation ?? 0, field: "rewards.gossip_reputation" })}
          ${field({ label: "Loot table", value: rewards.loot_table || "", field: "rewards.loot_table", path: "/rewards/loot_table", placeholder: `${project.namespace}:quest/reward`, issues })}
          ${field({ label: "Memory event", value: rewards.memory_event || "", field: "rewards.memory_event", placeholder: "villagerretaliation:player_completed_quest", help: "Optional villager memory recorded on completion.", className: "full" })}
        </div>
      </section>
      <section class="section-card">
        <h2>After completion</h2>
        <div class="form-grid">
          ${checkboxField({ label: "Repeatable", detail: "Allow players to start this quest again.", checked: Boolean(availability.repeatable), field: "availability.repeatable" })}
          ${numberField({ label: "Maximum completions", value: availability.max_completions ?? 1, field: "availability.max_completions", min: 0 })}
          ${field({ label: "Completion cooldown", value: availability.completion_cooldown || "", field: "availability.completion_cooldown", placeholder: "1d", help: "Examples: 30m, 1d. Leave blank for no cooldown." })}
          ${field({ label: "Exclusive group", value: availability.exclusive_group || "", field: "availability.exclusive_group", placeholder: `${project.namespace}:village_choice`, help: "Optional. Prevents another quest in the same group from being active." })}
        </div>
      </section>
      <section class="section-card">
        <h2>Advanced reward actions</h2>
        ${textareaField({ label: "Actions JSON", value: JSON.stringify(rewards.actions || [], null, 2), field: "rewards.actions", dataType: "json", className: "full", help: metadataStatus === "ready" ? `Supported action types: ${(registryMetadata.registries?.actions || []).map((entry) => entry.id).join(", ")}.` : "Registry metadata could not be loaded. The exported quest should be checked with the repository validator." })}
      </section>
    </div>`;
  }

  function renderReview(quest, issues) {
    const summary = model.summarizeIssues(issues);
    const statusClass = summary.error ? "error" : summary.warning ? "warning" : "valid";
    const icon = summary.error ? "circle-x" : summary.warning ? "triangle-alert" : "circle-check";
    const message = summary.error ? `${summary.error} error${summary.error === 1 ? "" : "s"} must be fixed before safe export.` : summary.warning ? `${summary.warning} warning${summary.warning === 1 ? "" : "s"} should be reviewed.` : "This quest passes all browser checks.";
    const json = rawJsonDraft || JSON.stringify(model.stripBuilderFields(quest), null, 2);
    els.editor.innerHTML = `<div class="editor-content">
      ${pageHeader("Review", "Inspect the generated Quest v2 file or make an advanced JSON edit.", `<button class="button button-secondary" type="button" data-action="copy-json"><i data-lucide="copy"></i>Copy JSON</button><button class="button button-primary" type="button" data-action="download-json"><i data-lucide="download"></i>Download JSON</button>`)}
      <div class="status-strip ${statusClass}"><i data-lucide="${icon}"></i><div><strong>${summary.error ? "Not ready to export" : summary.warning ? "Ready with warnings" : "Ready to export"}</strong><span>${escapeHtml(message)}</span></div></div>
      <section class="section-card code-panel" style="margin-top:16px">
        <div class="path-display"><code>${escapeHtml(model.questFilePath(quest))}</code><button class="button button-secondary" type="button" data-action="format-json">Format</button></div>
        ${rawJsonError ? `<div class="raw-error" role="alert">${escapeHtml(rawJsonError)}</div>` : ""}
        <label class="visually-hidden" for="raw-json">Quest JSON</label>
        <textarea id="raw-json" spellcheck="false" autocomplete="off">${escapeHtml(json)}</textarea>
        <div class="modal-actions"><button class="button button-secondary" type="button" data-action="reset-json">Reset</button><button class="button button-primary" type="button" data-action="apply-json">Apply JSON changes</button></div>
      </section>
    </div>`;
  }

  function renderScenes(issues) {
    const scene = currentScene();
    const sceneOptions = (project.scenes || []).map((item) => ({ value: item.id, label: model.titleFromId(item.id) }));
    if (!scene) {
      els.editor.innerHTML = `<div class="editor-content">${pageHeader("Scenes", "Create persistent actor and step graphs exported under data/<namespace>/quest_scenes/.", `<button class="button button-primary" type="button" data-action="add-scene"><i data-lucide="plus"></i>Add scene</button>`)}<div class="empty-state"><p>No scene resources in this project.</p><button class="button button-primary" type="button" data-action="add-scene">Add scene</button></div></div>`;
      return;
    }
    const actorTypes = (registryMetadata.registries?.actor_types || []).filter((item) => item.browser_available !== false).map((item) => ({ value: item.id, label: item.title || model.titleFromId(item.id) }));
    const stepTypes = (registryMetadata.registries?.scene_steps || []).filter((item) => item.browser_available !== false).map((item) => ({ value: item.id, label: item.title || model.titleFromId(item.id) }));
    const stepOptions = (scene.steps || []).map((step) => ({ value: step.id, label: step.id }));
    els.editor.innerHTML = `<div class="editor-content">
      ${pageHeader("Scenes", "Edit stable actor declarations and graph steps. Validation uses the same registered metadata as runtime compilation.", `<button class="button button-secondary" type="button" data-action="add-scene"><i data-lucide="plus"></i>Add scene</button><button class="button button-plain" type="button" data-action="delete-scene"><i data-lucide="trash-2"></i>Delete</button>`)}
      <section class="section-card"><div class="form-grid">
        ${selectField({ label: "Scene resource", value: scene.id, sceneSelect: true, options: sceneOptions, className: "span-4" })}
        ${field({ label: "Scene id", value: scene.id, sceneField: "id", path: "/id", className: "span-4", issues })}
        ${selectField({ label: "Ownership", value: scene.ownership || "player", sceneField: "ownership", options: ["player", "party", "quest_instance", "world"], className: "span-2" })}
        ${selectField({ label: "Entry step", value: scene.entry_step || "", sceneField: "entry_step", options: stepOptions, className: "span-2", path: "/entry_step", issues })}
        ${field({ label: "Scene timeout (ticks)", value: scene.timeout_ticks ?? "", sceneField: "timeout_ticks", dataType: "number", min: 0, className: "span-2" })}
        ${selectField({ label: "Failure policy", value: scene.failure_policy || "fail_scene", sceneField: "failure_policy", options: ["fail_scene", "cancel_scene", "block_for_repair", "run_failure_step"], className: "span-3" })}
        ${selectField({ label: "Cancellation policy", value: scene.cancellation_policy || "cancel_scene", sceneField: "cancellation_policy", options: ["fail_scene", "cancel_scene", "block_for_repair", "run_failure_step"], className: "span-3" })}
        ${selectField({ label: "Cleanup policy", value: scene.cleanup_policy || "owned_entities", sceneField: "cleanup_policy", options: ["none", "owned_entities", "encounters", "all_owned", "preserve_world"], className: "span-3" })}
      </div></section>
      <section class="section-card"><div class="section-card-header"><div><h2>Actors</h2><p>Bindings are explicit; fixed actors are never replaced by proximity.</p></div><button class="button button-secondary" type="button" data-action="add-scene-actor"><i data-lucide="plus"></i>Add actor</button></div>
        <div class="objective-list">${(scene.actors || []).map((actor, index) => `<article class="objective-card"><div class="objective-header"><strong>${escapeHtml(actor.alias || `Actor ${index + 1}`)}</strong><button class="icon-button remove-button" type="button" data-action="remove-scene-actor" data-actor-index="${index}" aria-label="Remove actor"><i data-lucide="trash-2"></i></button></div><div class="form-grid">
          ${field({ label: "Stable alias", value: actor.alias || "", actorField: "alias", actorIndex: index, className: "span-2" })}
          ${selectField({ label: "Actor type", value: actor.type || "villagerretaliation:villager", actorField: "type", actorIndex: index, options: actorTypes, className: "span-3" })}
          ${selectField({ label: "Binding source", value: actor.binding_source || "unbound", actorField: "binding_source", actorIndex: index, options: ["owner_player", "party_member", "quest_provider", "uuid", "marker", "encounter", "owned_spawn", "unbound"], className: "span-2" })}
          ${field({ label: "Binding reference", value: actor.binding || "", actorField: "binding", actorIndex: index, className: "span-3" })}
          ${selectField({ label: "Replacement", value: actor.replacement_policy || "fixed", actorField: "replacement_policy", actorIndex: index, options: ["fixed", "operator_rebindable", "compatible_replacement", "respawn_if_owned", "optional"], className: "span-3" })}
          ${selectField({ label: "Missing actor", value: actor.missing_actor_policy || "block", actorField: "missing_actor_policy", actorIndex: index, options: ["block", "fail", "skip", "wait_until_timeout"], className: "span-2" })}
          ${selectField({ label: "Death policy", value: actor.death_policy || "apply_missing_policy", actorField: "death_policy", actorIndex: index, options: ["fail", "block", "apply_missing_policy", "respawn_if_owned", "continue_with_snapshot"], className: "span-3" })}
          ${checkboxField({ label: "Required actor", detail: "Block or fail when its policy cannot recover this binding.", checked: actor.required !== false, actorField: "required", actorIndex: index, className: "span-3" })}
          ${field({ label: "Required capabilities", value: (actor.capabilities || []).join(", "), actorField: "capabilities", actorIndex: index, dataType: "list", className: "span-4" })}
          ${field({ label: "Missing timeout (ticks)", value: actor.timeout_ticks ?? "", actorField: "timeout_ticks", actorIndex: index, dataType: "number", min: 0, className: "span-2" })}
          ${textareaField({ label: "Actor filters (JSON)", value: JSON.stringify(actor.filters || {}, null, 2), actorField: "filters", actorIndex: index, dataType: "json", className: "full" })}
        </div></article>`).join("")}</div></section>
      <section class="section-card"><div class="section-card-header"><div><h2>Steps</h2><p>Every step id is authored and remains stable across reloads.</p></div><button class="button button-secondary" type="button" data-action="add-scene-step"><i data-lucide="plus"></i>Add step</button></div>
        <div class="objective-list">${(scene.steps || []).map((step, index) => `<article class="objective-card"><div class="objective-header"><strong>${escapeHtml(step.id || `Step ${index + 1}`)}</strong><button class="icon-button remove-button" type="button" data-action="remove-scene-step" data-step-index="${index}" aria-label="Remove step"><i data-lucide="trash-2"></i></button></div><div class="form-grid">
          ${field({ label: "Stable step id", value: step.id || "", stepField: "id", stepIndex: index, className: "span-2" })}
          ${selectField({ label: "Step type", value: step.type || "villagerretaliation:wait_ticks", stepField: "type", stepIndex: index, options: stepTypes, className: "span-4" })}
          ${field({ label: "Actor aliases", value: (step.actors || []).join(", "), stepField: "actors", stepIndex: index, dataType: "list", className: "span-3" })}
          ${selectField({ label: "Next step", value: step.next || "", stepField: "next", stepIndex: index, options: [{ value: "", label: "Terminal / transitions" }, ...stepOptions], className: "span-3" })}
          ${selectField({ label: "Failure step", value: step.failure_step || "", stepField: "failure_step", stepIndex: index, options: [{ value: "", label: "Use scene policy" }, ...stepOptions], className: "span-3" })}
          ${textareaField({ label: "Named transitions (JSON)", value: JSON.stringify(step.transitions || {}, null, 2), stepField: "transitions", stepIndex: index, dataType: "json", className: "span-4" })}
          ${textareaField({ label: "Step data (JSON)", value: JSON.stringify(step.data || {}, null, 2), stepField: "data", stepIndex: index, dataType: "json", className: "full" })}
        </div></article>`).join("")}</div></section>
      <section class="section-card"><h2>Export path</h2><div class="path-display"><code>${escapeHtml(model.sceneFilePath(scene))}</code></div></section>
    </div>`;
  }

  function renderChecks(issues) {
    const summary = model.summarizeIssues(issues);
    if (metadataStatus === "error") issues = [...issues, { severity: "warning", code: "registry.unavailable", path: "/", message: "Registry metadata could not be loaded.", hint: "Browser checks are limited. Run the repository validator before shipping." }];
    els.checkSummary.textContent = summary.error ? `${summary.error} error${summary.error === 1 ? "" : "s"}, ${summary.warning} warning${summary.warning === 1 ? "" : "s"}` : summary.warning ? `${summary.warning} warning${summary.warning === 1 ? "" : "s"}` : "No problems found";
    if (!issues.length) {
      els.checksList.innerHTML = `<div class="all-clear"><i data-lucide="circle-check"></i><p>No problems found.</p><small>The quest is structurally ready to export.</small></div>`;
      return;
    }
    els.checksList.innerHTML = issues.map((issue, index) => `<button type="button" class="check-item ${issue.severity}" data-issue-index="${index}" data-issue-path="${escapeHtml(issue.path)}"><i data-lucide="${issue.severity === "error" ? "circle-x" : issue.severity === "warning" ? "triangle-alert" : "info"}"></i><span><strong>${escapeHtml(issue.message)}</strong><span>${escapeHtml(issue.hint || "")}</span><code>${escapeHtml(issue.path)}</code></span></button>`).join("");
  }

  function field(options) {
    const error = fieldIssue(options.issues, options.path);
    const attrs = controlAttributes(options);
    const input = `<input type="${options.type || "text"}" value="${escapeHtml(options.value ?? "")}" ${attrs} ${options.placeholder ? `placeholder="${escapeHtml(options.placeholder)}"` : ""} ${error ? `aria-invalid="true"` : ""}>`;
    if (options.bare) return `<div>${options.label ? `<label>${escapeHtml(options.label)}</label>` : ""}${input}${error ? `<div class="field-error">${escapeHtml(error.message)}</div>` : ""}</div>`;
    return `<div class="field ${options.className || ""}"><label>${escapeHtml(options.label)}</label>${input}${options.help ? `<span class="field-help">${escapeHtml(options.help)}</span>` : ""}${error ? `<div class="field-error">${escapeHtml(error.message)}</div>` : ""}</div>`;
  }

  function numberField(options) {
    return field({ ...options, type: "number", dataType: "number", min: options.min });
  }

  function textareaField(options) {
    const error = fieldIssue(options.issues, options.path);
    const attrs = controlAttributes(options);
    const content = `<textarea ${attrs} ${options.placeholder ? `placeholder="${escapeHtml(options.placeholder)}"` : ""} ${error ? `aria-invalid="true"` : ""}>${escapeHtml(options.value ?? "")}</textarea>`;
    if (options.bare) return `<div>${options.label ? `<label>${escapeHtml(options.label)}</label>` : ""}${content}</div>`;
    return `<div class="field ${options.className || ""}"><label>${escapeHtml(options.label)}</label>${content}${options.help ? `<span class="field-help">${escapeHtml(options.help)}</span>` : ""}${error ? `<div class="field-error">${escapeHtml(error.message)}</div>` : ""}</div>`;
  }

  function selectField(options) {
    const error = fieldIssue(options.issues, options.path);
    const attrs = controlAttributes(options);
    const content = `<select ${attrs} ${error ? `aria-invalid="true"` : ""}>${(options.options || []).map((option) => {
      const value = typeof option === "string" ? option : option.value;
      const label = typeof option === "string" ? model.titleFromId(option) : option.label;
      return `<option value="${escapeHtml(value)}" ${String(value) === String(options.value ?? "") ? "selected" : ""}>${escapeHtml(label)}</option>`;
    }).join("")}</select>`;
    if (options.bare) return `<div>${options.label ? `<label>${escapeHtml(options.label)}</label>` : ""}${content}</div>`;
    return `<div class="field ${options.className || ""}"><label>${escapeHtml(options.label)}</label>${content}${options.help ? `<span class="field-help">${escapeHtml(options.help)}</span>` : ""}${error ? `<div class="field-error">${escapeHtml(error.message)}</div>` : ""}</div>`;
  }

  function checkboxField(options) {
    const attrs = controlAttributes({ ...options, dataType: "boolean" });
    return `<label class="field checkbox-field ${options.className || ""}"><input type="checkbox" ${options.checked ? "checked" : ""} ${attrs}><span><strong>${escapeHtml(options.label)}</strong>${escapeHtml(options.detail || "")}</span></label>`;
  }

  function controlAttributes(options) {
    const values = [];
    if (options.label) values.push(`aria-label="${escapeHtml(options.label)}"`);
    if (options.field) values.push(`data-field="${escapeHtml(options.field)}"`);
    if (options.stageField) values.push(`data-stage-field="${escapeHtml(options.stageField)}"`);
    if (options.objectiveField) values.push(`data-objective-field="${escapeHtml(options.objectiveField)}"`);
    if (options.objectiveIndex !== undefined) values.push(`data-objective-index="${options.objectiveIndex}"`);
    if (options.dialogueField) values.push(`data-dialogue-field="${escapeHtml(options.dialogueField)}"`);
    if (options.responseField) values.push(`data-response-field="${escapeHtml(options.responseField)}"`);
    if (options.responseIndex !== undefined) values.push(`data-response-index="${options.responseIndex}"`);
    if (options.sceneField) values.push(`data-scene-field="${escapeHtml(options.sceneField)}"`);
    if (options.sceneSelect) values.push("data-scene-select");
    if (options.actorField) values.push(`data-actor-field="${escapeHtml(options.actorField)}"`);
    if (options.actorIndex !== undefined) values.push(`data-actor-index="${options.actorIndex}"`);
    if (options.stepField) values.push(`data-step-field="${escapeHtml(options.stepField)}"`);
    if (options.stepIndex !== undefined) values.push(`data-step-index="${options.stepIndex}"`);
    if (options.selectStage) values.push("data-select-stage-control");
    if (options.dataType) values.push(`data-type="${escapeHtml(options.dataType)}"`);
    if (options.min !== undefined) values.push(`min="${escapeHtml(options.min)}"`);
    return values.join(" ");
  }

  function fieldIssue(issues, path) {
    if (!path || !issues) return null;
    return issues.find((issue) => issue.severity === "error" && (issue.path === path || issue.path.startsWith(`${path}/`))) || null;
  }

  function parseControlValue(control) {
    if (control.dataset.type === "boolean") return control.checked;
    if (control.dataset.type === "number") return control.value === "" ? undefined : Number(control.value);
    if (control.dataset.type === "list") return control.value.split(/[\n,]+/).map((item) => item.trim()).filter(Boolean);
    if (control.dataset.type === "lines") return control.value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    if (control.dataset.type === "json") {
      try { return JSON.parse(control.value || "null"); }
      catch { showToast("That field does not contain valid JSON.", true); control.focus(); throw new Error("Invalid field JSON"); }
    }
    return control.value;
  }

  function getNested(target, path) {
    return String(path).split(".").reduce((value, key) => value?.[key], target);
  }

  function setNested(target, path, value, options = {}) {
    const parts = String(path).split(".");
    let owner = target;
    for (const key of parts.slice(0, -1)) {
      if (!owner[key] || typeof owner[key] !== "object" || Array.isArray(owner[key])) owner[key] = {};
      owner = owner[key];
    }
    const key = parts.at(-1);
    if (options.deleteEmpty && (value === "" || value === undefined || (Array.isArray(value) && value.length === 0))) delete owner[key];
    else owner[key] = value;
  }

  function updateFromControl(control) {
    let value;
    try { value = parseControlValue(control); } catch { return; }
    const quest = currentQuest();
    const stage = currentStage();
    if (control.dataset.field) {
      const path = control.dataset.field;
      commitMutation(() => {
        if (path === "id") {
          const previous = quest.id;
          quest.id = value;
          if (project.selectedQuestId === previous) project.selectedQuestId = value;
        } else setNested(quest, path, value, { deleteEmpty: !["provider.type", "entry_stage"].includes(path) });
      });
      return;
    }
    if (control.dataset.stageField && stage) {
      const path = control.dataset.stageField;
      commitMutation(() => {
        if (path === "id") {
          const previous = stage.id;
          model.renameStage(quest, previous, value);
          selectedStageId = value;
        } else if (path === "next") {
          if (value) stage.next = value; else delete stage.next;
        } else setNested(stage, path, value, { deleteEmpty: true });
      });
      return;
    }
    if (control.dataset.objectiveField && stage) {
      const index = Number(control.dataset.objectiveIndex);
      const objective = stage.objectives[index];
      const path = control.dataset.objectiveField;
      if (!objective) return;
      commitMutation(() => {
        if (path === "type") {
          const replacement = model.createObjective(value, index);
          replacement.id = objective.id || replacement.id;
          replacement.optional = objective.optional;
          stage.objectives[index] = replacement;
        } else if (path === "id") {
          const previous = objective.id;
          objective.id = value;
          stage.complete_when = (stage.complete_when || []).map((id) => id === previous ? value : id);
        } else setNested(objective, path, value, { deleteEmpty: true });
        syncCompletionRules(stage);
      });
      return;
    }
    if (control.dataset.dialogueField && stage) {
      commitMutation(() => setNested(stage.dialogue[selectedSlot], control.dataset.dialogueField, value, { deleteEmpty: true }));
      return;
    }
    if (control.dataset.responseField && stage) {
      const response = stage.dialogue?.[selectedSlot]?.responses?.[Number(control.dataset.responseIndex)];
      if (!response) return;
      const path = control.dataset.responseField;
      commitMutation(() => updateResponse(response, path, value, quest));
      return;
    }
    const scene = currentScene();
    if (control.dataset.sceneField && scene) {
      commitMutation(() => { const previous = scene.id; setNested(scene, control.dataset.sceneField, value, { deleteEmpty: false }); if (control.dataset.sceneField === "id" && selectedSceneId === previous) selectedSceneId = value; });
      return;
    }
    if (control.dataset.actorField && scene) {
      commitMutation(() => setNested(scene.actors[Number(control.dataset.actorIndex)], control.dataset.actorField, value, { deleteEmpty: true }));
      return;
    }
    if (control.dataset.stepField && scene) {
      commitMutation(() => setNested(scene.steps[Number(control.dataset.stepIndex)], control.dataset.stepField, value, { deleteEmpty: true }));
    }
  }

  function updateResponse(response, path, value, quest) {
    if (path === "outcome") {
      for (const key of ["stage", "next", "scene", "complete", "abandon", "fail", "transition"]) delete response[key];
      if (value === "stage") response.stage = quest.stages.find((stage) => stage.id !== selectedStageId)?.id || "";
      if (value === "scene") response.scene = "scene_id";
      if (value === "complete") response.complete = true;
      if (value === "fail") response.fail = true;
      if (value === "abandon") response.abandon = true;
      return;
    }
    if (path === "destination") {
      if (Object.hasOwn(response, "stage") || Object.hasOwn(response, "next")) response.stage = value;
      else response.scene = value;
      return;
    }
    setNested(response, path, value, { deleteEmpty: true });
  }

  function syncCompletionRules(stage) {
    const required = (stage.objectives || []).filter((objective) => !objective.optional && objective.id).map((objective) => objective.id);
    if (required.length) stage.complete_when = required; else delete stage.complete_when;
  }

  function handleAction(action, trigger) {
    const quest = currentQuest();
    const stage = currentStage();
    if (action === "add-stage") {
      commitMutation(() => {
        const id = model.uniqueId("new_stage", quest.stages.map((item) => item.id));
        const next = model.createStage(id, { title: "New Stage" });
        quest.stages.push(next);
        if (!quest.entry_stage) quest.entry_stage = id;
        selectedStageId = id;
      });
    } else if (action === "duplicate-stage" && stage) {
      commitMutation(() => {
        const copy = model.clone(stage);
        copy.id = model.uniqueId(`${stage.id}_copy`, quest.stages.map((item) => item.id));
        copy.title = `${stage.title || model.titleFromId(stage.id)} Copy`;
        copy.objectives = (copy.objectives || []).map((objective, index) => ({ ...objective, id: model.uniqueId(`${objective.id}_copy`, quest.stages.flatMap((item) => (item.objectives || []).map((entry) => entry.id))) }));
        syncCompletionRules(copy);
        quest.stages.splice(quest.stages.indexOf(stage) + 1, 0, copy);
        selectedStageId = copy.id;
      });
    } else if (action === "delete-stage" && stage) {
      openConfirmation("Delete stage", `Delete “${stage.title || stage.id}”? Any transitions pointing here will be removed.`, "Delete stage", () => {
        commitMutation(() => model.removeStage(quest, stage.id));
        closeModals();
      });
    } else if (action === "add-objective" && stage) {
      commitMutation(() => {
        const objective = model.createObjective("item_check", stage.objectives.length);
        objective.id = model.uniqueId(objective.id, quest.stages.flatMap((item) => (item.objectives || []).map((entry) => entry.id)));
        stage.objectives.push(objective);
        syncCompletionRules(stage);
      });
    } else if (action === "remove-objective" && stage) {
      const index = Number(trigger.dataset.objectiveIndex);
      commitMutation(() => { stage.objectives.splice(index, 1); syncCompletionRules(stage); });
    } else if (action === "enable-slot" && stage) {
      const slotName = trigger.dataset.slot;
      commitMutation(() => {
        stage.dialogue ||= {};
        stage.dialogue[slotName] = model.createDialogueSlot(slotName, quest.metadata?.title || model.titleFromId(quest.id));
      });
    } else if (action === "disable-slot" && stage) {
      const slotName = trigger.dataset.slot;
      openConfirmation("Remove dialogue", `Remove the ${SLOT_LABELS[slotName].toLowerCase()} and its responses from this stage?`, "Remove dialogue", () => {
        commitMutation(() => delete stage.dialogue[slotName]); closeModals();
      });
    } else if (action === "add-response" && stage) {
      commitMutation(() => {
        const responses = stage.dialogue[selectedSlot].responses ||= [];
        responses.push({ id: model.uniqueId("response", responses.map((response) => response.id)), label: "Player response" });
      });
    } else if (action === "remove-response" && stage) {
      const index = Number(trigger.dataset.responseIndex);
      commitMutation(() => stage.dialogue[selectedSlot].responses.splice(index, 1));
    } else if (action === "duplicate-quest") {
      duplicateQuest();
    } else if (action === "delete-quest") {
      deleteQuest();
    } else if (action === "copy-json") {
      copyText(JSON.stringify(model.stripBuilderFields(quest), null, 2));
    } else if (action === "download-json") {
      downloadText(JSON.stringify(model.stripBuilderFields(quest), null, 2) + "\n", model.questFilePath(quest).split("/").at(-1));
    } else if (action === "apply-json") {
      applyRawJson();
    } else if (action === "format-json") {
      formatRawJson();
    } else if (action === "reset-json") {
      rawJsonDraft = ""; rawJsonError = ""; render();
    } else if (action === "center-graph") {
      document.querySelector(".graph-scroll")?.scrollTo({ left: 0, top: 0, behavior: "smooth" });
    } else if (action === "add-scene") {
      commitMutation(() => { const scene = model.createScene(project.namespace); scene.id = model.uniqueId(scene.id, project.scenes.map((item) => item.id)); project.scenes.push(scene); selectedSceneId = scene.id; });
    } else if (action === "delete-scene" && currentScene()) {
      commitMutation(() => { project.scenes = project.scenes.filter((item) => item !== currentScene()); selectedSceneId = project.scenes[0]?.id || ""; });
    } else if (action === "add-scene-actor" && currentScene()) {
      commitMutation(() => currentScene().actors.push({ alias: model.uniqueId("actor", currentScene().actors.map((item) => item.alias)), type: "villagerretaliation:villager", required: true, binding_source: "unbound", replacement_policy: "fixed", missing_actor_policy: "block" }));
    } else if (action === "remove-scene-actor" && currentScene()) {
      commitMutation(() => currentScene().actors.splice(Number(trigger.dataset.actorIndex), 1));
    } else if (action === "add-scene-step" && currentScene()) {
      commitMutation(() => currentScene().steps.push({ id: model.uniqueId("new_step", currentScene().steps.map((item) => item.id)), type: "villagerretaliation:wait_ticks", data: { ticks: 20 } }));
    } else if (action === "remove-scene-step" && currentScene()) {
      commitMutation(() => currentScene().steps.splice(Number(trigger.dataset.stepIndex), 1));
    }
  }

  function duplicateQuest() {
    const quest = currentQuest();
    commitMutation(() => {
      const copy = model.clone(quest);
      const namespace = quest.id?.split(":")[0] || project.namespace;
      const path = quest.id?.split(":")[1] || "quest";
      copy.id = uniqueQuestId(`${namespace}:${path}_copy`);
      copy.metadata ||= {};
      copy.metadata.title = `${copy.metadata.title || model.titleFromId(quest.id)} Copy`;
      copy.ui ||= {};
      copy.ui.title = copy.metadata.title;
      project.quests.push(copy);
      project.selectedQuestId = copy.id;
      selectedStageId = copy.entry_stage;
    });
    showToast("Quest duplicated.");
  }

  function deleteQuest() {
    const quest = currentQuest();
    if (project.quests.length === 1) {
      showToast("A project must contain at least one quest.", true);
      return;
    }
    openConfirmation("Delete quest", `Delete “${quest.metadata?.title || quest.id}” from this project?`, "Delete quest", () => {
      commitMutation(() => {
        project.quests = project.quests.filter((item) => item !== quest);
        project.selectedQuestId = project.quests[0].id;
        selectedStageId = project.quests[0].entry_stage;
      });
      closeModals();
    });
  }

  function uniqueQuestId(requested) {
    const existing = new Set(project.quests.map((quest) => quest.id));
    if (!existing.has(requested)) return requested;
    const [namespace, path] = requested.split(":");
    let index = 2;
    while (existing.has(`${namespace}:${path}_${index}`)) index += 1;
    return `${namespace}:${path}_${index}`;
  }

  function createBlankQuest(namespace) {
    const quest = model.createLinearQuest(namespace);
    quest.id = `${model.namespaceify(namespace)}:untitled_quest`;
    quest.metadata = { title: "Untitled Quest", description: "", questline: "" };
    quest.entry_stage = "start";
    quest.stages = [model.createStage("start", { title: "Start", empty: true })];
    quest.rewards = {};
    quest.ui = { title: "Untitled Quest", icon: "minecraft:book" };
    return quest;
  }

  function addFromTemplate(template) {
    const make = () => template === "branching" ? model.createBranchingQuest(project.namespace) : template === "blank" ? createBlankQuest(project.namespace) : model.createLinearQuest(project.namespace);
    if (templateMode === "new") {
      const freshQuest = make();
      project = model.normalizeProject({ version: 1, name: "Quest Project", namespace: project.namespace, selectedQuestId: freshQuest.id, quests: [freshQuest] });
      undoStack = [];
      redoStack = [];
      activeView = "setup";
      selectedStageId = freshQuest.entry_stage;
      scheduleSave();
      render();
      closeModals();
      showToast("New project created.");
      return;
    }
    commitMutation(() => {
      const quest = make();
      quest.id = uniqueQuestId(quest.id);
      project.quests.push(quest);
      project.selectedQuestId = quest.id;
      selectedStageId = quest.entry_stage;
      activeView = "setup";
    });
    closeModals();
    showToast("Quest added to the project.");
  }

  function applyRawJson() {
    const textarea = document.querySelector("#raw-json");
    if (!textarea) return;
    rawJsonDraft = textarea.value;
    try {
      const parsed = JSON.parse(rawJsonDraft);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("Quest JSON must contain one object.");
      const previous = currentQuest();
      commitMutation(() => {
        const index = project.quests.indexOf(previous);
        project.quests[index] = parsed;
        project.selectedQuestId = parsed.id || previous.id;
      });
      rawJsonDraft = "";
      rawJsonError = "";
      showToast("JSON changes applied.");
    } catch (error) {
      rawJsonError = error.message;
      render();
      document.querySelector("#raw-json")?.focus();
    }
  }

  function formatRawJson() {
    const textarea = document.querySelector("#raw-json");
    if (!textarea) return;
    try {
      rawJsonDraft = JSON.stringify(JSON.parse(textarea.value), null, 2);
      rawJsonError = "";
      textarea.value = rawJsonDraft;
    } catch (error) {
      rawJsonDraft = textarea.value;
      rawJsonError = error.message;
      render();
    }
  }

  async function copyText(value) {
    try { await navigator.clipboard.writeText(value); showToast("Quest JSON copied."); }
    catch { showToast("The browser blocked clipboard access.", true); }
  }

  function downloadText(value, name, type = "application/json") {
    downloadBlob(new Blob([value], { type }), name);
  }

  function downloadBlob(blob, name) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  }

  function createDatapackFiles() {
    const files = {
      "pack.mcmeta": JSON.stringify({
        pack: {
          pack_format: 34,
          description: project.name || "Villager Retaliation quest pack"
        },
        villagerretaliation: {
          pack_version: "1.0.0-beta.12"
        }
      }, null, 2) + "\n"
    };
    for (const quest of project.quests) {
      const path = model.questFilePath(quest);
      if (Object.hasOwn(files, path)) throw new Error(`Two quests export to ${path}. Give them different ids.`);
      files[path] = JSON.stringify(model.stripBuilderFields(quest), null, 2) + "\n";
    }
    for (const scene of project.scenes || []) {
      const path = model.sceneFilePath(scene);
      if (Object.hasOwn(files, path)) throw new Error(`Two resources export to ${path}. Give them different ids.`);
      files[path] = JSON.stringify(model.stripBuilderFields(scene), null, 2) + "\n";
    }
    return files;
  }

  function exportDatapack() {
    try {
      const bytes = zipUtils.createZip(createDatapackFiles());
      downloadBlob(new Blob([bytes], { type: "application/zip" }), `${model.slugify(project.name, "quest_pack")}.zip`);
      closeModals();
      showToast(`Exported ${project.quests.length} quest${project.quests.length === 1 ? "" : "s"} as a datapack.`);
    } catch (error) {
      showToast(error.message, true);
    }
  }

  function openConfirmation(title, message, actionLabel, callback) {
    els.confirmTitle.textContent = title;
    els.confirmMessage.textContent = message;
    els.confirmAction.textContent = actionLabel;
    openModal(els.confirmDialog);
    pendingConfirmation = callback;
  }

  function openModal(modal) {
    closeModals();
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    window.requestAnimationFrame(() => modal.querySelector("button, input, select, textarea")?.focus());
  }

  function closeModals() {
    for (const modal of document.querySelectorAll(".modal-overlay")) {
      modal.classList.remove("is-open");
      modal.setAttribute("aria-hidden", "true");
    }
    pendingConfirmation = null;
  }

  function drawGraphLines() {
    const graph = document.querySelector("#quest-graph");
    const svg = graph?.querySelector(".graph-lines");
    if (!graph || !svg) return;
    const bounds = graph.getBoundingClientRect();
    svg.setAttribute("viewBox", `0 0 ${graph.scrollWidth} ${graph.scrollHeight}`);
    const paths = [];
    for (const edge of model.collectEdges(currentQuest())) {
      const from = graph.querySelector(`[data-node-id="${cssEscape(edge.from)}"]`);
      const to = graph.querySelector(`[data-node-id="${cssEscape(edge.to)}"]`);
      if (!from || !to) continue;
      const a = from.getBoundingClientRect();
      const b = to.getBoundingClientRect();
      const x1 = a.right - bounds.left;
      const y1 = a.top - bounds.top + a.height / 2;
      const x2 = b.left - bounds.left;
      const y2 = b.top - bounds.top + b.height / 2;
      const bend = Math.max(24, Math.abs(x2 - x1) * .42);
      const direction = x2 >= x1 ? 1 : -1;
      const d = `M ${x1} ${y1} C ${x1 + bend * direction} ${y1}, ${x2 - bend * direction} ${y2}, ${x2} ${y2}`;
      paths.push(`<path class="${edge.kind === "response" ? "is-branch" : ""}" d="${d}" marker-end="url(#arrow)"></path>`);
    }
    svg.innerHTML = `<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#626262"></path></marker></defs>${paths.join("")}`;
  }

  function cssEscape(value) {
    return window.CSS?.escape ? window.CSS.escape(value) : String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[character]));
  }

  function handleIssueNavigation(path) {
    const match = String(path).match(/^\/stages\/(\d+)(?:\/dialogue\/([^/]+))?/);
    if (match) {
      const stage = currentQuest().stages[Number(match[1])];
      if (stage) selectedStageId = stage.id;
      if (match[2]) { activeView = "dialogue"; selectedSlot = match[2]; }
      else activeView = "stages";
    } else if (path.startsWith("/rewards")) activeView = "rewards";
    else activeView = "setup";
    render();
    window.requestAnimationFrame(() => els.editor.querySelector('[aria-invalid="true"]')?.focus());
  }

  els.workflowNav.addEventListener("click", (event) => {
    const button = event.target.closest("[data-view]");
    if (!button) return;
    activeView = button.dataset.view;
    rawJsonDraft = "";
    render();
    els.editor.focus();
  });

  els.questList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-select-quest]");
    if (!button) return;
    project.selectedQuestId = button.dataset.selectQuest;
    selectedStageId = currentQuest().entry_stage || currentQuest().stages?.[0]?.id || "";
    rawJsonDraft = "";
    render();
  });

  els.stageShortcuts.addEventListener("click", (event) => {
    const button = event.target.closest("[data-select-stage]");
    if (!button) return;
    selectedStageId = button.dataset.selectStage;
    render();
  });

  els.editor.addEventListener("click", (event) => {
    const view = event.target.closest("[data-view]");
    if (view) { activeView = view.dataset.view; render(); return; }
    const stageButton = event.target.closest("[data-select-stage]");
    if (stageButton) { selectedStageId = stageButton.dataset.selectStage; render(); return; }
    const slotButton = event.target.closest("[data-select-slot]");
    if (slotButton) { selectedSlot = slotButton.dataset.selectSlot; render(); return; }
    const action = event.target.closest("[data-action]");
    if (action) handleAction(action.dataset.action, action);
  });

  els.editor.addEventListener("change", (event) => {
    const control = event.target.closest("input, select, textarea");
    if (!control) return;
    if (control.id === "raw-json") { rawJsonDraft = control.value; return; }
    if (control.matches("[data-select-stage-control]")) { selectedStageId = control.value; render(); return; }
    if (control.matches("[data-scene-select]")) { selectedSceneId = control.value; render(); return; }
    updateFromControl(control);
  });

  els.editor.addEventListener("input", (event) => {
    if (event.target.id === "raw-json") rawJsonDraft = event.target.value;
  });

  els.projectName.addEventListener("change", () => commitMutation(() => { project.name = els.projectName.value.trim() || "Quest Project"; }));
  els.undoButton.addEventListener("click", undo);
  els.redoButton.addEventListener("click", redo);
  els.addQuestButton.addEventListener("click", () => { templateMode = "add"; openModal(els.templateDialog); });
  els.newProjectButton.addEventListener("click", () => {
    openConfirmation("Create a new project", "Your current project is saved locally, but the active draft will be replaced. Export it first if you need a separate copy.", "Choose template", () => {
      closeModals(); templateMode = "new"; openModal(els.templateDialog);
    });
  });
  els.confirmAction.addEventListener("click", () => pendingConfirmation?.());
  els.checksToggle.addEventListener("click", () => {
    els.checksPanel.classList.toggle("is-collapsed");
    const collapsed = els.checksPanel.classList.contains("is-collapsed");
    els.checksToggle.setAttribute("aria-label", collapsed ? "Expand checks" : "Collapse checks");
    els.checksToggle.innerHTML = `<i data-lucide="${collapsed ? "panel-right-open" : "panel-right-close"}"></i>`;
    renderIcons();
    if (activeView === "stages") window.requestAnimationFrame(drawGraphLines);
  });
  els.checksList.addEventListener("click", (event) => {
    const issue = event.target.closest("[data-issue-path]");
    if (issue) handleIssueNavigation(issue.dataset.issuePath);
  });
  els.importButton.addEventListener("click", () => els.importInput.click());
  els.exportButton.addEventListener("click", () => renderExportDialog());
  els.importInput.addEventListener("change", () => importFiles([...els.importInput.files]));

  document.addEventListener("click", (event) => {
    const template = event.target.closest("[data-template]");
    if (template) addFromTemplate(template.dataset.template);
    if (event.target.closest("[data-close-modal]")) closeModals();
    if (event.target.classList.contains("modal-overlay")) closeModals();
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeModals();
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "z" && !event.shiftKey) { event.preventDefault(); undo(); }
    if ((event.ctrlKey || event.metaKey) && (event.key.toLowerCase() === "y" || (event.key.toLowerCase() === "z" && event.shiftKey))) { event.preventDefault(); redo(); }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") { event.preventDefault(); saveProject(); showToast("Project saved locally."); }
  });
  window.addEventListener("resize", () => { if (activeView === "stages") window.requestAnimationFrame(drawGraphLines); });

  function renderExportDialog() {
    const allIssues = model.validateProject(project, registryMetadata);
    const errors = allIssues.filter((issue) => issue.severity === "error");
    els.exportDialogContent.innerHTML = `${errors.length ? `<div class="status-strip error"><i data-lucide="circle-x"></i><div><strong>${errors.length} blocking error${errors.length === 1 ? "" : "s"}</strong><span>Fix the errors before exporting a datapack. Individual JSON files remain available for troubleshooting.</span></div></div>` : `<div class="status-strip valid"><i data-lucide="circle-check"></i><div><strong>Project is ready</strong><span>All ${project.quests.length} quest${project.quests.length === 1 ? "" : "s"} pass browser validation.</span></div></div>`}
      <div class="export-option"><div><strong>Current quest JSON</strong><p>Download ${escapeHtml(model.questFilePath(currentQuest()))}.</p></div><button class="button button-secondary" type="button" data-export="quest"><i data-lucide="file-json"></i>Download</button></div>
      <div class="export-option"><div><strong>Editable project backup</strong><p>Preserves every quest so this project can be imported later.</p></div><button class="button button-secondary" type="button" data-export="project"><i data-lucide="archive"></i>Download</button></div>
      <div class="export-option"><div><strong>Datapack zip</strong><p>Creates pack.mcmeta and the correct data/&lt;namespace&gt;/quests paths.</p></div><button class="button button-primary" type="button" data-export="datapack" ${errors.length ? "disabled" : ""}><i data-lucide="package-check"></i>Export pack</button></div>`;
    openModal(els.exportDialog);
    renderIcons();
  }

  els.exportDialogContent.addEventListener("click", (event) => {
    const button = event.target.closest("[data-export]");
    if (!button) return;
    if (button.dataset.export === "quest") downloadText(JSON.stringify(model.stripBuilderFields(currentQuest()), null, 2) + "\n", model.questFilePath(currentQuest()).split("/").at(-1));
    if (button.dataset.export === "project") downloadText(JSON.stringify(project, null, 2) + "\n", `${model.slugify(project.name, "quest_project")}.vr-quests.json`);
    if (button.dataset.export === "datapack") exportDatapack();
  });

  async function importFiles(files) {
    els.importInput.value = "";
    if (!files.length) return;
    const imported = [];
    const importedScenes = [];
    const failures = [];
    for (const file of files) {
      try {
        if (/\.zip$/i.test(file.name)) {
          const packFiles = await zipUtils.readZip(new Uint8Array(await file.arrayBuffer()));
          const questFiles = zipUtils.decodeJsonFiles(packFiles, (path) => /^data\/[a-z0-9_.-]+\/quests\/.+\.json$/i.test(path));
          const quests = questFiles.map((entry) => entry.value).filter((value) => value?.schema === model.SCHEMA_ID);
          const sceneFiles = zipUtils.decodeJsonFiles(packFiles, (path) => /^data\/[a-z0-9_.-]+\/quest_scenes\/.+\.json$/i.test(path));
          importedScenes.push(...sceneFiles.map((entry) => entry.value).filter((value) => value?.schema === model.SCENE_SCHEMA_ID));
          if (!quests.length && !importedScenes.length) throw new Error("No Quest v2 or scene v1 files were found in the datapack.");
          imported.push(...quests);
        } else {
          const parsed = JSON.parse(await file.text());
          if (parsed?.version && Array.isArray(parsed.quests)) { imported.push(...parsed.quests); importedScenes.push(...(parsed.scenes || [])); }
          else if (parsed?.schema === model.SCHEMA_ID) imported.push(parsed);
          else if (parsed?.schema === model.SCENE_SCHEMA_ID) importedScenes.push(parsed);
          else throw new Error("This is not a Quest v2, scene v1, or Quest Builder project backup.");
        }
      } catch (error) { failures.push(`${file.name}: ${error.message}`); }
    }
    if (imported.length || importedScenes.length) {
      commitMutation(() => {
        for (const quest of imported) {
          const index = project.quests.findIndex((existing) => existing.id === quest.id);
          if (index >= 0) project.quests[index] = quest;
          else project.quests.push(quest);
        }
        for (const scene of importedScenes) {
          const index = project.scenes.findIndex((existing) => existing.id === scene.id);
          if (index >= 0) project.scenes[index] = scene; else project.scenes.push(scene);
        }
        if (imported.length) { project.selectedQuestId = imported.at(-1).id; selectedStageId = imported.at(-1).entry_stage || imported.at(-1).stages?.[0]?.id || ""; }
        if (importedScenes.length) selectedSceneId = importedScenes.at(-1).id;
      });
      showToast(`Imported ${imported.length} quest${imported.length === 1 ? "" : "s"} and ${importedScenes.length} scene${importedScenes.length === 1 ? "" : "s"}.`);
    }
    if (failures.length) showToast(failures.join(" "), true);
  }

  loadProject();
  render();
  scheduleSave();
  loadMetadata();
  if (startupMessage) window.setTimeout(() => showToast(startupMessage, startupMessage.includes("damaged")), 120);
})();
