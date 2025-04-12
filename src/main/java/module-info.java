module com.postq {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.fxmisc.richtext;
    requires static lombok;


    opens com.postq to javafx.fxml;
    exports com.postq;
    exports com.postq.model;
    exports com.postq.util;
    exports com.postq.service;
    exports com.postq.controller;
    opens com.postq.util to javafx.fxml;
    opens com.postq.model to javafx.fxml;
    opens com.postq.service to javafx.fxml;
    opens com.postq.controller to javafx.fxml;
}