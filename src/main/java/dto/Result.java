package dto;

import kob.KOB;

import java.math.BigDecimal;

public class Result {
    private long id;
    private final Game session;
    private Player player;
    private final double result;
    private double score;
    private BigDecimal playerMasterScoreBeforeGame;
    private BigDecimal playerMasterScoreAfterGame;
    private boolean debutGame;

    public Result(long id, Game session, Player player, double result, double score, BigDecimal playerMasterScoreBeforeGame) {
        this.id = id;
        this.session = session;
        this.player = player;
        this.result = result;
        this.score = score;
        this.playerMasterScoreBeforeGame = playerMasterScoreBeforeGame;
    }

    public Result(Game session, Player player, double result) {
        this.session = session;
        this.player = player;
        this.result = result;
    }


    public Game getSession() {
        return session;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public double getResult() {
        return result;
    }


    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }


    public BigDecimal getPlayerMasterScoreBeforeGame() {
        return playerMasterScoreBeforeGame;
    }

    public void setPlayerMasterScoreBeforeGame(BigDecimal playerMasterScoreBeforeGame) {
        this.playerMasterScoreBeforeGame = playerMasterScoreBeforeGame;
    }

    public boolean isDebutGame() {
        return debutGame;
    }

    public void setDebutGame(boolean debutGame) {
        this.debutGame = debutGame;
    }

    public BigDecimal getPlayerMasterScoreAfterGame() {
        return playerMasterScoreAfterGame;
    }

    public void setPlayerMasterScoreAfterGame(BigDecimal playerMasterScoreAfterGame) {
        this.playerMasterScoreAfterGame = playerMasterScoreAfterGame;
    }

    @Override
    public String toString() {
        return "Result{" +
                "On " + (session==null?"":session.getDate()) +
                "(" + (session==null?"":session.getId()) + ")" +
                ", " + player.getName() +
                " finished " + result +
                ", scoring " + KOB.DF.format(score) +
                " points (Original master score: " + KOB.DF.format(playerMasterScoreBeforeGame!=null?playerMasterScoreBeforeGame:0) + ")}\n";
    }


}
