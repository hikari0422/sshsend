package com.hikari0422.sshsend.ssh;

import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.PublicKey;

/**
 * Accepts an unknown host key once and persists it in OpenSSH's known_hosts
 * file. A changed key is still rejected by {@link OpenSSHKnownHosts}.
 */
final class TrustOnFirstUseKnownHosts extends OpenSSHKnownHosts {
    TrustOnFirstUseKnownHosts(File knownHostsFile) throws IOException {
        super(knownHostsFile);
    }

    @Override
    protected synchronized boolean hostKeyUnverifiableAction(String hostname, PublicKey key) {
        KeyType keyType = KeyType.fromKey(key);
        if (keyType == KeyType.UNKNOWN) {
            return false;
        }

        try {
            File knownHostsFile = getFile();
            File parent = knownHostsFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            OpenSSHKnownHosts.HostEntry entry = new HostEntry(null, hostname, keyType, key);
            write(entry);
            entries().add(entry);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the SSH host key", e);
        }
    }
}
