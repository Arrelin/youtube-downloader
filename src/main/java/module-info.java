module com.arrelin.youtubedownloader {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;

    opens com.arrelin.youtubedownloader to javafx.fxml;
    exports com.arrelin.youtubedownloader;
}