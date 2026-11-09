package io.infra.adapter.email.smtp;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One delivery's exchange with the SMTP server: the sockets the delivery's thread opened, and whether its time ran
 * out (notification decision D104, standard-link point 14a).
 *
 * <p>When the time is up the exchange closes those sockets. That ends a connect, read or write blocked on them at
 * once, which no socket timeout does for the delivery as a whole, and the server never sees the end of a message
 * it has not yet acknowledged. A socket opened after that moment is closed as soon as it is made.</p>
 *
 * @author Brix Platform Team
 * @since 3.3.0
 */
final class SmtpExchange implements AutoCloseable {

    private static final ThreadLocal<SmtpExchange> CURRENT = new ThreadLocal<>();

    private final List<Socket> sockets = new ArrayList<>();
    private boolean expired;
    private boolean closed;
    private ScheduledFuture<?> timer;

    private SmtpExchange() {
    }

    /**
     * Opens the exchange of the current thread.
     *
     * @param deadlines runs the end of the exchange
     * @param timeLeft how long the exchange may last; positive
     * @return the exchange, to close when the delivery ends
     */
    static SmtpExchange open(ScheduledExecutorService deadlines, Duration timeLeft) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("an SMTP exchange is already open on this thread");
        }
        SmtpExchange exchange = new SmtpExchange();
        exchange.timer = deadlines.schedule(exchange::expire, timeLeft.toNanos(), TimeUnit.NANOSECONDS);
        CURRENT.set(exchange);
        return exchange;
    }

    /**
     * Hands a socket made on the current thread to its exchange. A socket made outside any delivery, such as the
     * mail health check's, is left alone: its socket timeouts bound it.
     *
     * @param socket the new socket
     * @return the socket
     */
    static Socket track(Socket socket) {
        SmtpExchange exchange = CURRENT.get();
        if (exchange != null) {
            exchange.add(socket);
        }
        return socket;
    }

    /**
     * Returns whether the exchange's time ran out.
     *
     * @return {@code true} once the exchange has closed its sockets for that reason
     */
    synchronized boolean expired() {
        return expired;
    }

    @Override
    public void close() {
        timer.cancel(false);
        synchronized (this) {
            closed = true;
        }
        CURRENT.remove();
    }

    private synchronized void add(Socket socket) {
        if (expired) {
            closeQuietly(socket);
        } else {
            sockets.add(socket);
        }
    }

    private synchronized void expire() {
        if (closed) {
            return;
        }
        expired = true;
        sockets.forEach(SmtpExchange::closeQuietly);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // The socket is unusable either way; the blocked call on it reports the failure.
        }
    }
}
