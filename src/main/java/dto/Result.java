package dto;

import kob.KOB;

import java.math.BigDecimal;

public class Result extends DataTransferObject<Result> {
    private long id;
    private final Game session;
    private Player player;
    private final double result;
    private double score;
    private BigDecimal playerMasterScoreBeforeGame;

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

    @Override
    public String toString() {
        return "Result{" +
                "On " + session.getDate() +
                ", " + player.getName() +
                " finished " + result +
                ", scoring " + KOB.DF.format(score) +
                " points (Original master score: " + KOB.DF.format(playerMasterScoreBeforeGame!=null?playerMasterScoreBeforeGame:0) + ")}";
    }

    @Override
    public Result save() {
        return KOB.getInstance().getResultDao().insertResult(this);
    }
}
