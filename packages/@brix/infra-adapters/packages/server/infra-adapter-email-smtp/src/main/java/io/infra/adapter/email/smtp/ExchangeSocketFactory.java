package io.infra.adapter.email.smtp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.SocketFactory;

/**
 * Makes the SMTP adapter's sockets, and hands each to the delivery running on the thread that makes it, so the
 * delivery can close it when its time is up ({@link SmtpExchange}).
 *
 * <p>JavaMail asks for an unconnected socket and connects it itself, under the connect timeout. TLS, STARTTLS
 * or implicit, is layered over this socket, so closing it ends the TLS connection too.</p>
 *
 * @author Brix Platform Team
 * @since 3.3.0
 */
final class ExchangeSocketFactory extends SocketFactory {

    @Override
    public Socket createSocket() {
        return SmtpExchange.track(new Socket());
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return connected(new InetSocketAddress(host, port), null);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return connected(new InetSocketAddress(host, port), new InetSocketAddress(localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return connected(new InetSocketAddress(host, port), null);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return connected(new InetSocketAddress(address, port), new InetSocketAddress(localAddress, localPort));
    }

    private Socket connected(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = createSocket();
        try {
            if (local != null) {
                socket.bind(local);
            }
            socket.connect(remote);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }
}
