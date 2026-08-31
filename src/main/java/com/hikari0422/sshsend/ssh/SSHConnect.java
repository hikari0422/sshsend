package com.hikari0422.sshsend.ssh;


import net.schmizz.sshj.SSHClient;

import java.io.File;
import java.io.IOException;

public class SSHConnect implements AutoCloseable {

    private SSHClient ssh;

    public void connect(String host, int port, String username, String passwd) throws IOException {
        close();
        SSHClient client = new SSHClient();
        try {
            File knownHosts = new File(new File(System.getProperty("user.home"), ".ssh"), "known_hosts");
            client.addHostKeyVerifier(new TrustOnFirstUseKnownHosts(knownHosts));
            client.connect(host, port);
            client.authPassword(username, passwd);
            ssh = client;
        } catch (IOException | RuntimeException e) {
            try {
                client.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    public SSHClient getConnect() {
        return ssh;
    }

    public boolean isConnected() {
        return ssh != null && ssh.isConnected() && ssh.isAuthenticated();
    }

    @Override
    public void close() throws IOException {
        SSHClient client = ssh;
        ssh = null;
        if (client != null) {
            client.close();
        }
    }
}
