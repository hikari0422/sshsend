package com.hikari0422.sshsend.ssh;


import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.IOException;

public class SSHConnect {

    private SSHClient ssh;

    public void connect(String host, int port, String username, String passwd) throws IOException {
        ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(host, port);
        ssh.authPassword(username, passwd);

        System.out.println("SSH connected");

        // try (SFTPClient sftp = ssh.newSFTPClient()) {
        //     for (RemoteResourceInfo file : sftp.ls(".")) {
        //         System.out.println(
        //                 file.getName()
        //                 + "|" + (file.isDirectory() ? "資料夾" : "檔案")
        //                 + "| 大小" + file.getAttributes().getSize()
        //         );
        //     }
        // }
    }

    public SSHClient getConnect() {
        return ssh;
    }

    public void disconnect() throws IOException {
        if (ssh != null) {
            ssh.disconnect();
            ssh.close();
        }
    }
}

