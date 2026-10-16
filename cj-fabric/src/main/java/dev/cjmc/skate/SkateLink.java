package dev.cjmc.skate;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The UDP link to the Skate engine in host mode: datagrams out, and the newest pose that comes back (read by a thread of its own). */
final class SkateLink implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");

    private final DatagramSocket socket;
    private final InetSocketAddress engine;
    private volatile SkateProtocol.Pose latest;
    private volatile long latestNs;
    private volatile boolean closed;
    private boolean warnedSend;

    SkateLink(InetSocketAddress engine) throws IOException {
        this.engine = engine;
        this.socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        this.socket.setSoTimeout(200);
        Thread thread = new Thread(this::receive, "cj-skate-link");
        thread.setDaemon(true);
        thread.start();
    }

    private void receive() {
        byte[] buffer = new byte[65536];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (!closed) {
            try {
                socket.receive(packet);
                SkateProtocol.Pose pose = SkateProtocol.decodePose(buffer, packet.getLength());
                if (pose != null) {
                    latest = pose;
                    latestNs = System.nanoTime();
                }
            } catch (SocketTimeoutException e) {
                // nothing arrived: look again
            } catch (IOException e) {
                if (!closed) {
                    LOG.warn("skate link: receive failed: {}", e.toString());
                }
                return;
            }
        }
    }

    void send(byte[] data) {
        try {
            socket.send(new DatagramPacket(data, data.length, engine));
        } catch (IOException e) {
            if (!warnedSend) {
                warnedSend = true;
                LOG.warn("skate link: cannot send to {}: {}", engine, e.toString());
            }
        }
    }

    /** The newest pose of the engine, or null since the link opened (or since `forget`). */
    SkateProtocol.Pose latest() {
        return latest;
    }

    long nanosSinceLatest() {
        return latest == null ? Long.MAX_VALUE : System.nanoTime() - latestNs;
    }

    /** What arrived before is old: the next ride starts without a pose. */
    void forget() {
        latest = null;
    }

    @Override
    public void close() {
        closed = true;
        socket.close();
    }
}
