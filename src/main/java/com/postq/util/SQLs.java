package com.postq.util;

import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SQLs {

    public static String getSQL(String text, int pos){
        int start = pos - 1;
        while (start >= 0 && text.charAt(start) != ';') {
            start--;
        }

        int end = pos > 0 ? pos - 1 : 0;
        while (end < text.length() && text.charAt(end) != ';') {
            end++;
        }

        if(end >= text.length()){
            end--;
        }

        String sql = text.substring(start + 1, end + 1);
        return sql.trim();
    }

    public static void copySelectedRowsAsSQL(TableView<List<String>> tableView, String tableName) {
        ObservableList<List<String>> selectedRows = tableView.getSelectionModel().getSelectedItems();
        if (selectedRows == null || selectedRows.isEmpty()) {
            return;
        }

        // 获取列名（TableColumn 的标题）
        List<String> columnNames = tableView.getColumns().stream()
                .map(column -> column.getText())
                .collect(Collectors.toList());

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (")
                .append(String.join(", ", columnNames)).append(") VALUES ");

        List<String> rowSQLs = new ArrayList<>();

        for (List<String> row : selectedRows) {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < columnNames.size(); i++) {
                String value = i < row.size() ? row.get(i) : null;
                if (value == null) {
                    values.add("NULL");
                } else {
                    String str = value.toString().replace("'", "''");
                    values.add("'" + str + "'");
                }
            }
            rowSQLs.add("(" + String.join(", ", values) + ")");
        }

        sql.append(String.join(", ", rowSQLs)).append(";");

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(sql.toString());
        clipboard.setContent(content);
    }

    public static String formatSQL(String sql) {
        return sql;
    }

}
