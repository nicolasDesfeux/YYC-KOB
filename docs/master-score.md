# Master Score Calculation

The master score is a single number representing a player's overall skill level. It starts at **50** for every player and evolves over time as they play games.

---

## Phase 1 — Game Score (per game)

Before computing a player's master score, each game is assigned a **score range** based on the master scores of the players who participated.

### Score Range

1. **Top anchor (`highestPoint`)**: average master score of the top 25% of players in the game, plus a margin of 10. Capped at the highest individual master score if the average+margin is lower.
2. **Bottom anchor (`lowestPoint`)**: average master score of the bottom 25% of players in the game, minus a margin of 10. Capped at the lowest individual master score if the average−margin is higher.

This ensures the score range is always anchored to the actual skill level of that session's field.

### Individual Game Score

Each player receives a game score via linear interpolation between the two anchors:

```
gameScore = highestPoint − (finishingPosition − 1) × (highestPoint − lowestPoint) / (numPlayers − 1)
```

- 1st place receives `highestPoint`
- Last place receives `lowestPoint`
- All other positions are evenly distributed between the two

> **Tied positions**: when two players tie (e.g. both finish 2nd), they each receive a position of 2.5, and the interpolation formula handles the fractional rank naturally.

---

## Phase 2 — Master Score (rolling weighted average)

A player's master score is computed from their game scores using a **three-bucket windowing** system. The window is measured in number of sessions (games), not calendar time.

| Bucket | Which games | How it counts |
|--------|-------------|---------------|
| Recent | Last 8 sessions overall | Each game score counted individually |
| Mid    | Sessions 9–16 ago | Averaged into a single value |
| Old    | Sessions 17+ ago | Averaged into a single value |

The master score is the average of: all individual recent scores + the mid average (if any) + the old average (if any).

> **"Sessions overall"** means the 8 most recent sessions organised, regardless of whether the player participated. A player who played in only 3 of the last 8 sessions will have at most 3 recent game scores.

### 1-Year Limit

Results older than **1 year relative to the game being computed** are excluded entirely from the master score calculation. If a player has no valid results within the window, their master score resets to **50**.

### Starting Score

- New players start at **50**.
- If all of a player's results fall outside the 1-year window, their score resets to **50**.

---

## Constants

| Constant | Value | Meaning |
|----------|-------|---------|
| `INITIAL_SCORE` | 50 | Default master score |
| `SCORE_RANGE_MARGIN` | 10 | Points added/subtracted to top/bottom anchor |
| `RECENT_WINDOW_SIZE` | 8 | Number of sessions defining the "recent" bucket |
| `QUARTILE_DIVISOR` | 4 | Divisor for top/bottom 25% (numPlayers / 4) |
| `MINIMUM_NB_PLAYERS` | 8 | Minimum players for a game to count |
| `LIMIT_TO_A_YEAR` | true | Whether to enforce the 1-year cutoff |

---

## Summary

```
masterScore = average of:
  - game scores from the last 8 sessions individually   (recent)
  - average of game scores from sessions 9–16 ago       (mid, if any)
  - average of game scores from sessions 17+ ago        (old, if any)

  filtered to results within the last calendar year
  reset to 50 if no valid results remain
```
