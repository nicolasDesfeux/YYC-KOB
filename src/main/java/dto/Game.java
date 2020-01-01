package dto;


import java.io.File;
import java.sql.Date;

public class Game {
    private long id;
    private Date date;
    private double highestPoint;
    private double lowestPoint;
    private boolean isComplete;

    public Game(long id, Date date, double highestPoint, double lowestPoint) {
        this.id = id;
        this.date = date;
        this.highestPoint = highestPoint;
        this.lowestPoint = lowestPoint;
        this.isComplete = false;
    }

    public Game(long id, Date date, double highestPoint, double lowestPoint, boolean isComplete) {
        this.id = id;
        this.date = date;
        this.highestPoint = highestPoint;
        this.lowestPoint = lowestPoint;
        this.isComplete = isComplete;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getHighestPoint() {
        return highestPoint;
    }

    public void setHighestPoint(double highestPoint) {
        this.highestPoint = highestPoint;
    }

    public double getLowestPoint() {
        return lowestPoint;
    }

    public void setLowestPoint(double lowestPoint) {
        this.lowestPoint = lowestPoint;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }

    @Override
    public String toString() {
        return "Game{" +
                "id=" + id +
                ", date=" + date +
                ", highestPoint=" + highestPoint +
                ", lowestPoint=" + lowestPoint +
                "}\n";
    }

    public File getGameScoreSheetAsPDF(){
        return null;
    }
}
