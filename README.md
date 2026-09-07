# YYC-KOB

Ranking engine for **King of the Beach Calgary** — a recurring beach volleyball
series that has been running for years, historically tracked in spreadsheets.

This application reads game results from Google Sheets, computes a rolling
**master score** for every player, and publishes an interactive HTML dashboard.

---

## Table of contents

- [How scoring works](#how-scoring-works)
  - [1. The master score](#1-the-master-score)
  - [2. Each game gets its own score range](#2-each-game-gets-its-own-score-range)
  - [3. Each finish position maps to a game score](#3-each-finish-position-maps-to-a-game-score)
  - [4. Game scores roll up into the master score](#4-game-scores-roll-up-into-the-master-score)
  - [5. The one-year window](#5-the-one-year-window)
  - [Worked example](#worked-example)
  - [Tuning constants](#tuning-constants)
- [Tiers and winners](#tiers-and-winners)
- [Spreadsheet structure](#spreadsheet-structure)
- [Adding new games](#adding-new-games)
- [Command line](#command-line)
- [The dashboard](#the-dashboard)
- [Setup](#setup)
- [Automated publishing](#automated-publishing)
- [Project layout](#project-layout)
- [Known quirks](#known-quirks)

---

## How scoring works

The goal is a score that reflects **how you are playing now**, measured against
**the people you actually played against**, without letting one great or awful
night dominate, and without letting results from years ago linger forever.

Four ideas do the work:

1. Every player carries a **master score**, starting at 50.
2. Each game builds a **score range** from the master scores of who showed up.
3. Your **finish position** maps linearly onto that range to give a game score.
4. Your game scores are **weighted by recency** to produce your new master score.

### 1. The master score

Every player starts at `INITIAL_SCORE = 50`. There is no cap and no floor — the
scale floats with the group. A player with no results inside the one-year window
resets to 50 and drops off the ranking.

Scores are computed by a **single forward pass** over the full game history, in
game-ID order. Each game is scored using the master scores as they stood
*immediately before that game*, so the history is reconstructed exactly rather
than being back-fitted.

### 2. Each game gets its own score range

A night against the top of the ladder should be worth more than the same finish
against a soft field. So each game derives its own ceiling and floor from the
players present.

Let `N` be the number of participants and `q = N / 4` (integer division — the
quartile size). Sort participants by their **pre-game** master score, descending.

```
averageTop    = mean(master score of the top q players)    + SCORE_RANGE_MARGIN
averageBottom = mean(master score of the bottom q players) - SCORE_RANGE_MARGIN
```

`SCORE_RANGE_MARGIN` is 10. It exists so that the best player on the night can
still gain ground and the weakest can still lose it — without it, the range
would be pinned to the field and nobody could move past its edges.

Two clamps then guarantee the range actually covers everyone present:

```
if averageTop    < highest pre-game master score:  averageTop    = that score
if averageBottom > lowest  pre-game master score:  averageBottom = that score
```

The result is stored as the game's `highestPoint` and `lowestPoint`.

> These are carried as full-precision `double` values. The spreadsheet displays
> them rounded to one decimal, so hand-checking against the sheet can differ in
> the last digit.

### 3. Each finish position maps to a game score

Finish positions are spread linearly across the range: 1st place receives
`highestPoint`, last place receives `lowestPoint`, and everyone else sits at an
even increment between.

```
gameScore = highestPoint − (finish − 1) × (highestPoint − lowestPoint) / (N − 1)
```

**Ties** are recorded as fractional positions. Two players tied for first are
both entered as `1.5`, which lands them exactly halfway between the 1st- and
2nd-place scores — the correct average of the positions they shared. This falls
out of the formula for free; no special-casing is needed.

### 4. Game scores roll up into the master score

This is the part that makes the score responsive without making it jumpy.

A player's results are sorted by **gap** — the distance in game IDs between the
game being scored and the result in question:

```
gap = currentGameId − resultGameId
```

Note that the gap counts **games the group played**, not games *you* played. Sit
out three weeks and your results age just as fast as everyone else's.

Results then fall into three buckets, with `W = RECENT_WINDOW_SIZE = 8`
(roughly four weeks at two games per week):

| Bucket      | Condition        | Contribution                          |
| ----------- | ---------------- | ------------------------------------- |
| **Recent**  | `gap ≤ 8`        | each result counts **individually**   |
| **Mid**     | `8 < gap < 16`   | all results collapse to **one** value (their mean) |
| **Old**     | `gap ≥ 16`       | all results collapse to **one** value (their mean) |

The master score is the mean of those contributions:

```
n = (number of recent results) + (1 if mid bucket non-empty) + (1 if old bucket non-empty)

masterScore = ( Σ recent scores  +  mean(mid)  +  mean(old) ) / n
```

The effect: your last ~8 games each pull with full weight, while everything
older is compressed into at most two data points. Recent form dominates, but
history still anchors you — and a single bad night among eight cannot tank you.

A player with no qualifying results resets to 50 and is dropped from the
ranking.

### 5. The one-year window

With `LIMIT_TO_A_YEAR = true`, results older than one year are excluded outright.

The cutoff is measured from **today** (or from the date given to `--as-of`), not
from the date of the most recent game. This matters during off-season gaps: if
the last game was played in November and you run the tool in March, results from
the previous February have genuinely aged out and must not count.

Because the forward pass scores each game as of *that game's* date, a final
recalculation pass runs after the loop using the reference date, so the
published standings always reflect the window as of now.

### Worked example

Real numbers from game #447 — 20 players:

```
Average master score, top 25% (5 players)      66.4
Average master score, bottom 25% (5 players)   36.0

Ceiling:  66.4 + 10  =  76.4
Floor:    36.0 − 10  =  26.0  →  clamped to 25.5
                                 (lowest player sat at 25.5, so the floor
                                  drops to include them)

Increment: (76.4 − 25.5) / (20 − 1) = 2.679
```

Which produces:

| Finish | Calculation          | Game score |
| ------ | -------------------- | ---------- |
| 1      | 76.4 − 0 × 2.679     | **76.4**   |
| 1.5    | 76.4 − 0.5 × 2.679   | **75.1**   |
| 3      | 76.4 − 2 × 2.679     | **71.0**   |
| 5      | 76.4 − 4 × 2.679     | **65.7**   |
| 20     | 76.4 − 19 × 2.679    | **25.5**   |

Now suppose a player finished 3rd (game score 71.0) and their recent history is:

```
Recent (gap ≤ 8):   68.2, 71.0, 64.5, 70.1        → 4 individual values
Mid    (gap 9–15):  66.0, 69.4                    → mean = 67.7  (1 value)
Old    (gap ≥ 16):  61.2, 63.8, 59.9              → mean = 61.6  (1 value)

n = 4 + 1 + 1 = 6
masterScore = (68.2 + 71.0 + 64.5 + 70.1 + 67.7 + 61.6) / 6 = 67.2
```

### Tuning constants

All in `KOB.java`:

| Constant              | Value  | Meaning                                                      |
| --------------------- | ------ | ------------------------------------------------------------ |
| `INITIAL_SCORE`       | `50`   | Starting master score for a new or returning player           |
| `MINIMUM_NB_PLAYERS`  | `8`    | Attendance gate — see [Known quirks](#known-quirks)           |
| `SCORE_RANGE_MARGIN`  | `10`   | Padding above/below the field so the edges can still move     |
| `RECENT_WINDOW_SIZE`  | `8`    | Bucket width in games; recent ≤ 8, mid 9–15, old ≥ 16         |
| `QUARTILE_DIVISOR`    | `4`    | Divisor for the top/bottom sample (4 → quartiles)             |
| `LIMIT_TO_A_YEAR`     | `true` | Whether results older than a year are discarded               |

---

## Tiers and winners

King of the Beach runs on courts of four. The dashboard groups each game into
tiers of four by finish position:

```
numTiers   = max(1, N / 4)
playerTier = min((finish − 1) / 4 + 1, numTiers)
```

A **winner** is anyone who took their court — finish positions 1, 5, 9, 13, …
(that is, `(finish − 1) % 4 == 0`). The Games tab lists winners in tier order,
tier 1 first.

Tiers are a presentation concept only. They do not feed into scoring — the score
formula is purely positional across the whole field.

---

## Spreadsheet structure

The workbook holds several sheets:

**`Game Results`** — the source of truth. One row per game.

| Col A   | Col B        | Col C onward                       |
| ------- | ------------ | ---------------------------------- |
| Game ID | Date (ISO)   | One column per player; cell holds their finish position |

**`Game Input`** — staging area for entering new games. See below.

**`Computed Scores`** — the score cache: `Game ID | Player | Score`, one row per
player per game. Written after every run. Editing a value here overrides the
computed score for that game, and the override survives future runs — useful for
correcting a historical result without rewriting the source data.

**`Ranking`**, **`Statistics`**, **`MasterScores`** — generated output.

**`Sheet8`** — legacy reference scores from the original spreadsheet system,
retained so `--compare` can validate the engine against it.

---

## Adding new games

Paste results into the **`Game Input`** sheet and run the app. No flag needed —
the import runs automatically before every ranking computation.

Layout — one column per game, headers containing the game ID:

| Players        | G451 | G452 |
| -------------- | ---- | ---- |
| Chris Mitchell | 1.5  | 4    |
| Brandon Burnside | 1.5 | 2   |
| Mark Patton    | 20   | 18   |

The importer will:

1. Parse each game ID from its column header (any header matching `G<number>`).
2. Append one correctly-ordered row per game to `Game Results`.
3. **Register unknown players automatically** as new columns in the
   `Game Results` header.
4. **Skip any game ID already present**, so a re-run cannot double-count.
5. Clear the staging sheet.

The **date column is left blank** — fill it in on `Game Results` afterwards.
Games without a valid date will not load on the next run.

---

## Command line

```bash
java -jar target/kob-1.0-SNAPSHOT.jar [command] [--debug-player "Name"]
```

| Command             | Effect                                                              |
| ------------------- | ------------------------------------------------------------------- |
| *(none)*            | Import staged games, recompute rankings, write the dashboard         |
| `--as-of YYYY-MM-DD`| Rebuild the standings as they were on a given date                   |
| `--compare`         | Score the full history from scratch and report accuracy vs `Sheet8`  |
| `--clear-cache`     | Wipe `Computed Scores` so the next run recomputes from scratch       |
| `--debug-player "Name"` | Print that player's bucket-by-bucket breakdown; combines with any command |

`--as-of` never writes to the score cache, so historical queries cannot corrupt
the live data.

`--compare` reports per-game mean absolute error, worst single-player deviation,
and an overall MAE against the legacy `Sheet8` values — the quickest way to tell
whether a change to the engine drifted from the historical baseline.

Every run also prints a table for the last five games showing finish order,
pre- and post-game master scores, the score range, and the quartile averages.

---

## The dashboard

Generated as a single self-contained `dashboard.html`:

- **Ranking** — podium plus a sortable, searchable table of current standings.
- **Statistics** — global leaders (most wins, most games, per-tier records) and
  a per-player breakdown.
- **Games** — full history. Each game expands to show tier groupings, winners,
  tier movement, and per-player score deltas. Each is labelled with whether it
  counted and which bucket it currently falls in (recent / mid avg / old avg).
- **Evolution** — an animated bar chart race of the top 10 over time, plus
  per-player score and rank charts.

---

## Setup

**Requirements:** Java 21, Maven, a Google Cloud service account.

1. **Create a service account** in the Google Cloud console with the Sheets API
   enabled, and download its JSON key.

2. **Save the key** to `src/main/resources/credentials.json`.
   This path is gitignored — never commit it.

3. **Share the spreadsheet** with the service account's `client_email` (found in
   the JSON key), granting **Editor** access. The spreadsheet can live in anyone's
   Google account; only this share is required.

4. **Point the app at the sheet** in `src/main/resources/config.properties`:

   ```properties
   dao.type=GSheet
   gsheet.spreadsheet.id=<spreadsheet id from its URL>
   html.output.path=dashboard.html
   ```

5. **Build and run:**

   ```bash
   mvn package
   java -jar target/kob-1.0-SNAPSHOT.jar
   ```

---

## Automated publishing

`.github/workflows/update-rankings.yml` builds the project, runs it, and deploys
`dashboard.html` to GitHub Pages — twice daily, and on demand from the Actions
tab.

Required one-time setup:

1. **Repository secret** `CREDENTIALS_JSON` — the service account key,
   base64-encoded:

   ```bash
   base64 -i src/main/resources/credentials.json | pbcopy
   ```

   Add it under *Settings → Secrets and variables → Actions*.

2. **Enable Pages** under *Settings → Pages*, serving from the `gh-pages` branch
   at root.

The dashboard then lives at
`https://<user>.github.io/<repo>/dashboard.html` — a single link to share.

---

## Project layout

```
src/main/java/
├── kob/
│   ├── KOB.java                  Entry point, scoring engine, CLI
│   └── StatisticsComputer.java   Global and per-player statistics
├── dto/
│   ├── Game.java                 A session: id, date, score range
│   ├── Player.java               A player and their current master score
│   └── Result.java               One player's finish in one game
└── dao/
    ├── DaoFactory.java           Selects the GSheet or JDBC backend
    ├── HtmlWriter.java           Builds the dashboard
    ├── daoInterface/             Backend-agnostic contracts
    ├── daoGSheet/                Google Sheets implementation
    └── daoJdbc/                  SQL implementation (secondary)
```

Switch backends with `dao.type` in `config.properties`. The Google Sheets path
is the maintained one; the JDBC path does not implement the score cache or game
input staging.

---

## Known quirks

**The attendance gate is off by one.** `MINIMUM_NB_PLAYERS` is 8, but the loaders
in `GameDaoGSheet` and `ResultDaoGSheet` test `count > MINIMUM_NB_PLAYERS`, so a
game needs **9 or more** players to load at all. `HtmlWriter` separately tests
`>= MINIMUM_NB_PLAYERS`. Since games with 8 or fewer players never load, that
branch is unreachable for exactly-8 and the effective threshold everywhere is 9.
Worth reconciling to a single comparison.

**Score cache versus engine changes.** `Computed Scores` is replayed over the
forward pass, so cached values win over freshly computed ones. After changing
the scoring rules, run `--clear-cache` — otherwise the old numbers persist and
the dashboard will appear not to have picked up the change.

**Dates are entered by hand.** The importer leaves the date column blank. A game
with no date will not load, which can look like a silent import failure.
