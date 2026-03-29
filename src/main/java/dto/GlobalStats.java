package dto;

import java.util.Map;

public class GlobalStats {

    public final int maxTier;

    public final Player mostGamesPlayed;
    public final int mostGamesPlayedCount;
    public final Map<Integer, Player> mostGamesPerTier;
    public final Map<Integer, Integer> mostGamesPerTierCount;

    public final Player mostWins;
    public final int mostWinsCount;
    public final double mostWinsRate;                        // wins / gamesPlayed for that player
    public final Map<Integer, Player> mostWinsPerTier;
    public final Map<Integer, Integer> mostWinsPerTierCount;
    public final Map<Integer, Double> mostWinsPerTierRate;   // wins-in-tier / games-in-tier for that player

    public final Player mostMovedUp;
    public final int mostMovedUpCount;
    public final double mostMovedUpRate;                     // movesUp / gamesPlayed for that player

    public final Player mostMovedDown;
    public final int mostMovedDownCount;
    public final double mostMovedDownRate;                   // movesDown / gamesPlayed for that player

    public final Player mostConsistent;
    public final int mostConsistentCount;
    public final double mostConsistentRate;                  // stays / gamesPlayed for that player

    public GlobalStats(int maxTier,
                       Player mostGamesPlayed, int mostGamesPlayedCount,
                       Map<Integer, Player> mostGamesPerTier, Map<Integer, Integer> mostGamesPerTierCount,
                       Player mostWins, int mostWinsCount, double mostWinsRate,
                       Map<Integer, Player> mostWinsPerTier, Map<Integer, Integer> mostWinsPerTierCount, Map<Integer, Double> mostWinsPerTierRate,
                       Player mostMovedUp, int mostMovedUpCount, double mostMovedUpRate,
                       Player mostMovedDown, int mostMovedDownCount, double mostMovedDownRate,
                       Player mostConsistent, int mostConsistentCount, double mostConsistentRate) {
        this.maxTier = maxTier;
        this.mostGamesPlayed = mostGamesPlayed;
        this.mostGamesPlayedCount = mostGamesPlayedCount;
        this.mostGamesPerTier = mostGamesPerTier;
        this.mostGamesPerTierCount = mostGamesPerTierCount;
        this.mostWins = mostWins;
        this.mostWinsCount = mostWinsCount;
        this.mostWinsRate = mostWinsRate;
        this.mostWinsPerTier = mostWinsPerTier;
        this.mostWinsPerTierCount = mostWinsPerTierCount;
        this.mostWinsPerTierRate = mostWinsPerTierRate;
        this.mostMovedUp = mostMovedUp;
        this.mostMovedUpCount = mostMovedUpCount;
        this.mostMovedUpRate = mostMovedUpRate;
        this.mostMovedDown = mostMovedDown;
        this.mostMovedDownCount = mostMovedDownCount;
        this.mostMovedDownRate = mostMovedDownRate;
        this.mostConsistent = mostConsistent;
        this.mostConsistentCount = mostConsistentCount;
        this.mostConsistentRate = mostConsistentRate;
    }
}
