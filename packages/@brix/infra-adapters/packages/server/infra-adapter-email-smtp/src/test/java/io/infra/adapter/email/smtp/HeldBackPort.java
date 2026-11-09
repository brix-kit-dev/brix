package io.infra.adapter.email.smtp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * A loopback port whose accept queue is full and that never accepts, so that a further connect to it waits. Linux
 * holds such a connect back; a platform that refuses it instead reports {@link #holdsBack()} false.
 */
final class HeldBackPort implements AutoCloseable {

    private final ServerSocket full;
    private final List<Socket> queued = new ArrayList<>();
    private boolean holdsBack;

    private HeldBackPort() throws IOException {
        full = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    static HeldBackPort open() throws IOException {
        HeldBackPort port = new HeldBackPort();
        try {
            for (int i = 0; i < 16 && !port.holdsBack; i++) {
                Socket socket = new Socket();
                port.queued.add(socket);
                try {
                    socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port.port()), 200);
                } catch (SocketTimeoutException e) {
                    port.holdsBack = true;
                }
            }
            return port;
        } catch (IOException | RuntimeException e) {
            port.close();
            throw e;
        }
    }

    /** Whether a connect to this port now waits. */
    boolean holdsBack() {
        return holdsBack;
    }

    int port() {
        return full.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        for (Socket socket : queued) {
            socket.close();
        }
        full.close();
    }
}
