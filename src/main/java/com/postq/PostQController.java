package com.postq;

import com.postq.model.Database;
import com.postq.model.Item;
import com.postq.model.ItemType;
import com.postq.model.Table;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostQController {

    @FXML private SplitPane mainSplitPane;
    @FXML private TreeView<Item> dbTreeView;
    @FXML private TextArea sqlEditor;
    @FXML private TableView<List<String>> resultTable;
    @FXML private Button addDbButton;
    @FXML private Button queryButton;
    @FXML private Label statusLabel;

    private final Map<String,Connection> connections = new HashMap<>();

    @FXML
    private void initialize() {


        dbTreeView.setShowRoot(false);
        dbTreeView.setRoot(new TreeItem<>());


        dbTreeView.setOnContextMenuRequested(event -> {
            TreeItem<Item> selectedItem = dbTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getParent() != dbTreeView.getRoot()) {
                if(ItemType.TABLE.equals(selectedItem.getValue().getItemType())){
                    showContextMenu(event, selectedItem);
                }

            }
        });

        dbTreeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Item> selected = dbTreeView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    Item item = selected.getValue();
                    if (item.getItemType() == ItemType.DB && selected.getChildren().isEmpty()) {
                        loadTablesForDatabase(selected);
                    } else if (item.getItemType() == ItemType.TABLE) {
                        Table table = (Table) item;
                        String currentText = sqlEditor.getText();
                        String newText = currentText + (currentText.isEmpty() ? "" : "\n") + "SELECT * FROM " + table.getTableName() + ";\n";
                        sqlEditor.setText(newText);
                        sqlEditor.positionCaret(newText.length());
                    }
                }
            }
        });

        addDbButton.setOnAction(e -> onAddDatabase());
        queryButton.setOnAction(e -> onRunQuery());

        Platform.runLater(() -> {
            mainSplitPane.setDividerPositions(0.2);
        });

        mainSplitPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            mainSplitPane.setDividerPositions(0.2);
        });
    }

    private void onAddDatabase() {
        Dialog<Connection> dialog = new Dialog<>();
        dialog.setTitle("Add Database");
        dialog.setHeaderText("Enter connection details");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField titleField = new TextField("172.16.10.188");
        TextField hostField = new TextField("172.16.10.188");
        TextField portField = new TextField("5432");
        TextField dbField = new TextField("dev-fms");
        TextField userField = new TextField("dev_fms");
        PasswordField passField = new PasswordField();

        grid.addRow(0, new Label("Title:"), titleField);
        grid.addRow(1, new Label("Host:"), hostField);
        grid.addRow(2, new Label("Port:"), portField);
        grid.addRow(3, new Label("Database:"), dbField);
        grid.addRow(4, new Label("User:"), userField);
        grid.addRow(5, new Label("Password:"), passField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL
        );

        Database database = new Database();
        database.setTitle(hostField.getText());
        database.setPort(portField.getText());
        database.setHost(hostField.getText());
        database.setUserName(userField.getText());
        database.setPassword(passField.getText());
        database.setDatabaseName(dbField.getText());

        dialog.setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                try {
                    String url = database.getUrl();
                    Connection conn = DriverManager.getConnection(url, userField.getText(), passField.getText());
                    TreeItem<Item> item = new TreeItem<>(database);
                    dbTreeView.getRoot().getChildren().add(item);
                    connections.put(titleField.getText(),conn);
                    return conn;
                } catch (SQLException e) {
                    showAlert("Connection Failed", e.getMessage());
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void onRunQuery() {
        TreeItem<Item> selected = dbTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected == dbTreeView.getRoot()) {
            showAlert("No Database Selected", "Please select a database first.");
            return;
        }

        TreeItem<Item> selectedDB = selected;
        if(selected.getValue().getItemType().equals(ItemType.TABLE)){
            selectedDB= selected.getParent();
        }

        String dbName = ((Database)selectedDB.getValue()).getTitle();
        Connection connection = connections.get(dbName);

        if (connection == null) {
            showAlert("Connection Error", "Database not connected.");
            return;
        }

        String sql = sqlEditor.getText();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            resultTable.getItems().clear();
            resultTable.getColumns().clear();

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            for (int i = 1; i <= cols; i++) {
                final int colIdx = i;
                TableColumn<List<String>, String> column = new TableColumn<>(meta.getColumnName(i));
                column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(colIdx - 1)));
                resultTable.getColumns().add(column);
            }

            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= cols; i++) {
                    row.add(rs.getString(i));
                }
                resultTable.getItems().add(row);
            }

        } catch (SQLException e) {
            showAlert("Query Failed", e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }

    private void loadTablesForDatabase(TreeItem<Item> dbItem) {
        Database dbName = (Database) dbItem.getValue();

        Connection conn = connections.get(dbName.getTitle());

        if (conn == null) {
            showAlert("Connection Error", "No active connection for " + dbName);
            return;
        }

        try {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
            dbItem.getChildren().clear();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                Table table = new Table();
                table.setTableName(tableName);
                TreeItem<Item> tableItem = new TreeItem<>(table);
                dbItem.getChildren().add(tableItem);
            }
        } catch (SQLException e) {
            showAlert("Error", "Could not load tables: " + e.getMessage());
        }
    }



    private void showTableStructure(TreeItem<Item> tableItem) {
        Table table = (Table) tableItem.getValue();
        Database database = (Database) tableItem.getParent().getValue();
        Connection conn = connections.get(database.getTitle());

        if (conn == null) {
            showAlert("连接错误", "无法获取数据库连接：" + database.getTitle());
            return;
        }

        try {
            // ========== 字段结构 TableView ==========
            TableView<List<String>> columnTable = new TableView<>();
            TableColumn<List<String>, String> colName = new TableColumn<>("字段名");
            colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(0)));

            TableColumn<List<String>, String> colType = new TableColumn<>("类型");
            colType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(1)));

            TableColumn<List<String>, String> colNotNull = new TableColumn<>("非空");
            colNotNull.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(2)));

            TableColumn<List<String>, String> colDefault = new TableColumn<>("默认值");
            colDefault.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(3)));

            TableColumn<List<String>, String> colComment = new TableColumn<>("注释");
            colComment.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(4)));

            columnTable.getColumns().addAll(colName, colType, colNotNull, colDefault, colComment);

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

            try (PreparedStatement stmt = conn.prepareStatement(colSql)) {
                stmt.setString(1, table.getTableName());
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
                        columnTable.getItems().add(row);
                    }
                }
            }

            // ========== 索引 TableView ==========
            TableView<List<String>> indexTable = new TableView<>();
            TableColumn<List<String>, String> idxName = new TableColumn<>("索引名");
            idxName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(0)));

            TableColumn<List<String>, String> idxDef = new TableColumn<>("定义");
            idxDef.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(1)));

            indexTable.getColumns().addAll(idxName, idxDef);

            String idxSql = "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = ?";
            try (PreparedStatement stmt = conn.prepareStatement(idxSql)) {
                stmt.setString(1, table.getTableName());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String name = rs.getString("indexname");
                    String def = rs.getString("indexdef");
                    if (name != null && !name.trim().isEmpty()) {
                        List<String> row = List.of(name, def != null ? def : "");
                        indexTable.getItems().add(row);
                    }
                }
            }

            // ========== 使用上下结构的 SplitPane ==========
            SplitPane splitPane = new SplitPane();
            splitPane.setOrientation(Orientation.VERTICAL);
            splitPane.getItems().addAll(columnTable, indexTable);
            splitPane.setDividerPositions(0.7); // 上70%，下30%

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("表结构 - " + table.getTableName());
            dialog.getDialogPane().setContent(splitPane);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.setResizable(true);
            dialog.setWidth(800);
            dialog.setHeight(600);

            dialog.showAndWait();

        } catch (SQLException e) {
            showAlert("错误", "获取表结构失败: " + e.getMessage());
        }
    }



    private void showContextMenu(ContextMenuEvent event, TreeItem<Item> tableItem) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem showStructureItem = new MenuItem("Show Structure");

        // 为菜单项添加点击事件
        showStructureItem.setOnAction(e -> showTableStructure(tableItem));

        // 向右键菜单添加选项
        contextMenu.getItems().add(showStructureItem);

        // 显示菜单
        contextMenu.show(dbTreeView, event.getScreenX(), event.getScreenY());
    }



}
