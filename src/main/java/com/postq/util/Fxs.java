package com.postq.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class Fxs {

    public static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
