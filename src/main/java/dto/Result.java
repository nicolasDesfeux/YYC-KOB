package dto;

import kob.KOB;

import java.sql.Date;

public class Result extends DataTransferObject<Result> {
    private long id;
    private Game session;
    private Player player;
    private long result;
    private double score;
    private Date dateForLight;
    private double playerMasterScoreBeforeGame;

    public Result(long id, Game session, Player player, long result, double score, double playerMasterScoreBeforeGame) {
        this.id = id;
        this.session = session;
        this.player = player;
        this.result = result;
        this.score = score;
        this.playerMasterScoreBeforeGame = playerMasterScoreBeforeGame;
    }

    public Result(long id, long result, double score, Date date, double playerMasterScoreBeforeGame) {
        this.id = id;
        this.result = result;
        this.score = score;
        this.dateForLight = date;
        this.playerMasterScoreBeforeGame = playerMasterScoreBeforeGame;
    }

    public Result(Game session, Player player, long result) {
        this.session = session;
        this.player = player;
        this.result = result;
    }

    public Result(Game session, Player player) {
        this.session = session;
        this.player = player;
    }

    public Game getSession() {
        return session;
    }

    public void setSession(Game session) {
        this.session = session;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public long getResult() {
        return result;
    }

    public void setResult(long result) {
        this.result = result;
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

    public Date getDateForLight() {
        return dateForLight;
    }

    public void setDateForLight(Date dateForLight) {
        this.dateForLight = dateForLight;
    }

    public static int compare(Result result, Result result1) {
        return Double.compare(result1.playerMasterScoreBeforeGame, result.playerMasterScoreBeforeGame);
    }

    public double getPlayerMasterScoreBeforeGame() {
        return playerMasterScoreBeforeGame;
    }

    public void setPlayerMasterScoreBeforeGame(double playerMasterScoreBeforeGame) {
        this.playerMasterScoreBeforeGame = playerMasterScoreBeforeGame;
    }

    @Override
    public String toString() {
        return "Result{" +
                "On " + (session != null ? session.getDate() : dateForLight) +
                ", " + player.getName() +
                " finished " + result +
                ", scoring " + KOB.DF.format(score) +
                " points (Original master score: " + KOB.DF.format(playerMasterScoreBeforeGame) + ")}";
    }

    @Override
    public Result save() {
        return KOB.getInstance().getResultDao().insertResult(this);
    }
}
