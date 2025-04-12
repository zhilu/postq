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
    opens com.postq.util to javafx.fxml;
}