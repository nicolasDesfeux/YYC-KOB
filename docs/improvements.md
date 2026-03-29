# Potential Improvements

---

## Scoring Model

### Decay instead of hard cutoff
The 1-year cutoff drops results abruptly. A time-decay weight (e.g. exponential decay) would create a smoother transition where older results gradually matter less rather than disappearing entirely.

### Configurable window sizes
`RECENT_WINDOW_SIZE`, `SCORE_RANGE_MARGIN`, `MINIMUM_NB_PLAYERS`, and `LIMIT_TO_A_YEAR` are hard-coded constants. Moving them to `config.properties` would allow tuning without a recompile.

### Score floor
Master scores can go negative with no lower bound. A configurable floor (e.g. 0) would prevent scores from becoming meaningless outliers that distort the score range for future games.

---

## Data Quality

### Tied results are entered manually
When two players tie, their shared position (e.g. 2.5) must be manually entered into the sheet. An input validation step or a dedicated tie-entry UI would reduce data entry errors.

### No validation on sheet input
Malformed rows (wrong data type, duplicate player column, out-of-range position) are silently skipped or cause runtime warnings. A pre-run validation pass that reports all sheet issues at once would be safer.

### Player name normalisation
Player lookup is case-sensitive and whitespace-sensitive (`equals`). A normalisation step (trim + lowercase) at load time would make the system resilient to minor inconsistencies in the sheet header.

---

## Architecture

### Persist computed scores
Every run replays the full game history from scratch (O(N) games × players). Storing the computed master score after each session and only processing new games incrementally would dramatically reduce runtime as the dataset grows.

### Decouple computation from Google Sheets
`GSheetConnector` mixes sheet I/O with formatting logic. Separating "write data" from "apply formatting" and introducing a thin domain-agnostic writer interface would make it easier to add other output targets (CSV export, a web dashboard, etc.).

### Unit tests
There are no automated tests. The scoring formula, tier calculation, windowing logic, and statistics computation are all candidates for unit tests that would catch regressions quickly.

### Configuration class
Constants scattered across `KOB` are used throughout the codebase. A dedicated `Config` class loaded once at startup would centralise all tuneable parameters and make dependency injection cleaner.

---

## Features

### Historical ranking view
Currently only the final ranking is written to the sheet. Writing a snapshot of the ranking after each session would enable historical trend analysis directly in the spreadsheet.

### Player profile page
A per-player sheet tab showing their score evolution over time, tier history, and win rate trend would give players meaningful personal feedback.

### Streak tracking
Tracking consecutive wins, consecutive appearances, or longest winning streak within a tier would add interesting statistics with relatively little extra computation.

### Leaderboard by tier
A separate ranking per tier (who has the best win rate in tier 1? tier 2?) would be more meaningful to players who mostly play in the same tier.

### Attendance forecast
Using participation rate history to flag players as "active" vs "inactive" would keep the published ranking clean by filtering out players who haven't played recently (currently the 1-year filter on the ranking does this coarsely).
