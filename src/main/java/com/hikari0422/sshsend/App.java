package com.hikari0422.sshsend;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

import com.hikari0422.sshsend.ssh.ConnectionManager;

public class App extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("main.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 820, 680);
        Controller controller = fxmlLoader.getController();
        ConnectionManager connectionManager = new ConnectionManager();
        controller.setConnectionManager(connectionManager);
        stage.setTitle("SSHSend");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            controller.close();
            try {
                connectionManager.close();
            } catch (IOException e) {
                System.err.println("Failed to close SSH connection: " + e.getMessage());
            }
        });
        stage.show();
    }
}
