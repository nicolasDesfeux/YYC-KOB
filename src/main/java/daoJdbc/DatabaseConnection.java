package daoJdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class DatabaseConnection {

    private static DatabaseConnection instance;
    private Connection connection;
    private static final String URL = "jdbc:mysql://localhost:3306/kingOfTheBeach";
    private static final String USER = "kobApplication";
    private static final String PASS = "kob2019";

    private DatabaseConnection() throws SQLException {
        this.connection = DriverManager.getConnection(URL, USER, PASS);
    }

    public Connection getConnection() {
        return connection;
    }

    public static DatabaseConnection getInstance() {
        try{
            if (instance == null) {
                instance = new DatabaseConnection();
            } else if (instance.getConnection().isClosed()) {
                instance = new DatabaseConnection();
            }
        }catch(SQLException e){
            System.err.println("Database Connection Creation Failed : " + e.getMessage());
        }
        return instance;
    }
}