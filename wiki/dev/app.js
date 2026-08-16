const DATA = window.VR_DEVELOPER_WIKI_DATA || { version: "development", pages: [], examples: [], packs: [] };
const els = {
  nav: document.querySelector("#wiki-nav"),
  content: document.querySelector("#wiki-content"),
  toc: document.querySelector("#page-toc"),
  crumb: document.querySelector("#page-crumb"),
  search: document.querySelector("#wiki-search"),
  searchQuery: document.querySelector("#wiki-search-query"),
  palette: document.querySelector("#search-palette"),
  paletteSearch: document.querySelector("#palette-search"),
  paletteResults: document.querySelector("#palette-results"),
  paletteStatus: document.querySelector("#palette-status"),
  menuToggle: document.querySelector("#menu-toggle"),
  copyStatus: document.querySelector("#copy-status"),
  playerWikiLink: document.querySelector("#player-wiki-link")
};
let exampleFilter = "Starter";
let copyStatusTimer = 0;
let paletteActiveIndex = -1;
let cachedSearchItems = null;

if (location.protocol === "file:") els.playerWikiLink.href = "../player/index.html";

function escapeHtml(value) {
  return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}
function slugFor(value) {
  return String(value || "").toLowerCase().replace(/<[^>]+>/g, "").replace(/`([^`]+)`/g, "$1")
    .replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "section";
}
function icon(name, className = "inline-icon") {
  return `<i data-lucide="${escapeHtml(name)}" class="${className}" aria-hidden="true"></i>`;
}
function renderIcons() {
  window.lucide?.createIcons({ attrs: { "stroke-width": 1.8 } });
}
function cleanRenderedCopy(...roots) {
  roots.forEach((root) => window.VR_WIKI_SEARCH.cleanTypographyWithin(root));
}

function pageUrl(slug) {
  return `#/docs/${slug}`;
}
function currentRoute() {
  const value = location.hash.replace(/^#\/?/, "");
  const [routePath, queryString = ""] = value.split("?");
  const searchQuery = new URLSearchParams(queryString).get("search") || "";
  if (!routePath || routePath === "home") return { type: "home", id: "home", searchQuery };
  const [type, ...rest] = routePath.split("/");
  if (["docs", "example", "examples"].includes(type)) return { type, id: rest.join("/"), searchQuery };
  return { type: "docs", id: type, searchQuery };
}
function groupBy(items) {
  return items.reduce((groups, item) => {
    (groups[item.group] ||= []).push(item);
    return groups;
  }, {});
}

function renderNav() {
  const route = currentRoute();
  const groups = groupBy(DATA.pages);
  els.nav.innerHTML = `
    <a class="nav-link ${route.type === "home" ? "is-active" : ""}" href="#/home">${icon("home")}<span>Overview</span></a>
    <a class="nav-link ${route.type.startsWith("example") ? "is-active" : ""}" href="#/examples">
      ${icon("code-xml")}<span>Example Library</span><span class="nav-count">${DATA.examples.length}</span>
    </a>
    ${Object.entries(groups).map(([group, pages]) => `
      <details class="nav-group nav-page-group" ${group === "Getting Started" || pages.some((page) => route.id === page.slug) ? "open" : ""}>
        <summary class="nav-heading">${icon("chevron-right", "disclosure-icon")}<span>${escapeHtml(group)}</span></summary>
        <div class="nav-page-links">
          ${pages.map((page) => `
            <a class="nav-link ${route.type === "docs" && route.id === page.slug ? "is-active" : ""}" href="${pageUrl(page.slug)}">
              ${icon(page.icon)}<span>${escapeHtml(page.title)}</span>
            </a>
          `).join("")}
        </div>
      </details>
    `).join("")}
  `;
  renderIcons();
}

function docHeader(title, description, options = {}) {
  return `
    <header class="doc-header">
      <div class="doc-title-row">${icon(options.icon || "file-text", "title-icon")}<h1>${escapeHtml(title)}</h1></div>
      <p>${escapeHtml(description)}</p>
      ${options.summary || ""}
    </header>
  `;
}
function summaryMarkup(items) {
  return `<div class="developer-summary">${items.map(([itemIcon, text]) => `<span>${icon(itemIcon)}${text}</span>`).join("")}</div>`;
}

function renderHome() {
  const wikiPages = DATA.pages.filter((page) => page.sourceKind === "wiki");
  const groups = groupBy(wikiPages);
  const starters = DATA.examples.filter((example) => example.level === "Starter").length;
  const advanced = DATA.examples.length - starters;
  const paths = [
    ["package-open", "Build your first pack", "Folder layout, namespaces, overrides, testing, and the smallest working files.", "pack-development"],
    ["route", "Author a quest", "Start with one file, then move into branches, scenes, encounters, and recovery.", "first-quest"],
    ["message-square-text", "Write villager dialogue", "Talk options, matching lines, translations, notifications, and event reactions.", "dialogue"],
    ["store", "Change progression and economy", "Gifts, loot, skill trades, sell prices, armor, and builder structures.", "skill-trades"]
  ];
  els.content.innerHTML = `
    ${docHeader("Villager Retaliation Developer Wiki", "Current pack-author documentation for datapacks, resource packs, quests, dialogue, village systems, and integration work.", {
      icon: "code-xml",
      summary: summaryMarkup([
        ["git-branch", `VR ${escapeHtml(DATA.version)}`],
        ["files", `${DATA.pages.length} reference pages`],
        ["code-2", `${starters} starter and ${advanced} advanced JSON examples`],
        ["folder-down", `${DATA.packs.length} complete example packs`]
      ])
    })}
    <section class="doc-section">
      <h2 id="choose-a-path">Choose a path</h2>
      <div class="path-list">${paths.map(([pathIcon, title, description, slug]) => `
        <a class="path-link" href="${pageUrl(slug)}">
          ${icon(pathIcon)}
          <span class="path-copy">
            <span class="link-heading"><strong>${title}</strong><span class="link-meta">Start here</span></span>
            <span>${description}</span>
          </span>
        </a>
      `).join("")}</div>
    </section>
    <section class="doc-section">
      <h2 id="example-library">Learn from working JSON</h2>
      <p>The example library and reference pages are generated together, so copyable snippets and field guidance stay in sync.</p>
      <div class="path-list"><a class="path-link" href="#/examples">
        ${icon("code-xml")}
        <span class="path-copy">
          <span class="link-heading"><strong>Open the example library</strong><span class="link-meta">${DATA.examples.length} examples</span></span>
          <span>Filter by complexity, copy a snippet, and jump back to its authoritative page.</span>
        </span>
      </a></div>
    </section>
    <section class="doc-section">
      <h2 id="all-systems">All authoring areas</h2>
      ${Object.entries(groups).map(([group, pages]) => `
        <h3 class="group-heading">${escapeHtml(group)}</h3>
        <div class="system-list">${pages.map((page) => `
          <a class="system-link" href="${pageUrl(page.slug)}">
            ${icon(page.icon)}
            <span class="system-copy">
              <span class="link-heading"><strong>${escapeHtml(page.title)}</strong><span class="link-meta">${DATA.examples.filter((example) => example.page === page.slug).length} JSON</span></span>
              <span>${escapeHtml(page.description)}</span>
            </span>
          </a>
        `).join("")}</div>
      `).join("")}
    </section>
  `;
  els.crumb.innerHTML = "<span>Developer Wiki</span><strong>Overview</strong>";
  buildToc();
}

function renderRelated(page) {
  const related = page.related.map((slug) => DATA.pages.find((candidate) => candidate.slug === slug)).filter(Boolean);
  if (!related.length) return "";
  return `<section class="doc-section"><h2 id="related-reference">Related reference</h2><div class="related-list">
    ${related.map((item) => `<a class="related-link" href="${pageUrl(item.slug)}">
      ${icon(item.icon)}
      <span class="related-copy">
        <strong>${escapeHtml(item.title)}</strong>
        <span>${escapeHtml(item.description)}</span>
      </span>
    </a>`).join("")}
  </div></section>`;
}

function renderDoc(slug) {
  const page = DATA.pages.find((candidate) => candidate.slug === slug) || DATA.pages[0];
  if (!page) return renderHome();
  const count = DATA.examples.filter((example) => example.page === page.slug).length;
  els.content.innerHTML = `
    ${docHeader(page.title, page.description, {
      icon: page.icon,
      summary: count ? summaryMarkup([["code-2", `${count} copyable JSON example${count === 1 ? "" : "s"}`], ["list-tree", `${page.headings.length} documented sections`]]) : ""
    })}
    <div class="markdown-body">${markdownToHtml(page.markdown)}</div>
    ${renderRelated(page)}
  `;
  els.crumb.innerHTML = `<span>${escapeHtml(page.group)}</span><strong>${escapeHtml(page.title)}</strong>`;
  buildToc();
}

function codeBlock(code, language = "") {
  return `<div class="code-block" data-language="${escapeHtml(language)}">
    <button class="copy-code" type="button">Copy</button><pre><code>${escapeHtml(code)}</code></pre>
  </div>`;
}

function renderExamples(focusedId = "") {
  if (focusedId) exampleFilter = "All";
  const visible = exampleFilter === "All" ? DATA.examples : DATA.examples.filter((item) => item.level === exampleFilter);
  const starters = DATA.examples.filter((item) => item.level === "Starter").length;
  const advanced = DATA.examples.length - starters;
  const filterIcons = { Starter: "sprout", Advanced: "waypoints", All: "list-filter" };
  els.content.innerHTML = `
    ${docHeader("Example Library", "Copy working JSON from every documented datapack surface, then follow the source link for exact field behavior and override rules.", {
      icon: "code-xml",
      summary: summaryMarkup([["sprout", `${starters} starter examples`], ["waypoints", `${advanced} advanced examples`], ["folder-down", `${DATA.packs.length} complete packs`]])
    })}
    <section class="doc-section">
      <h2 id="complete-packs">Complete packs</h2>
      <p>Use snippets to learn a field; use complete packs to see paths, ownership, and several systems working together.</p>
      <div class="pack-list">${DATA.packs.map((pack) => `
        <div class="pack-entry">${icon("folder")}<span class="pack-copy">
          <strong>${escapeHtml(pack.title)}</strong><span>${escapeHtml(pack.description)}</span>
          <span class="pack-files">${pack.files.length} files &middot; <code>example-packs/${escapeHtml(pack.id)}/</code></span>
        </span></div>
      `).join("")}</div>
    </section>
    <section class="doc-section">
      <h2 id="copyable-examples">Copyable examples</h2>
      <div class="example-toolbar" role="group" aria-label="Example complexity">${["Starter", "Advanced", "All"].map((filter) => `
        <button class="example-filter ${filter === exampleFilter ? "is-active" : ""}" type="button" data-example-filter="${filter}">
          ${icon(filterIcons[filter], "example-filter-icon")}
          <span>${filter}</span>
          <span class="example-filter-count">${filter === "All" ? DATA.examples.length : DATA.examples.filter((item) => item.level === filter).length}</span>
        </button>
      `).join("")}</div>
      <div class="example-list">${visible.map((example) => `
        <article id="${example.id}" class="example-entry">
          <div class="example-heading"><h2>${escapeHtml(example.section)}</h2><span class="example-level">${example.level}</span></div>
          <p class="example-origin"><a href="${pageUrl(example.page)}">${escapeHtml(example.pageTitle)}</a></p>
          ${codeBlock(example.code, example.language)}
        </article>
      `).join("")}</div>
    </section>
  `;
  els.crumb.innerHTML = "<span>Developer Wiki</span><strong>Example Library</strong>";
  buildToc();
  if (focusedId) requestAnimationFrame(() => document.getElementById(focusedId)?.scrollIntoView({ block: "start" }));
}

function renderInline(text) {
  const held = [];
  const hold = (html) => {
    const token = `@@H${held.length}@@`;
    held.push(html);
    return token;
  };
  let output = escapeHtml(text);
  output = output.replace(/`([^`]+)`/g, (_, code) => hold(`<code>${code}</code>`));
  output = output.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, href) => {
    const markdownLink = String(href).match(/([^/#]+)\.md(?:#(.+))?$/i);
    if (markdownLink) {
      const target = DATA.pages.find((page) => page.slug === slugFor(markdownLink[1]));
      if (target) {
        const anchor = markdownLink[2] ? `#${slugFor(markdownLink[2])}` : "";
        return hold(`<a href="${pageUrl(target.slug)}${anchor}">${label}</a>`);
      }
    }
    if (String(href).startsWith("#")) return hold(`<a href="${escapeHtml(href)}">${label}</a>`);
    return hold(`<a href="${escapeHtml(href)}" target="_blank" rel="noreferrer">${label}</a>`);
  });
  output = output.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  output = output.replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, "<em>$1</em>");
  return output.replace(/@@H(\d+)@@/g, (_, index) => held[Number(index)] || "");
}

function markdownToHtml(markdown) {
  const lines = String(markdown || "").split(/\r?\n/);
  let html = "", paragraph = [], listType = "", listItems = [], tableRows = [], language = "", code = [];
  let inCode = false, skippedTitle = false;
  const flushParagraph = () => {
    if (paragraph.length) html += `<p>${renderInline(paragraph.join(" "))}</p>`;
    paragraph = [];
  };
  const flushList = () => {
    if (listType) html += `<${listType}>${listItems.map((item) => `<li>${renderInline(item)}</li>`).join("")}</${listType}>`;
    listType = "";
    listItems = [];
  };
  const flushTable = () => {
    if (!tableRows.length) return;
    const rows = tableRows.filter((row) => !/^\|\s*:?-+/.test(row.trim()));
    html += `<div class="table-wrap"><table>${rows.map((row, index) => {
      const cells = row.trim().replace(/^\||\|$/g, "").split("|").map((cell) => cell.trim());
      const tag = index ? "td" : "th";
      return `<tr>${cells.map((cell) => `<${tag}>${renderInline(cell)}</${tag}>`).join("")}</tr>`;
    }).join("")}</table></div>`;
    tableRows = [];
  };
  const flush = () => { flushParagraph(); flushList(); flushTable(); };

  for (const line of lines) {
    if (line.startsWith("```")) {
      flush();
      if (inCode) {
        html += codeBlock(code.join("\n"), language);
        code = [];
        language = "";
      } else {
        language = line.slice(3).trim();
      }
      inCode = !inCode;
      continue;
    }
    if (inCode) {
      code.push(line);
      continue;
    }
    const heading = line.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      flush();
      if (heading[1].length === 1 && !skippedTitle) {
        skippedTitle = true;
        continue;
      }
      const level = Math.max(2, heading[1].length);
      html += `<h${level} id="${slugFor(heading[2])}">${renderInline(heading[2])}</h${level}>`;
      continue;
    }
    if (/^\|.+\|$/.test(line.trim())) {
      flushParagraph();
      flushList();
      tableRows.push(line);
      continue;
    }
    flushTable();
    const unordered = line.match(/^\s*-\s+(.+)$/);
    const ordered = line.match(/^\s*\d+\.\s+(.+)$/);
    if (unordered || ordered) {
      flushParagraph();
      const nextType = unordered ? "ul" : "ol";
      if (listType && listType !== nextType) flushList();
      listType = nextType;
      listItems.push((unordered || ordered)[1]);
      continue;
    }
    flushList();
    if (!line.trim()) {
      flushParagraph();
      continue;
    }
    const trimmed = line.trim();
    if (trimmed === "<details>") {
      flushParagraph();
      html += '<details class="reference-panel">';
      continue;
    } else if (trimmed === "</details>") {
      flushParagraph();
      html += "</details>";
      continue;
    }
    const summary = trimmed.match(/^<summary><strong>(.+)<\/strong><\/summary>$/);
    if (summary) {
      flushParagraph();
      html += `<summary><strong>${renderInline(summary[1])}</strong></summary>`;
      continue;
    }
    if (trimmed.startsWith(">")) {
      flushParagraph();
      html += `<blockquote>${renderInline(trimmed.replace(/^>\s?/, ""))}</blockquote>`;
      continue;
    }
    paragraph.push(trimmed);
  }
  flush();
  if (inCode) html += codeBlock(code.join("\n"), language);
  return html;
}

function buildToc() {
  const headings = [...els.content.querySelectorAll("h2[id], h3[id]")];
  els.toc.innerHTML = headings.length ? `<div class="toc-title">On this page</div>${headings.map((heading) => `
    <a class="toc-link ${heading.tagName === "H3" ? "is-sub" : ""}" href="#${heading.id}" data-toc-target="${heading.id}">${escapeHtml(heading.textContent)}</a>
  `).join("")}` : "";
}

function searchIndex() {
  if (cachedSearchItems) return cachedSearchItems;

  const pageResults = DATA.pages.map((page) => ({
    kind: "page",
    type: page.group || "Reference",
    title: page.title,
    description: page.description,
    url: pageUrl(page.slug),
    icon: page.icon || "file-text",
    keywords: `${page.text || ""} ${(page.headings || []).map((heading) => heading.title).join(" ")} json schema configuration datapack developer guide reference`,
    featured: page.group === "Getting Started",
    boost: page.group === "Getting Started" ? 25 : 0
  }));
  const exampleResults = DATA.examples.map((example) => ({
    kind: "example",
    type: `${example.level} example`,
    title: example.section,
    description: `${example.pageTitle} - Copyable JSON`,
    url: `#/example/${example.id}`,
    icon: "code-2",
    keywords: `${example.code} json snippet datapack example ${example.level}`,
    featured: example.level === "Starter",
    boost: example.level === "Starter" ? 4 : 0
  }));

  cachedSearchItems = [...pageResults, ...exampleResults];
  return cachedSearchItems;
}

function runWikiSearch(query, limit = 12) {
  return window.VR_WIKI_SEARCH.search(searchIndex(), query, { limit });
}
function searchResultUrl(url, query) {
  const value = String(query || "").trim();
  if (!value) return url;
  return `${url}${url.includes("?") ? "&" : "?"}search=${encodeURIComponent(value)}`;
}

function paletteResultElements() {
  return [...els.paletteResults.querySelectorAll(".palette-result")];
}

function setPaletteActive(index, options = {}) {
  const results = paletteResultElements();
  if (!results.length) {
    paletteActiveIndex = -1;
    els.paletteSearch.removeAttribute("aria-activedescendant");
    return;
  }

  paletteActiveIndex = Math.max(0, Math.min(index, results.length - 1));
  results.forEach((result, resultIndex) => {
    const isCurrent = resultIndex === paletteActiveIndex;
    result.classList.toggle("is-current", isCurrent);
    result.setAttribute("aria-selected", String(isCurrent));
  });

  const activeResult = results[paletteActiveIndex];
  els.paletteSearch.setAttribute("aria-activedescendant", activeResult.id);
  if (options.scroll !== false) activeResult.scrollIntoView({ block: "nearest" });
}

function movePaletteSelection(direction) {
  const results = paletteResultElements();
  if (!results.length) return;
  const current = paletteActiveIndex < 0 ? 0 : paletteActiveIndex;
  setPaletteActive((current + direction + results.length) % results.length);
}

function renderPaletteResults() {
  const query = els.paletteSearch.value.trim();
  const outcome = runWikiSearch(query, 12);
  els.paletteStatus.textContent = query
    ? `${outcome.total} result${outcome.total === 1 ? "" : "s"} for "${query}"`
    : "Suggested references and examples";

  els.paletteResults.innerHTML = outcome.results.length ? outcome.results.map((result, index) => `
    <a id="palette-result-${index}" class="palette-result ${index === 0 ? "is-current" : ""}" href="${searchResultUrl(result.url, query)}" role="option" aria-selected="${index === 0}">
      ${icon(result.icon || "file-text")}
      <span>${escapeHtml(result.type)}</span>
      <strong>${window.VR_WIKI_SEARCH.highlight(result.title, query)}</strong>
      <p>${window.VR_WIKI_SEARCH.highlight(result.description, query)}</p>
    </a>
  `).join("") : `
    <div class="palette-empty">
      <strong>No results for "${escapeHtml(query)}".</strong>
      <span>Try fewer words, check the spelling, or search for a system, JSON field, example, or pack feature.</span>
    </div>
  `;
  cleanRenderedCopy(els.paletteResults, els.paletteStatus);
  renderIcons();
  setPaletteActive(outcome.results.length ? 0 : -1, { scroll: false });
}
function openPalette() {
  renderPaletteResults();
  document.body.classList.add("is-search-open");
  els.palette.classList.add("is-open");
  els.palette.setAttribute("aria-hidden", "false");
  els.search.setAttribute("aria-expanded", "true");
  requestAnimationFrame(() => {
    els.paletteSearch.focus();
    els.paletteSearch.select();
  });
}
function closePalette() {
  document.body.classList.remove("is-search-open");
  els.palette.classList.remove("is-open");
  els.palette.setAttribute("aria-hidden", "true");
  els.search.setAttribute("aria-expanded", "false");
  els.paletteSearch.removeAttribute("aria-activedescendant");
  paletteActiveIndex = -1;
}
function followPaletteResult(result) {
  if (!result) return;
  closePalette();
  location.hash = result.getAttribute("href");
}
function showCopyStatus() {
  clearTimeout(copyStatusTimer);
  els.copyStatus.textContent = "Copied example to clipboard.";
  els.copyStatus.classList.add("is-visible");
  copyStatusTimer = setTimeout(() => els.copyStatus.classList.remove("is-visible"), 1600);
}
async function copyCode(button) {
  const value = button.closest(".code-block")?.querySelector("code")?.textContent || "";
  try {
    await navigator.clipboard.writeText(value);
  } catch {
    const textarea = document.createElement("textarea");
    textarea.value = value;
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.append(textarea);
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
  }
  button.textContent = "Copied";
  showCopyStatus();
  setTimeout(() => { button.textContent = "Copy"; }, 1200);
}

function render() {
  const route = currentRoute();
  renderNav();
  if (route.type === "home") renderHome();
  else if (route.type === "examples") renderExamples();
  else if (route.type === "example") renderExamples(route.id);
  else renderDoc(route.id);
  renderIcons();
  document.body.classList.remove("is-menu-open");
  els.menuToggle.setAttribute("aria-expanded", "false");
  cleanRenderedCopy(els.nav, els.content, els.toc, els.crumb);
  if (route.searchQuery) {
    window.requestAnimationFrame(() => {
      const scope = route.type === "example" ? document.getElementById(route.id) || els.content : els.content;
      const firstMatch = window.VR_WIKI_SEARCH.highlightWithin(scope, route.searchQuery);
      firstMatch?.scrollIntoView({ behavior: "smooth", block: "center" });
    });
  }
}

els.search.addEventListener("click", openPalette);
els.search.addEventListener("keydown", (event) => {
  if (event.key !== "ArrowDown") return;
  event.preventDefault();
  openPalette();
});
els.paletteSearch.addEventListener("input", () => {
  els.searchQuery.textContent = els.paletteSearch.value || "Search documentation";
  renderPaletteResults();
});
els.paletteSearch.addEventListener("keydown", (event) => {
  if (event.key === "ArrowDown") {
    event.preventDefault();
    movePaletteSelection(1);
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    movePaletteSelection(-1);
  } else if (event.key === "Home") {
    event.preventDefault();
    setPaletteActive(0);
  } else if (event.key === "End") {
    event.preventDefault();
    setPaletteActive(paletteResultElements().length - 1);
  } else if (event.key === "Enter") {
    event.preventDefault();
    followPaletteResult(paletteResultElements()[paletteActiveIndex]);
  }
});
els.paletteResults.addEventListener("mousemove", (event) => {
  const result = event.target.closest(".palette-result");
  if (!result) return;
  setPaletteActive(paletteResultElements().indexOf(result), { scroll: false });
});
els.paletteResults.addEventListener("click", (event) => {
  const result = event.target.closest(".palette-result");
  if (!result) return;
  event.preventDefault();
  followPaletteResult(result);
});
els.palette.addEventListener("click", (event) => { if (event.target === els.palette) closePalette(); });
els.menuToggle.addEventListener("click", () => {
  const open = document.body.classList.toggle("is-menu-open");
  els.menuToggle.setAttribute("aria-expanded", String(open));
});
els.nav.addEventListener("click", () => document.body.classList.remove("is-menu-open"));
els.toc.addEventListener("click", (event) => {
  const link = event.target.closest("[data-toc-target]");
  if (!link) return;
  event.preventDefault();
  document.getElementById(link.dataset.tocTarget)?.scrollIntoView({ behavior: "smooth", block: "start" });
});
els.content.addEventListener("click", (event) => {
  const copyButton = event.target.closest(".copy-code");
  if (copyButton) return void copyCode(copyButton);
  const filter = event.target.closest("[data-example-filter]");
  if (filter) {
    exampleFilter = filter.dataset.exampleFilter;
    renderExamples();
    cleanRenderedCopy(els.content, els.toc, els.crumb);
    renderIcons();
  }
});
document.addEventListener("keydown", (event) => {
  if (event.altKey && event.key.toLowerCase() === "q") {
    event.preventDefault();
    openPalette();
  } else if (event.key === "Escape") {
    closePalette();
  }
});
window.addEventListener("hashchange", render);
if (!location.hash) location.hash = "#/home";
render();
