// FILE: subagent-thread-index.js
// Purpose: Exposes locally persisted subagent sessions as synthetic thread/list entries.
// Layer: CLI helper
// Exports: augmentThreadListResult
// Depends on: fs, path, ./rollout-watch

const fs = require("fs");
const path = require("path");
const { resolveSessionsRoot } = require("./rollout-watch");

const DEFAULT_RECENT_ROLLOUT_LIMIT = 256;

function augmentThreadListResult(resultObject, requestParams = null) {
  if (!resultObject || typeof resultObject !== "object") {
    return resultObject;
  }

  if (readBoolean(requestParams?.archived)) {
    return resultObject;
  }

  if (hasCursorValue(requestParams?.cursor)) {
    return resultObject;
  }

  const pageKey = resolveThreadListPageKey(resultObject);
  if (!pageKey || !Array.isArray(resultObject[pageKey])) {
    return resultObject;
  }

  const syntheticThreads = listPersistedSubagentThreads();
  if (syntheticThreads.length === 0) {
    return resultObject;
  }

  const mergedById = new Map();
  for (const thread of resultObject[pageKey]) {
    const threadId = readString(thread?.id) || readString(thread?.thread_id);
    if (!threadId) {
      continue;
    }
    mergedById.set(threadId, thread);
  }

  let didInject = false;
  for (const thread of syntheticThreads) {
    if (mergedById.has(thread.id)) {
      continue;
    }
    mergedById.set(thread.id, thread);
    didInject = true;
  }

  if (!didInject) {
    return resultObject;
  }

  const mergedThreads = Array.from(mergedById.values()).sort(compareThreadsByRecency);
  const limit = readPositiveInteger(requestParams?.limit);
  const nextPage = limit ? mergedThreads.slice(0, limit) : mergedThreads;
  return {
    ...resultObject,
    [pageKey]: nextPage,
  };
}

function listPersistedSubagentThreads() {
  const sessionsRoot = resolveSessionsRoot();
  const rolloutFiles = collectRecentRolloutFiles(sessionsRoot, DEFAULT_RECENT_ROLLOUT_LIMIT);
  const threadsById = new Map();

  for (const rolloutPath of rolloutFiles) {
    const thread = readSubagentThreadFromRollout(rolloutPath);
    if (!thread) {
      continue;
    }

    const existing = threadsById.get(thread.id);
    if (!existing || compareThreadsByRecency(thread, existing) < 0) {
      threadsById.set(thread.id, thread);
    }
  }

  return Array.from(threadsById.values());
}

function collectRecentRolloutFiles(rootDir, limit) {
  if (!rootDir || !fs.existsSync(rootDir)) {
    return [];
  }

  const files = [];
  const stack = [rootDir];

  while (stack.length > 0) {
    const currentDir = stack.pop();
    let entries = [];
    try {
      entries = fs.readdirSync(currentDir, { withFileTypes: true });
    } catch {
      continue;
    }

    for (const entry of entries) {
      const entryPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        stack.push(entryPath);
        continue;
      }
      if (!entry.isFile() || !entry.name.startsWith("rollout-") || !entry.name.endsWith(".jsonl")) {
        continue;
      }

      let stat = null;
      try {
        stat = fs.statSync(entryPath);
      } catch {
        continue;
      }

      files.push({
        filePath: entryPath,
        modifiedAtMs: stat.mtimeMs || 0,
      });
    }
  }

  return files
    .sort((left, right) => right.modifiedAtMs - left.modifiedAtMs)
    .slice(0, limit)
    .map((entry) => entry.filePath);
}

function readSubagentThreadFromRollout(rolloutPath) {
  let contents = "";
  try {
    contents = fs.readFileSync(rolloutPath, "utf8");
  } catch {
    return null;
  }

  const lines = contents.split("\n");
  if (lines.length === 0) {
    return null;
  }

  const sessionMeta = safeParseJSON(lines[0]);
  if (sessionMeta?.type !== "session_meta") {
    return null;
  }

  const metaPayload = sessionMeta.payload;
  const thread = buildSubagentThread(metaPayload);
  if (!thread) {
    return null;
  }

  let latestPreview = "";
  let latestTimestamp = thread.updated_at || thread.created_at || null;
  for (const rawLine of lines) {
    const parsed = safeParseJSON(rawLine);
    if (!parsed || parsed.type !== "event_msg") {
      continue;
    }

    const eventType = readString(parsed.payload?.type);
    const eventTimestamp = readString(parsed.timestamp) || readString(parsed.payload?.timestamp);
    if (eventTimestamp) {
      latestTimestamp = eventTimestamp;
    }

    if (eventType === "user_message" || eventType === "agent_message") {
      const message = readString(parsed.payload?.message);
      if (message) {
        latestPreview = compactPreview(message);
      }
    }
  }

  return {
    ...thread,
    preview: latestPreview || thread.preview || null,
    updated_at: latestTimestamp || thread.updated_at || thread.created_at || null,
  };
}

function buildSubagentThread(metaPayload) {
  if (!metaPayload || typeof metaPayload !== "object") {
    return null;
  }

  const spawn = metaPayload.source?.subagent?.thread_spawn;
  const threadId = readString(metaPayload.id);
  const parentThreadId = readString(spawn?.parent_thread_id);
  if (!threadId || !parentThreadId) {
    return null;
  }

  const agentNickname = readString(metaPayload.agent_nickname) || readString(spawn?.agent_nickname);
  const agentRole = readString(metaPayload.agent_role) || readString(spawn?.agent_role);
  const timestamp = readString(metaPayload.timestamp) || null;

  return {
    id: threadId,
    title: agentNickname || "Subagent",
    name: agentNickname || "Subagent",
    preview: agentRole || "subagent",
    cwd: readString(metaPayload.cwd) || null,
    created_at: timestamp,
    updated_at: timestamp,
    parent_thread_id: parentThreadId,
    agent_nickname: agentNickname,
    agent_role: agentRole,
  };
}

function resolveThreadListPageKey(resultObject) {
  if (Array.isArray(resultObject.data)) {
    return "data";
  }
  if (Array.isArray(resultObject.items)) {
    return "items";
  }
  if (Array.isArray(resultObject.threads)) {
    return "threads";
  }
  return null;
}

function compareThreadsByRecency(left, right) {
  return parseTimestamp(right?.updated_at || right?.updatedAt || right?.created_at || right?.createdAt)
    - parseTimestamp(left?.updated_at || left?.updatedAt || left?.created_at || left?.createdAt);
}

function parseTimestamp(rawValue) {
  if (typeof rawValue === "number" && Number.isFinite(rawValue)) {
    return rawValue > 1_000_000_000_000 ? rawValue : rawValue * 1000;
  }

  const rawString = readString(rawValue);
  if (!rawString) {
    return 0;
  }

  const numeric = Number(rawString);
  if (Number.isFinite(numeric)) {
    return numeric > 1_000_000_000_000 ? numeric : numeric * 1000;
  }

  const parsed = Date.parse(rawString);
  return Number.isFinite(parsed) ? parsed : 0;
}

function compactPreview(message) {
  const normalized = readString(message);
  if (!normalized) {
    return "";
  }
  return normalized.replace(/\s+/g, " ").slice(0, 140);
}

function hasCursorValue(cursor) {
  if (cursor == null) {
    return false;
  }
  if (typeof cursor === "string") {
    return cursor.trim().length > 0;
  }
  if (typeof cursor === "object") {
    return Object.keys(cursor).length > 0;
  }
  return true;
}

function readPositiveInteger(value) {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return Math.trunc(value);
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    if (Number.isFinite(parsed) && parsed > 0) {
      return Math.trunc(parsed);
    }
  }

  return null;
}

function readBoolean(value) {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "string") {
    return value.trim().toLowerCase() === "true";
  }
  return false;
}

function readString(value) {
  return typeof value === "string" && value.trim() ? value.trim() : "";
}

function safeParseJSON(rawValue) {
  if (!rawValue) {
    return null;
  }

  try {
    return JSON.parse(rawValue);
  } catch {
    return null;
  }
}

module.exports = {
  augmentThreadListResult,
};
