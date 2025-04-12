package com.postq;

import com.postq.model.Database;
import com.postq.model.Item;
import com.postq.model.ItemType;
import com.postq.model.Table;
import com.postq.util.Fxs;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PostQController {

    @FXML private SplitPane mainSplitPane;
    @FXML private TreeView<Item> dbTreeView;
    @FXML private TabPane sqlTabPane;
    @FXML private CodeArea defaultCodeArea;
    @FXML private TabPane resultTabPane;
    @FXML private Button addDbButton;
    @FXML private Button queryButton;
    @FXML private Button newTabButton;
    @FXML private Label statusLabel;



    @FXML
    private void initialize() {
        dbTreeView.setShowRoot(false);
        dbTreeView.setRoot(new TreeItem<>());

        DatabaseConfigManager configManager = new DatabaseConfigManager();
        Properties properties = configManager.loadConfig();

        if (!properties.isEmpty()) {
            String[] dbTitles = configManager.getAllDatabaseTitles();
            for (String dbTitle : dbTitles) {
                TreeItem<Item> item = new TreeItem<>(configManager.getDatabase(dbTitle));
                dbTreeView.getRoot().getChildren().add(item);
            }
        }

        dbTreeView.setOnMouseClicked(this::onTreeClick);
        dbTreeView.setOnContextMenuRequested(this::onTreeRightClick);

        addDbButton.setOnAction(e -> onAddDatabase());
        queryButton.setOnAction(e -> onRunQuery());
        newTabButton.setOnAction(e -> onQueryTab());

        defaultCodeArea.textProperty().addListener((obs, oldText, newText) ->
                defaultCodeArea.setStyleSpans(0, computeHighlighting(newText)));
        defaultCodeArea.setOnKeyPressed(event -> Platform.runLater(() -> handleKeyReleased(event, defaultCodeArea)));
        defaultCodeArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                autoCompletePopup.hide();
            }
        });
        defaultCodeArea.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> autoCompletePopup.hide());

        Platform.runLater(() -> {
            mainSplitPane.setDividerPositions(0.2);
        });

        mainSplitPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            mainSplitPane.setDividerPositions(0.2);
        });
    }

    private void onTreeClick(MouseEvent event){
        if (event.getClickCount() == 2) {
            TreeItem<Item> selected = dbTreeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Item item = selected.getValue();
                if (item.getItemType() == ItemType.DB && selected.getChildren().isEmpty()) {
                    List<TreeItem<Item>> tables = DatabaseManager.INSTANCE.loadTables((Database) selected.getValue());
                    selected.getChildren().clear();
                    selected.getChildren().addAll(tables);
                } else if (item.getItemType() == ItemType.TABLE) {
                    Table table = (Table) item;
                    Tab currentTab = sqlTabPane.getSelectionModel().getSelectedItem();
                    if (currentTab != null && currentTab.getContent() instanceof CodeArea codeArea) {
                        String currentText = codeArea.getText();
                        String newText = currentText + (currentText.isEmpty() ? "" : "\n") + "SELECT * FROM " + table.getTableName() + ";\n";
                        codeArea.replaceText(newText);
                        codeArea.moveTo(newText.length());
                        codeArea.requestFollowCaret();
                    }
                }
            }
        }
    }
    private void onTreeRightClick(ContextMenuEvent event){
        TreeItem<Item> selectedItem = dbTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getParent() != dbTreeView.getRoot()) {
            if(ItemType.TABLE.equals(selectedItem.getValue().getItemType())){
                showContextMenu(event, selectedItem);
            }
        }
    }

    private void onQueryTab(){
        int tabCount = sqlTabPane.getTabs().size() + 1;
        Tab newTab = createNewSqlTab("SQL " + tabCount);
        sqlTabPane.getTabs().add(newTab);
        sqlTabPane.getSelectionModel().select(newTab);
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
        new DatabaseConfigManager().saveConfig(
                titleField.getText(),
                hostField.getText(),
                portField.getText(),
                userField.getText(),
                passField.getText(),
                dbField.getText()
        );

        dialog.setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    Connection conn = DatabaseManager.INSTANCE.getConnection(database);
                    TreeItem<Item> item = new TreeItem<>(database);
                    dbTreeView.getRoot().getChildren().add(item);
                    return conn;
            }
            return null;
        });

        dialog.showAndWait();
    }

    private Database getDatabase(){
        TreeItem<Item> selected = dbTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected == dbTreeView.getRoot()) {
            Fxs.showAlert("No Database Selected", "Please select a database first.");
        }
        TreeItem<Item> selectedDB = selected;
        if(selected.getValue().getItemType().equals(ItemType.TABLE)){
            selectedDB= selected.getParent();
        }
        return (Database) selectedDB.getValue();
    }

    private void onRunQuery() {
        TreeItem<Item> selected = dbTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected == dbTreeView.getRoot()) {
            Fxs.showAlert("No Database Selected", "Please select a database first.");
            return;
        }

        TreeItem<Item> selectedDB = selected;
        if(selected.getValue().getItemType().equals(ItemType.TABLE)){
            selectedDB= selected.getParent();
        }

        String dbName = ((Database)selectedDB.getValue()).getTitle();
        Connection connection = DatabaseManager.INSTANCE.getConnection(dbName);

        if (connection == null) {
            Fxs.showAlert("Connection Error", "Database not connected.");
            return;
        }

        Tab selectedTab = sqlTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || !(selectedTab.getContent() instanceof CodeArea codeArea)) {
            Fxs.showAlert("错误", "未找到有效的 SQL 编辑区域");
            return;
        }
        String sql = codeArea.getText();

        TableView<List<String>> resultTable = new TableView<>();

        int count = 0;
        long start = System.currentTimeMillis();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            resultTable.getItems().clear();
            resultTable.getColumns().clear();

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            for (int i = 1; i <= cols; i++) {
                final int colIdx = i;
                TableColumn<List<String>, String> column = new TableColumn<>(meta.getColumnName(i));
                column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(colIdx - 1)));
                resultTable.getColumns().add(column);
            }


            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= cols; i++) {
                    row.add(rs.getString(i));
                }
                count++;
                resultTable.getItems().add(row);
            }

        } catch (SQLException e) {
            Fxs.showAlert("Query Failed", e.getMessage());
        }
        long end = System.currentTimeMillis();

        long duration = end - start;

        Label statusLabel = new Label("查询成功 | 耗时 " + duration + "ms | 查询数量：" + count + "条" );

        HBox statusBar = new HBox(statusLabel);
        statusBar.setMinHeight(24);
        statusBar.setMaxHeight(24);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 4 8;");

        VBox resultBox = new VBox(resultTable, statusBar);
        resultBox.setSpacing(5);
        VBox.setVgrow(statusBar, Priority.NEVER);

        Tab resultTab = new Tab("Result " + (resultTabPane.getTabs().size() + 1));
        resultTab.setContent(resultBox);
        resultTab.setClosable(true);

        // 添加并选中这个结果页
        resultTabPane.getTabs().add(resultTab);
        resultTabPane.getSelectionModel().select(resultTab);
    }





    private void showTableStructure(TreeItem<Item> tableItem) {
        Table table = (Table) tableItem.getValue();
        Database database = (Database) tableItem.getParent().getValue();
        Connection conn = DatabaseManager.INSTANCE.getConnection(database.getTitle());

        if (conn == null) {
            Fxs.showAlert("连接错误", "无法获取数据库连接：" + database.getTitle());
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

            // 设置Dialog的最小宽度和最小高度来控制显示大小
            dialog.getDialogPane().setMinWidth(960);  // 设置最小宽度
            dialog.getDialogPane().setMinHeight(600);



            // 使用 showAndWait() 确保对话框阻塞线程并正常响应关闭
            dialog.showAndWait();

        } catch (SQLException e) {
            Fxs.showAlert("错误", "获取表结构失败: " + e.getMessage());
        }
    }




    private void showContextMenu(ContextMenuEvent event, TreeItem<Item> tableItem) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem showStructureItem = new MenuItem("Show Structure");

        // 为菜单项添加点击事件
        showStructureItem.setOnAction(e -> showTableStructure(tableItem));

        MenuItem viewSampleItem = new MenuItem("查看样例");
        viewSampleItem.setOnAction(e -> showTableSample(tableItem));
        // 向右键菜单添加选项
        contextMenu.getItems().addAll(showStructureItem,viewSampleItem);

        // 显示菜单
        contextMenu.show(dbTreeView, event.getScreenX(), event.getScreenY());
    }

    private void showTableSample(TreeItem<Item> tableItem) {
        Table table = (Table) tableItem.getValue();
        Database database = (Database) tableItem.getParent().getValue();
        Connection conn = DatabaseManager.INSTANCE.getConnection(database.getTitle());

        if (conn == null) {
            Fxs.showAlert("连接错误", "无法获取数据库连接：" + database.getTitle());
            return;
        }

        // 构造查询 SQL 语句，查询表的前 10 行数据
        String sql = "SELECT * FROM " + table.getTableName() + " LIMIT 10;";

        // 将查询的 SQL 语句设置到 SQL 编辑器
        Tab currentTab = sqlTabPane.getSelectionModel().getSelectedItem();
        if (currentTab != null && currentTab.getContent() instanceof CodeArea codeArea) {
            codeArea.replaceText(sql);
        }

        TableView<List<String>> resultTable = new TableView<>();


        long start = System.currentTimeMillis();
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
            Fxs.showAlert("查询失败", "无法执行查询：" + e.getMessage());
        }

        long end = System.currentTimeMillis();

        long duration = end - start;

        Label statusLabel = new Label("查询成功 | 耗时 " + duration + "ms");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setMinHeight(24);
        statusBar.setMaxHeight(24);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 4 8;");

        VBox resultBox = new VBox(resultTable, statusBar);
        resultBox.setSpacing(5);
        VBox.setVgrow(statusBar, Priority.NEVER);

        Tab resultTab = new Tab("Result " + (resultTabPane.getTabs().size() + 1));
        resultTab.setContent(resultBox);
        resultTab.setClosable(true);

        // 添加并选中这个结果页
        resultTabPane.getTabs().add(resultTab);
        resultTabPane.getSelectionModel().select(resultTab);
    }


    private static final String[] KEYWORDS = new String[]{
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE",
            "CREATE", "TABLE", "DROP", "ALTER", "INTO", "VALUES",
            "JOIN", "ON", "AS", "AND", "OR", "NOT", "NULL", "IS"
    };

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>\\b(" + String.join("|", KEYWORDS) + ")\\b)"
                    + "|(?<STRING>'[^']*')"
                    + "|(?<COMMENT>--[^\n]*)",
            Pattern.CASE_INSENSITIVE
    );
    
    private Tab createNewSqlTab(String title) {
        CodeArea sqlEditor = new CodeArea();
        sqlEditor.setWrapText(true);
        sqlEditor.textProperty().addListener((obs, oldText, newText) ->
                sqlEditor.setStyleSpans(0, computeHighlighting(newText)));

        sqlEditor.setOnKeyReleased(event -> Platform.runLater(() -> handleKeyReleased(event, sqlEditor)));
        sqlEditor.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                autoCompletePopup.hide();
            }
        });
        sqlEditor.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> autoCompletePopup.hide());

        Tab tab = new Tab(title, sqlEditor);
        tab.setClosable(true);

        return tab;
    }

    private StyleSpans<? extends Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("STRING") != null ? "string" :
                                    matcher.group("COMMENT") != null ? "comment" :
                                            null;
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private ContextMenu autoCompletePopup = new ContextMenu();

    private void handleKeyReleased(KeyEvent event, CodeArea codeArea) {
        String text = codeArea.getText();
        int caretPos = codeArea.getCaretPosition();


        // 获取当前词
        String word = getCurrentWord(text, caretPos);

        Database database = getDatabase();
        List<String> suggestions = null;
        if(isTable(text,word)){
            suggestions = DatabaseManager.INSTANCE.getTableSuggestion(database, word);
        }else {
            suggestions = DatabaseManager.INSTANCE.getFieldSuggestion(database, word);
        }

        if (!suggestions.isEmpty()) {
            showAutoCompletePopup(codeArea, suggestions, word);
        }else {
            autoCompletePopup.hide();
        }
    }

    private boolean isTable(String text, String word) {
        return true;
    }

    private String getCurrentWord(String text, int caretPos) {
        int start = caretPos - 1;
        while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) {
            start--;
        }
        return text.substring(start + 1, caretPos);
    }

    private List<String> getSuggestions(String word, Map<String, List<String>> tableFieldMap) {
        List<String> suggest = new ArrayList<>(); //
        if(word.length() == 0){
            return suggest;
        }

        tableFieldMap.values().forEach(suggest::addAll);                // 字段名
        return suggest.stream()
                .filter(name -> name.toLowerCase().startsWith(word.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void showAutoCompletePopup(CodeArea codeArea, List<String> suggestions, String prefix) {
        autoCompletePopup.getItems().clear();
        for (String suggestion : suggestions) {
            MenuItem item = new MenuItem(suggestion);
            item.setOnAction(e -> {
                int pos = codeArea.getCaretPosition();
                String text = codeArea.getText();
                String before = text.substring(0, pos - prefix.length());
                String after = text.substring(pos);
                codeArea.replaceText(before + suggestion + after);
                codeArea.moveTo((before + suggestion).length());
            });
            autoCompletePopup.getItems().add(item);
        }

        // 显示在 caret 附近
        autoCompletePopup.show(codeArea, codeArea.localToScreen(codeArea.getCaretBounds().get()).getMinX(),
                codeArea.localToScreen(codeArea.getCaretBounds().get()).getMaxY());
    }

}
