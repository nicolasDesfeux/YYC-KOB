package dto;
import kob.KOB;

import java.util.Objects;

public class Player {
    private long id;
    private String name;
    private double masterScore;
    private boolean hasResults;

    public Player(long id, String name, boolean hasScore) {
        this.id = id;
        this.name = name;
        this.hasResults = hasScore;
        this.masterScore = KOB.INITIAL_SCORE;
    }

    public Player(String name) {
        this.name = name;
        this.masterScore = KOB.INITIAL_SCORE;
        this.hasResults = false;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getMasterScore() {
        return masterScore;
    }

    public void setMasterScore(double masterScore) {
        this.masterScore = masterScore;
    }

    public boolean isHasResults() {
        return hasResults;
    }

    public void setHasResults(boolean hasResults) {
        this.hasResults = hasResults;
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", masterScore=" + masterScore +
                "}\n";
    }


    public static int compare(Player o1, Player o2) {
        return Double.compare(o1.getMasterScore(), o2.getMasterScore());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return id == player.id &&
                Objects.equals(name, player.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
