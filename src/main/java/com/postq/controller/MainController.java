package com.postq.controller;

import com.postq.model.Database;
import com.postq.model.Item;
import com.postq.model.ItemType;
import com.postq.model.Table;
import com.postq.service.ConfigManager;
import com.postq.service.DatabaseManager;
import com.postq.util.FXs;
import com.postq.util.SQLs;
import com.postq.util.Strings;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainController {

    @FXML private SplitPane mainSplitPane;
    @FXML private TreeView<Item> dbTreeView;
    @FXML private TabPane sqlTabPane;
    @FXML private CodeArea defaultCodeArea;
    @FXML private TabPane resultTabPane;
    @FXML private Button addDbButton;
    @FXML private Button queryButton;
    @FXML private Button newTabButton;
    @FXML private Button formatButton;
    @FXML private Label statusLabel;

    private final Popup autoCompletePopup = new Popup();;
    private final ListView<String> suggestionList = new ListView<>();



    @FXML
    private void initialize() {
        initTreeView(dbTreeView);

        addDbButton.setOnAction(e -> onAddDatabase());
        queryButton.setOnAction(e -> onRunQuery());
        newTabButton.setOnAction(e -> onQueryTab());
        formatButton.setOnAction(e -> formatSQL());

        initCodeArea(defaultCodeArea);

        initPopup(autoCompletePopup);

        suggestionList.setOnKeyReleased(this::confirmSuggestion);


        FXs.initCode(mainSplitPane,this::onRunQuery,this::onShowTableStructure);

        Platform.runLater(() -> {
            mainSplitPane.setDividerPositions(0.2);
        });
    }


    // 组将初始化
    private void initCodeArea(CodeArea defaultCodeArea) {
        defaultCodeArea.textProperty()
                .addListener((obs, oldText, newText) ->
                        defaultCodeArea.setStyleSpans(0, computeHighlighting(newText)));
        defaultCodeArea.setOnKeyReleased(event -> Platform.runLater(() -> handleKeyReleased(event, defaultCodeArea)));
        defaultCodeArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                autoCompletePopup.hide();
            }
        });
        defaultCodeArea.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> autoCompletePopup.hide());
    }

    private void initTreeView(TreeView<Item> dbTreeView) {
        dbTreeView.setShowRoot(false);
        dbTreeView.setRoot(new TreeItem<>());
        dbTreeView.setOnMouseClicked(this::onTreeClick);
        dbTreeView.setOnContextMenuRequested(this::onTreeRightClick);

        List<Item> databases = ConfigManager.instance.getDatabases();
        dbTreeView.getRoot().getChildren().addAll(databases.stream().map(TreeItem::new).toList());
    }


    private void initPopup(Popup autoCompletePopup) {
        autoCompletePopup.getContent().add(suggestionList);
        autoCompletePopup.setAutoHide(true);
        autoCompletePopup.setHideOnEscape(true);
    }

    // -- 按钮action

    private void formatSQL() {
        Tab currentTab = sqlTabPane.getSelectionModel().getSelectedItem();
        if (currentTab != null && currentTab.getContent() instanceof CodeArea codeArea) {
            String sql = codeArea.getText();
            String newSQL = SQLs.formatSQL(sql);
            codeArea.replaceText(newSQL);
            codeArea.moveTo(newSQL.length());
            codeArea.requestFollowCaret();
        }
    }

    private void onShowTableStructure() {
        TreeItem<Item> selected = dbTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected == dbTreeView.getRoot()) {
            FXs.showAlert("No Database Selected", "Please select a database first.");
        }else if(selected.getValue().isTable()){
            showTableStructure(selected);
        }
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
        String title = "SQL "+ tabCount;
        CodeArea newCodeArea = new CodeArea();
        initCodeArea(newCodeArea);

        Tab tab = new Tab(title, newCodeArea);
        tab.setClosable(true);
        sqlTabPane.getTabs().add(tab);
        sqlTabPane.getSelectionModel().select(tab);
    }




    private void onAddDatabase() {
        Dialog<Connection> dialog = new Dialog<>();
        dialog.setTitle("Add Database");
        dialog.setHeaderText("Enter connection details");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField titleField = new TextField();
        TextField hostField = new TextField();
        TextField portField = new TextField("5432");
        TextField dbField = new TextField();
        TextField userField = new TextField();
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
        new ConfigManager().saveConfig(
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
            FXs.showAlert("No Database Selected", "Please select a database first.");
            return null;
        }
        TreeItem<Item> selectedDB = selected;
        if(selected.getValue().getItemType().equals(ItemType.TABLE)){
            selectedDB= selected.getParent();
        }
        return (Database) selectedDB.getValue();
    }

    private void onRunQuery() {
        Database database = getDatabase();

        Tab selectedTab = sqlTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || !(selectedTab.getContent() instanceof CodeArea codeArea)) {
            FXs.showAlert("错误", "未找到有效的 SQL 编辑区域");
            return;
        }
        int caretPosition = codeArea.getCaretPosition();

        String sql = codeArea.getSelectedText();
        if(Strings.isEmpty(sql)){
            sql = SQLs.getSQL(codeArea.getText(), caretPosition);
        }

        if(Strings.isEmpty(sql)){
            return;
        }

        queryAndDisplay(database, sql);

    }

    public void queryAndDisplay(Database database,String sql){
        long start = System.currentTimeMillis();
        TableView<List<String>> resultTable = DatabaseManager.INSTANCE.query(database, sql);
        resultTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        int count = resultTable.getItems().size();
        long end = System.currentTimeMillis();

        long duration = end - start;

        Label statusLabel = new Label("查询成功 | 耗时 " + duration + "ms | 查询数量：" + count + "条" );


        ContextMenu contextMenu = new ContextMenu();

        MenuItem copyAsSQLItem = new MenuItem("复制为 SQL 插入语句");
        copyAsSQLItem.setOnAction(e -> {
            SQLs.copySelectedRowsAsSQL(resultTable, "table_name_holder"); // 替换为你的表名
        });

        contextMenu.getItems().add(copyAsSQLItem);

        resultTable.setContextMenu(contextMenu);

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
            FXs.showAlert("连接错误", "无法获取数据库连接：" + database.getTitle());
            return;
        }

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

        List<List<String>> tableSchema = DatabaseManager.INSTANCE.getTableSchema(database, table.getTableName());
        columnTable.getItems().addAll(tableSchema);

        // ========== 索引 TableView ==========
        TableView<List<String>> indexTable = new TableView<>();
        TableColumn<List<String>, String> idxName = new TableColumn<>("索引名");
        idxName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(0)));

        TableColumn<List<String>, String> idxDef = new TableColumn<>("定义");
        idxDef.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(1)));

        indexTable.getColumns().addAll(idxName, idxDef);

        List<List<String>> indexData = DatabaseManager.INSTANCE.getIndex(database, table.getTableName());
        indexTable.getItems().addAll(indexData);

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
            FXs.showAlert("连接错误", "无法获取数据库连接：" + database.getTitle());
            return;
        }

        String sql = "SELECT * FROM " + table.getTableName() + " LIMIT 10;";
        queryAndDisplay(database,sql);
    }


    private static final String[] KEYWORDS = new String[]{
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE",
            "CREATE", "TABLE", "DROP", "ALTER", "INTO", "VALUES",
            "JOIN", "ON", "AS", "AND", "OR", "NOT", "NULL", "IS","LIMIT"
    };

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>\\b(" + String.join("|", KEYWORDS) + ")\\b)"
                    + "|(?<STRING>'[^']*')"
                    + "|(?<COMMENT>--[^\n]*)",
            Pattern.CASE_INSENSITIVE
    );
    

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



    private void handleKeyReleased(KeyEvent event, CodeArea codeArea) {
        if (event.getCode().isModifierKey() || event.getText().isEmpty()) {
            return;
        }
        String text = codeArea.getText();
        int caretPos = codeArea.getCaretPosition();

        String word = getCurrentWord(text, caretPos);

        Database database = getDatabase();
        if(Strings.isEmpty(word) || Objects.isNull(database)){
            return;
        }
        List<String> suggestions = null;
        if (isTable(text, word)) {
            suggestions = DatabaseManager.INSTANCE.getTableSuggestion(database, word);
        } else {
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
        while (start >= 0 && (Character.isLetterOrDigit(text.charAt(start)) || isSymbol(text.charAt(start))) ) {
            start--;
        }
        return text.substring(start + 1, caretPos);
    }

    public boolean isSymbol(char c) {
        String symbols = "!@#$%^&*()_+-=|{}[]:;\"'<>,.?/\\~`";
        return symbols.indexOf(c) >= 0;
    }

    public void confirmSuggestion(KeyEvent e){
        if (e.getCode() == KeyCode.ENTER) {
            String selected = suggestionList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                int pos = defaultCodeArea.getCaretPosition();
                String text = defaultCodeArea.getText();
                String prefix = getCurrentWord(text, pos);
                String before = text.substring(0, pos - prefix.length());
                String after = text.substring(pos);
                defaultCodeArea.replaceText(before + selected + after);
                defaultCodeArea.moveTo((before + selected).length());
            }
            autoCompletePopup.hide();
            e.consume();
        } else if (e.getCode() == KeyCode.ESCAPE) {
            autoCompletePopup.hide();
            e.consume();
        } else if (e.getCode() == KeyCode.DOWN) {
            suggestionList.getSelectionModel().selectNext();
            suggestionList.scrollTo(suggestionList.getSelectionModel().getSelectedIndex());
            e.consume();
        } else if (e.getCode() == KeyCode.UP) {
            suggestionList.getSelectionModel().selectPrevious();
            suggestionList.scrollTo(suggestionList.getSelectionModel().getSelectedIndex());
            e.consume();
        }
    }


    private void showAutoCompletePopup(CodeArea codeArea, List<String> suggestions, String prefix) {
        suggestionList.getItems().setAll(suggestions);
        suggestionList.getSelectionModel().selectFirst();

        Platform.runLater(() -> {
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();
            int textLength = text.length();

            if (textLength > 0 && ' ' == text.charAt(textLength - 1)) {
                return;
            }

            if (caretPosition > 0 && caretPosition <= textLength) {
                // 获取光标位置的屏幕坐标
                Optional<Bounds> maybeBounds = codeArea.getCharacterBoundsOnScreen(caretPosition - prefix.length(), caretPosition);

                if (maybeBounds.isPresent()) {
                    Bounds screenBounds = maybeBounds.get();
                    double x = screenBounds.getMinX();
                    double y = screenBounds.getMaxY();

                    // 设置并显示弹窗
                    autoCompletePopup.getContent().setAll(suggestionList);
                    autoCompletePopup.show(codeArea.getScene().getWindow(), x, y);
                    suggestionList.requestFocus();
                } else {
                    System.out.println("⚠️ 获取光标位置失败，弹窗未显示");
                }
            }
        });
    }

}
