package com.postq.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.Objects;


public class FXs {

    public static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }

    public static void bind(SplitPane pane, KeyCodeCombination code, Runnable object) {
        Platform.runLater(() -> {
            Scene scene = pane.getScene();
            if(Objects.isNull(scene)){
                scene.getAccelerators().put(code, object);
            }else {
                pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.getAccelerators().put(code, object);
                    }
                });
            }
        });
    }
}
