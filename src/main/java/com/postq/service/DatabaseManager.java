package com.postq.service;

import com.postq.model.Database;
import com.postq.model.Item;
import com.postq.model.Table;
import com.postq.util.FXs;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DatabaseManager {

    public static DatabaseManager INSTANCE= new DatabaseManager();

    private final Map<String,Connection> connections = new HashMap<>();

    private final Map<String,AutoCompleteTrie> tables = new HashMap<>();
    private final Map<String,AutoCompleteTrie> fields = new HashMap<>();

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
            FXs.showAlert("Connection Error", "No active connection for " + db.getTitle());
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
            FXs.showAlert("Error", "Could not load tables: " + e.getMessage());
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

    public List<String> getFieldSuggestion(Database database, String tableName, String word) {
        AutoCompleteTrie trie = fields.get(tableName);
        if(Objects.isNull(trie)) {
            trie = getTableTrie(database, tableName);
            fields.put(tableName, trie);
        }
        int idx = word.lastIndexOf('.');
        String fieldName = word.substring(idx+1);
        return trie.getSuggestions(fieldName);
    }

    public List<List<String>> getIndex(Database database, String tableName) {
        List<List<String>> data = new ArrayList<>();
        Connection conn = getConnection(database);
        String idxSql = "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = ?";
        try (PreparedStatement stmt = conn.prepareStatement(idxSql)) {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("indexname");
                String def = rs.getString("indexdef");
                if (name != null && !name.trim().isEmpty()) {
                    List<String> row = List.of(name, def != null ? def : "");
                    data.add(row);
                }
            }
        } catch (Exception e){
            FXs.showAlert("错误", "获取索引失败: " + e.getMessage());
        }
        return data;
    }

    public AutoCompleteTrie getTableTrie(Database database, String tableName) {
        String sql = """
            SELECT a.attname
            FROM pg_attribute a
            JOIN pg_class c ON a.attrelid = c.oid
            JOIN pg_namespace n ON c.relnamespace = n.oid
            WHERE n.nspname = 'public' 
              AND c.relname = ?
              AND a.attnum > 0 
              AND NOT a.attisdropped
            ORDER BY a.attnum
        """;
        AutoCompleteTrie trie = new AutoCompleteTrie();
        Connection conn = getConnection(database);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String column = rs.getString("attname");
                    trie.insert(column);
                }
            }
        } catch (Exception e){
            FXs.showAlert("错误", "获取表字段失败: " + e.getMessage());
        }
        return trie;
    }

    public List<List<String>> getTableSchema(Database database, String tableName) {
        String colSql = """
        SELECT
            a.attname AS column_name,
            format_type(a.atttypid, a.atttypmod) AS data_type,
            a.attnotnull AS not_null,
            pg_get_expr(d.adbin, d.adrelid) AS default_value,
            col_description(a.attrelid, a.attnum) AS comment
        FROM
            pg_attribute a
        LEFT JOIN pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum
        WHERE
            a.attrelid = ?::regclass
            AND a.attnum > 0
            AND NOT a.attisdropped
        ORDER BY a.attnum;
        """;

        List<List<String>> data = new ArrayList<>();
        Connection conn = getConnection(database);

        try (PreparedStatement stmt = conn.prepareStatement(colSql)) {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("column_name");
                String type = rs.getString("data_type");
                String notNull = rs.getBoolean("not_null") ? "是" : "否";
                String defVal = rs.getString("default_value");
                String comment = rs.getString("comment");

                if (name != null && !name.trim().isEmpty()) {
                    List<String> row = List.of(
                            name,
                            type != null ? type : "",
                            notNull,
                            defVal != null ? defVal : "",
                            comment != null ? comment : ""
                    );
                    data.add(row);
                }
            }
        } catch (Exception e){
            FXs.showAlert("错误", "获取表结构失败: " + e.getMessage());
        }
        return data;
    }

    public TableView<List<String>> query(Database database, String sql) {
        TableView<List<String>> resultTable = new TableView<>();
        Connection conn = getConnection(database);
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            resultTable.getItems().clear();
            resultTable.getColumns().clear();

            // 获取结果集元数据
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            // 设置表头
            for (int i = 1; i <= cols; i++) {
                final int colIdx = i;
                TableColumn<List<String>, String> column = new TableColumn<>(meta.getColumnName(i));
                column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(colIdx - 1)));
                resultTable.getColumns().add(column);
            }

            // 填充数据
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= cols; i++) {
                    row.add(rs.getString(i));
                }
                resultTable.getItems().add(row);
            }

        } catch (SQLException e) {
            FXs.showAlert("查询失败", "无法执行查询：" + e.getMessage());
        }
        return resultTable;
    }
}
