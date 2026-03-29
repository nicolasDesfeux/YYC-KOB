# Statistics Calculations

Statistics are computed in `StatisticsComputer` after the master score forward pass, so that each result carries the player's pre-game master score as a snapshot.

---

## Tier System

Every game is divided into tiers of 4 players each.

```
numTiers     = max(1, numPlayers / 4)       — last tier absorbs the remainder
```

Two kinds of tier are tracked per result:

| Tier | Based on | Used for |
|------|----------|----------|
| **Starting tier** | Master score rank before the game (top 4 → tier 1, next 4 → tier 2, …) | Move up/down, teammate tracking |
| **Finishing tier** | Finishing position (positions 1–4 → tier 1, 5–8 → tier 2, …) | Games per tier, wins per tier |

A player **wins** a tier when their finishing position is first within that tier:
```
won = (finishingPosition − 1) % 4 == 0   →   positions 1, 5, 9, 13, …
```

---

## Per-Player Statistics

| Stat | Calculation |
|------|-------------|
| **Games Played** | Total games participated in |
| **Participation %** | Games played / (total sessions since that player's first game) |
| **Wins** | Total tier wins across all games |
| **Win %** | Wins / games played |
| **T[n] Games** | Games where the player finished in tier n |
| **T[n] Games %** | T[n] games / total games played |
| **T[n] Wins** | Wins achieved while finishing in tier n |
| **T[n] Win %** | T[n] wins / T[n] games |
| **Moved Up** | Times the finishing tier was better (lower number) than the starting tier |
| **Move Up %** | Moved up / games played |
| **Moved Down** | Times the finishing tier was worse (higher number) than the starting tier |
| **Move Down %** | Moved down / games played |
| **Top Tier** | The tier in which the player has most frequently finished |
| **Best Teammate** | Player most often in the same starting tier when the player won or moved up |
| **Worst Teammate** | Player most often in the same starting tier when the player moved down |

---

## Global Statistics

Global stats highlight the single leading player for each metric.

| Stat | Description |
|------|-------------|
| **Most games played** | Player with the highest total game count |
| **Most games in Tier n** | Player who has finished in tier n the most |
| **Most wins** | Player with the most tier wins overall |
| **Most wins in Tier n** | Player with the most wins in tier n (with win rate) |
| **Most moved up** | Player who most often finished in a better tier than they started |
| **Most moved down** | Player who most often finished in a worse tier than they started |
| **Most consistent** | Player who most often stayed in the same tier they started in |

---

## Notes

- Statistics cover **all games** in the dataset, not just the last year (unlike the master score).
- A game must have more than **8 players** (`MINIMUM_NB_PLAYERS`) to be counted.
- Players with no pre-game master score snapshot (e.g. the very first game ever) fall back to using their finishing tier as their starting tier — they are counted as "stayed" for that game.
