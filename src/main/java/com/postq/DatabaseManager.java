package com.postq;

import com.postq.model.Database;
import com.postq.model.Item;
import com.postq.model.Table;
import com.postq.util.AutoCompleteTrie;
import com.postq.util.Fxs;
import javafx.scene.control.TreeItem;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DatabaseManager {

    public static DatabaseManager INSTANCE= new DatabaseManager();

    private final Map<String,Connection> connections = new HashMap<>();

    private final Map<String,AutoCompleteTrie> tables = new HashMap<>();

    public Connection getConnection(String key){
        return connections.get(key);
    }

    public Connection getConnection(Database db){
        Connection conn = connections.get(db.getTitle());
        if(Objects.nonNull(conn)){
            return conn;
        }

        String url = db.getUrl();
        try {
            conn = DriverManager.getConnection(url, db.getUserName(), db.getPassword());
            connections.put(db.getTitle(), conn);
        } catch (SQLException e) {
            Fxs.showAlert("Connection Error", "No active connection for " + db.getTitle());
        }
        return conn;
    }

    public List<TreeItem<Item>> loadTables(Database database) {
        AutoCompleteTrie trie = new AutoCompleteTrie();
        List<TreeItem<Item>> tableItems = new ArrayList<>();
        Connection conn = DatabaseManager.INSTANCE.getConnection(database);
        try {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                Table table = new Table();
                table.setTableName(tableName);
                TreeItem<Item> tableItem = new TreeItem<>(table);
                tableItems.add(tableItem);
                trie.insert(tableName);
            }
        } catch (SQLException e) {
            Fxs.showAlert("Error", "Could not load tables: " + e.getMessage());
        }
        tables.put(database.getTitle(), trie);
        return tableItems;
    }

    public List<String> getTableSuggestion(Database database, String word) {
        AutoCompleteTrie trie = tables.get(database.getTitle());
        if (Objects.isNull(trie)) {
            return new ArrayList<>();
        }
        return trie.getSuggestions(word);
    }

    public List<String> getFieldSuggestion(Database database, String word) {
        return new ArrayList<>();
    }
}
