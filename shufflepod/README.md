# :shufflepod

Fork-only data layer for the "Podcasts" fork (see `docs/SHUFFLEPOD.md`).
It depends only on `:model`, so upstream modules such as `:storage:database` can call into it through small hooks.

- All fork data lives in its own SQLite file, `shufflepod.db` (`ShufflepodDatabase`), never in AntennaPod's database.
- `Shufflepod.init(context)` must run at app start (done in `ClientConfigurator`). It loads everything into in-memory caches, so reads are cheap and can happen on any thread; writes update the cache immediately and are persisted on a background thread.
- Episodes are stored with both AntennaPod's item ID (for fast lookups) and a stable key (feed URL + episode identifying value), so rows can be re-linked if IDs change.
- Pure logic (no Android dependencies), such as `QueuePlacement`, is unit tested under `src/test`.
