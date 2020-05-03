package com.Minecraft.DeStilleGast.TogglePVP;

import java.sql.*;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by DeStilleGast on 11-9-2016.
 */
public class Connector {

    private Logger logger;

    private final String host, database, user, password;
    private Connection con;

    public Connector(Logger logger, String host, String database, String user, String password) {
        this.logger = logger;

        this.host= host;
        this.database= database;
        this.user= user;
        this.password= password;
    }


    public void open() {
        try {
//            logger.info("Atempting to login to database");
            con = DriverManager.getConnection("jdbc:mysql://" + host + ":3306/" + database+ "?autoReconnect=true&serverTimezone=" + TimeZone.getDefault().getID(),
                    user, password);
//            System.out.println(ZoneId.systemDefault().toString());



//            logger.log(Level.INFO, "[MySQL] The connection to MySQL is made!");
        } catch (SQLException e) {
            logger.log(Level.INFO, "[MySQL] The connection to MySQL couldn't be made! reason: " + e.getMessage() + ", Caused by " + e.getCause().getMessage());
        }
    }

    public void close() {
        try {
            if (con != null) {
                con.close();
//                logger.log(Level.INFO, "[MySQL] The connection to MySQL is ended successfully!");
            }
        } catch (SQLException e) {

            e.printStackTrace();
        }
    }

    /**
     * Request a prepare statement with the given query
     * @param qry Query
     * @return PreparedStatedment with given query
     */
    public PreparedStatement prepareStatement(String qry) throws SQLException {
        return con.prepareStatement(qry);
    }

    /**
     * Execute a update/insert statement
     * @param statement PrepareStatement with Update/Insert statement
     */
    public int update(PreparedStatement statement) throws SQLException {
        try {
            return statement.executeUpdate();
        } catch (SQLException e) {
            open();
            e.printStackTrace();
            throw e; // ook al wordt het hier opgevangen, het dingetje is, we kunnen anders niet zien of het gelukt of gefaald heeft
        } finally {
            statement.close();
        }
    }

    /**
     * Check if there is a connection to the MySQL server
     * @return true if there is a connection
     */
    public boolean hasConnection() {
        return con != null;
    }

    /**
     * Execute SELECT statement
     * @param statement PrepareStatement with SELECT query
     * @return ResultSet from given PrepareStatement/query
     */
    public ResultSet query(PreparedStatement statement) throws SQLException {
        return statement.executeQuery();
    }

    public void createTable(String tb){
        try {
            open();
            update(prepareStatement("CREATE TABLE IF NOT EXISTS " + tb));
        }catch (SQLException ex){
            ex.printStackTrace();
        }finally {
            close();
        }
    }
}
