package com.hikari0422.sshsend.ssh;

import java.io.IOException;
import java.util.List;

import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

public class SFTPService {
    private SFTPClient sftp;

    public SFTPService(SSHConnect ssh) throws IOException {
        sftp = ssh.getConnect().newSFTPClient();
    }

    
}
