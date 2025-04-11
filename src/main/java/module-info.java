module com.postq {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires static lombok;


    opens com.postq to javafx.fxml;
    exports com.postq;
}