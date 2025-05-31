module org.kyj.fx.wsb.dashboard {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
	requires java.sql;
	requires java.desktop;
	requires javafx.swing;
    opens org.kyj.fx.wbs.dashboard to javafx.fxml;
    exports org.kyj.fx.wbs.dashboard;
}