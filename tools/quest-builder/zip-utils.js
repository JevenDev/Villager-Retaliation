(function zipUtilsFactory(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.QuestBuilderZip = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createZipUtils() {
  "use strict";

  const encoder = new TextEncoder();
  const decoder = new TextDecoder("utf-8");

  function u16(value) {
    return new Uint8Array([value & 0xff, (value >>> 8) & 0xff]);
  }

  function u32(value) {
    return new Uint8Array([value & 0xff, (value >>> 8) & 0xff, (value >>> 16) & 0xff, (value >>> 24) & 0xff]);
  }

  function readUint16(bytes, offset) {
    return bytes[offset] | (bytes[offset + 1] << 8);
  }

  function readUint32(bytes, offset) {
    return (bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16) | (bytes[offset + 3] << 24)) >>> 0;
  }

  function concatBytes(...parts) {
    const result = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0));
    let offset = 0;
    for (const part of parts) {
      result.set(part, offset);
      offset += part.length;
    }
    return result;
  }

  const crcTable = (() => {
    const table = new Uint32Array(256);
    for (let index = 0; index < table.length; index += 1) {
      let current = index;
      for (let bit = 0; bit < 8; bit += 1) current = current & 1 ? 0xedb88320 ^ (current >>> 1) : current >>> 1;
      table[index] = current >>> 0;
    }
    return table;
  })();

  function crc32(data) {
    let current = 0xffffffff;
    for (const byte of data) current = crcTable[(current ^ byte) & 0xff] ^ (current >>> 8);
    return (current ^ 0xffffffff) >>> 0;
  }

  function toDosDateTime(date) {
    const safeYear = Math.max(1980, date.getFullYear());
    return {
      dosTime: (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2),
      dosDate: ((safeYear - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate()
    };
  }

  function createZip(files, options = {}) {
    const localParts = [];
    const centralParts = [];
    const date = options.date instanceof Date ? options.date : new Date();
    const { dosTime, dosDate } = toDosDateTime(date);
    let offset = 0;
    for (const [path, value] of Object.entries(files || {}).sort(([a], [b]) => a.localeCompare(b))) {
      const normalizedPath = normalizePath(path);
      if (!normalizedPath || normalizedPath.endsWith("/")) continue;
      const nameBytes = encoder.encode(normalizedPath);
      const data = value instanceof Uint8Array ? value : encoder.encode(String(value));
      const crc = crc32(data);
      const localHeader = concatBytes(
        u32(0x04034b50), u16(20), u16(0x0800), u16(0), u16(dosTime), u16(dosDate),
        u32(crc), u32(data.length), u32(data.length), u16(nameBytes.length), u16(0), nameBytes
      );
      localParts.push(localHeader, data);
      centralParts.push(concatBytes(
        u32(0x02014b50), u16(20), u16(20), u16(0x0800), u16(0), u16(dosTime), u16(dosDate),
        u32(crc), u32(data.length), u32(data.length), u16(nameBytes.length), u16(0), u16(0),
        u16(0), u16(0), u32(0), u32(offset), nameBytes
      ));
      offset += localHeader.length + data.length;
    }
    const centralDirectory = concatBytes(...centralParts);
    const end = concatBytes(
      u32(0x06054b50), u16(0), u16(0), u16(centralParts.length), u16(centralParts.length),
      u32(centralDirectory.length), u32(offset), u16(0)
    );
    return concatBytes(...localParts, centralDirectory, end);
  }

  async function readZip(input, options = {}) {
    const bytes = input instanceof Uint8Array ? input : new Uint8Array(input);
    let endOffset = -1;
    for (let index = bytes.length - 22; index >= Math.max(0, bytes.length - 66000); index -= 1) {
      if (readUint32(bytes, index) === 0x06054b50) { endOffset = index; break; }
    }
    if (endOffset < 0) throw new Error("Could not find the zip directory. The file may be damaged.");
    let offset = readUint32(bytes, endOffset + 16);
    const centralEnd = offset + readUint32(bytes, endOffset + 12);
    const files = {};
    while (offset + 46 <= centralEnd) {
      if (readUint32(bytes, offset) !== 0x02014b50) throw new Error("The zip directory contains an invalid entry.");
      const method = readUint16(bytes, offset + 10);
      const expectedCrc = readUint32(bytes, offset + 16);
      const compressedSize = readUint32(bytes, offset + 20);
      const expectedSize = readUint32(bytes, offset + 24);
      const nameLength = readUint16(bytes, offset + 28);
      const extraLength = readUint16(bytes, offset + 30);
      const commentLength = readUint16(bytes, offset + 32);
      const localOffset = readUint32(bytes, offset + 42);
      const path = normalizePath(decoder.decode(bytes.slice(offset + 46, offset + 46 + nameLength)));
      offset += 46 + nameLength + extraLength + commentLength;
      if (!path || path.endsWith("/")) continue;
      if (readUint32(bytes, localOffset) !== 0x04034b50) throw new Error(`Invalid zip header for ${path}.`);
      const localNameLength = readUint16(bytes, localOffset + 26);
      const localExtraLength = readUint16(bytes, localOffset + 28);
      const dataStart = localOffset + 30 + localNameLength + localExtraLength;
      const compressed = bytes.slice(dataStart, dataStart + compressedSize);
      const data = await decompress(compressed, method, options.inflateRaw);
      if (data.length !== expectedSize) throw new Error(`Unexpected uncompressed size for ${path}.`);
      if (crc32(data) !== expectedCrc) throw new Error(`Checksum failed for ${path}.`);
      files[path] = data;
    }
    return normalizePackRoot(files);
  }

  async function decompress(data, method, inflateRaw) {
    if (method === 0) return data;
    if (method !== 8) throw new Error(`Zip compression method ${method} is not supported.`);
    if (inflateRaw) return new Uint8Array(await inflateRaw(data));
    if (typeof DecompressionStream !== "function") throw new Error("This browser cannot decompress this zip. Import the quest JSON files directly instead.");
    try {
      const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
      return new Uint8Array(await new Response(stream).arrayBuffer());
    } catch {
      const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate"));
      return new Uint8Array(await new Response(stream).arrayBuffer());
    }
  }

  function normalizePath(path) {
    const normalized = String(path || "").replaceAll("\\", "/").replace(/^\/+/, "");
    const segments = [];
    for (const segment of normalized.split("/")) {
      if (!segment || segment === ".") continue;
      if (segment === "..") throw new Error(`Unsafe zip path: ${path}`);
      segments.push(segment);
    }
    return segments.join("/");
  }

  function normalizePackRoot(files) {
    const entries = Object.entries(files || {});
    const packMetaPath = entries.map(([path]) => path).find((path) => path === "pack.mcmeta" || path.endsWith("/pack.mcmeta"));
    if (!packMetaPath || packMetaPath === "pack.mcmeta") return Object.fromEntries(entries);
    const prefix = packMetaPath.slice(0, -"pack.mcmeta".length);
    return Object.fromEntries(entries.map(([path, value]) => [path.startsWith(prefix) ? path.slice(prefix.length) : path, value]));
  }

  function decodeJsonFiles(files, predicate = () => true) {
    const result = [];
    for (const [path, bytes] of Object.entries(files || {})) {
      if (!path.toLowerCase().endsWith(".json") || !predicate(path)) continue;
      try { result.push({ path, value: JSON.parse(decoder.decode(bytes).replace(/^\uFEFF/, "")) }); }
      catch (error) { throw new Error(`${path} contains invalid JSON: ${error.message}`); }
    }
    return result;
  }

  return { createZip, readZip, normalizePath, normalizePackRoot, decodeJsonFiles, crc32, concatBytes };
});
