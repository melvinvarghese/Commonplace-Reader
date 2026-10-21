package im.commonplace.reader.fixtures;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Disposable test APK endpoint, not part of the reader or its network policy. */
public class NetworkProbeProvider extends ContentProvider {
    private ServerSocket server;
    private final AtomicInteger requests = new AtomicInteger();
    @Override public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method, String argument, Bundle extras) {
        Bundle result = new Bundle();
        try {
            if ("start".equals(method)) {
                if (server != null) server.close();
                requests.set(0);
                final ServerSocket socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
                server = socket;
                Thread worker = new Thread(() -> {
                    while (!socket.isClosed()) {
                        try (Socket client = socket.accept()) {
                            client.setSoTimeout(2000);
                            InputStream input = client.getInputStream();
                            int matched = 0;
                            while (matched < 4) {
                                int c = input.read();
                                if (c < 0) break;
                                int expected = matched % 2 == 0 ? '\r' : '\n';
                                matched = c == expected ? matched + 1 : 0;
                            }
                            requests.incrementAndGet();
                            client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".getBytes(StandardCharsets.US_ASCII));
                        } catch (Exception ignored) { if (socket.isClosed()) return; }
                    }
                }, "synthetic-network-probe");
                worker.setDaemon(true);
                worker.start();
                // Positive control proves the endpoint works before the counter is reset.
                try (Socket client = new Socket("127.0.0.1", socket.getLocalPort())) {
                    client.setSoTimeout(2000);
                    client.getOutputStream().write("GET /control HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    if (client.getInputStream().read() != 'H') throw new IllegalStateException("endpoint control failed");
                }
                if (requests.get() != 1) throw new IllegalStateException("endpoint control not recorded");
                requests.set(0);
                result.putInt("port", socket.getLocalPort());
                result.putBoolean("controlPassed", true);
            } else if ("stop".equals(method)) {
                if (server != null) server.close();
                server = null;
            }
            result.putInt("requests", requests.get());
            result.putBoolean("listening", server != null && !server.isClosed());
        } catch (Exception error) { throw new IllegalStateException("Synthetic endpoint failed", error); }
        return result;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
