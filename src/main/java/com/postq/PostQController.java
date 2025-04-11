package com.postq;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
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

        TextField hostField = new TextField("localhost");
        TextField portField = new TextField("5432");
        TextField dbField = new TextField();
        TextField userField = new TextField();
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
}
