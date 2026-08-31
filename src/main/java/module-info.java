module com.hikari0422.sshsend {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.hierynomus.sshj;
    requires java.prefs;


    opens com.hikari0422.sshsend to javafx.fxml;
    exports com.hikari0422.sshsend;
    exports com.hikari0422.sshsend.ssh;
}
