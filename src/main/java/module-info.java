module com.postq {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens com.postq to javafx.fxml;
    exports com.postq;
}