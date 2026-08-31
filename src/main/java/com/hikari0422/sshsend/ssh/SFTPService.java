package com.hikari0422.sshsend.ssh;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.RemoteFile;

public class SFTPService implements AutoCloseable {
    private final SFTPClient sftp;

    public SFTPService(SSHConnect ssh) throws IOException {
        if (!ssh.isConnected()) {
            throw new IllegalStateException("SSH connection is not established.");
        }
        sftp = ssh.getConnect().newSFTPClient();
    }

    public SFTPClient getClient() {
        return sftp;
    }

    public void upload(Path localFile, String remotePath) throws IOException {
        sftp.put(localFile.toString(), remotePath);
    }

    public String canonicalize(String remotePath) throws IOException {
        return sftp.canonicalize(remotePath);
    }

    public List<RemoteResourceInfo> list(String remoteDirectory) throws IOException {
        return sftp.ls(remoteDirectory).stream()
                .filter(item -> !item.getName().equals(".") && !item.getName().equals(".."))
                .sorted(Comparator.comparing(RemoteResourceInfo::isDirectory).reversed()
                        .thenComparing(RemoteResourceInfo::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public void download(String remoteFile, Path localFile) throws IOException {
        sftp.get(remoteFile, localFile.toString());
    }

    public byte[] readPreview(String remotePath, int maximumBytes) throws IOException {
        try (RemoteFile file = sftp.open(remotePath)) {
            int length = (int) Math.min(file.length(), maximumBytes);
            byte[] result = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = file.read(offset, result, offset, length - offset);
                if (read <= 0) break;
                offset += read;
            }
            return offset == length ? result : java.util.Arrays.copyOf(result, offset);
        }
    }

    @Override
    public void close() throws IOException {
        sftp.close();
    }
}
