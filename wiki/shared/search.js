(() => {
  "use strict";

  const preparedItems = new WeakMap();
  const lowSignalTokens = new Set(["json", "example", "examples", "guide", "page", "wiki", "datapack", "data", "pack"]);
  const synonymMap = new Map([
    ["rep", ["reputation", "trust"]],
    ["xp", ["experience"]],
    ["job", ["jobs", "profession", "work"]],
    ["jobs", ["job", "profession", "work"]],
    ["hire", ["hiring", "workforce"]],
    ["hired", ["hiring", "workforce"]],
    ["raid", ["raids", "siege", "defense"]],
    ["raids", ["raid", "siege", "defense"]],
    ["trade", ["trades", "market"]],
    ["trades", ["trade", "market"]],
    ["sell", ["market", "price"]],
    ["prices", ["price", "market", "sell"]],
    ["gift", ["gifts"]],
    ["gifts", ["gift"]],
    ["dialog", ["dialogue"]],
    ["dialogs", ["dialogue"]],
    ["notify", ["notification", "notifications"]],
    ["datapack", ["data", "pack"]],
    ["resourcepack", ["resource", "pack"]],
    ["json", ["schema", "configuration"]],
    ["scene", ["scenes", "cinematic"]],
    ["scenes", ["scene", "cinematic"]],
    ["villagers", ["villager"]]
  ]);

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function normalize(value) {
    return String(value ?? "")
      .replace(/([a-z])([A-Z])/g, "$1 $2")
      .normalize("NFKD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase()
      .replace(/&/g, " and ")
      .replace(/[^a-z0-9]+/g, " ")
      .trim()
      .replace(/\s+/g, " ");
  }

  function wordVariants(token) {
    const variants = new Map([[token, 1]]);
    const add = (value, weight) => {
      if (value) variants.set(value, Math.max(weight, variants.get(value) || 0));
    };
    if (token.length > 4 && token.endsWith("ies")) add(token.slice(0, -3) + "y", 0.92);
    if (token.length > 5 && token.endsWith("ing")) add(token.slice(0, -3), 0.92);
    if (token.length > 4 && token.endsWith("ed")) add(token.slice(0, -2), 0.92);
    if (token.length > 4 && token.endsWith("es")) add(token.slice(0, -2), 0.92);
    if (token.length > 3 && token.endsWith("s")) add(token.slice(0, -1), 0.92);
    for (const related of synonymMap.get(token) || []) add(related, 0.72);
    return [...variants].map(([word, weight]) => ({ word, weight }));
  }

  function editDistanceWithin(left, right, maximum) {
    if (Math.abs(left.length - right.length) > maximum) return maximum + 1;
    let previous = Array.from({ length: right.length + 1 }, (_, index) => index);
    for (let row = 1; row <= left.length; row += 1) {
      const current = [row];
      let rowMinimum = current[0];
      for (let column = 1; column <= right.length; column += 1) {
        const substitution = previous[column - 1] + (left[row - 1] === right[column - 1] ? 0 : 1);
        current[column] = Math.min(previous[column] + 1, current[column - 1] + 1, substitution);
        rowMinimum = Math.min(rowMinimum, current[column]);
      }
      if (rowMinimum > maximum) return maximum + 1;
      previous = current;
    }
    return previous[right.length];
  }

  function isAdjacentTransposition(left, right) {
    if (left.length !== right.length) return false;
    const differences = [];
    for (let index = 0; index < left.length; index += 1) {
      if (left[index] !== right[index]) differences.push(index);
      if (differences.length > 2) return false;
    }
    return differences.length === 2
      && differences[1] === differences[0] + 1
      && left[differences[0]] === right[differences[1]]
      && left[differences[1]] === right[differences[0]];
  }

  function prepare(item) {
    if (preparedItems.has(item)) return preparedItems.get(item);
    const title = normalize(item.title);
    const description = normalize(item.description);
    const type = normalize(item.type);
    const keywords = normalize(item.keywords);
    const prepared = {
      title,
      description,
      type,
      keywords,
      titleWords: title.split(" ").filter(Boolean),
      descriptionWords: description.split(" ").filter(Boolean),
      typeWords: type.split(" ").filter(Boolean),
      keywordWords: keywords.split(" ").filter(Boolean)
    };
    prepared.fuzzyWords = [...new Set([...prepared.titleWords, ...prepared.typeWords, ...prepared.keywordWords])];
    preparedItems.set(item, prepared);
    return prepared;
  }

  function variantScore(variant, prepared) {
    if (prepared.titleWords.includes(variant)) return 52;
    if (prepared.titleWords.some((word) => word.startsWith(variant))) return 42;
    if (prepared.title.includes(variant)) return 31;
    if (prepared.typeWords.includes(variant)) return 29;
    if (prepared.keywordWords.includes(variant)) return 25;
    if (prepared.keywordWords.some((word) => word.startsWith(variant))) return 21;
    if (prepared.descriptionWords.includes(variant)) return 18;
    if (prepared.descriptionWords.some((word) => word.startsWith(variant))) return 14;
    if (prepared.keywords.includes(variant)) return 12;
    if (prepared.description.includes(variant)) return 9;

    if (variant.length < 4) return 0;
    const maximum = variant.length >= 8 ? 2 : 1;
    let fuzzyBest = 0;
    const fields = [
      [prepared.titleWords, 26, 18],
      [prepared.typeWords, 20, 14],
      [prepared.keywordWords, 15, 10]
    ];
    for (const [words, oneEditScore, twoEditScore] of fields) {
      for (const word of words) {
        if (Math.abs(word.length - variant.length) > maximum) continue;
        const distance = isAdjacentTransposition(variant, word) ? 1 : editDistanceWithin(variant, word, maximum);
        if (distance <= maximum) fuzzyBest = Math.max(fuzzyBest, distance === 1 ? oneEditScore : twoEditScore);
      }
    }
    return fuzzyBest;
  }

  function scoreItem(item, query, originalIndex) {
    const prepared = prepare(item);
    const normalizedQuery = normalize(query);
    const tokens = normalizedQuery.split(" ").filter(Boolean);
    if (!tokens.length) return null;

    let score = 0;
    if (prepared.title === normalizedQuery) score += 240;
    else if (prepared.title.startsWith(normalizedQuery)) score += 150;
    else if (prepared.title.includes(normalizedQuery)) score += 105;
    if (prepared.type === normalizedQuery) score += 70;
    else if (prepared.type.includes(normalizedQuery)) score += 38;
    if (prepared.keywords.includes(normalizedQuery)) score += 24;
    if (prepared.description.includes(normalizedQuery)) score += 28;

    const compactQuery = normalizedQuery.replaceAll(" ", "");
    if (tokens.length === 1 && compactQuery.length >= 4) {
      if (prepared.title.replaceAll(" ", "").includes(compactQuery)) score += 55;
      if (prepared.keywords.replaceAll(" ", "").includes(compactQuery)) score += 25;
    }

    for (let tokenIndex = 0; tokenIndex < tokens.length; tokenIndex += 1) {
      const token = tokens[tokenIndex];
      let best = 0;
      for (const variant of wordVariants(token)) {
        best = Math.max(best, variantScore(variant.word, prepared) * variant.weight);
      }
      if (!best) return null;
      const signalWeight = lowSignalTokens.has(token) ? 0.58 : 1;
      const orderWeight = tokenIndex === 0 ? 1.08 : 1;
      score += best * signalWeight * orderWeight;
    }

    return { ...item, score, originalIndex };
  }

  function search(items, query, options = {}) {
    const limit = Math.max(1, Number(options.limit || 12));
    const normalizedQuery = normalize(query);
    if (!normalizedQuery) {
      const ranked = items
        .map((item, originalIndex) => ({ ...item, score: Number(item.boost || 0), originalIndex }))
        .sort((left, right) => Number(right.featured) - Number(left.featured) || right.score - left.score || left.originalIndex - right.originalIndex);
      return { query: "", total: ranked.length, results: ranked.slice(0, limit) };
    }

    const ranked = items
      .map((item, originalIndex) => scoreItem(item, normalizedQuery, originalIndex))
      .filter(Boolean)
      .sort((left, right) => right.score - left.score || left.title.localeCompare(right.title) || left.originalIndex - right.originalIndex);

    return { query: normalizedQuery, total: ranked.length, results: ranked.slice(0, limit) };
  }

  function highlight(value, query) {
    const source = String(value ?? "");
    const queryTokens = normalize(query).split(" ").filter((token) => token.length >= 2);
    const sourceWords = [...new Set(source.match(/[a-zA-Z0-9]+/g) || [])];
    const terms = new Set();

    for (const queryToken of queryTokens) {
      for (const sourceWord of sourceWords) {
        const normalizedWord = normalize(sourceWord);
        for (const variant of wordVariants(queryToken)) {
          const isDirectMatch = normalizedWord === variant.word
            || (variant.word.length >= 3 && normalizedWord.startsWith(variant.word));
          const maximum = queryToken.length >= 8 ? 2 : 1;
          const isFuzzyMatch = variant.word === queryToken
            && queryToken.length >= 4
            && (isAdjacentTransposition(queryToken, normalizedWord)
              || editDistanceWithin(queryToken, normalizedWord, maximum) <= maximum);
          if (isDirectMatch || isFuzzyMatch) {
            terms.add(sourceWord);
            break;
          }
        }
      }
    }

    if (!terms.size) return escapeHtml(source);
    const pattern = [...terms]
      .sort((left, right) => right.length - left.length)
      .map((term) => term.replace(/[.*+?^$()|[\]\\]/g, "\\$&"))
      .join("|");
    const matcher = new RegExp("(" + pattern + ")", "ig");
    return source.split(matcher).map((part, index) => index % 2
      ? "<mark>" + escapeHtml(part) + "</mark>"
      : escapeHtml(part)).join("");
  }

  function cleanTypography(value) {
    return String(value ?? "")
      .replace(/\s*\u2014\s*/g, " - ")
      .replace(/\s*;\s*/g, ", ");
  }

  function cleanTypographyWithin(root) {
    if (!root) return root;
    const documentRef = root.ownerDocument || document;
    const nodeFilter = documentRef.defaultView?.NodeFilter || NodeFilter;
    const walker = documentRef.createTreeWalker(root, nodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        const parent = node.parentElement;
        if (!parent) return nodeFilter.FILTER_REJECT;
        if (parent.closest("pre, code, script, style, textarea")) return nodeFilter.FILTER_REJECT;
        return nodeFilter.FILTER_ACCEPT;
      }
    });
    const textNodes = [];
    while (walker.nextNode()) textNodes.push(walker.currentNode);
    for (const textNode of textNodes) textNode.nodeValue = cleanTypography(textNode.nodeValue);

    const attributeNames = ["aria-label", "placeholder", "title"];
    const elements = [root, ...root.querySelectorAll(attributeNames.map((name) => `[${name}]`).join(","))];
    for (const element of elements) {
      for (const attributeName of attributeNames) {
        if (element.hasAttribute?.(attributeName)) {
          element.setAttribute(attributeName, cleanTypography(element.getAttribute(attributeName)));
        }
      }
    }
    return root;
  }

  function highlightWithin(root, query) {
    if (!root || !normalize(query)) return null;
    const documentRef = root.ownerDocument || document;
    const nodeFilter = documentRef.defaultView?.NodeFilter || NodeFilter;
    const walker = documentRef.createTreeWalker(root, nodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        const parent = node.parentElement;
        if (!parent || !node.nodeValue?.trim()) return nodeFilter.FILTER_REJECT;
        if (parent.closest("mark, script, style, button, input, textarea, svg")) return nodeFilter.FILTER_REJECT;
        return nodeFilter.FILTER_ACCEPT;
      }
    });
    const textNodes = [];
    while (walker.nextNode()) textNodes.push(walker.currentNode);

    for (const textNode of textNodes) {
      const highlighted = highlight(textNode.nodeValue, query);
      if (!highlighted.includes("<mark>")) continue;
      const template = documentRef.createElement("template");
      template.innerHTML = highlighted.replaceAll("<mark>", '<mark class="page-search-hit">');
      textNode.replaceWith(template.content);
    }
    return root.querySelector(".page-search-hit");
  }

  window.VR_WIKI_SEARCH = { cleanTypography, cleanTypographyWithin, highlight, highlightWithin, normalize, search };
})();
