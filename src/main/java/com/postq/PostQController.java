package com.postq;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.GridPane;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostQController {

    @FXML private TreeView<String> dbTreeView;
    @FXML private TextArea sqlEditor;
    @FXML private TableView<List<String>> resultTable;
    @FXML private Button addDbButton;
    @FXML private Button queryButton;

    private final List<Connection> connections = new ArrayList<>();

    @FXML
    private void initialize() {
        dbTreeView.setRoot(new TreeItem<>("Databases"));


        dbTreeView.setOnContextMenuRequested(event -> {
            TreeItem<String> selectedItem = dbTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getParent() != dbTreeView.getRoot()) {
                showContextMenu(event, selectedItem);
            }
        });

        dbTreeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<String> selected = dbTreeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getParent() == dbTreeView.getRoot() && selected.getChildren().isEmpty()) {
                    loadTablesForDatabase(selected);
                }
            }
        });

        addDbButton.setOnAction(e -> onAddDatabase());
        queryButton.setOnAction(e -> onRunQuery());
    }

    private void onAddDatabase() {
        Dialog<Connection> dialog = new Dialog<>();
        dialog.setTitle("Add Database");
        dialog.setHeaderText("Enter connection details");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField hostField = new TextField("172.16.10.188");
        TextField portField = new TextField("5432");
        TextField dbField = new TextField("dev-fms");
        TextField userField = new TextField("dev_fms");
        PasswordField passField = new PasswordField();

        grid.addRow(0, new Label("Host:"), hostField);
        grid.addRow(1, new Label("Port:"), portField);
        grid.addRow(2, new Label("Database:"), dbField);
        grid.addRow(3, new Label("User:"), userField);
        grid.addRow(4, new Label("Password:"), passField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL
        );

        dialog.setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                try {
                    String url = "jdbc:postgresql://" + hostField.getText() + ":" + portField.getText() + "/" + dbField.getText();
                    Connection conn = DriverManager.getConnection(url, userField.getText(), passField.getText());
                    TreeItem<String> item = new TreeItem<>(dbField.getText());
                    dbTreeView.getRoot().getChildren().add(item);
                    connections.add(conn);
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
        TreeItem<String> selected = dbTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected == dbTreeView.getRoot()) {
            showAlert("No Database Selected", "Please select a database first.");
            return;
        }

        String dbName = selected.getValue();
        Connection connection = connections.stream().filter(conn -> {
            try {
                return !conn.isClosed() && conn.getCatalog().equals(dbName);
            } catch (SQLException e) {
                return false;
            }
        }).findFirst().orElse(null);

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

    private void loadTablesForDatabase(TreeItem<String> dbItem) {
        String dbName = dbItem.getValue();

        Connection conn = connections.stream().filter(c -> {
            try {
                return !c.isClosed() && c.getCatalog().equals(dbName);
            } catch (SQLException e) {
                return false;
            }
        }).findFirst().orElse(null);

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
                TreeItem<String> tableItem = new TreeItem<>(tableName);
                addContextMenuToTable(tableItem, conn, tableName);
                dbItem.getChildren().add(tableItem);
            }
        } catch (SQLException e) {
            showAlert("Error", "Could not load tables: " + e.getMessage());
        }
    }

    private void addContextMenuToTable(TreeItem<String> tableItem, Connection conn, String tableName) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem showStructureItem = new MenuItem("Show Structure");
        showStructureItem.setOnAction(e -> showTableStructure(tableName));
        contextMenu.getItems().add(showStructureItem);

    }

    private void showTableStructure(String tableName) {
        // 假设已有数据库连接
        Connection conn = connections.get(0);  // 假设你有获取当前数据库连接的方法

        try {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet columns = meta.getColumns(null, null, tableName, null);

            // 创建弹窗来显示表结构
            StringBuilder tableStructure = new StringBuilder("Columns in table " + tableName + ":\n");
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                tableStructure.append(columnName).append(" - ").append(columnType).append("\n");
            }

            // 显示弹窗
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Table Structure");
            alert.setHeaderText("Structure of table " + tableName);
            alert.setContentText(tableStructure.toString());
            alert.showAndWait();

        } catch (SQLException e) {
            showAlert("Error", "Could not retrieve table structure: " + e.getMessage());
        }
    }

    private void showContextMenu(ContextMenuEvent event, TreeItem<String> tableItem) {
        // 创建右键菜单
        ContextMenu contextMenu = new ContextMenu();
        MenuItem showStructureItem = new MenuItem("Show Structure");

        // 为菜单项添加点击事件
        showStructureItem.setOnAction(e -> showTableStructure(tableItem.getValue()));

        // 向右键菜单添加选项
        contextMenu.getItems().add(showStructureItem);

        // 显示菜单
        contextMenu.show(dbTreeView, event.getScreenX(), event.getScreenY());
    }



}
