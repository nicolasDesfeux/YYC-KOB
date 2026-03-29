package dao;

import dto.Game;
import dto.GlobalStats;
import dto.Player;
import dto.PlayerStats;
import dto.Result;
import kob.KOB;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a self-contained HTML dashboard (no external dependencies)
 * with three tabs: Ranking, Statistics, and Games.
 */
public class HtmlWriter {

    public void write(String outputPath, List<Player> ranking,
                      GlobalStats global, List<PlayerStats> playerStats,
                      List<Game> allGames, Map<Game, List<Result>> resultsByGame) throws IOException {
        try (PrintWriter out = new PrintWriter(outputPath, "UTF-8")) {
            out.print(buildHtml(ranking, global, playerStats, allGames, resultsByGame));
        }
    }

    private String buildHtml(List<Player> ranking, GlobalStats global, List<PlayerStats> playerStats,
                              List<Game> allGames, Map<Game, List<Result>> resultsByGame) {
        String ts = LocalDateTime.now(ZoneId.of("Canada/Mountain"))
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>KOB Dashboard</title>\n");
        sb.append(css());
        sb.append("</head><body>\n");

        sb.append("<header>");
        sb.append("<div class=\"header-inner\">");
        sb.append("<span class=\"logo\">&#127183; KOB</span>");
        sb.append("<span class=\"ts\">Updated ").append(ts).append("</span>");
        sb.append("</div>");
        sb.append("<nav class=\"tabs\">");
        sb.append("<button class=\"tab active\" onclick=\"showTab('ranking',this)\">&#127942; Ranking</button>");
        sb.append("<button class=\"tab\" onclick=\"showTab('statistics',this)\">&#128202; Statistics</button>");
        sb.append("<button class=\"tab\" onclick=\"showTab('games',this)\">&#127917; Games</button>");
        sb.append("</nav>");
        sb.append("</header>\n");

        sb.append("<main>\n");
        sb.append(rankingTab(ranking));
        sb.append(statisticsTab(global, playerStats));
        sb.append(gamesTab(allGames, resultsByGame));
        sb.append("</main>\n");

        sb.append(js(playerStats, global.maxTier, allGames, resultsByGame));
        sb.append("</body></html>");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Ranking tab
    // -------------------------------------------------------------------------

    private String rankingTab(List<Player> ranking) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"tab-ranking\" class=\"tab-content\">\n");

        if (ranking.size() >= 3) {
            sb.append("<div class=\"podium\">\n");
            podiumCard(sb, ranking, 1, "silver", "2nd", "&#129208;");
            podiumCard(sb, ranking, 0, "gold",   "1st", "&#127942;");
            podiumCard(sb, ranking, 2, "bronze", "3rd", "&#129209;");
            sb.append("</div>\n");
        }

        sb.append("<div class=\"table-wrap\">\n");
        sb.append("<input class=\"search-box\" type=\"text\" placeholder=\"Search player...\" oninput=\"filterTable('rankingTable',this.value)\">\n");
        sb.append("<table id=\"rankingTable\" class=\"data-table sortable\">\n");
        sb.append("<thead><tr>");
        sb.append("<th onclick=\"sortTable('rankingTable',0,'num')\">#</th>");
        sb.append("<th onclick=\"sortTable('rankingTable',1,'str')\">Player</th>");
        sb.append("<th onclick=\"sortTable('rankingTable',2,'num')\">Master Score</th>");
        sb.append("</tr></thead><tbody>\n");

        for (int i = 0; i < ranking.size(); i++) {
            Player p = ranking.get(i);
            String cls = i == 0 ? " class=\"rank-gold\"" : i == 1 ? " class=\"rank-silver\"" : i == 2 ? " class=\"rank-bronze\"" : "";
            sb.append("<tr").append(cls).append(">");
            sb.append("<td>").append(i + 1).append("</td>");
            sb.append("<td>").append(esc(p.getName())).append("</td>");
            sb.append("<td>").append(String.format("%.2f", p.getMasterScore().setScale(2, RoundingMode.HALF_UP).doubleValue())).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table></div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private void podiumCard(StringBuilder sb, List<Player> ranking, int idx, String cls, String label, String medal) {
        Player p = ranking.get(idx);
        sb.append("<div class=\"podium-card ").append(cls).append("\">");
        sb.append("<div class=\"medal\">").append(medal).append("</div>");
        sb.append("<div class=\"podium-rank\">").append(label).append("</div>");
        sb.append("<div class=\"podium-name\">").append(esc(p.getName())).append("</div>");
        sb.append("<div class=\"podium-score\">")
          .append(String.format("%.2f", p.getMasterScore().setScale(2, RoundingMode.HALF_UP).doubleValue()))
          .append("</div>");
        sb.append("</div>\n");
    }

    // -------------------------------------------------------------------------
    // Statistics tab
    // -------------------------------------------------------------------------

    private String statisticsTab(GlobalStats g, List<PlayerStats> players) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"tab-statistics\" class=\"tab-content\" style=\"display:none\">\n");

        sb.append("<section class=\"section\">\n");
        sb.append("<h2 class=\"section-title\">Global Highlights</h2>\n");
        sb.append("<div class=\"cards\">\n");
        globalCard(sb, "&#127942; Most Wins",        g.mostWins,       g.mostWinsCount + " wins",   pct(g.mostWinsRate));
        globalCard(sb, "&#127919; Most Games",        g.mostGamesPlayed, g.mostGamesPlayedCount + " games", null);
        globalCard(sb, "&#11014; Most Moved Up",      g.mostMovedUp,    g.mostMovedUpCount + " times",   pct(g.mostMovedUpRate));
        globalCard(sb, "&#11015; Most Moved Down",    g.mostMovedDown,  g.mostMovedDownCount + " times",  pct(g.mostMovedDownRate));
        globalCard(sb, "&#9654; Most Consistent",     g.mostConsistent, g.mostConsistentCount + " times", pct(g.mostConsistentRate));
        for (int t = 1; t <= g.maxTier; t++) {
            Player p = g.mostWinsPerTier.get(t);
            int cnt  = g.mostWinsPerTierCount.getOrDefault(t, 0);
            double r = g.mostWinsPerTierRate.getOrDefault(t, 0.0);
            globalCard(sb, "&#127941; T" + t + " Most Wins", p, cnt + " wins", pct(r));
        }
        sb.append("</div>\n</section>\n");

        sb.append("<section class=\"section\">\n");
        sb.append("<h2 class=\"section-title\">Individual Statistics</h2>\n");

        sb.append("<div class=\"player-selector\">\n");
        sb.append("<label for=\"playerPicker\">View player: </label>\n");
        sb.append("<select id=\"playerPicker\" onchange=\"onPlayerPick()\">\n");
        sb.append("<option value=\"\">— All players —</option>\n");
        for (PlayerStats ps : players)
            sb.append("<option value=\"").append(esc(ps.player.getName())).append("\">")
              .append(esc(ps.player.getName())).append("</option>\n");
        sb.append("</select>\n</div>\n");

        sb.append("<div id=\"playerCard\" class=\"player-card\" style=\"display:none\"></div>\n");

        sb.append("<div class=\"table-wrap\">\n");
        sb.append("<input class=\"search-box\" type=\"text\" placeholder=\"Search player...\" oninput=\"filterTable('statsTable',this.value)\">\n");
        sb.append("<table id=\"statsTable\" class=\"data-table sortable\">\n");
        sb.append("<thead><tr>\n");
        int col = 0;
        statsHeader(sb, col++, "str",  "Player");
        statsHeader(sb, col++, "num",  "Games");
        statsHeader(sb, col++, "num",  "Participation");
        statsHeader(sb, col++, "num",  "Wins");
        statsHeader(sb, col++, "num",  "Win %");
        for (int t = 1; t <= g.maxTier; t++) {
            statsHeader(sb, col++, "num", "T" + t + " Games");
            statsHeader(sb, col++, "num", "T" + t + " Games %");
            statsHeader(sb, col++, "num", "T" + t + " Wins");
            statsHeader(sb, col++, "num", "T" + t + " Win %");
        }
        statsHeader(sb, col++, "num", "Moved Up");
        statsHeader(sb, col++, "num", "Move Up %");
        statsHeader(sb, col++, "num", "Moved Down");
        statsHeader(sb, col++, "num", "Move Down %");
        statsHeader(sb, col++, "str", "Home Tier");
        statsHeader(sb, col++, "str", "Best Teammate");
        statsHeader(sb, col,   "str", "Worst Teammate");
        sb.append("</tr></thead><tbody>\n");

        for (PlayerStats ps : players) {
            sb.append("<tr data-player=\"").append(esc(ps.player.getName())).append("\">\n");
            sb.append("<td>").append(esc(ps.player.getName())).append("</td>");
            sb.append("<td>").append(ps.gamesPlayed).append("</td>");
            sb.append("<td>").append(pct(ps.participationRate)).append("</td>");
            sb.append("<td>").append(ps.totalWins).append("</td>");
            sb.append("<td>").append(pct(ps.gamesPlayed > 0 ? (double) ps.totalWins / ps.gamesPlayed : 0)).append("</td>");
            for (int t = 1; t <= g.maxTier; t++) {
                int gt = ps.gamesByTier.getOrDefault(t, 0);
                int wt = ps.winsByTier.getOrDefault(t, 0);
                sb.append("<td>").append(gt).append("</td>");
                sb.append("<td>").append(pct(ps.gamesPlayed > 0 ? (double) gt / ps.gamesPlayed : 0)).append("</td>");
                sb.append("<td>").append(wt).append("</td>");
                sb.append("<td>").append(pct(gt > 0 ? (double) wt / gt : 0)).append("</td>");
            }
            sb.append("<td>").append(ps.movesUp).append("</td>");
            sb.append("<td>").append(pct(ps.gamesPlayed > 0 ? (double) ps.movesUp / ps.gamesPlayed : 0)).append("</td>");
            sb.append("<td>").append(ps.movesDown).append("</td>");
            sb.append("<td>").append(pct(ps.gamesPlayed > 0 ? (double) ps.movesDown / ps.gamesPlayed : 0)).append("</td>");
            sb.append("<td>Tier ").append(ps.mostPlayedTier).append("</td>");
            sb.append("<td>").append(ps.bestTeammate  != null ? esc(ps.bestTeammate.getName())  : "—").append("</td>");
            sb.append("<td>").append(ps.worstTeammate != null ? esc(ps.worstTeammate.getName()) : "—").append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table></div>\n</section>\n</div>\n");
        return sb.toString();
    }

    private void globalCard(StringBuilder sb, String title, Player player, String count, String rate) {
        String name = player != null ? esc(player.getName()) : "—";
        sb.append("<div class=\"stat-card\">");
        sb.append("<div class=\"stat-title\">").append(title).append("</div>");
        sb.append("<div class=\"stat-name\">").append(name).append("</div>");
        sb.append("<div class=\"stat-count\">").append(count).append("</div>");
        if (rate != null) sb.append("<div class=\"stat-rate\">").append(rate).append("</div>");
        sb.append("</div>\n");
    }

    private void statsHeader(StringBuilder sb, int col, String type, String label) {
        sb.append("<th onclick=\"sortTable('statsTable',").append(col).append(",'").append(type).append("')\">")
          .append(label).append("</th>");
    }

    // -------------------------------------------------------------------------
    // Games tab
    // -------------------------------------------------------------------------

    private String gamesTab(List<Game> allGames, Map<Game, List<Result>> resultsByGame) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"tab-games\" class=\"tab-content\" style=\"display:none\">\n");
        sb.append("<section class=\"section\">\n");
        sb.append("<h2 class=\"section-title\">Game History</h2>\n");

        sb.append("<div class=\"games-filters\">\n");
        sb.append("<input class=\"search-box\" type=\"text\" placeholder=\"Search player...\" id=\"gamePlayerFilter\" oninput=\"filterGames()\">\n");
        sb.append("</div>\n");

        sb.append("<div id=\"gamesList\">\n");

        // Most recent first
        List<Game> sorted = allGames.stream()
                .filter(g -> resultsByGame.containsKey(g) && !resultsByGame.get(g).isEmpty())
                .sorted(Comparator.comparing(Game::getId).reversed())
                .collect(Collectors.toList());

        java.time.LocalDate lastGameDate = allGames.stream()
                .max(Comparator.comparing(Game::getId))
                .map(Game::getDate).orElse(null);

        for (Game game : sorted) {
            List<Result> results = resultsByGame.get(game);
            int numPlayers = results.size();
            int numTiers = Math.max(1, numPlayers / 4);

            boolean enoughPlayers = numPlayers >= KOB.MINIMUM_NB_PLAYERS;
            boolean withinYear = !KOB.LIMIT_TO_A_YEAR || lastGameDate == null
                    || game.getDate().isAfter(lastGameDate.minusYears(1));
            boolean counted = enoughPlayers && withinYear;
            String notCountedReason = !enoughPlayers ? "< " + KOB.MINIMUM_NB_PLAYERS + " players"
                    : !withinYear ? "&gt; 1 year old" : "";

            // Compute starting tiers from pre-game master score rank
            List<Result> byScore = results.stream()
                    .filter(r -> r.getPlayerMasterScoreBeforeGame() != null)
                    .sorted((a, b) -> b.getPlayerMasterScoreBeforeGame().compareTo(a.getPlayerMasterScoreBeforeGame()))
                    .collect(Collectors.toList());
            Map<String, Integer> startingTiers = new HashMap<>();
            for (int i = 0; i < byScore.size(); i++)
                startingTiers.put(byScore.get(i).getPlayer().getName(), Math.min(i / 4 + 1, numTiers));

            // Find winners and build player name list for filtering
            List<String> winnerNames = results.stream()
                    .filter(r -> ((int) r.getResult() - 1) % 4 == 0)
                    .map(r -> r.getPlayer().getName())
                    .sorted()
                    .collect(Collectors.toList());
            List<String> allNames = results.stream()
                    .map(r -> r.getPlayer().getName())
                    .collect(Collectors.toList());

            String winnersStr = winnerNames.stream().map(HtmlWriter::esc).collect(Collectors.joining(", "));
            String allNamesAttr = String.join("|", allNames).toLowerCase();

            sb.append("<div class=\"game-card\" data-players=\"").append(esc(allNamesAttr)).append("\">\n");
            sb.append("<div class=\"game-header\" onclick=\"toggleGame(this)\">\n");
            sb.append("<div class=\"game-meta\">");
            sb.append("<span class=\"game-id\">Game #").append(game.getId()).append("</span>");
            sb.append("<span class=\"game-date\">").append(game.getDate()).append("</span>");
            sb.append("<span class=\"game-players\">").append(numPlayers).append(" players &middot; ")
              .append(numTiers).append(" tier").append(numTiers > 1 ? "s" : "").append("</span>");
            if (counted)
                sb.append("<span class=\"scoring-yes\">&#10003; counted</span>");
            else
                sb.append("<span class=\"scoring-no\">&#10007; not counted (").append(notCountedReason).append(")</span>");
            sb.append("</div>");
            sb.append("<div class=\"game-winners\">&#127942; ").append(winnersStr.isEmpty() ? "—" : winnersStr).append("</div>");
            sb.append("<span class=\"game-chevron\">&#9660;</span>");
            sb.append("</div>\n"); // game-header

            sb.append("<div class=\"game-detail\" style=\"display:none\">\n");
            // Group by finishing tier
            for (int t = 1; t <= numTiers; t++) {
                final int tier = t;
                List<Result> tierResults = results.stream()
                        .filter(r -> Math.min((int)(r.getResult() - 1) / 4 + 1, numTiers) == tier)
                        .sorted(Comparator.comparingDouble(Result::getResult))
                        .collect(Collectors.toList());

                sb.append("<div class=\"tier-row\">\n");
                sb.append("<div class=\"tier-label\">Tier ").append(t).append("</div>\n");
                sb.append("<div class=\"tier-players\">\n");

                for (Result r : tierResults) {
                    String name = r.getPlayer().getName();
                    int finishTier = Math.min((int)(r.getResult() - 1) / 4 + 1, numTiers);
                    int startTier  = startingTiers.getOrDefault(name, finishTier);
                    boolean won    = ((int) r.getResult() - 1) % 4 == 0;
                    int delta      = finishTier - startTier; // negative = up, positive = down

                    String badgeCls = r.isDebutGame() ? "badge-debut"
                            : delta < 0 ? "badge-up" : delta > 0 ? "badge-down" : "badge-stay";
                    String winIcon  = won ? "<span class=\"win-icon\">&#127942;</span>" : "";
                    String posStr   = r.getResult() == (int) r.getResult()
                            ? String.valueOf((int) r.getResult())
                            : String.format("%.1f", r.getResult());

                    // Score delta
                    String deltaStr = null;
                    String deltaCls = "";
                    if (counted && r.getPlayerMasterScoreAfterGame() != null && r.getPlayerMasterScoreBeforeGame() != null) {
                        double d = r.getPlayerMasterScoreAfterGame().doubleValue()
                                 - r.getPlayerMasterScoreBeforeGame().doubleValue();
                        deltaStr = (d >= 0 ? "+" : "") + String.format("%.1f", d);
                        deltaCls = d > 0.05 ? " delta-pos" : d < -0.05 ? " delta-neg" : " delta-neu";
                    }

                    sb.append("<div class=\"player-badge ").append(badgeCls).append("\">");
                    sb.append(winIcon);
                    sb.append("<span class=\"badge-name\">").append(esc(name)).append("</span>");
                    sb.append("<span class=\"badge-pos\">#").append(posStr).append("</span>");
                    if (r.isDebutGame())
                        sb.append("<span class=\"badge-move\">NEW</span>");
                    else if (delta != 0)
                        sb.append("<span class=\"badge-move\">")
                          .append(delta < 0 ? "&#11014;" : "&#11015;")
                          .append(" T").append(startTier).append("→T").append(finishTier).append("</span>");
                    if (deltaStr != null)
                        sb.append("<span class=\"badge-delta").append(deltaCls).append("\">").append(deltaStr).append("</span>");
                    sb.append("</div>\n");
                }

                sb.append("</div>\n</div>\n"); // tier-players, tier-row
            }
            sb.append("</div>\n"); // game-detail
            sb.append("</div>\n"); // game-card
        }

        sb.append("</div>\n</section>\n</div>\n"); // gamesList, section, tab
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // CSS
    // -------------------------------------------------------------------------

    private String css() {
        return "<style>\n" +
            ":root{--navy:#1E4470;--blue:#4472C4;--light:#E0EBFA;--gold:#c9a227;--silver:#9e9e9e;--bronze:#a0522d;" +
            "--bg:#f4f6fb;--card:#fff;--text:#1a1a2e;--muted:#6b7280;--radius:10px;--shadow:0 2px 12px rgba(0,0,0,.10);" +
            "--up:#16a34a;--down:#dc2626;--stay:#6b7280}" +
            "*{box-sizing:border-box;margin:0;padding:0}" +
            "body{font-family:'Segoe UI',system-ui,sans-serif;background:var(--bg);color:var(--text);font-size:14px}" +
            "header{background:var(--navy);color:#fff;position:sticky;top:0;z-index:100;box-shadow:0 2px 8px rgba(0,0,0,.3)}" +
            ".header-inner{display:flex;justify-content:space-between;align-items:center;padding:12px 24px}" +
            ".logo{font-size:1.3em;font-weight:700;letter-spacing:.05em}" +
            ".ts{font-size:.8em;opacity:.7}" +
            ".tabs{display:flex;gap:4px;padding:0 20px}" +
            ".tab{background:transparent;color:rgba(255,255,255,.7);border:none;border-bottom:3px solid transparent;" +
            "padding:10px 20px;cursor:pointer;font-size:.95em;font-weight:600;transition:all .2s}" +
            ".tab:hover{color:#fff;background:rgba(255,255,255,.1)}" +
            ".tab.active{color:#fff;border-bottom-color:#fff}" +
            "main{max-width:1400px;margin:0 auto;padding:24px}" +
            ".section{margin-bottom:32px}" +
            ".section-title{font-size:1.15em;font-weight:700;color:var(--navy);margin-bottom:14px;" +
            "padding-bottom:6px;border-bottom:2px solid var(--light)}" +
            // Podium
            ".podium{display:flex;justify-content:center;align-items:flex-end;gap:16px;margin-bottom:32px;padding:24px 0}" +
            ".podium-card{text-align:center;border-radius:var(--radius);padding:20px 28px;min-width:160px;" +
            "box-shadow:var(--shadow);transition:transform .2s}" +
            ".podium-card:hover{transform:translateY(-4px)}" +
            ".gold{background:linear-gradient(135deg,#f9e07a,#c9a227);order:2;padding-bottom:40px}" +
            ".silver{background:linear-gradient(135deg,#e8e8e8,#9e9e9e);order:1;padding-bottom:24px}" +
            ".bronze{background:linear-gradient(135deg,#e8c4a0,#a0522d);order:3;padding-bottom:16px}" +
            ".medal{font-size:2.2em;margin-bottom:6px}" +
            ".podium-rank{font-size:.8em;font-weight:700;opacity:.7;text-transform:uppercase;letter-spacing:.08em}" +
            ".podium-name{font-size:1em;font-weight:700;margin:6px 0}" +
            ".podium-score{font-size:1.4em;font-weight:800}" +
            // Stat cards
            ".cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px}" +
            ".stat-card{background:var(--card);border-radius:var(--radius);padding:16px;box-shadow:var(--shadow);" +
            "border-left:4px solid var(--blue)}" +
            ".stat-title{font-size:.75em;font-weight:700;color:var(--blue);text-transform:uppercase;letter-spacing:.07em;margin-bottom:8px}" +
            ".stat-name{font-size:1em;font-weight:700;margin-bottom:4px}" +
            ".stat-count{font-size:.9em;color:var(--muted)}" +
            ".stat-rate{font-size:.85em;color:var(--navy);font-weight:600;margin-top:2px}" +
            // Player selector / card
            ".player-selector{display:flex;align-items:center;gap:10px;margin-bottom:14px}" +
            ".player-selector label{font-weight:600;color:var(--navy)}" +
            ".player-selector select{padding:7px 12px;border:1.5px solid var(--blue);border-radius:6px;" +
            "font-size:.95em;background:#fff;cursor:pointer;min-width:220px}" +
            ".player-card{background:var(--card);border-radius:var(--radius);padding:20px 24px;margin-bottom:18px;" +
            "box-shadow:var(--shadow);border-top:4px solid var(--blue)}" +
            ".pc-name{font-size:1.3em;font-weight:800;color:var(--navy);margin-bottom:14px}" +
            ".pc-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}" +
            ".pc-item{background:var(--bg);border-radius:8px;padding:12px 14px}" +
            ".pc-label{font-size:.72em;font-weight:700;color:var(--muted);text-transform:uppercase;letter-spacing:.07em;margin-bottom:4px}" +
            ".pc-value{font-size:1.1em;font-weight:700;color:var(--text)}" +
            ".pc-sub{font-size:.8em;color:var(--muted);margin-top:2px}" +
            // Tables
            ".table-wrap{overflow-x:auto;border-radius:var(--radius);box-shadow:var(--shadow)}" +
            ".search-box{width:260px;padding:8px 12px;border:1.5px solid #ddd;border-radius:6px;font-size:.9em;margin-bottom:10px}" +
            ".data-table{width:100%;border-collapse:collapse;background:var(--card)}" +
            ".data-table thead tr{background:var(--navy);color:#fff}" +
            ".data-table th{padding:10px 12px;text-align:left;font-size:.8em;font-weight:600;cursor:pointer;" +
            "user-select:none;white-space:nowrap}" +
            ".data-table th:hover{background:var(--blue)}" +
            ".data-table td{padding:8px 12px;border-bottom:1px solid #eef0f5;white-space:nowrap}" +
            ".data-table tbody tr:hover{background:var(--light)}" +
            ".data-table tbody tr:nth-child(even){background:#f9fafc}" +
            ".data-table tbody tr:nth-child(even):hover{background:var(--light)}" +
            ".rank-gold td:first-child{font-weight:800;color:#8a6800}" +
            ".rank-silver td:first-child{font-weight:700;color:#555}" +
            ".rank-bronze td:first-child{font-weight:700;color:#7a3e1a}" +
            ".highlight{background:var(--light)!important;outline:2px solid var(--blue)}" +
            "th.sort-asc::after{content:' ▲';font-size:.7em}" +
            "th.sort-desc::after{content:' ▼';font-size:.7em}" +
            // Games tab
            ".games-filters{margin-bottom:14px}" +
            ".game-card{background:var(--card);border-radius:var(--radius);margin-bottom:10px;box-shadow:var(--shadow);overflow:hidden}" +
            ".game-card.hidden{display:none}" +
            ".game-header{display:flex;align-items:center;justify-content:space-between;padding:14px 18px;" +
            "cursor:pointer;user-select:none;transition:background .15s}" +
            ".game-header:hover{background:var(--light)}" +
            ".game-meta{display:flex;align-items:center;gap:16px}" +
            ".game-id{font-weight:800;color:var(--navy);font-size:1em;min-width:80px}" +
            ".game-date{color:var(--muted);font-size:.9em}" +
            ".game-players{color:var(--muted);font-size:.85em}" +
            ".game-winners{font-size:.9em;font-weight:600;color:var(--gold);flex:1;text-align:center}" +
            ".game-chevron{font-size:.8em;color:var(--muted);transition:transform .2s}" +
            ".game-chevron.open{transform:rotate(180deg)}" +
            ".game-detail{padding:0 18px 18px}" +
            ".tier-row{display:flex;align-items:flex-start;gap:12px;margin-top:12px}" +
            ".tier-label{font-size:.75em;font-weight:800;color:var(--navy);background:var(--light);" +
            "border-radius:6px;padding:4px 10px;white-space:nowrap;margin-top:4px;min-width:52px;text-align:center}" +
            ".tier-players{display:flex;flex-wrap:wrap;gap:8px}" +
            ".player-badge{display:flex;align-items:center;gap:5px;border-radius:20px;padding:5px 12px;font-size:.85em;font-weight:600}" +
            ".badge-up{background:#dcfce7;color:var(--up);border:1px solid #86efac}" +
            ".badge-down{background:#fee2e2;color:var(--down);border:1px solid #fca5a5}" +
            ".badge-stay{background:#f3f4f6;color:var(--stay);border:1px solid #d1d5db}" +
            ".badge-debut{background:#faf5ff;color:#7c3aed;border:1px solid #c4b5fd}" +
            ".badge-delta{font-size:.75em;font-weight:700;margin-left:2px;opacity:.9}" +
            ".delta-pos{color:var(--up)}.delta-neg{color:var(--down)}.delta-neu{color:var(--stay)}" +
            ".scoring-yes{font-size:.75em;font-weight:700;color:#16a34a;background:#dcfce7;" +
            "border:1px solid #86efac;border-radius:4px;padding:2px 7px}" +
            ".scoring-no{font-size:.75em;font-weight:700;color:#9a3412;background:#fee2e2;" +
            "border:1px solid #fca5a5;border-radius:4px;padding:2px 7px}" +
            ".badge-name{font-weight:700}" +
            ".badge-pos{font-size:.78em;opacity:.7}" +
            ".badge-move{font-size:.75em;opacity:.85}" +
            ".win-icon{font-size:1em;margin-right:2px}" +
            "@media(max-width:600px){.podium{flex-direction:column;align-items:center}.podium-card{order:unset!important}" +
            ".game-meta{flex-direction:column;gap:4px;align-items:flex-start}}" +
            "</style>\n";
    }

    // -------------------------------------------------------------------------
    // JavaScript
    // -------------------------------------------------------------------------

    private String js(List<PlayerStats> players, int maxTier,
                      List<Game> allGames, Map<Game, List<Result>> resultsByGame) {
        StringBuilder data = new StringBuilder("const PLAYERS={\n");
        for (PlayerStats ps : players) {
            String key = ps.player.getName().replace("\\", "\\\\").replace("'", "\\'");
            data.append("'").append(key).append("':{");
            data.append("games:").append(ps.gamesPlayed).append(",");
            data.append("par:'").append(pct(ps.participationRate)).append("',");
            data.append("wins:").append(ps.totalWins).append(",");
            data.append("winPct:'").append(pct(ps.gamesPlayed > 0 ? (double) ps.totalWins / ps.gamesPlayed : 0)).append("',");
            data.append("movesUp:").append(ps.movesUp).append(",");
            data.append("movesUpPct:'").append(pct(ps.gamesPlayed > 0 ? (double) ps.movesUp / ps.gamesPlayed : 0)).append("',");
            data.append("movesDown:").append(ps.movesDown).append(",");
            data.append("movesDownPct:'").append(pct(ps.gamesPlayed > 0 ? (double) ps.movesDown / ps.gamesPlayed : 0)).append("',");
            data.append("homeTier:").append(ps.mostPlayedTier).append(",");
            data.append("best:'").append(ps.bestTeammate  != null ? ps.bestTeammate.getName().replace("'", "\\'")  : "—").append("',");
            data.append("worst:'").append(ps.worstTeammate != null ? ps.worstTeammate.getName().replace("'", "\\'") : "—").append("',");
            data.append("tiers:{");
            for (int t = 1; t <= maxTier; t++) {
                int gt = ps.gamesByTier.getOrDefault(t, 0);
                int wt = ps.winsByTier.getOrDefault(t, 0);
                data.append(t).append(":{g:").append(gt).append(",w:").append(wt).append("},");
            }
            data.append("}},\n");
        }
        data.append("};\nconst MAX_TIER=").append(maxTier).append(";\n");

        return "<script>\n" + data +
            "function showTab(name,btn){" +
            "document.querySelectorAll('.tab-content').forEach(e=>e.style.display='none');" +
            "document.getElementById('tab-'+name).style.display='';" +
            "document.querySelectorAll('.tab').forEach(b=>b.classList.remove('active'));" +
            "btn.classList.add('active');}\n" +

            "function filterTable(id,q){" +
            "q=q.toLowerCase();" +
            "document.querySelectorAll('#'+id+' tbody tr').forEach(r=>{" +
            "r.style.display=r.textContent.toLowerCase().includes(q)?'':' none';});}\n" +

            "function filterGames(){" +
            "var q=document.getElementById('gamePlayerFilter').value.toLowerCase().trim();" +
            "document.querySelectorAll('.game-card').forEach(card=>{" +
            "var players=card.dataset.players||'';" +
            "card.classList.toggle('hidden',q!==''&&!players.includes(q));});}\n" +

            "function toggleGame(hdr){" +
            "var detail=hdr.nextElementSibling;" +
            "var chevron=hdr.querySelector('.game-chevron');" +
            "var open=detail.style.display!=='none';" +
            "detail.style.display=open?'none':'';" +
            "chevron.classList.toggle('open',!open);}\n" +

            "function onPlayerPick(){" +
            "var name=document.getElementById('playerPicker').value;" +
            "var card=document.getElementById('playerCard');" +
            "document.querySelectorAll('#statsTable tbody tr').forEach(r=>{" +
            "r.classList.remove('highlight');r.style.display='';" +
            "if(name&&r.dataset.player!==name)r.style.display='none';" +
            "else if(name)r.classList.add('highlight');});" +
            "if(!name){card.style.display='none';return;}" +
            "var p=PLAYERS[name];if(!p){card.style.display='none';return;}" +
            "var tierRows='';" +
            "for(var t=1;t<=MAX_TIER;t++){var td=p.tiers[t]||{g:0,w:0};" +
            "var wr=td.g>0?Math.round(td.w/td.g*100)+'%':'—';" +
            "tierRows+='<div class=\"pc-item\"><div class=\"pc-label\">Tier '+t+'</div>" +
            "<div class=\"pc-value\">'+td.g+' games</div><div class=\"pc-sub\">'+td.w+' wins ('+wr+')</div></div>';}" +
            "card.innerHTML='<div class=\"pc-name\">'+name+'</div><div class=\"pc-grid\">" +
            "<div class=\"pc-item\"><div class=\"pc-label\">Games Played</div><div class=\"pc-value\">'+p.games+'</div><div class=\"pc-sub\">'+p.par+' participation</div></div>" +
            "<div class=\"pc-item\"><div class=\"pc-label\">Wins</div><div class=\"pc-value\">'+p.wins+'</div><div class=\"pc-sub\">'+p.winPct+' win rate</div></div>" +
            "<div class=\"pc-item\"><div class=\"pc-label\">Moved Up</div><div class=\"pc-value\">'+p.movesUp+'</div><div class=\"pc-sub\">'+p.movesUpPct+'</div></div>" +
            "<div class=\"pc-item\"><div class=\"pc-label\">Moved Down</div><div class=\"pc-value\">'+p.movesDown+'</div><div class=\"pc-sub\">'+p.movesDownPct+'</div></div>" +
            "<div class=\"pc-item\"><div class=\"pc-label\">Home Tier</div><div class=\"pc-value\">Tier '+p.homeTier+'</div></div>" +
            "<div class=\"pc-item\"><div class=\"pc-label\">Best Teammate</div><div class=\"pc-value\">'+p.best+'</div></div>" +
            "<div class=\"pc-item\"><div class=\"pc-label\">Worst Teammate</div><div class=\"pc-value\">'+p.worst+'</div></div>" +
            "'+tierRows+'</div>';" +
            "card.style.display='block';}\n" +

            "function sortTable(id,col,type){" +
            "var tbl=document.getElementById(id);" +
            "var th=tbl.querySelectorAll('th')[col];" +
            "var asc=th.classList.contains('sort-asc');" +
            "tbl.querySelectorAll('th').forEach(h=>{h.classList.remove('sort-asc','sort-desc');});" +
            "th.classList.add(asc?'sort-desc':'sort-asc');" +
            "var rows=Array.from(tbl.querySelectorAll('tbody tr'));" +
            "rows.sort((a,b)=>{" +
            "var av=a.cells[col]?a.cells[col].textContent.trim():'';" +
            "var bv=b.cells[col]?b.cells[col].textContent.trim():'';" +
            "if(type==='num'){av=parseFloat(av.replace('%',''))||0;bv=parseFloat(bv.replace('%',''))||0;" +
            "return asc?bv-av:av-bv;}" +
            "return asc?bv.localeCompare(av):av.localeCompare(bv);});" +
            "rows.forEach(r=>tbl.querySelector('tbody').appendChild(r));}\n" +
            "</script>\n";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }
}
