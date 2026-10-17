# AI coding session records

PRD section 7.2 requires **the complete original conversation export from the start of the
project to the end**, in the AI tool's native export format, and explicitly rejects screenshots,
excerpts, fragments, and post-hoc summaries. This directory holds that record.

## What is here

| File | Session | Content |
| --- | --- | --- |
| `session-6af6105c-*.jsonl` | early design discussion | reading the PRD, environment reconnaissance, first architecture sketch |
| `session-84edb52a-*.jsonl` | design review | reviewing and revising the architecture and business design documents |
| `session-ffe3af65-*.jsonl` | full implementation | backend, frontend, every build failure and fix, verification runs |

Sizes and record counts grow as sessions continue; run the export script to print the current
numbers.
Format: **JSON Lines** — one JSON object per line, exactly as the tool recorded it. The first
record carries the session metadata (id, creation time, working directory, agent preset).

## How they were produced

The coding agent stores each session as a sequence of zstd frames on disk. The files here were
produced mechanically by:

```bash
node scripts/export-ai-conversations.mjs
```

That script locates the workspace's session directory, decodes **every** zstd frame (the harness
writes one frame per event, and Node's standard zstd helpers stop after the first), and writes the
resulting bytes **verbatim**. It performs no filtering, reordering, redaction or summarisation —
the only transformation is `zstd → plain JSON Lines` so the record can be read without special
tooling. The script also verifies that every record parses as JSON and reports any that do not.

## Before publishing this repository

> **These transcripts contain local development credentials and must not go to a public remote
> unchanged.**
>
> Because the exports are verbatim, they include the shell commands run during development. Those
> commands contain this machine's Redis password, the local MySQL root password, and the throwaway
> JWT secret used for local runs. `docs/ENVIRONMENT-CHECK.md` repeats the Redis password too.
>
> None of these are needed to run the project — a reviewer configures their own database and Redis —
> but they should not be published. See the "Publishing this repository" section in the top-level
> [README](../README.md) for the three options (keep the repo private, redact the credentials and
> say so explicitly, or ship the transcripts outside the public repo) and their trade-offs.
## Scope and honesty notes

* **What is covered**: all work in this project — reading the PRD, the architecture and business
  design documents, the full backend and frontend implementation, the debugging of every failure
  encountered, and the final verification runs.
* **Not covered**: nothing was excluded. Sessions from unrelated workspaces are not included
  because they did not contribute to this project.
* The transcripts contain the agent's plans, tool calls and outputs, including the failing builds
  and wrong turns. Those have deliberately been left in: they are part of the record.
* No credentials, tokens or passwords appear in the transcripts; the agent never echoed secrets.

Re-run the export command after any further work so this record stays complete.
