package dto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import kob.KOB;

import java.math.BigDecimal;
import java.util.Objects;

public class Player extends DataTransferObject<Player> {
    private Long id;
    private final String name;
    @JsonIgnore
    private BigDecimal masterScore;
    @JsonIgnore
    private boolean hasResults;

    public Player(long id, String name, boolean hasScore) {
        this.id = id;
        this.name = name;
        this.hasResults = hasScore;
        this.masterScore = BigDecimal.valueOf(KOB.INITIAL_SCORE);
    }

    public Player(String name) {
        this.name = name;
        this.masterScore = BigDecimal.valueOf(KOB.INITIAL_SCORE);
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

    public BigDecimal getMasterScore() {
        return masterScore;
    }

    public void setMasterScore(BigDecimal masterScore) {
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
        return o1.getMasterScore().compareTo(o2.getMasterScore());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id) &&
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
