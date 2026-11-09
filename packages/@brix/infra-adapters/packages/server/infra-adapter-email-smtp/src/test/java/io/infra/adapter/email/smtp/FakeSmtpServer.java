package io.infra.adapter.email.smtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scripted SMTP server on the loopback address for the adapter's tests, one thread per connection.
 */
final class FakeSmtpServer implements AutoCloseable {

    /** How the server answers. */
    enum Behaviour {
        /** Answers every command at once and accepts the message. */
        NORMAL,
        /** Accepts the connection and never says anything. */
        SILENT,
        /** Answers everything, the greeting included, after the delay. */
        SLOW,
        /** Refuses the recipient with 550, quoting the address as real servers do. */
        REJECT_RECIPIENT,
        /** Accepts the message at once, then answers QUIT only after the delay. */
        SLOW_QUIT
    }

    private final ServerSocket server;
    private final Behaviour behaviour;
    private final Duration delay;
    private final List<String> accepted = new CopyOnWriteArrayList<>();
    private final List<Socket> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicInteger endsOfData = new AtomicInteger();

    private FakeSmtpServer(Behaviour behaviour, Duration delay) throws IOException {
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        this.behaviour = behaviour;
        this.delay = delay;
        Thread acceptor = new Thread(this::accept, "fake-smtp-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    static FakeSmtpServer start(Behaviour behaviour) throws IOException {
        return new FakeSmtpServer(behaviour, Duration.ZERO);
    }

    static FakeSmtpServer start(Behaviour behaviour, Duration delay) throws IOException {
        return new FakeSmtpServer(behaviour, delay);
    }

    int port() {
        return server.getLocalPort();
    }

    /** The messages the server acknowledged, headers and body as sent. */
    List<String> accepted() {
        return List.copyOf(accepted);
    }

    /** How many connections the server took. */
    int connections() {
        return connections.get();
    }

    /** How many times a client finished sending a message's data, acknowledged or not. */
    int endsOfData() {
        return endsOfData.get();
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Socket client : clients) {
            client.close();
        }
    }

    private void accept() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                clients.add(client);
                connections.incrementAndGet();
                Thread session = new Thread(() -> serve(client), "fake-smtp-session");
                session.setDaemon(true);
                session.start();
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void serve(Socket client) {
        try (client) {
            if (behaviour == Behaviour.SILENT) {
                client.getInputStream().read();
                return;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(),
                    StandardCharsets.ISO_8859_1));
            OutputStream out = client.getOutputStream();
            reply(out, "220 fake ESMTP ready", false);
            String line;
            while ((line = in.readLine()) != null) {
                String command = line.length() >= 4 ? line.substring(0, 4).toUpperCase() : line.toUpperCase();
                switch (command) {
                    case "EHLO", "HELO" -> reply(out, "250-fake\r\n250 OK", false);
                    case "MAIL", "RSET", "NOOP" -> reply(out, "250 OK", false);
                    case "RCPT" -> reply(out, behaviour == Behaviour.REJECT_RECIPIENT
                            ? "550 5.1.1 " + line.substring(line.indexOf('<')) + ": Recipient address rejected"
                            : "250 OK", false);
                    case "DATA" -> {
                        reply(out, "354 End data with <CR><LF>.<CR><LF>", false);
                        StringBuilder data = new StringBuilder();
                        String dataLine;
                        while ((dataLine = in.readLine()) != null && !".".equals(dataLine)) {
                            data.append(dataLine).append('\n');
                        }
                        if (dataLine == null) {
                            return;
                        }
                        endsOfData.incrementAndGet();
                        reply(out, "250 OK queued", false);
                        accepted.add(data.toString());
                    }
                    case "QUIT" -> {
                        reply(out, "221 bye", behaviour == Behaviour.SLOW_QUIT);
                        return;
                    }
                    default -> reply(out, "502 command not implemented", false);
                }
            }
        } catch (IOException | InterruptedException closed) {
            // The client went away or the test closed the server.
        }
    }

    private void reply(OutputStream out, String text, boolean slowQuit) throws IOException, InterruptedException {
        if (behaviour == Behaviour.SLOW || slowQuit) {
            Thread.sleep(delay.toMillis());
        }
        out.write((text + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }
}
