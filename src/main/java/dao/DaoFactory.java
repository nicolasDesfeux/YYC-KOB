package dao;

import dao.daoGSheet.GameDaoGSheet;
import dao.daoGSheet.GSheetConnector;
import dao.daoGSheet.GSheetRankingWriter;
import dao.daoGSheet.GSheetScoreCacheDao;
import dao.daoGSheet.PlayerDaoGSheet;
import dao.daoGSheet.ResultDaoGSheet;
import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.RankingWriter;
import dao.daoInterface.ResultDao;
import dao.daoInterface.ScoreCacheDao;
import dao.daoJdbc.ConsoleRankingWriter;
import dao.daoJdbc.GameDaoJDBC;
import dao.daoJdbc.PlayerDaoJDBC;
import dao.daoJdbc.ResultDaoJDBC;

import java.util.Properties;

public class DaoFactory {

    private final String type;
    private final GSheetConnector connector; // null for JDBC

    private DaoFactory(String type, GSheetConnector connector) {
        this.type = type;
        this.connector = connector;
    }

    public static DaoFactory forType(String type, Properties properties) {
        GSheetConnector connector = null;
        if ("GSheet".equals(type)) {
            connector = new GSheetConnector(properties.getProperty("gsheet.spreadsheet.id"));
        }
        return new DaoFactory(type, connector);
    }

    public GameDao createGameDao() {
        return switch (type) {
            case "GSheet" -> new GameDaoGSheet(connector);
            case "JDBC"   -> new GameDaoJDBC();
            default -> throw new IllegalArgumentException("Unknown DAO type: " + type);
        };
    }

    public PlayerDao createPlayerDao() {
        return switch (type) {
            case "GSheet" -> new PlayerDaoGSheet(connector);
            case "JDBC"   -> new PlayerDaoJDBC();
            default -> throw new IllegalArgumentException("Unknown DAO type: " + type);
        };
    }

    public ResultDao createResultDao(GameDao gameDao, PlayerDao playerDao) {
        return switch (type) {
            case "GSheet" -> new ResultDaoGSheet(connector, gameDao, playerDao);
            case "JDBC"   -> new ResultDaoJDBC();
            default -> throw new IllegalArgumentException("Unknown DAO type: " + type);
        };
    }

    public RankingWriter createRankingWriter() {
        return switch (type) {
            case "GSheet" -> new GSheetRankingWriter(connector);
            case "JDBC"   -> new ConsoleRankingWriter();
            default -> throw new IllegalArgumentException("Unknown DAO type: " + type);
        };
    }

    public ScoreCacheDao createScoreCacheDao() {
        return switch (type) {
            case "GSheet" -> new GSheetScoreCacheDao(connector);
            default       -> null; // no-op for JDBC
        };
    }
}
