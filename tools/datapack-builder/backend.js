(() => {
  "use strict";

  function createDatapackBackend(options) {
    const constants = options.constants;
    const dialogueKindKeys = options.dialogueKindKeys;
    const packVersions = options.packVersions;
    const currentPackVersion = options.currentPackVersion;
    const packVersionIds = packVersions.map((version) => version.id);
    const packVersionNamespace = options.packVersionNamespace;
    const packVersionStorageKey = options.packVersionStorageKey;
    const encoder = new TextEncoder();

    function createInitialState() {
      return {
        meta: {
          packName: "Villager Retaliation Pack",
          description: "Custom Villager Retaliation datapack",
          packVersion: currentPackVersion,
          packFormat: 34,
          namespace: "my_pack",
          slug: "my_pack",
          locale: "en_us"
        },
        dialogue: {
          layout: "folders",
          fileName: "my_pack_dialogue",
          folderName: "my_pack",
          options: [],
          lines: [],
          messages: [],
          openings: [],
          closings: [],
          pacify: []
        },
        forcedDialogue: {
          fileName: "my_pack_forced_dialogue",
          entries: []
        },
        quests: {
          modules: [],
          v1Imports: []
        },
        notifications: {
          fileName: "my_pack_notifications",
          notifications: []
        },
        gifts: {
          fileName: "my_pack_gifts",
          preferences: [],
          rewards: []
        },
        pacification: {
          fileName: "my_pack_pacification",
          payments: []
        },
        stories: {
          namespace: "my_pack",
          structureFileName: "my_pack_structures",
          biomeFileName: "my_pack_biomes",
          radius: 96,
          structures: [],
          biomes: []
        },
        names: {
          male_names: [],
          female_names: []
        },
        extraFiles: {}
      };
    }

    function slugify(value, fallback = "my_pack") {
      const slug = String(value || "")
        .trim()
        .toLowerCase()
        .replace(/['"]/g, "")
        .replace(/[^a-z0-9_./-]+/g, "_")
        .replace(/_+/g, "_")
        .replace(/^_+|_+$/g, "");
      return slug || fallback;
    }

    function namespaceify(value, fallback = "my_pack") {
      const namespace = slugify(value, fallback).replace(/[^a-z0-9_.-]/g, "_");
      return namespace || fallback;
    }

    function normalizeFileName(value, fallback) {
      return slugify(value, fallback).replace(/\.json$/i, "");
    }

    function parseList(value) {
      if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean);
      if (typeof value === "string") {
        return value
          .split(/[\n,]+/)
          .map((item) => item.trim())
          .filter(Boolean);
      }
      return [];
    }

    function parseNumber(value) {
      if (value === "" || value === null || value === undefined) return undefined;
      const number = Number(value);
      return Number.isFinite(number) ? number : undefined;
    }

    function parseInteger(value) {
      const number = parseNumber(value);
      return number === undefined ? undefined : Math.trunc(number);
    }

    function unique(values) {
      return [...new Set(parseList(values))];
    }

    function stripTextBom(source) {
      return String(source ?? "").replace(/^\uFEFF/, "");
    }

    function cleanObject(value) {
      if (Array.isArray(value)) {
        return value
          .map((item) => cleanObject(item))
          .filter((item) => item !== undefined && !(Array.isArray(item) && item.length === 0));
      }
      if (value && typeof value === "object" && !(value instanceof Uint8Array)) {
        const result = {};
        for (const [key, child] of Object.entries(value)) {
          if (key.startsWith("__")) continue;
          const cleaned = cleanObject(child);
          const emptyArray = Array.isArray(cleaned) && cleaned.length === 0;
          const emptyObject = cleaned && typeof cleaned === "object" && !Array.isArray(cleaned) && Object.keys(cleaned).length === 0;
          if (cleaned !== undefined && cleaned !== "" && !emptyArray && !emptyObject) {
            result[key] = cleaned;
          }
        }
        return result;
      }
      return value === null ? undefined : value;
    }

    function cleanArray(entries) {
      return Array.isArray(entries) ? entries.map((entry) => cleanObject(entry)) : [];
    }

    function safeJson(value) {
      return JSON.stringify(cleanObject(value), null, 2) + "\n";
    }

    function packVersionInfo(version) {
      return packVersions.find((candidate) => candidate.id === version) || packVersions[packVersions.length - 1];
    }

    function normalizePackVersion(value) {
      if (typeof value !== "string") return "";
      const text = value.trim();
      if (!text) return "";
      const lower = text.toLowerCase();
      return packVersionIds.find((id) => id.toLowerCase() === lower || id.toLowerCase().endsWith(lower)) || "";
    }

    function readPackVersion(json) {
      const vr = json?.[packVersionNamespace] || json?.villager_retaliation || json?.vr;
      return normalizePackVersion(
        vr?.[packVersionStorageKey]
          || vr?.packVersion
          || json?.[packVersionStorageKey]
          || json?.packVersion
      );
    }

    function packVersionIndex(version) {
      return packVersionIds.indexOf(normalizePackVersion(version));
    }

    function packVersionAtLeast(version, minimumVersion) {
      const versionIndex = packVersionIndex(version);
      const minimumIndex = packVersionIndex(minimumVersion);
      return versionIndex >= 0 && minimumIndex >= 0 && versionIndex >= minimumIndex;
    }

    function supportsBeta12DialogueFields(stateOrVersion) {
      const version = typeof stateOrVersion === "string" ? stateOrVersion : stateOrVersion?.meta?.packVersion;
      return packVersionAtLeast(version || currentPackVersion, "1.0.0-beta.12");
    }

    function makePackMeta(state) {
      const version = packVersionInfo(state.meta.packVersion);
      return cleanObject({
        pack: {
          pack_format: state.meta.packFormat || version.packFormat,
          description: state.meta.description || state.meta.packName || "Villager Retaliation datapack"
        },
        [packVersionNamespace]: {
          [packVersionStorageKey]: version.id
        }
      });
    }

    function dialogueKindIndex(segments) {
      return segments.findIndex((segment) => dialogueKindKeys.includes(segment));
    }

    function professionFromDialoguePathSegments(segments) {
      const professionIndex = segments.indexOf("professions");
      if (professionIndex < 0) return "";
      const kindIndex = dialogueKindIndex(segments);
      if (kindIndex <= professionIndex) {
        const afterProfession = segments.slice(professionIndex + 1);
        if (afterProfession.length === 0) return "";
        if (constants.professions.includes(afterProfession[0]) || afterProfession.length === 1) return afterProfession[0];
        return `${afterProfession[0]}:${afterProfession[1]}`;
      }
      const professionSegments = segments.slice(professionIndex + 1, kindIndex);
      if (professionSegments.length === 0) return "";
      if (professionSegments.length === 1 || constants.professions.includes(professionSegments[0])) {
        return professionSegments[0];
      }
      return `${professionSegments[0]}:${professionSegments.slice(1).join("/")}`;
    }

    function contentNamespace(state) {
      return namespaceify(state?.meta?.namespace, "my_pack");
    }

    function dialoguePathInfo(path) {
      const match = path.match(/^data\/([^/]+)\/dialogue\/([^/]+)\/(.+)\.json$/);
      if (!match) return null;
      const relative = match[3];
      const segments = relative.split("/");
      const kindIndex = dialogueKindIndex(segments);
      const kind = kindIndex >= 0 ? segments[kindIndex] : "";
      return {
        namespace: match[1],
        locale: match[2],
        relative,
        kind,
        folderName: kindIndex > 0 ? segments.slice(0, kindIndex).join("/") : "",
        profession: professionFromDialoguePathSegments(segments)
      };
    }

    function dialogueEntriesFromJson(json, kind) {
      if (Array.isArray(json?.[kind])) return cleanArray(json[kind]);
      if (Array.isArray(json)) return cleanArray(json);
      if (!json || typeof json !== "object") return [];
      const entry = cleanObject(json);
      return Object.keys(entry).length > 0 ? [entry] : [];
    }

    function dialogueOutputEntry(path, entry) {
      const result = cleanObject(entry);
      const profession = dialoguePathInfo(path)?.profession;
      if (profession && Array.isArray(result.professions) && result.professions.length === 1 && result.professions[0] === profession) {
        delete result.professions;
      }
      return result;
    }

    function dialogueFilePayload(path, value) {
      const info = dialoguePathInfo(path);
      const payload = Object.fromEntries(dialogueKindKeys
        .filter((key) => Array.isArray(value[key]) && value[key].length > 0)
        .map((key) => [key, value[key].map((entry) => dialogueOutputEntry(path, entry))]));
      const kind = info?.kind;
      if (!kind) return payload;
      const nonEmptyKinds = Object.keys(payload);
      if (nonEmptyKinds.length !== 1 || nonEmptyKinds[0] !== kind) return payload;
      return payload[kind].length === 1 ? payload[kind][0] : { [kind]: payload[kind] };
    }

    function dialogueUsesFolderLayout(state) {
      return supportsBeta12DialogueFields(state) && state.dialogue.layout !== "bundle";
    }

    function dialogueBundlePath(state) {
      return `data/${contentNamespace(state)}/dialogue/${state.meta.locale}/${state.dialogue.fileName}.json`;
    }

    function dialogueFolderName(state) {
      return normalizeFileName(state.dialogue.folderName || state.meta.slug, state.meta.slug || "my_pack");
    }

    function dialogueFileStem(value, fallback) {
      return normalizeFileName(value, fallback)
        .replace(/[:/]+/g, "_")
        .replace(/_+/g, "_")
        .replace(/^_+|_+$/g, "") || fallback;
    }

    function defaultDialogueEntryPath(state, kind, entry = {}, index = 0) {
      if (!dialogueUsesFolderLayout(state)) return dialogueBundlePath(state);
      const stemSource = entry.request || entry.key || entry.id || kind;
      const stem = dialogueFileStem(stemSource, kind);
      const order = String(Math.max(0, index)).padStart(2, "0");
      return `data/${contentNamespace(state)}/dialogue/${state.meta.locale}/${dialogueFolderName(state)}/${kind}/${order}_${stem}.json`;
    }

    function forcedDialoguePath(state) {
      return `data/${contentNamespace(state)}/forced_dialogue/${state.forcedDialogue.fileName}.json`;
    }

    function notificationsPath(state) {
      return `data/villagerretaliation/notifications/${state.meta.locale}/${state.notifications.fileName}.json`;
    }

    function giftsPath(state) {
      return `data/villagerretaliation/gifts/${state.gifts.fileName}.json`;
    }

    function pacificationPath(state) {
      return `data/villagerretaliation/pacification/${state.pacification.fileName}.json`;
    }

    function structurePath(state) {
      return `data/${state.stories.namespace}/story_structures/${state.stories.structureFileName}.json`;
    }

    function biomePath(state) {
      return `data/${state.stories.namespace}/story_biomes/${state.stories.biomeFileName}.json`;
    }

    function namesPath() {
      return "data/villagerretaliation/villager_names/preset_names.json";
    }

    function hasAnyEntries(state, section, keys) {
      return keys.some((key) => state[section][key].length > 0);
    }

    function generatedFiles(state) {
      const files = { ...state.extraFiles };
      files["pack.mcmeta"] = safeJson(makePackMeta(state));

      if (state.quests.modules.length > 0) {
        Object.assign(files, generatedQuestFiles(state));
      }

      if (hasAnyEntries(state, "dialogue", dialogueKindKeys)) {
        Object.assign(files, generatedDialogueFiles(state));
      }

      if (state.forcedDialogue.entries.length > 0) {
        Object.assign(files, generatedForcedDialogueFiles(state));
      }

      if (state.notifications.notifications.length > 0) {
        files[notificationsPath(state)] = safeJson({ notifications: state.notifications.notifications });
      }

      if (hasAnyEntries(state, "gifts", ["preferences", "rewards"])) {
        files[giftsPath(state)] = safeJson({
          preferences: state.gifts.preferences,
          rewards: state.gifts.rewards
        });
      }

      if (state.pacification.payments.length > 0) {
        files[pacificationPath(state)] = safeJson({ payments: state.pacification.payments });
      }

      if (state.stories.structures.length > 0) {
        files[structurePath(state)] = safeJson({
          radius: state.stories.radius || 96,
          entries: state.stories.structures
        });
      }

      if (state.stories.biomes.length > 0) {
        files[biomePath(state)] = safeJson({ entries: state.stories.biomes });
      }

      if (state.names.male_names.length > 0 || state.names.female_names.length > 0) {
        files[namesPath()] = safeJson({
          male_names: state.names.male_names,
          female_names: state.names.female_names
        });
      }

      return files;
    }

    function generatedDialogueFiles(state) {
      const grouped = new Map();
      for (const kind of dialogueKindKeys) {
        for (const [index, entry] of state.dialogue[kind].entries()) {
          const path = entry.__sourcePath || defaultDialogueEntryPath(state, kind, entry, index);
          if (!grouped.has(path)) {
            grouped.set(path, Object.fromEntries(dialogueKindKeys.map((key) => [key, []])));
          }
          grouped.get(path)[kind].push(entry);
        }
      }
      return Object.fromEntries([...grouped.entries()].map(([path, value]) => [path, safeJson(dialogueFilePayload(path, value))]));
    }

    function generatedForcedDialogueFiles(state) {
      const grouped = new Map();
      for (const entry of state.forcedDialogue.entries) {
        const path = entry.__sourcePath || forcedDialoguePath(state);
        if (!grouped.has(path)) grouped.set(path, { entries: [] });
        grouped.get(path).entries.push(entry);
      }
      return Object.fromEntries([...grouped.entries()].map(([path, value]) => [path, safeJson(value)]));
    }

    function generatedQuestFiles(state) {
      const files = {};
      for (const [index, entry] of state.quests.modules.entries()) {
        files[questModulePath(state, entry, index)] = JSON.stringify(stripBuilderFields(entry), null, 2) + "\n";
      }
      return files;
    }

    function stripBuilderFields(value) {
      if (Array.isArray(value)) return value.map(stripBuilderFields);
      if (value && typeof value === "object" && !(value instanceof Uint8Array)) {
        const result = {};
        for (const [key, child] of Object.entries(value)) {
          if (key.startsWith("__")) continue;
          result[key] = stripBuilderFields(child);
        }
        return result;
      }
      return value;
    }

    function questModulePath(state, entry, index = 0) {
      if (entry?.__sourcePath) return entry.__sourcePath;
      const parts = resourceLocationParts(entry?.id);
      const namespace = namespaceify(parts.namespace || contentNamespace(state), contentNamespace(state));
      const fallbackName = `quest_${String(index + 1).padStart(2, "0")}`;
      const fileName = normalizeFileName(entry?.__fileName || parts.path || fallbackName, fallbackName);
      return `data/${namespace}/quests/${fileName}.json`;
    }

    function resourceLocationParts(id) {
      const text = String(id || "").trim();
      const match = text.match(/^([a-z0-9_.-]+):([a-z0-9_./-]+)$/);
      return match ? { namespace: match[1], path: match[2] } : { namespace: "", path: "" };
    }

    function isQuestV2Module(json) {
      return Boolean(json && typeof json === "object" && !Array.isArray(json) && json.schema === "villagerretaliation:quest/v2");
    }

    function isQuestV1Resource(json) {
      return Boolean(
        json
        && typeof json === "object"
        && !Array.isArray(json)
        && !isQuestV2Module(json)
        && typeof json.id === "string"
        && (Array.isArray(json.objectives) || json.offer || json.rules || json.tracker || json.display)
      );
    }

    function normalizeQuestModuleEntry(json, path) {
      const entry = stripBuilderFields(json);
      if (path) entry.__sourcePath = path;
      return entry;
    }

    function normalizeQuestV1Import(json, path) {
      const title = json?.display?.title || json?.title || json?.id || path;
      return {
        id: String(json?.id || ""),
        title: String(title || path),
        sourcePath: path,
        suggestion: "Run the migration tool to create a quest module v2 copy; the builder will not overwrite this legacy resource."
      };
    }

    function upsertQuestV1Import(state, json, path) {
      state.quests.v1Imports = state.quests.v1Imports.filter((entry) => entry.sourcePath !== path);
      state.quests.v1Imports.push(normalizeQuestV1Import(json, path));
    }

    function removeQuestV1Import(state, path) {
      state.quests.v1Imports = state.quests.v1Imports.filter((entry) => entry.sourcePath !== path);
    }

    function replaceQuestModuleFile(state, path, json) {
      const namespaceMatch = path.match(/^data\/([^/]+)\/quests\/.+\.json$/);
      if (namespaceMatch) state.meta.namespace = namespaceify(namespaceMatch[1], state.meta.namespace || "my_pack");
      state.quests.modules = state.quests.modules.filter((entry, index) => questModulePath(state, entry, index) !== path);
      state.quests.modules.push(normalizeQuestModuleEntry(json, path));
      delete state.extraFiles[path];
      removeQuestV1Import(state, path);
    }

    function normalizeImportedPaths(fileMap) {
      const normalizedInput = {};
      for (const [path, value] of Object.entries(fileMap)) {
        normalizedInput[path.replaceAll("\\", "/").replace(/^\/+/, "")] = value;
      }
      const paths = Object.keys(normalizedInput);
      const packPath = paths.find((path) => path === "pack.mcmeta" || path.endsWith("/pack.mcmeta"));
      const normalized = {};
      if (!packPath || packPath === "pack.mcmeta") {
        Object.assign(normalized, normalizedInput);
      } else {
        const prefix = packPath.slice(0, -"pack.mcmeta".length);
        for (const [path, value] of Object.entries(normalizedInput)) {
          normalized[path.startsWith(prefix) ? path.slice(prefix.length) : path] = value;
        }
      }

      return normalizeNamespaceRootImportPaths(normalized);
    }

    function normalizeNamespaceRootImportPaths(fileMap) {
      const paths = Object.keys(fileMap);
      if (paths.some((path) => path.startsWith("data/"))) return fileMap;
      const namespaceRoots = new Set(paths
        .filter(isNamespaceRootDataPath)
        .map((path) => path.split("/")[0]));
      if (namespaceRoots.size === 0) return fileMap;

      const normalized = {};
      for (const [path, value] of Object.entries(fileMap)) {
        const namespaceRoot = path.split("/")[0];
        normalized[namespaceRoots.has(namespaceRoot) ? `data/${path}` : path] = value;
      }
      return normalized;
    }

    function isNamespaceRootDataPath(path) {
      return /^[a-z0-9_.-]+\/(?:dialogue|dialogue_trees|forced_dialogue|notifications|gifts|pacification|quests|villager_names|story_structures|story_biomes)\/.+\.json$/i.test(path);
    }

    function isTextPath(path) {
      return /\.(json|mcmeta|mcfunction|txt|md|lang)$/i.test(path);
    }

    function importedKnownKind(state, path) {
      if (/^data\/[^/]+\/dialogue\/[^/]+\/.+\.json$/.test(path)) return "dialogue";
      if (/^data\/[^/]+\/dialogue_trees\/[^/]+\/.+\.json$/.test(path)) return "dialogue_trees";
      if (/^data\/[^/]+\/forced_dialogue\/.+\.json$/.test(path)) return "forced_dialogue";
      if (/^data\/villagerretaliation\/notifications\/[^/]+\/.+\.json$/.test(path)) return "notifications";
      if (/^data\/villagerretaliation\/gifts\/.+\.json$/.test(path)) return "gifts";
      if (/^data\/villagerretaliation\/pacification\/.+\.json$/.test(path)) return "pacification";
      if (/^data\/[^/]+\/quests\/.+\.json$/.test(path)) return "quests";
      if (/^data\/[^/]+\/story_structures\/.+\.json$/.test(path)) return "story_structures";
      if (/^data\/[^/]+\/story_biomes\/.+\.json$/.test(path)) return "story_biomes";
      if (path === namesPath(state)) return "names";
      return "";
    }

    function inferPackVersionFromFiles(files, beta12DialogueKeys = []) {
      for (const [path, value] of Object.entries(files)) {
        if (path.replace(/^\/+/, "") === "pack.mcmeta" && typeof value === "string") {
          try {
            const version = readPackVersion(JSON.parse(value));
            if (version) return version;
          } catch {
            // Malformed pack metadata is preserved by the importer.
          }
        }
      }
      const paths = Object.keys(files).map((path) => path.replace(/^\/+/, ""));
      const hasBeta12DialogueField = Object.entries(files).some(([path, value]) => (
        /^data\/[^/]+\/dialogue\/.+\.json$/.test(path.replace(/^\/+/, ""))
        && typeof value === "string"
        && jsonContainsAnyKey(value, beta12DialogueKeys)
      ));
      if (hasBeta12DialogueField) return "1.0.0-beta.12";
      const hasBeta12Path = paths.some((path) => (
        /^data\/[^/]+\/dialogue_trees\/[^/]+\/.+\.json$/.test(path)
        || /^data\/[^/]+\/quests\/.+\.json$/.test(path)
      ));
      if (hasBeta12Path) return "1.0.0-beta.12";
      const hasBeta11Path = paths.some((path) => (
        /^data\/[^/]+\/forced_dialogue\/.+\.json$/.test(path)
        || /^data\/villagerretaliation\/pacification\/.+\.json$/.test(path)
        || /^data\/villagerretaliation\/villager_names\/preset_names\.json$/.test(path)
        || /^data\/[^/]+\/story_(structures|biomes)\/.+\.json$/.test(path)
      ));
      return hasBeta11Path ? "1.0.0-beta.11" : "";
    }

    function jsonContainsAnyKey(source, keys) {
      try {
        return valueContainsAnyKey(JSON.parse(stripTextBom(source)), new Set(keys));
      } catch {
        return false;
      }
    }

    function valueContainsAnyKey(value, keys) {
      if (Array.isArray(value)) return value.some((item) => valueContainsAnyKey(item, keys));
      if (!value || typeof value !== "object") return false;
      return Object.entries(value).some(([key, child]) => keys.has(key) || valueContainsAnyKey(child, keys));
    }

    function applyEditedFile(state, path, source) {
      const json = () => parseEditedJson(source);
      if (path === "pack.mcmeta") {
        const parsed = json();
        if (!parsed) return false;
        const pack = parsed.pack || {};
        if (Object.hasOwn(pack, "description")) state.meta.description = pack.description || "";
        if (Object.hasOwn(pack, "pack_format")) {
          const packFormat = Number(pack.pack_format);
          state.meta.packFormat = Number.isFinite(packFormat) ? Math.trunc(packFormat) : pack.pack_format;
        }
        state.meta.packVersion = readPackVersion(parsed) || state.meta.packVersion;
        return true;
      }

      if (path.match(/^data\/[^/]+\/dialogue\/([^/]+)\/(.+)\.json$/)) {
        const parsed = json();
        if (!parsed) return false;
        replaceDialogueFile(state, path, parsed);
        return true;
      }

      if (path.match(/^data\/[^/]+\/forced_dialogue\/(.+)\.json$/)) {
        const parsed = json();
        if (!parsed) return false;
        replaceForcedDialogueFile(state, path, parsed);
        return true;
      }

      if (path.match(/^data\/[^/]+\/quests\/.+\.json$/)) {
        const parsed = json();
        if (!parsed) return false;
        if (isQuestV2Module(parsed)) {
          replaceQuestModuleFile(state, path, parsed);
        } else {
          state.extraFiles[path] = source;
          if (isQuestV1Resource(parsed)) upsertQuestV1Import(state, parsed, path);
        }
        return true;
      }

      if (path.match(/^data\/[^/]+\/dialogue_trees\/[^/]+\/.+\.json$/)) {
        const parsed = json();
        if (!parsed) return false;
        state.extraFiles[path] = source;
        return true;
      }

      const notificationMatch = path.match(/^data\/villagerretaliation\/notifications\/([^/]+)\/(.+)\.json$/);
      if (notificationMatch) {
        const parsed = json();
        if (!parsed) return false;
        state.meta.locale = notificationMatch[1];
        state.notifications.fileName = normalizeFileName(notificationMatch[2].split("/").pop(), state.notifications.fileName);
        state.notifications.notifications = cleanArray(normalizeNotificationEntries(parsed));
        return true;
      }

      const giftMatch = path.match(/^data\/villagerretaliation\/gifts\/(.+)\.json$/);
      if (giftMatch) {
        const parsed = json();
        if (!parsed) return false;
        state.gifts.fileName = normalizeFileName(giftMatch[1].split("/").pop(), state.gifts.fileName);
        state.gifts.preferences = cleanArray(parsed.preferences);
        state.gifts.rewards = cleanArray(parsed.rewards);
        return true;
      }

      const pacificationMatch = path.match(/^data\/villagerretaliation\/pacification\/(.+)\.json$/);
      if (pacificationMatch) {
        const parsed = json();
        if (!parsed) return false;
        state.pacification.fileName = normalizeFileName(pacificationMatch[1].split("/").pop(), state.pacification.fileName);
        state.pacification.payments = cleanArray(parsed.payments);
        return true;
      }

      const structureMatch = path.match(/^data\/([^/]+)\/story_structures\/(.+)\.json$/);
      if (structureMatch) {
        const parsed = json();
        if (!parsed) return false;
        state.stories.namespace = structureMatch[1];
        state.stories.structureFileName = normalizeFileName(structureMatch[2].split("/").pop(), state.stories.structureFileName);
        state.stories.radius = parseInteger(parsed.radius) || state.stories.radius;
        state.stories.structures = cleanArray(normalizeStoryEntries(parsed, "structure"));
        return true;
      }

      const biomeMatch = path.match(/^data\/([^/]+)\/story_biomes\/(.+)\.json$/);
      if (biomeMatch) {
        const parsed = json();
        if (!parsed) return false;
        state.stories.namespace = biomeMatch[1];
        state.stories.biomeFileName = normalizeFileName(biomeMatch[2].split("/").pop(), state.stories.biomeFileName);
        state.stories.biomes = cleanArray(normalizeStoryEntries(parsed, "biome"));
        return true;
      }

      if (path === namesPath()) {
        const parsed = json();
        if (!parsed) return false;
        state.names.male_names = unique([...parseList(parsed.male_names), ...parseList(parsed.names)]);
        state.names.female_names = parseList(parsed.female_names);
        return true;
      }

      state.extraFiles[path] = source;
      return true;
    }

    function parseEditedJson(source) {
      try {
        return JSON.parse(stripTextBom(source));
      } catch {
        return null;
      }
    }

    function replaceDialogueFile(state, path, json) {
      const info = dialoguePathInfo(path);
      if (info.namespace) state.meta.namespace = namespaceify(info.namespace, state.meta.namespace || "my_pack");
      state.meta.locale = info.locale;
      state.dialogue.fileName = normalizeFileName(info.relative.split("/").pop(), state.dialogue.fileName);
      if (info.kind) {
        state.dialogue.layout = "folders";
        if (info.folderName) state.dialogue.folderName = normalizeFileName(info.folderName, state.dialogue.folderName || state.meta.slug);
      }
      state.notifications.notifications = state.notifications.notifications.filter((entry) => entry.__sourcePath !== path);
      const routedNotifications = isNotificationEntry(json) ? [json] : [];
      if (info.kind) {
        state.dialogue[info.kind] = state.dialogue[info.kind].filter((entry, index) => (entry.__sourcePath || defaultDialogueEntryPath(state, info.kind, entry, index)) !== path);
        state.dialogue[info.kind].push(...withDefaultProfession(dialogueEntriesFromJson(json, info.kind), info.profession).map((entry) => ({ ...entry, __sourcePath: path })));
        delete state.extraFiles[path];
        return;
      }
      for (const kind of dialogueKindKeys) {
        state.dialogue[kind] = state.dialogue[kind].filter((entry, index) => (entry.__sourcePath || defaultDialogueEntryPath(state, kind, entry, index)) !== path);
        const entries = cleanArray(json[kind]);
        const dialogueEntries = [];
        for (const entry of entries) {
          if (isNotificationEntry(entry)) {
            routedNotifications.push(entry);
          } else {
            dialogueEntries.push(entry);
          }
        }
        state.dialogue[kind].push(...dialogueEntries.map((entry) => ({ ...entry, __sourcePath: path })));
      }
      if (routedNotifications.length > 0) {
        mergeArray(state, "notifications", "notifications", routedNotifications, path);
      }
      delete state.extraFiles[path];
    }

    function replaceForcedDialogueFile(state, path, json) {
      const forcedDialogueMatch = path.match(/^data\/[^/]+\/forced_dialogue\/(.+)\.json$/);
      const namespaceMatch = path.match(/^data\/([^/]+)\/forced_dialogue\/.+\.json$/);
      if (namespaceMatch) state.meta.namespace = namespaceify(namespaceMatch[1], state.meta.namespace || "my_pack");
      state.forcedDialogue.fileName = normalizeFileName(forcedDialogueMatch[1].split("/").pop(), state.forcedDialogue.fileName);
      state.forcedDialogue.entries = state.forcedDialogue.entries.filter((entry) => (entry.__sourcePath || forcedDialoguePath(state)) !== path);
      state.forcedDialogue.entries.push(...cleanArray(normalizeForcedDialogueEntries(json)).map((entry) => ({ ...entry, __sourcePath: path })));
    }

    function ingestFiles(state, files) {
      const extra = {};
      for (const [path, value] of Object.entries(files)) {
        const normalizedPath = path.replace(/^\/+/, "");
        if (normalizedPath.endsWith("/")) continue;
        if (normalizedPath === "pack.mcmeta" && typeof value === "string") {
          try {
            const json = JSON.parse(stripTextBom(value));
            state.meta.description = json.pack?.description || state.meta.description;
            state.meta.packFormat = Number(json.pack?.pack_format) || state.meta.packFormat;
            state.meta.packVersion = readPackVersion(json) || state.meta.packVersion;
          } catch {
            extra[normalizedPath] = value;
          }
          continue;
        }
        if (typeof value === "string" && ingestKnownJson(state, normalizedPath, value)) {
          continue;
        }
        extra[normalizedPath] = value;
      }
      state.extraFiles = { ...state.extraFiles, ...extra };
    }

    function ingestKnownJson(state, path, source) {
      let json;
      try {
        json = JSON.parse(stripTextBom(source));
      } catch {
        return false;
      }

      const dialogueInfo = dialoguePathInfo(path);
      if (dialogueInfo) {
        if (dialogueInfo.namespace) state.meta.namespace = namespaceify(dialogueInfo.namespace, state.meta.namespace || "my_pack");
        state.meta.locale = dialogueInfo.locale;
        state.dialogue.fileName = normalizeFileName(dialogueInfo.relative.split("/").pop(), state.dialogue.fileName);
        if (dialogueInfo.kind) {
          state.dialogue.layout = "folders";
          if (dialogueInfo.folderName) state.dialogue.folderName = normalizeFileName(dialogueInfo.folderName, state.dialogue.folderName || state.meta.slug);
          const entries = withDefaultProfession(dialogueEntriesFromJson(json, dialogueInfo.kind), dialogueInfo.profession);
          if (entries.length > 0) {
            mergeArray(state, "dialogue", dialogueInfo.kind, entries, path);
            return true;
          }
          return false;
        }
        let importedDialogue = false;
        for (const kind of dialogueKindKeys) {
          const entries = withDefaultProfession(json[kind], dialogueInfo.profession);
          if (!Array.isArray(entries)) continue;
          mergeArray(state, "dialogue", kind, entries, path);
          importedDialogue = true;
        }
        return importedDialogue;
      }

      const notificationMatch = path.match(/^data\/villagerretaliation\/notifications\/([^/]+)\/(.+)\.json$/);
      if (notificationMatch) {
        state.meta.locale = notificationMatch[1];
        state.notifications.fileName = normalizeFileName(notificationMatch[2].split("/").pop(), state.notifications.fileName);
        mergeArray(state, "notifications", "notifications", normalizeNotificationEntries(json), path);
        return true;
      }

      const forcedDialogueMatch = path.match(/^data\/[^/]+\/forced_dialogue\/(.+)\.json$/);
      if (forcedDialogueMatch) {
        const namespaceMatch = path.match(/^data\/([^/]+)\/forced_dialogue\/.+\.json$/);
        if (namespaceMatch) state.meta.namespace = namespaceify(namespaceMatch[1], state.meta.namespace || "my_pack");
        state.forcedDialogue.fileName = normalizeFileName(forcedDialogueMatch[1].split("/").pop(), state.forcedDialogue.fileName);
        mergeArray(state, "forcedDialogue", "entries", normalizeForcedDialogueEntries(json), path);
        return true;
      }

      if (/^data\/[^/]+\/quests\/.+\.json$/.test(path)) {
        if (isQuestV2Module(json)) {
          replaceQuestModuleFile(state, path, json);
        } else {
          state.extraFiles[path] = stripTextBom(source);
          if (isQuestV1Resource(json)) upsertQuestV1Import(state, json, path);
        }
        return true;
      }

      if (/^data\/[^/]+\/dialogue_trees\/[^/]+\/.+\.json$/.test(path)) {
        state.extraFiles[path] = stripTextBom(source);
        return true;
      }

      const giftMatch = path.match(/^data\/villagerretaliation\/gifts\/(.+)\.json$/);
      if (giftMatch) {
        state.gifts.fileName = normalizeFileName(giftMatch[1].split("/").pop(), state.gifts.fileName);
        mergeArray(state, "gifts", "preferences", json.preferences);
        mergeArray(state, "gifts", "rewards", json.rewards);
        return true;
      }

      const pacificationMatch = path.match(/^data\/villagerretaliation\/pacification\/(.+)\.json$/);
      if (pacificationMatch) {
        state.pacification.fileName = normalizeFileName(pacificationMatch[1].split("/").pop(), state.pacification.fileName);
        mergeArray(state, "pacification", "payments", json.payments);
        return true;
      }

      const structureMatch = path.match(/^data\/([^/]+)\/story_structures\/(.+)\.json$/);
      if (structureMatch) {
        state.stories.namespace = structureMatch[1];
        state.stories.structureFileName = normalizeFileName(structureMatch[2].split("/").pop(), state.stories.structureFileName);
        if (json.radius) state.stories.radius = json.radius;
        mergeArray(state, "stories", "structures", normalizeStoryEntries(json, "structure"));
        return true;
      }

      const biomeMatch = path.match(/^data\/([^/]+)\/story_biomes\/(.+)\.json$/);
      if (biomeMatch) {
        state.stories.namespace = biomeMatch[1];
        state.stories.biomeFileName = normalizeFileName(biomeMatch[2].split("/").pop(), state.stories.biomeFileName);
        mergeArray(state, "stories", "biomes", normalizeStoryEntries(json, "biome"));
        return true;
      }

      if (path === namesPath()) {
        state.names.male_names = unique([...state.names.male_names, ...parseList(json.male_names), ...parseList(json.names)]);
        state.names.female_names = unique([...state.names.female_names, ...parseList(json.female_names)]);
        return true;
      }

      const forcedEntries = normalizeForcedDialogueEntries(json);
      if (forcedEntries.length > 0) {
        mergeArray(state, "forcedDialogue", "entries", forcedEntries);
        return true;
      }

      const notificationEntries = normalizeNotificationEntries(json);
      if (notificationEntries.length > 0) {
        mergeArray(state, "notifications", "notifications", notificationEntries);
        return true;
      }

      if (dialogueKindKeys.some((kind) => Array.isArray(json[kind]))) {
        for (const kind of dialogueKindKeys) {
          mergeArray(state, "dialogue", kind, json[kind]);
        }
        return true;
      }

      if (Array.isArray(json.male_names) || Array.isArray(json.female_names) || Array.isArray(json.names)) {
        state.names.male_names = unique([...state.names.male_names, ...parseList(json.male_names), ...parseList(json.names)]);
        state.names.female_names = unique([...state.names.female_names, ...parseList(json.female_names)]);
        return true;
      }

      let matchedTopLevelPackJson = false;
      if (Array.isArray(json.notifications)) {
        mergeArray(state, "notifications", "notifications", normalizeNotificationEntries(json));
        matchedTopLevelPackJson = true;
      }
      if (Array.isArray(json.preferences)) {
        mergeArray(state, "gifts", "preferences", json.preferences);
        matchedTopLevelPackJson = true;
      }
      if (Array.isArray(json.rewards)) {
        mergeArray(state, "gifts", "rewards", json.rewards);
        matchedTopLevelPackJson = true;
      }
      if (Array.isArray(json.payments)) {
        mergeArray(state, "pacification", "payments", json.payments);
        matchedTopLevelPackJson = true;
      }
      if (matchedTopLevelPackJson) return true;

      const detected = detectJsonKind(json);
      if (detected) {
        mergeArray(state, detected.section, detected.kind, json[detected.key]);
        return true;
      }

      return false;
    }

    function detectJsonKind(json) {
      if (Array.isArray(json.entries) && json.entries.some(isForcedDialogueEntry)) return { section: "forcedDialogue", kind: "entries", key: "entries" };
      if (Array.isArray(json.notifications)) return { section: "notifications", kind: "notifications", key: "notifications" };
      if (Array.isArray(json.preferences)) return { section: "gifts", kind: "preferences", key: "preferences" };
      if (Array.isArray(json.rewards)) return { section: "gifts", kind: "rewards", key: "rewards" };
      if (Array.isArray(json.payments)) return { section: "pacification", kind: "payments", key: "payments" };
      return null;
    }

    function isForcedDialogueEntry(entry) {
      if (isNotificationEntry(entry)) return false;
      return Boolean(entry && typeof entry === "object" && forcedTriggerValue(entry) && (hasForcedDialogueLine(entry) || Array.isArray(entry.options)));
    }

    function hasForcedDialogueLine(entry) {
      return Boolean(entry?.line || (Array.isArray(entry?.lines) && entry.lines.some((line) => String(line ?? "").trim())));
    }

    function forcedTriggerValue(entry) {
      return entry?.trigger ?? entry?.event ?? "";
    }

    function hasNotificationText(entry) {
      if (Array.isArray(entry?.lines) && entry.lines.length > 0) return parseList(entry.lines.join("\n")).length > 0;
      return parseList(entry?.text ?? "").length > 0;
    }

    function isNotificationEntry(entry) {
      if (!entry || typeof entry !== "object" || Array.isArray(entry)) return false;
      if (!entry.trigger || !hasNotificationText(entry)) return false;
      if (constants.forcedDialogueTriggers.includes(forcedTriggerValue(entry))) return false;
      const notificationKeys = [
        "id",
        "kind",
        "world_text_kind",
        "style",
        "weight",
        "professions",
        "player_item",
        "player_items",
        "player_item_tag",
        "player_item_tags",
        "player_item_slot",
        "player_item_slots",
        "min_player_item_durability",
        "max_player_item_durability",
        "min_player_item_durability_percent",
        "max_player_item_durability_percent",
        "min_held_item_durability",
        "max_held_item_durability",
        "min_held_item_durability_percent",
        "max_held_item_durability_percent",
        "player_item_enchantment",
        "player_item_enchantments",
        "held_item_enchantment",
        "held_item_enchantments",
        "min_player_item_enchantment_level",
        "max_player_item_enchantment_level",
        "min_held_item_enchantment_level",
        "max_held_item_enchantment_level",
        "target_entity_type",
        "target_entity",
        "target_entity_types",
        "target_entities",
        "reputation_level",
        "reputation_levels",
        "min_reputation",
        "max_reputation",
        "color",
        "text_color",
        "chat_color"
      ];
      return notificationKeys.some((key) => Object.hasOwn(entry, key));
    }

    function normalizeForcedDialogueEntries(json) {
      if (Array.isArray(json.entries) && json.entries.some(isForcedDialogueEntry)) return json.entries;
      if (isForcedDialogueEntry(json)) return [json];
      return [];
    }

    function normalizeNotificationEntries(json) {
      if (Array.isArray(json?.notifications)) return json.notifications;
      if (Array.isArray(json?.entries) && json.entries.some(isNotificationEntry)) return json.entries;
      if (Array.isArray(json?.lines) && json.lines.some(isNotificationEntry)) return json.lines;
      if (Array.isArray(json) && json.some(isNotificationEntry)) return json;
      if (isNotificationEntry(json)) return [json];
      return [];
    }

    function normalizeStoryEntries(json, type) {
      if (Array.isArray(json.entries)) return json.entries;
      if (type === "structure" && (json.structure || json.structures)) return [json];
      if (type === "biome" && (json.biome || json.biomes)) return [json];
      return [];
    }

    function withDefaultProfession(entries, profession) {
      if (!Array.isArray(entries) || !profession) return entries;
      return entries.map((entry) => entry.professions ? entry : { ...entry, professions: [profession] });
    }

    function mergeArray(state, section, kind, entries, sourcePath = "") {
      if (!Array.isArray(entries)) return;
      state[section][kind].push(...entries.map((entry) => {
        const cleaned = cleanObject(entry);
        if (sourcePath && cleaned && typeof cleaned === "object") {
          cleaned.__sourcePath = sourcePath;
        }
        return cleaned;
      }));
    }

    function createZip(files) {
      const localParts = [];
      const centralParts = [];
      let offset = 0;
      const now = new Date();
      const { dosTime, dosDate } = toDosDateTime(now);

      for (const [path, value] of Object.entries(files).sort(([a], [b]) => a.localeCompare(b))) {
        const nameBytes = encoder.encode(path);
        const data = value instanceof Uint8Array ? value : encoder.encode(String(value));
        const crc = crc32(data);
        const localHeader = concatBytes(
          u32(0x04034b50),
          u16(20),
          u16(0x0800),
          u16(0),
          u16(dosTime),
          u16(dosDate),
          u32(crc),
          u32(data.length),
          u32(data.length),
          u16(nameBytes.length),
          u16(0),
          nameBytes
        );
        localParts.push(localHeader, data);
        const centralHeader = concatBytes(
          u32(0x02014b50),
          u16(20),
          u16(20),
          u16(0x0800),
          u16(0),
          u16(dosTime),
          u16(dosDate),
          u32(crc),
          u32(data.length),
          u32(data.length),
          u16(nameBytes.length),
          u16(0),
          u16(0),
          u16(0),
          u16(0),
          u32(0),
          u32(offset),
          nameBytes
        );
        centralParts.push(centralHeader);
        offset += localHeader.length + data.length;
      }

      const centralOffset = offset;
      const centralDirectory = concatBytes(...centralParts);
      const end = concatBytes(
        u32(0x06054b50),
        u16(0),
        u16(0),
        u16(centralParts.length),
        u16(centralParts.length),
        u32(centralDirectory.length),
        u32(centralOffset),
        u16(0)
      );

      return concatBytes(...localParts, centralDirectory, end);
    }

    function toDosDateTime(date) {
      const dosTime = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
      const dosDate = ((date.getFullYear() - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
      return { dosTime, dosDate };
    }

    function u16(value) {
      return new Uint8Array([value & 0xff, (value >>> 8) & 0xff]);
    }

    function u32(value) {
      return new Uint8Array([value & 0xff, (value >>> 8) & 0xff, (value >>> 16) & 0xff, (value >>> 24) & 0xff]);
    }

    function concatBytes(...parts) {
      const length = parts.reduce((sum, part) => sum + part.length, 0);
      const result = new Uint8Array(length);
      let offset = 0;
      for (const part of parts) {
        result.set(part, offset);
        offset += part.length;
      }
      return result;
    }

    const crcTable = (() => {
      const table = new Uint32Array(256);
      for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) {
          c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        }
        table[n] = c >>> 0;
      }
      return table;
    })();

    function crc32(data) {
      let c = 0xffffffff;
      for (let i = 0; i < data.length; i++) {
        c = crcTable[(c ^ data[i]) & 0xff] ^ (c >>> 8);
      }
      return (c ^ 0xffffffff) >>> 0;
    }

    return {
      createInitialState,
      slugify,
      namespaceify,
      normalizeFileName,
      parseList,
      parseInteger,
      unique,
      stripTextBom,
      cleanObject,
      cleanArray,
      safeJson,
      packVersionInfo,
      normalizePackVersion,
      readPackVersion,
      packVersionAtLeast,
      supportsBeta12DialogueFields,
      contentNamespace,
      makePackMeta,
      dialoguePathInfo,
      dialogueEntriesFromJson,
      dialogueOutputEntry,
      dialogueFilePayload,
      dialogueUsesFolderLayout,
      dialogueBundlePath,
      dialogueFolderName,
      dialogueFileStem,
      defaultDialogueEntryPath,
      forcedDialoguePath,
      notificationsPath,
      giftsPath,
      pacificationPath,
      structurePath,
      biomePath,
      namesPath,
      generatedFiles,
      generatedDialogueFiles,
      generatedForcedDialogueFiles,
      generatedQuestFiles,
      questModulePath,
      isQuestV2Module,
      isQuestV1Resource,
      normalizeQuestModuleEntry,
      normalizeQuestV1Import,
      replaceQuestModuleFile,
      normalizeImportedPaths,
      normalizeNamespaceRootImportPaths,
      isNamespaceRootDataPath,
      isTextPath,
      importedKnownKind,
      inferPackVersionFromFiles,
      jsonContainsAnyKey,
      valueContainsAnyKey,
      applyEditedFile,
      parseEditedJson,
      replaceDialogueFile,
      replaceForcedDialogueFile,
      ingestFiles,
      ingestKnownJson,
      detectJsonKind,
      isForcedDialogueEntry,
      hasForcedDialogueLine,
      forcedTriggerValue,
      isNotificationEntry,
      normalizeForcedDialogueEntries,
      normalizeNotificationEntries,
      normalizeStoryEntries,
      withDefaultProfession,
      mergeArray,
      createZip
    };
  }

  window.VR_DATAPACK_BACKEND = { create: createDatapackBackend };
})();
