#!/usr/bin/env node
/**
 * Exports the raw AI coding session transcripts into `ai-conversations/`.
 *
 * PRD section 7.2 requires the complete original conversation record from project start to
 * finish, and explicitly rejects screenshots, excerpts and post-hoc summaries. This script
 * therefore performs a *format* conversion only:
 *
 *   ~/.dsh/sessions/<workspace-key>/<session-id>/session.vN.jsonl.zstd
 *        -> ai-conversations/<session-id>.jsonl
 *
 * The decompressed bytes are written verbatim - no filtering, no reordering, no editing.
 * Re-run it after any further work so the export stays complete.
 *
 * Usage:
 *   node scripts/export-ai-conversations.mjs
 *   node scripts/export-ai-conversations.mjs --workspace "D:\\G\\MyRep"
 *   node scripts/export-ai-conversations.mjs --sessions-dir "C:\\Users\\me\\.dsh\\sessions"
 */

import { createRequire } from 'node:module'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { zstdDecompressSync } from 'node:zlib'

const require = createRequire(import.meta.url)
const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDir, '..')
const outputDir = join(projectRoot, 'ai-conversations')

function parseArgs(argv) {
  const args = { workspace: process.cwd(), sessionsDir: null, keepVersions: false }
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i]
    if (token === '--workspace') args.workspace = argv[++i]
    else if (token === '--sessions-dir') args.sessionsDir = argv[++i]
    else if (token === '--keep-versions') args.keepVersions = true
  }
  return args
}

/**
 * Mirrors how the harness names a workspace folder: every run of non-alphanumeric
 * characters collapses to a single dash, wrapped in double dashes.
 *   D:\G\MyRep  ->  --D-G-MyRep--
 */
function workspaceKeyFor(path) {
  const collapsed = resolve(path).replace(/[^A-Za-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  return `--${collapsed}--`
}

/** Highest session.vN.jsonl.zstd in a session folder (v4 is newer than v3). */
function pickTranscript(sessionDir) {
  const candidates = readdirSync(sessionDir)
    .map((name) => {
      const match = /^session(?:\.v(\d+))?\.jsonl\.zstd$/.exec(name)
      return match ? { name, version: match[1] ? Number(match[1]) : 1 } : null
    })
    .filter(Boolean)
    .sort((a, b) => b.version - a.version)
  return candidates.length > 0 ? join(sessionDir, candidates[0].name) : null
}

/**
 * Decodes a session transcript.
 *
 * The harness appends one zstd frame per event, so a transcript is hundreds of concatenated
 * frames rather than one. Node's zstd helpers stop after the first frame, so the frames are
 * located by their 4-byte magic and decoded individually, merging a slice with the next one
 * if it does not decode (which would mean the magic appeared inside compressed data).
 */
function decodeTranscript(compressed) {
  const MAGIC = Buffer.from([0x28, 0xb5, 0x2f, 0xfd])
  const offsets = []
  let cursor = compressed.indexOf(MAGIC, 0)
  while (cursor !== -1) {
    offsets.push(cursor)
    cursor = compressed.indexOf(MAGIC, cursor + MAGIC.length)
  }
  if (offsets.length === 0 || offsets[0] !== 0) {
    // Not a frame sequence we recognise; try it as a single frame.
    return zstdDecompressSync(compressed)
  }

  const parts = []
  let index = 0
  while (index < offsets.length) {
    let merged = 1
    let decoded = null
    while (index + merged <= offsets.length) {
      const start = offsets[index]
      const end = index + merged < offsets.length ? offsets[index + merged] : compressed.length
      try {
        decoded = zstdDecompressSync(compressed.subarray(start, end))
        break
      } catch {
        // The magic was inside compressed data: extend the slice and retry.
        merged += 1
      }
    }
    if (decoded === null) {
      throw new Error(`frame at offset ${offsets[index]} could not be decoded`)
    }
    parts.push(decoded)
    index += merged
  }
  return Buffer.concat(parts)
}

function main() {
  const args = parseArgs(process.argv.slice(2))
  const sessionsRoot = args.sessionsDir ?? join(homedir(), '.dsh', 'sessions')

  if (!existsSync(sessionsRoot)) {
    console.error(`Sessions directory not found: ${sessionsRoot}`)
    process.exit(1)
  }

  const key = workspaceKeyFor(args.workspace)
  let workspaceDir = join(sessionsRoot, key)

  if (!existsSync(workspaceDir)) {
    // Fall back to the folder whose name contains the workspace's basename.
    const base = resolve(args.workspace).split(/[\\/]/).pop()
    const match = readdirSync(sessionsRoot).find(
      (name) => name.includes(base) && statSync(join(sessionsRoot, name)).isDirectory()
    )
    if (match) {
      workspaceDir = join(sessionsRoot, match)
      console.log(`Workspace folder resolved by name: ${match}`)
    }
  }

  if (!existsSync(workspaceDir)) {
    // The project usually sits inside the harness workspace, so try each ancestor
    // directory before giving up (e.g. cwd D:\G\MyRep\webox -> key --D-G-MyRep--).
    const available = readdirSync(sessionsRoot).filter((name) =>
      statSync(join(sessionsRoot, name)).isDirectory()
    )
    let ancestor = resolve(args.workspace)
    while (!existsSync(workspaceDir)) {
      const parent = dirname(ancestor)
      if (parent === ancestor) break
      ancestor = parent
      const candidate = join(sessionsRoot, workspaceKeyFor(ancestor))
      if (available.includes(workspaceKeyFor(ancestor))) {
        workspaceDir = candidate
        console.log(`Workspace folder resolved from an ancestor directory: ${workspaceKeyFor(ancestor)}`)
      }
    }
  }

  if (!existsSync(workspaceDir)) {
    console.error(`No session folder for workspace ${args.workspace}.`)
    console.error(`Available: ${readdirSync(sessionsRoot).join(', ')}`)
    process.exit(1)
  }

  mkdirSync(outputDir, { recursive: true })

  const sessionDirs = readdirSync(workspaceDir)
    .map((name) => join(workspaceDir, name))
    .filter((path) => statSync(path).isDirectory())

  let exported = 0
  let skipped = 0
  const written = []

  for (const sessionDir of sessionDirs) {
    const transcript = pickTranscript(sessionDir)
    if (!transcript) {
      skipped += 1
      continue
    }
    const sessionId = transcript.split(/[\\/]/).slice(-2)[0]
    const compressed = readFileSync(transcript)
    let plain
    try {
      plain = decodeTranscript(compressed)
    } catch (error) {
      console.error(`  ! could not decompress ${transcript}: ${error.message}`)
      skipped += 1
      continue
    }
    // Sanity check: every line must be a JSON object, otherwise the record is incomplete.
    const rawLines = plain.toString('utf8').split('\n').filter((line) => line.trim().length > 0)
    const malformed = rawLines.filter((line) => {
      try {
        JSON.parse(line)
        return false
      } catch {
        return true
      }
    })
    if (malformed.length > 0) {
      console.error(`  ! ${sessionId}: ${malformed.length} record(s) did not parse as JSON`)
    }
    // Verbatim bytes; the only transformation is zstd -> plain JSON Lines.
    const target = join(outputDir, `${sessionId}.jsonl`)
    writeFileSync(target, plain)
    written.push({ sessionId, bytes: plain.length, lines: rawLines.length })
    exported += 1
  }

  written.sort((a, b) => a.sessionId.localeCompare(b.sessionId))
  console.log(`\nExported ${exported} session(s) to ${outputDir}`)
  for (const entry of written) {
    console.log(
      `  ${entry.sessionId}.jsonl  ${(entry.bytes / 1024).toFixed(1)} KB  ${entry.lines} record(s)`
    )
  }
  if (skipped > 0) {
    console.log(`  (${skipped} session folder(s) had no transcript)`)
  }
}

main()
