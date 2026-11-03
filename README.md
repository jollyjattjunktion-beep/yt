# Preet Sports YouTube Agent

A Preet Sports configuration and installer for Jake Schincariol's open-source [YouTube Agent Skills](https://github.com/Jakeschincariol/youtube-agent-skill).

## What this repository does

This repository installs the upstream 11 YouTube skills into a project-local Claude Code directory, then adds a Preet Sports voice profile:

- `/yt-script` — hooks and humanised cricket scripts
- `/yt-package` — title + thumbnail pairing
- `/yt-edit` — transcript edit decision list
- `/yt-comment` — comment triage and replies
- `/yt-plan` — publishing plan
- `/yt-viral` — niche outlier research from supplied/public data
- `/yt-retention` — retention export analysis
- `/yt-shorts` — Shorts ideas from long-form transcripts
- `/yt-seo` — descriptions, tags and search queries
- `/yt-chapters` — validated chapters
- `/yt-audit` — channel audit

## Install

### Windows PowerShell

From the root of this repository:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\install.ps1
```

### macOS / Linux

```bash
bash ./install.sh
```

The installer downloads the upstream MIT-licensed repository and copies its original skill folders into `.claude/skills/`. It also installs `templates/voice.md` as `.claude/youtube/voice.md`.

Then open this repository in Claude Code and try:

```
/yt-script India vs New Zealand ODI pre-match analysis
```

Python 3 is needed for the upstream helper scripts. The six upstream tools use the Python standard library; no pip packages are required.

## Preet Sports defaults

- Channel: Preet Sports
- Format: cricket live discussion, match previews, reactions and analysis
- Script language: natural Hinglish (Hindi-first); use Gurmukhi only when explicitly requested
- Voice: conversational, humanised, energetic but not shouty
- Titles: include the exact phrases **Live Match Reaction** and **Live Discussion**, and name both teams when applicable
- Descriptions: include a clear disclaimer that Preet Sports is not an official broadcast
- Never invent toss, playing XI, score, venue, player stats or match facts. Ask for missing facts or label them unconfirmed.
- No automatic publishing. Human approval is required before anything is posted.

## Upstream attribution and licence

The skills are authored by Jake Schincariol. The upstream project states that it is MIT licensed. This repository is an installer/configuration layer and does not claim authorship of the upstream skills. The installer fetches the upstream source at install time; review its current contents and licence before use.

Upstream: https://github.com/Jakeschincariol/youtube-agent-skill
