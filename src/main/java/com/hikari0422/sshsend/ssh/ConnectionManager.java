package com.hikari0422.sshsend.ssh;

import java.io.IOException;

public class ConnectionManager implements AutoCloseable {
    private SSHConnect connection;

    public synchronized void replace(SSHConnect newConnection) throws IOException {
        SSHConnect oldConnection = connection;
        connection = newConnection;
        if (oldConnection != null) {
            oldConnection.close();
        }
    }

    public synchronized SSHConnect requireConnection() {
        if (connection == null || !connection.isConnected()) {
            throw new IllegalStateException("Connect to an SSH server first.");
        }
        return connection;
    }

    public synchronized boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    @Override
    public synchronized void close() throws IOException {
        SSHConnect currentConnection = connection;
        connection = null;
        if (currentConnection != null) {
            currentConnection.close();
        }
    }
}
