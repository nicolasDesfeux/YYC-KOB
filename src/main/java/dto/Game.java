package dto;


import dao.daoInterface.ResultDao;
import dao.daoJdbc.ResultDaoJDBC;
import kob.KOB;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Game extends DataTransferObject<Game> {
    private long id;
    private LocalDate date;
    private double highestPoint;
    private double lowestPoint;

    private static double[][] PAYOUT = {{15, 5, 0, 0},
            {17, 7, 0, 0, 15, 5, 0, 0},
            {20, 10, 0, 0, 15, 5, 0, 0, 5, 0, 0, 0},
            {20, 10, 0, 0, 15, 5, 0, 0, 10, 0, 0, 0, 5, 0, 0, 0},
            {25, 15, 0, 0, 20, 10, 0, 0, 15, 5, 0, 0, 10, 0, 0, 0, 5, 0, 0, 0}};

    public Game(long id, LocalDate date, double highestPoint, double lowestPoint) {
        this.id = id;
        this.date = date;
        this.highestPoint = highestPoint;
        this.lowestPoint = lowestPoint;
    }

    public Game(LocalDate date) {
        this.date = date;
    }

    public LocalDate getDate() {
        return date;
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



    @Override
    public String toString() {
        return "Game{" +
                "id=" + id +
                ", date=" + date +
                ", highestPoint=" + highestPoint +
                ", lowestPoint=" + lowestPoint +
                "}\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Game game = (Game) o;
        return id == game.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public Game save() {
        return KOB.getInstance().getGameDao().insertGame(this);
    }

    public String getXmlExpanded() {
        StringBuilder game = new StringBuilder("<Game>");
        game.append("<Id>").append(this.getId()).append("</Id>");
        game.append("<Date>").append(this.getDate()).append("</Date>");
        game.append("<TopAvailableScore>").append(this.getHighestPoint()).append("</TopAvailableScore>");
        game.append("<BottomAvailableScore>").append(this.getLowestPoint()).append("</BottomAvailableScore>");
        game.append("<Participants>");
        ResultDao resultDao = new ResultDaoJDBC();
        List<Result> allResultsFromGame = resultDao.getAllResultsFromGame(this);
        allResultsFromGame.sort(Comparator.comparingDouble(Result::getResult));
        for (Result result : allResultsFromGame) {
            game.append("<Participant>");
            game.append("<PlayerName>").append(result.getPlayer().getName()).append("</PlayerName>");
            game.append("<PlayerResult>").append(result.getResult()).append("</PlayerResult>");
            game.append("<PlayerScore>").append(result.getScore()).append("</PlayerScore>");
            game.append("<PlayerPayOut>").append(PAYOUT[allResultsFromGame.size() / 4][(int) result.getResult() - 1]).append("</PlayerPayOut>");
            game.append("</Participant>");
        }
        game.append("</Participants>");
        game.append("</Game>");

        return game.toString();
    }
}
