# v28 handoff (paused again 10 Oct ~09:20, user request — Mac overloaded)

## Status 09:20
- Server effort fixes DONE + committed (8c7b89a), proxy live with them (P1 1-3, P2 5-7, 10 done).
- App fixes written but NOT built/committed yet (uncommitted in working tree):
  MetaRepo.isLive + setReasoningEffort guard (stale runtime → refuse, no global write),
  ModelSheet profile=chatProfile, fallback menu on model.options failure, On/Off keys for
  no-dial models, Ultra-weaker-than-top note, EffortRepo.strongest/ultraIsStrongest,
  BotMonitor clock keyed on task, EffortRepoTest +2. → Next: build + tests, emulator check, commit.


## Done (committed)
- Control Room redesign all screens + 5 data features + bug-review fixes (550236d, 67d29b6).
- Per-model reasoning effort (98704a1): proxy `GET /api/mobile-efforts` (server/mobile_efforts.py,
  built from Hermes' own request builders) + app EffortRepo + ModelSheet keys Off · native · Ultra.
  Verified on emulator: Opus 5.5 = Off/Low/Med/High/XHigh/Max/Ultra ("Ultra runs at Max"),
  GLM 5.3 = no XHigh. Ultra persists server-side; live Ultra turns OK on Opus + GLM.
  Blokees chat restored to Max. Test chats deleted.
- Design polish r4/r5 verified: Mac 2-up key grid, unified Agents footers, Give task aligned,
  Files names break at separators + solid slate strip, Settings tablet Enter segmented.
- Worktree hermes-mobile-v28 + branch v28-features removed.
- versionCode is still 30 / "27" (bump only at release).

## Next: fix effort reviewer findings (deleg_11a71e74), then release
P1
1. mobile_efforts cache: never cache `known:false` (or 30s TTL); warm `providers.list_providers()`
   once at proxy startup (executor); threading.Lock around cold path + cache writes/evict.
   (Race: parallel first calls / overlap with mobile-limits discovery → provider lookup None →
   full-ladder menu cached forever.)
2. Off key: derive `can_off` from builders — probe `{'enabled': False}` and require an explicit
   disable on the wire (thinking.type disabled, reasoning.enabled False, effort 'none',
   thinkingBudget 0, enable_thinking False). Responses/Codex routes → can_off False.
   Today Off shows for codex gpt-5.6/astra, copilot, xai, openrouter mandatory Claude → lies.
3. Per-model route like `agent/agent_init._resolve_api_mode`: copilot_model_api_mode (gpt-5.x →
   codex_responses, wire from `_github_models_reasoning_extra_body`: max/ultra → medium!),
   opencode_model_api_mode, azure_foundry_model_api_mode, nous_api_mode.
P2
4. No-dial models that can turn thinking off (glm-4.6): show Off/On keys.
5. Gemini/Vertex: read thinking_config.thinkingLevel / thinking_level (nested google form).
6. xAI: only grok_supports_reasoning_effort models have levels; map xai-oauth → xai.
7. OpenRouter/Ollama non-reasoning models (catalog supports_reasoning False) → dial False.
8. Legacy budget Claude (3.7, opus/sonnet 4.5, opus 4.1): max/ultra run at MEDIUM budget →
   hide/flag Ultra when it runs weaker than the strongest native level; fix docstring.
9. IMPORTANT: config.set key=reasoning with a stale runtime id writes GLOBAL
   agent.reasoning_effort (all chats/bots/cron). App: re-check runtime is live before
   setReasoningEffort (session.list / lazy resume), refuse otherwise. (Pre-existing in v27.)
10. Docstring "no network" false (OpenRouter caps warm); base_url not in cache key.
11. ModelSheet: if model.options fails → effort row shimmer forever → use Menu.fallback/retry.
12. BotMonitor remember(b.name, taskSecs==null) keeps first task's clock on chained bot-runs →
    key on taskSessionId/task.

## Then
- Rebuild + tests, emulator (claim 5572 lock): effort sheet on Opus, GLM 5.3, Copilot gpt-5.4,
  Codex gpt-5.6; Off hidden where it can't work.
- Optional: header solid bg on scroll; tablet 2-pane screenshot.
- Bump versionCode 31 / versionName "28"; APK → ~/Desktop + release/; push;
  `gh release create v28`; curl 200; kill emulator, remove lock; session-sweep; report.
