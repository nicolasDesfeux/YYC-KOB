# Potential Improvements

Status key: **Open** · **Partial** — some of it landed, the rest is described ·
**Done** — kept briefly for context, delete once it stops being useful.

Last reviewed against the codebase on 2026-09-07.

---

## Scoring Model

### Decay instead of hard cutoff — Open
The one-year cutoff drops results abruptly. A time-decay weight (e.g. exponential
decay) would create a smoother transition where older results gradually matter
less rather than disappearing entirely.

Now cheaper to try than it was: the buckets live in `ScoringEngine.masterScore`
as a pure function with tests around the existing boundaries, so an alternative
weighting can be written and compared without touching the forward pass.

### Score floor — Open
Master scores have no lower bound and can in principle go negative, which would
distort the score range for every subsequent game the player attends. A
configurable floor would prevent that. `scoring.` already has a home for the key.

### Configurable window sizes — Done
`KobConfig` reads `scoring.initial.score`, `scoring.minimum.players`,
`scoring.range.margin`, `scoring.recent.window`, `scoring.quartile.divisor`, and
`scoring.limit.to.year`, each falling back to its historical default. Malformed
values are reported and defaulted rather than failing the run.

---

## Data Quality

### Player name normalisation — Done
This turned out to be a live bug rather than hardening. `getAllPlayers` built
players from untrimmed sheet headers while `getPlayerByName` matched on exact
equality, so a trailing space or case difference produced a player nobody could
look up — and that player's results were skipped with only a warning. Both sides
now match on `Player.normaliseName` (trim, lower-case, collapse internal
whitespace), the lookup is indexed, and duplicate columns warn. The game importer
shares the same normaliser so import and scoring agree on identity.

### No validation on sheet input — Partial
The importer now skips duplicate game IDs, warns on unmatched players and on
duplicate player columns, and ignores blank header columns. What is still missing
is a **pre-run validation pass** that reports every problem in the sheet at once,
before anything is written — currently issues surface one at a time in the log
during a run that is already mutating the spreadsheet.

Worth including: out-of-range finish positions, positions duplicated within a
game, gaps in the finish sequence, and rows whose date is missing or unparseable.

### Tied results are entered manually — Open
A tie is recorded by hand as a fractional position (two players tied for first
are both `1.5`). The arithmetic handles this correctly and is covered by tests,
but nothing validates that the fractions are consistent — e.g. that exactly two
players share `1.5` and that nobody is left on a plain `2`.

### Missing dates are silent — Open *(new)*
The importer deliberately leaves the date column blank for manual entry, and
`GameDaoGSheet` skips any row whose date will not parse. A game imported but not
yet dated therefore vanishes with no clear signal that it is waiting on input.
A warning naming the undated game IDs would make this obvious.

---

## Architecture

### Unit tests — Done
35 JUnit 5 tests cover the score range and both clamps, the finish-to-score
mapping including fractional ties, all three recency buckets and their boundaries
at gap 8 and gap 16, the one-year cutoff tracking the reference date, config
parsing and fallback, and name normalisation. `surefire` runs them during
`mvn package`, so they gate CI.

Two tests deliberately pin *current* rather than *correct* behaviour and are
named to say so. See the quirks section below.

### Configuration class — Done
`KobConfig` is loaded once at startup and reached through `KOB.config()`.
`ScoringEngine` takes it as an explicit parameter, which is what lets tests
exercise alternative configurations without touching global state.

### Incremental computation — Open
*Previously filed as "persist computed scores", which now reads as done and is
not.* The `Computed Scores` sheet persists a snapshot after every game, but
`computeScoreEvolution` still replays the entire history on every run and applies
the cache as a per-game **override after** computing each game. The persistence
landed; the performance goal it was filed for did not.

Doing this properly means starting the forward pass from the last cached snapshot
and processing only newer games. The complication is the one-year window: results
ageing out change scores for players who did not play, so a run must still revisit
anyone whose oldest counted result has expired since the previous run.

### Decouple computation from Google Sheets — Partial
`ScoringEngine` is now pure and free of I/O, and `HtmlWriter` is a second output
target alongside the sheet writers. `GSheetConnector` still mixes sheet I/O with
presentation concerns (banding, podium colours, autofilter), so adding another
target still means going through it.

---

## Features

### Historical ranking view — Done
`--as-of DATE` rebuilds the standings for any date without writing to the cache,
and the Evolution tab animates the top ten over time.

### Player profile page — Done
The Evolution tab carries per-player score and rank charts, which covers the
intent better than the per-player sheet tab originally proposed.

### Streak tracking — Open
Consecutive wins, consecutive appearances, or longest run inside a tier. All
derivable from `resultsByGame` in `StatisticsComputer` with little extra work.

### Leaderboard by tier — Open
A separate ranking per tier — best win rate in tier 1, tier 2, and so on — is
more meaningful to players who mostly stay in one tier. `PlayerStats` already
carries `winsByTier` and `gamesByTier`, so this is presentation.

### Attendance forecast — Open
Flagging players as active or inactive from participation history would keep the
published ranking cleaner than the current one-year filter, which is coarse. The
ranking table now shows a last-played date, which makes the drop-offs visible but
still leaves the filtering to the reader.

---

## Known quirks

Both are documented in the README and pinned by tests; neither has been changed,
because both alter scoring behaviour and that is a decision rather than a cleanup.

### The attendance gate is off by one
`scoring.minimum.players` defaults to 8, but `GameDaoGSheet` and `ResultDaoGSheet`
test `count > minimumNbPlayers`, so a game needs **9 or more** players to load at
all. `HtmlWriter` separately tests `>=`. Since games of 8 never load, that branch
is unreachable and the effective threshold everywhere is 9. Reconciling these to a
single comparison would change which games count, so it needs a deliberate call on
what the threshold should actually be.

### A tiny field collapses the score floor to zero
When a field is smaller than `scoring.quartile.divisor` the quartile sample is
empty and both ends of the range start at zero. The ceiling is then clamped up to
the best player, but the floor is only ever clamped *downward*, so it stays at
zero rather than rising to the weakest player. Unreachable behind the attendance
gate; pinned by `ScoringEngineTest.tinyFieldCollapsesFloor` so that lowering the
gate cannot reintroduce it silently.
