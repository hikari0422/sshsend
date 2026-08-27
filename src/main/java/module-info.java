module com.hikari0422.sshsend {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.hierynomus.sshj;


    opens com.hikari0422.sshsend to javafx.fxml;
    exports com.hikari0422.sshsend;
    exports com.hikari0422.sshsend.ssh;
    opens com.hikari0422.sshsend.ssh to javafx.fxml;
}