module org.kyj.fx.wsb.dashboard {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    opens org.kyj.fx.wbs.dashboard to javafx.fxml;
    exports org.kyj.fx.wbs.dashboard;
}