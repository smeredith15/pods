# Shufflepod (working name)

A personal fork of [AntennaPod](https://github.com/AntennaPod/AntennaPod), the open-source Android podcast manager, extended with four features no existing app offers:

1. **Oldest-first shuffle.** Pick a random show from a chosen pool, then always play that show's oldest remaining episode.
2. **Listening stats.** Richer statistics built on a detailed listening log.
3. **Per-show checklists.** A small to-do list attached to each podcast.
4. **Following people.** Follow guests and creators across shows, not just shows.

This app is for personal use only. It will not be published to any app store.

> **Decisions made so far:** little prior Android/Java experience, so Claude does the building. Builds through GitHub Actions. Free flavor, no Chromecast. Android Auto must keep working. Mid-way through dozens of shows in Pocket Casts, so the "mark everything before date X as played" tool moves up into M1.

> **How to use this document:** It is a working plan, not finished documentation. Sections marked **DECISION** need an answer before that part is built. The full list is in [Open questions](#open-questions). Update this file as decisions are made so it stays the source of truth.

---

## Contents

- [Goals and non-goals](#goals-and-non-goals)
- [What AntennaPod already gives us](#what-antennapod-already-gives-us)
- [Getting set up](#getting-set-up)
- [Guiding principles for the fork](#guiding-principles-for-the-fork)
- [Finding your way around the codebase](#finding-your-way-around-the-codebase)
- [Custom data storage](#custom-data-storage)
- [Feature 1: Oldest-first shuffle](#feature-1-oldest-first-shuffle)
- [Feature 2: Listening stats](#feature-2-listening-stats)
- [Feature 3: Per-show checklists](#feature-3-per-show-checklists)
- [Feature 4: Following people](#feature-4-following-people)
- [Migrating from Pocket Casts](#migrating-from-pocket-casts)
- [Roadmap](#roadmap)
- [Staying in sync with upstream](#staying-in-sync-with-upstream)
- [Testing](#testing)
- [Backups](#backups)
- [Licensing](#licensing)
- [Risks](#risks)
- [Open questions](#open-questions)

---

## Goals and non-goals

**Goals**

- A reliable daily-driver podcast player on my own Android phone.
- Roughly Pocket Casts-level everyday features (inherited from AntennaPod, not rebuilt).
- The four custom features above, done well enough that I use them daily.
- Easy to keep up to date with upstream AntennaPod bug fixes.

**Non-goals**

- App store publication, other users, or support for other people's devices.
- iOS, web, or desktop versions.
- Cross-device sync (unless decided otherwise; see Open questions).
- Rewriting AntennaPod's existing features or UI.

---

## What AntennaPod already gives us

Before writing any code, confirm AntennaPod covers the baseline. Verify each item on the current release, since features change between versions.

| Wanted feature | AntennaPod equivalent | Notes |
|---|---|---|
| Automatic episode fetching | Automatic feed refresh on an interval | Configurable globally |
| New episodes into the queue | Per-podcast "new episode" behavior (inbox vs. queue) | Check the podcast's settings screen |
| Background playback | Built in | Includes notification and lock-screen controls |
| Downloads | Manual and automatic download, auto-delete rules | Per-podcast overrides available |
| Folders | Tags, displayed as folders in the subscriptions view | Close equivalent, not identical to Pocket Casts folders |
| Per-podcast playback speed | Per-podcast playback settings | Also skip intro/outro, volume adaptation |
| Stats | Basic statistics (time per podcast), yearly "Echo" recap | We extend this; see Feature 2 |
| Chapters, sleep timer, skip silence | Built in | Skip silence behavior may differ from Pocket Casts |

**Milestone 0 exists specifically to test this table in practice.** If something important from Pocket Casts is missing, add it to the roadmap now rather than discovering it later.

---

## Getting set up

### What's already done

- This repo (`smeredith15/pods`) **is** the fork. It contains AntennaPod's full git history, merged at release tag `3.12.2`, and `upstream` points at `https://github.com/AntennaPod/AntennaPod.git`.
- This plan lives at `docs/SHUFFLEPOD.md` so it doesn't collide with upstream's `README.md`.
- Application ID is `io.github.smeredith15.pods` (debug builds: `io.github.smeredith15.pods.debug`), so it installs alongside the official AntennaPod. File-provider authorities were changed to match.
- The app is named **"Podcasts"** on the phone ("Podcasts Debug" for debug builds). Set in `common.gradle`.
- We ship the **free** flavor (no Google Play Services, no Chromecast). Android Auto support is in the main manifest, so it works in the free flavor too.

### Building (GitHub Actions)

There is no local Android Studio setup. Every push to a fork branch runs `.github/workflows/shufflepod-build.yml`, which builds the APK and attaches it to the run.

To install a build:
1. On GitHub, open **Actions → Build APK**, then the latest run.
2. Under **Artifacts**, download `podcasts-apk-…` (a zip), and unzip it on the phone.
3. Open the `.apk` and allow "install unknown apps" for your browser or file manager when asked.

### Release signing (one-time setup, required before daily use)

Android only installs an update over an existing app if both are signed with the same key. Without signing secrets, CI makes debug builds with a throwaway key that changes every run, so each new build has to be uninstalled first, which **deletes all app data**. Once the four secrets below are set, CI makes signed release builds that update in place.

In the repo on GitHub: **Settings → Secrets and variables → Actions → New repository secret**, and add:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 release.keystore`) |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

**Back up the keystore file and its passwords outside the repo** (password manager). If they are lost, you have to uninstall and reinstall, which wipes app data.

### Android Auto with a sideloaded app

Android Auto hides apps that weren't installed from the Play Store. To make "Podcasts" appear: open Android Auto settings on the phone, tap **Version** repeatedly until developer mode is enabled, then in the top-right menu open **Developer settings** and turn on **Unknown sources**.

### Syncing with upstream

```bash
git fetch upstream --tags
git merge 3.13.0        # merge the next upstream release tag, not develop
```

Then resolve conflicts (search for `SHUFFLEPOD`) and push. CI will build the result.

## Guiding principles for the fork

These keep the fork maintainable, which is the main long-term risk.

1. **Add, don't modify.** Put new code in new files and, ideally, new packages or modules. When you must touch existing AntennaPod code, keep the change as small as possible (ideally a single hook call into your code).
2. **Keep custom data out of AntennaPod's database.** See [Custom data storage](#custom-data-storage). This is the single most important rule.
3. **Mark every upstream edit.** Put a comment like `// SHUFFLEPOD:` on every line or block you change in existing files, so merge conflicts are easy to find and resolve.
4. **Keep logic pure and testable.** Shuffle selection, person matching, and stats calculations should be plain classes that take data in and return results, with no Android dependencies, so they can be unit tested.
5. **Match the codebase's language.** AntennaPod is almost entirely Java. Writing additions in Java avoids build changes. Kotlin is possible but adds setup; decide once and be consistent. **DECISION:** Java or Kotlin?

---

## Finding your way around the codebase

AntennaPod was substantially restructured into more modules around version 3.4 (2024), so older blog posts and Stack Overflow answers may point at paths that no longer exist. Rather than relying on fixed paths, search the project for these class names (Android Studio: Shift twice, or `Ctrl/Cmd+Shift+F`):

| Class | What it is |
|---|---|
| `Feed`, `FeedItem`, `FeedMedia` | The core models: a podcast, an episode, and an episode's audio file |
| `PodDBAdapter` | Low-level SQLite access for AntennaPod's database |
| `DBReader`, `DBWriter` | Higher-level read/write helpers; start here for queries |
| `PlaybackService` | The background playback service |
| `UserPreferences` | Global settings |
| Classes with `Statistics` in the name | Existing stats screens, a good template for Feature 2 |
| Classes with `Subscription` or `Tag` in the name | Subscriptions grid and folders |
| Feed parser / namespace classes | RSS parsing, relevant for Feature 4 |

Also look at how the app broadcasts events internally (it has historically used an event bus). Several features below hook into "playback started/paused/ended" and "feed refreshed" events rather than modifying the code that fires them.

**First exercise:** trace what happens when an episode finishes playing, from the playback service to the next queue item starting. Understanding this path is needed for shuffle and for stats logging.

---

## Custom data storage

### Use a separate database

All Shufflepod data goes in its own SQLite database file (for example, `shufflepod.db`), not in AntennaPod's database. Reasons:

- AntennaPod has its own schema version and migrations. If you add tables there, every upstream schema change can collide with yours, and a bad merge can corrupt your real listening data.
- A separate file can be backed up, exported, reset, or inspected independently.

Use either a plain `SQLiteOpenHelper` (matches the existing codebase, no new dependencies) or Room (nicer API, adds annotation processing to the build). **DECISION:** Which one?

### Identifying shows and episodes

AntennaPod's internal feed and episode IDs are database row IDs. They are stable in normal use but change if you unsubscribe and resubscribe, restore certain backups, or reinstall. Store both:

- The internal ID, for fast lookups.
- A stable key: the feed's URL for shows, and the feed URL plus the episode's GUID for episodes (fall back to the enclosure URL if an episode has no GUID).

Write a small resolver that looks up the internal ID from the stable key when the internal ID no longer matches.

### Draft schema

```sql
-- Feature 1: shuffle pools
CREATE TABLE shuffle_pool (
  id            INTEGER PRIMARY KEY,
  name          TEXT NOT NULL,
  source_type   TEXT NOT NULL,        -- 'tag' or 'manual'
  source_value  TEXT                  -- tag name, if source_type = 'tag'
);
CREATE TABLE shuffle_pool_member (   -- only used for manual pools
  pool_id       INTEGER NOT NULL,
  feed_url      TEXT NOT NULL,
  PRIMARY KEY (pool_id, feed_url)
);

-- Feature 2: listening log (one row per continuous listening session)
CREATE TABLE listen_session (
  id             INTEGER PRIMARY KEY,
  feed_url       TEXT NOT NULL,
  episode_guid   TEXT NOT NULL,
  started_at     INTEGER NOT NULL,    -- epoch millis, wall clock
  ended_at       INTEGER NOT NULL,
  start_pos_ms   INTEGER NOT NULL,    -- position in episode
  end_pos_ms     INTEGER NOT NULL,
  speed          REAL NOT NULL,
  was_streamed   INTEGER NOT NULL,    -- 0/1
  source         TEXT                 -- 'shuffle', 'queue', 'manual', ...
);
CREATE TABLE episode_completion (
  feed_url       TEXT NOT NULL,
  episode_guid   TEXT NOT NULL,
  completed_at   INTEGER NOT NULL,
  pub_date       INTEGER,             -- copied for back-catalog stats
  PRIMARY KEY (feed_url, episode_guid)
);

-- Feature 3: checklists
CREATE TABLE checklist_item (
  id             INTEGER PRIMARY KEY,
  feed_url       TEXT NOT NULL,
  text           TEXT NOT NULL,
  done           INTEGER NOT NULL DEFAULT 0,
  sort_order     INTEGER NOT NULL,
  created_at     INTEGER NOT NULL,
  done_at        INTEGER,
  episode_guid   TEXT,                -- optional link to an episode
  position_ms    INTEGER              -- optional timestamp within it
);

-- Feature 4: people
CREATE TABLE person (
  id             INTEGER PRIMARY KEY,
  display_name   TEXT NOT NULL,
  created_at     INTEGER NOT NULL
);
CREATE TABLE person_alias (          -- name variants to match on
  person_id      INTEGER NOT NULL,
  alias          TEXT NOT NULL
);
CREATE TABLE person_exclusion (      -- phrases that cause false positives
  person_id      INTEGER NOT NULL,
  phrase         TEXT NOT NULL
);
CREATE TABLE person_match (
  id             INTEGER PRIMARY KEY,
  person_id      INTEGER NOT NULL,
  feed_url       TEXT NOT NULL,
  episode_guid   TEXT NOT NULL,
  episode_title  TEXT,
  source         TEXT NOT NULL,       -- 'local_text', 'person_tag', 'podcastindex'
  confidence     TEXT NOT NULL,       -- 'high' (tag/title), 'medium' (description)
  status         TEXT NOT NULL,       -- 'new', 'confirmed', 'rejected'
  found_at       INTEGER NOT NULL,
  UNIQUE (person_id, feed_url, episode_guid)
);
```

This schema is a starting point. Adjust it once the open questions are answered.

---

## Feature 1: Oldest-first shuffle

### Behavior

- The user defines one or more **shuffle pools**. A pool is either a tag/folder or a hand-picked set of shows. **DECISION:** Tags only, manual only, or both?
- **Shuffle play** picks a random show from the pool, finds that show's oldest *eligible* episode, and plays it.
- **Continuous shuffle:** when that episode ends, pick another show and repeat until stopped.
- **Eligible episode:** not marked played. Partially played episodes count, and resume from their saved position. **DECISION:** Should streaming be allowed, or only downloaded episodes? A "prefer downloaded, stream if online" option is a reasonable default.
- Shows with no eligible episodes are skipped. If the whole pool is exhausted, stop and say so.

### Selection options (build the first, consider the rest)

- **Uniform per show** (default): every show with remaining episodes is equally likely.
- **No immediate repeats:** never pick the same show twice in a row if another is available.
- **Weighted by backlog:** shows with more remaining episodes are picked more often. **DECISION:** Wanted?
- **Recency cooldown:** reduce the chance of shows played recently.

### Design

- `ShuffleEngine` (pure logic): takes a list of candidate shows, each with its oldest eligible episode and any history needed for options, plus a random number source. Returns the chosen episode. Passing the random source in makes it deterministic in tests.
- `ShuffleController` (Android side): loads candidates from AntennaPod's database via `DBReader`, calls the engine, and hands the result to playback.

**Integration approach: keep the queue topped up, don't rewrite the playback service.** AntennaPod already knows how to play the next queue item when an episode ends. While shuffle mode is active, the controller keeps exactly one shuffle-chosen episode at the front of the queue. When playback of that episode ends (listen for the playback-ended event), it picks the next one and inserts it. This avoids touching `PlaybackService` internals, which is the part of the codebase most likely to change upstream and most likely to break in subtle ways.

**DECISION:** When shuffle stops, what happens to episodes it added to the queue? Options: leave them, or remove the one it added if it's unplayed.

### UI

- A "Shuffle" action on the subscriptions screen or a folder's screen.
- A pool manager screen (create, rename, delete, choose shows).
- A visible indicator in the player while shuffle mode is active, with a "skip to next show" action.
- Optional later: a home screen widget or notification action.

### Edge cases

- **Truncated feeds.** Many podcasts only include their latest 100 to 300 episodes in the RSS feed. "Oldest remaining" then means oldest *in the feed*, not the true first episode. Some shows have back catalogs only on premium feeds. Nothing to fix here, just be aware.
- **Missing or wrong publish dates.** Sort by publish date, with the feed's own order as a tiebreaker. Some feeds republish old episodes with new dates.
- **Trailers and bonus episodes** may be the "oldest" episode. Consider a per-show "skip episodes shorter than N minutes" or title-keyword filter later.
- **Imported episodes you have already heard.** See [Migrating from Pocket Casts](#migrating-from-pocket-casts). This is the biggest practical issue for this feature.

### Done when

- I can start continuous shuffle from a folder, and it plays the oldest unplayed episode of a random show, then moves on to another show, for at least a full commute without manual intervention.
- `ShuffleEngine` has unit tests covering: empty pool, single show, exhausted shows, no-immediate-repeat, and deterministic output with a seeded random source.

---

## Feature 2: Listening stats

### Start with logging, not screens

Stats are only as good as the data behind them, and data can't be collected retroactively. So the first step is logging listening sessions accurately, starting as early as possible, even before any stats UI exists.

A **session** starts when playback starts or resumes, and ends on pause, stop, episode end, episode switch, or app/service shutdown. Record wall-clock start and end, episode position at start and end, speed, whether it was streamed, and what started it (shuffle, queue, manual).

Hook into AntennaPod's playback state events rather than modifying the playback service. Be careful with sessions that never get a clean end (the app is killed): periodically update the open session's end time and position (for example, every 30 seconds) so a killed process loses at most a few seconds.

When an episode is marked played, record it in `episode_completion` with its publish date.

### Export first

Before building charts, add **Export listening log as CSV**. That lets me explore the data on a computer (spreadsheet, Python, whatever) and find which stats are actually interesting before committing to building them into the app.

### Candidate stats

Mark the ones worth building. **DECISION:** Which of these matter, and what's missing?

- Listening time per show, per week or month.
- Real time vs. content time, and time "saved" by speed-up.
- Backlog per show: remaining episodes, total remaining duration, and projected time to finish at my current pace for that show.
- Back-catalog progress: "currently on 2017 episodes of Show X," or percentage through a show's feed.
- Completion rate: episodes finished vs. abandoned partway.
- Listening by hour of day and day of week.
- Streaks: consecutive days with listening.
- Shuffle vs. manual vs. queue share.
- Most-heard people (combines with Feature 4).
- Streamed vs. downloaded share (useful for data usage).

### Design

- `StatsCalculator` (pure logic) takes sessions and completions in, returns plain result objects. Unit test it with synthetic data, especially around midnight boundaries, time zones, and overlapping sessions.
- A stats screen modeled on the existing statistics screens. Start with plain lists and numbers. Add a charting library later only if needed.

### Done when

- Sessions are logged reliably (compare total logged time against AntennaPod's own playback time stats for a week; they should roughly agree).
- CSV export works.
- At least three chosen stats are visible in the app.

---

## Feature 3: Per-show checklists

### Behavior (proposed; confirm)

- Each show has an ordered list of checklist items with text and a done state.
- Items can optionally link to an episode and a timestamp. Tapping the link opens that episode at that position.
- A **quick-add from the player** button creates an item for the current show, pre-filled with the current episode and timestamp, so I can capture things mid-listen ("look up the book mentioned here").
- Completed items can be hidden or shown.

**DECISION:** What are the checklists actually for? The design changes depending on the answer:

- *Notes to self while listening* (book recommendations, things to look up): the quick-add-from-player flow is the core, and a combined "all open items across shows" view becomes important.
- *Tracking progress through a show* (for example, "finish season 2," "listen to the episodes with guest X"): items might want to link to multiple episodes or auto-complete when those episodes are played.
- *Something else entirely.*

Also: **DECISION:** Checklists per show only, or per episode too?

### Design

- Stored in `checklist_item` in the Shufflepod database.
- UI: a new section or tab on the show's info screen; the quick-add button in the player screen; optionally a global "open items" screen.

### Done when

- I can add, reorder, complete, and delete items on a show, and quick-add from the player with the timestamp captured.

---

## Feature 4: Following people

This is the most uncertain feature. Build it in phases and expect imperfect results.

### The core problem

RSS feeds have no reliable, structured "guest" field. The Podcasting 2.0 namespace defines a `<podcast:person>` tag for hosts and guests, but most shows don't use it. So matching is mostly text search over episode titles and descriptions, which produces both false positives (name collisions, episodes that only mention someone) and misses (guest not named in the text).

### Phase 1: Local matching (subscribed shows only, no network)

- A **Person** has a display name, one or more aliases (for example, "Dr. Jane Smith," "Jane Smith"), and optional exclusion phrases to cut false positives.
- After each feed refresh, run the matcher over new episodes. Also provide a "rescan everything" action for when a new person is added.
- **Matching rules:** case-insensitive, accent-insensitive, whole-word matches on any alias. A match in the title is higher confidence than a match in the description (show notes often mention people who don't appear).
- **Person tags:** check whether AntennaPod's feed parser already reads `<podcast:person>`. If not, adding it is a contained change in the parser's namespace handling (an upstream-compatible change you could even contribute back). Tag matches are high confidence.
- Store results in `person_match` with a status of new, confirmed, or rejected. Rejections are remembered, so the same false positive doesn't reappear.
- **UI:** a People screen listing followed people, each with their matched episodes (newest first), with actions to play, add to queue, confirm, or reject.
- **DECISION:** When a new match is found, what should happen? Options: nothing (just shows up in the People screen), a notification, add to inbox, or add to queue.

### Phase 2: Remote search (all podcasts, needs network and an API key)

Phase 1 only finds people on shows I already subscribe to. To find appearances on other shows, query a podcast search service on a schedule (for example, daily, using Android's `WorkManager`).

- **Podcast Index API** (free, requires signing up for an API key and secret). It has a search-by-person endpoint that combines person tags and text matching. Requests are authenticated with headers containing the key, the current Unix time, and a SHA-1 hash of key + secret + time. Keep the key and secret in `local.properties` or similar, never in git.
- **Listen Notes API** is an alternative with stronger full-text search but is paid beyond a small free tier.

Results from shows I'm not subscribed to need a way to be played. AntennaPod can already preview a podcast without subscribing, so the simplest approach is to link a result to that preview screen for its feed, then play the episode from there.

**DECISION:** Is Phase 2 wanted at all, or is Phase 1 (subscribed shows only) enough?

### Done when

- Phase 1: I can follow a person and see their episodes across my subscriptions, confirm or reject matches, and rejected matches stay rejected.
- Phase 2: New appearances on shows I don't subscribe to show up within a day.

---

## Migrating from Pocket Casts

1. **Export subscriptions** from Pocket Casts as OPML (in its import/export settings) and import the file into Shufflepod.
2. **The played-status problem.** OPML contains only the list of shows, not listening history. After import, every episode of every show looks unplayed. For most apps this is a nuisance. For oldest-first shuffle it's a real problem, because shuffle will happily start playing episodes from years ago that I already heard.
3. **Fixing played status**, from least to most effort:
   - Per show, use AntennaPod's multi-select to mark everything before a certain episode as played. Tedious, but works and needs no code.
   - Build a small **"mark all episodes before date X as played"** action per show. Probably worth it; it is simple and reusable.
   - Pull listening history from Pocket Casts through its unofficial API. This is undocumented, could break at any time, and may not be worth the effort. Only consider it if the manual approaches are too painful.
4. Positions of partially played episodes will not carry over.
5. Per-podcast settings (speed, auto-download, folders) need to be set up again by hand.

**DECISION:** Roughly how many shows are in Pocket Casts, and how many have deep back catalogs I've partially worked through? This decides whether the "mark before date" tool is needed early.

---

## Roadmap

Logging comes early because stats data can't be collected retroactively.

| Milestone | Scope | Exit criteria |
|---|---|---|
| **M0: Baseline** | Fork, rename, change app ID, release signing, install. Migrate subscriptions. Use unmodified for a week alongside Pocket Casts. | Confident AntennaPod covers the everyday features. List of any gaps written down. |
| **M1: Foundations** | Shufflepod database, stable-key resolver, listening session logging, CSV export. "Mark before date as played" if needed for migration. | Logging runs quietly in the background and CSV export works. |
| **M2: Shuffle** | Pools, `ShuffleEngine`, continuous shuffle via queue top-up, player indicator. | Full commute on continuous shuffle, no intervention. |
| **M3: Checklists** | Checklist storage and UI, quick-add from player. | Using it for real notes. |
| **M4: Stats UI** | Chosen stats, based on what the CSV exploration showed. | Three or more stats in-app. |
| **M5: People (local)** | People, aliases, matcher, person tags, People screen. | Following at least a few people with acceptable noise. |
| **M6: People (remote)** | Podcast Index integration, scheduled search. | Appearances on unsubscribed shows found automatically. |

After each milestone: merge upstream (see below), then daily-drive for a few days before starting the next one.

---

## Staying in sync with upstream

AntennaPod gets regular bug fixes, Android compatibility updates, and new features. Falling far behind makes catching up painful.

```bash
git fetch upstream
git checkout develop && git merge upstream/develop   # keep the mirror clean
git checkout shufflepod && git merge develop          # bring changes into the fork
```

(Check upstream's default branch name; adjust `develop` if it differs.)

- Merge roughly monthly, or after each upstream release.
- Resolve conflicts by searching for `SHUFFLEPOD:` markers.
- After every merge: build, run unit tests, and run the manual smoke test below before installing on the daily-driver phone.
- Consider merging from upstream release tags rather than the development branch, so the daily driver runs on code that has been through upstream's beta testing.

---

## Testing

- **Unit tests** for all pure logic: `ShuffleEngine`, `StatsCalculator`, the person matcher, and the stable-key resolver.
- **Manual smoke test** before installing any new build on the daily driver:
  - Play, pause, and resume from the lock screen and headphones.
  - Let an episode finish and confirm the next one starts.
  - Start continuous shuffle and let it advance at least once.
  - Refresh feeds and confirm new episodes arrive.
  - Download an episode and play it in airplane mode.
  - Add a checklist item from the player.
  - Check that a listening session was logged.
- Consider a **second install** (a debug build with a different application ID) for experiments, so the daily-driver install is never the test subject.

---

## Backups

- AntennaPod has its own database export. It will **not** include the Shufflepod database. Add a matching export/import for `shufflepod.db`, ideally combined into one "back up everything" action.
- Back up regularly, especially before installing a new build after an upstream merge.
- Back up the signing keystore separately (see setup step 7).

---

## Licensing

AntennaPod is licensed under the GPL-3.0. For purely personal use with no distribution, the license places no obligations on me. If I ever share the built app with anyone, I must also make the source code of the modified version available under the GPL. If the app is ever shared, it should also use its own name and icon rather than AntennaPod's.

---

## Risks

| Risk | Mitigation |
|---|---|
| Upstream changes break custom code | Additive design, `SHUFFLEPOD:` markers, regular small merges |
| Lost signing key forces uninstall and data loss | Back up the keystore; exports for both databases |
| Buggy build breaks daily listening | Smoke test before installing; keep the previous APK around to reinstall |
| Shuffle replays already-heard episodes | Fix played status during migration (see above) |
| Person matching is too noisy to be useful | Exclusion phrases, confirm/reject, title-only mode; accept it may stay "good enough" rather than precise |
| Project stalls partway | Each milestone leaves a usable app; the baseline is already a complete podcast player |

---

## Open questions

Answer these before or during the relevant milestone, then fold the answers into the sections above.

**General**
1. ~~How comfortable am I with Java/Kotlin and Android development?~~ **Answered:** little experience; Claude builds.
2. Java or Kotlin for new code?
3. ~~Do I need Chromecast?~~ **Answered:** no, so we use the free flavor.
4. ~~Do I need Android Auto or Wear OS?~~ **Answered:** Android Auto yes (check it works during M0). Wear OS not needed.
5. Is sync to a second device needed ever, or is one phone enough?
6. Plain SQLite or Room for the Shufflepod database?

**Shuffle**
7. Pools from tags/folders, hand-picked shows, or both?
8. Stream when an episode isn't downloaded, or downloaded only?
9. Uniform randomness per show, or weighted by backlog? Is "no immediate repeat" enough?
10. When shuffle stops, keep or remove the episode it queued?
11. Should trailers/short bonus episodes be skippable?

**Stats**
12. Which of the candidate stats matter most? What's missing from the list?
13. Where should stats live: a new screen, or extending the existing statistics screen?

**Checklists**
14. What are checklists mainly for: notes while listening, tracking progress through a show, or something else?
15. Per show only, or per episode too?
16. Is a combined "all open items" view needed?

**People**
17. Is Phase 1 (subscribed shows only) enough, or is Phase 2 (all podcasts) needed?
18. What should happen when a new match is found: nothing, notification, inbox, or queue?
19. Follow creators (hosts who start new shows) as well as guests? Hosts are better served by person tags and by searching for new feeds, which is a slightly different problem.

**Migration**
20. ~~Roughly how many shows?~~ **Answered:** partway through dozens of shows, so "mark before date as played" is part of M1.
