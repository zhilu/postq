package com.postq.util;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.input.*;

import java.util.List;
import java.util.Objects;


public class FXs {

    public static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }



    public static void initCode(SplitPane pane, Runnable ... acitons) {
        Platform.runLater(() -> {
            pane.setDividerPositions(0.2);

            Scene scene = pane.getScene();
            if (scene != null) {
                // F9 -> 运行查询
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F9),
                        acitons[0]
                );

                // Alt + S -> 显示表结构
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN),
                        acitons[1]
                );

            } else {
                // 界面尚未加载时监听 scene 变化
                pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.getAccelerators().put(
                                new KeyCodeCombination(KeyCode.F9),
                                acitons[0]
                        );
                        newScene.getAccelerators().put(
                                new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN),
                                acitons[1]
                        );
                    }
                });
            }
        });
    }


    public static void toClipBoard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    public static TableColumn<List<String>,String> tableColumn(String name,int index) {
        TableColumn<List<String>, String> tableColumn = new TableColumn<>(name);
        tableColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(index)));
        return tableColumn;
    }
}
