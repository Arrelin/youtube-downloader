module com.arrelin.youtubedownloader {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires java.youtube.downloader;
    requires com.sun.jna;
    requires java.sql;
    requires fastjson;
    requires org.json;

    opens com.arrelin.youtubedownloader to javafx.fxml;
    opens com.arrelin.youtubedownloader.helpers to com.sun.jna;

    exports com.arrelin.youtubedownloader;
}