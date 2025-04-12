package com.postq;

import com.postq.model.Database;
import com.postq.util.Fxs;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    public static Connection getConnection(Database db){
        String url = db.getUrl();
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, db.getUserName(), db.getPassword());
        } catch (SQLException e) {
            Fxs.showAlert("Connection Error", "No active connection for " + db.getTitle());
        }
        return conn;
    }
}
