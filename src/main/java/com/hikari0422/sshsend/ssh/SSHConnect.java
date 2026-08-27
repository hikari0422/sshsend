package com.hikari0422.sshsend.ssh;


import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResource;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.IOException;

public class SSHConnect {

    public void connect(String host, int port, String username, String passwd) throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(host, port);
        ssh.authPassword(username, passwd);
        System.out.println("connect.");

        try (SFTPClient sftp = ssh.newSFTPClient()) {
            for (RemoteResourceInfo file : sftp.ls(".")) {
                System.out.println(
                        file.getName()
                        + "|" + (file.isDirectory() ? "資料夾" : "檔案")
                        + "| 大小" + file.getAttributes().getSize()
                );
            }
        }
    }

    public static void main(String[] args) throws IOException {
    }
}

