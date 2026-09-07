package dto;
import kob.KOB;

import java.math.BigDecimal;
import java.util.Objects;

public class Player {
    private Long id;
    private final String name;

    private BigDecimal masterScore;

    private boolean hasResults;

    public Player(long id, String name, boolean hasScore) {
        this.id = id;
        this.name = name;
        this.hasResults = hasScore;
        this.masterScore = BigDecimal.valueOf(KOB.config().initialScore);
    }

    public Player(String name) {
        this.name = name;
        this.masterScore = BigDecimal.valueOf(KOB.config().initialScore);
        this.hasResults = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Canonical form used when matching a name across sheets.
     *
     * Player identity lives in spreadsheet column headers typed by hand, so the
     * same person can appear as "Chris Mitchell", "chris mitchell", or with a
     * stray trailing space. Matching on this form makes lookups tolerant of
     * those differences while the original spelling is what gets displayed.
     *
     * @return trimmed, lower-cased, with internal whitespace runs collapsed;
     *         empty string for a null input
     */
    public static String normaliseName(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    /** This player's name in {@link #normaliseName canonical} form. */
    public String getNormalisedName() {
        return normaliseName(name);
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


}
