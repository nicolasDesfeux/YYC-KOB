package dto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import kob.KOB;

import java.util.Objects;

public class Player extends DataTransferObject<Player> {
    private Long id;
    private String name;
    @JsonIgnore
    private double masterScore;
    @JsonIgnore
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    @Override
    public Player save() {
        if (this.id == null) {
            return KOB.getInstance().getPlayerDao().insertPlayer(this);
        } else {
            KOB.getInstance().getPlayerDao().updatePlayer(this);
            return this;
        }

    }
}
