package dto;

import java.sql.Date;

public class Result {
    private long id;
    private Game session;
    private Player player;
    private long result;
    private double score;
    private Date dateForLight;

    public Result(long id, Game session, Player player, long result, double score) {
        this.id = id;
        this.session = session;
        this.player = player;
        this.result = result;
        this.score = score;
    }

    public Result(long id, long result, double score, Date date) {
        this.id = id;
        this.result = result;
        this.score = score;
        this.dateForLight = date;
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

    @Override
    public String toString() {
        return "Result{" +
                "id=" + id +
                ", On " + (session!=null?session.getDate():dateForLight)+
                ", player " + player.getName() +
                " finshed " + result +
                ", scoring " + score +
                " points}\n";
    }
}
