package com.meanmutton.mechgame.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A WebSocket client on a plain socket, for Android and the desktop harness (the browser has its own WebSocket).
 * One thread connects and reads; sends happen on the caller's thread. Supports ws:// and wss:// (TLS, with the
 * certificate checked against the host name), text messages, fragmented messages, ping and close.
 *
 * Nothing in the browser build uses this class, so it isn't compiled into the JavaScript.
 */
public final class SocketLink implements Link, Runnable {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final String url;
    private volatile int state = CONNECTING;
    private final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<String>();
    private final SecureRandom random = new SecureRandom();
    private volatile Socket sock;
    private OutputStream out;

    public SocketLink(String url) {
        this.url = url;
        Thread t = new Thread(this, "hexfront-link");
        t.setDaemon(true);
        t.start();
    }

    public int state() {
        return state;
    }

    public String poll() {
        return received.poll();
    }

    public void send(String text) {
        if (state != OPEN) return;
        try {
            frame(1, text.getBytes("UTF-8"));
        } catch (IOException e) {
            shut();
        }
    }

    public void close() {
        if (state == OPEN) {
            try {
                frame(8, new byte[0]);
            } catch (IOException ignored) {
            }
        }
        shut();
    }

    private void shut() {
        state = CLOSED;
        Socket s = sock;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Client frames are always masked (RFC 6455). */
    private synchronized void frame(int opcode, byte[] p) throws IOException {
        int n = p.length;
        ByteArrayOutputStream b = new ByteArrayOutputStream(n + 14);
        b.write(0x80 | opcode);
        if (n < 126) b.write(0x80 | n);
        else if (n < 65536) {
            b.write(0x80 | 126);
            b.write(n >>> 8);
            b.write(n & 0xFF);
        } else {
            b.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) b.write(i >= 4 ? 0 : (n >>> (8 * i)) & 0xFF);
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        b.write(mask, 0, 4);
        for (int i = 0; i < n; i++) b.write(p[i] ^ mask[i & 3]);
        out.write(b.toByteArray());
        out.flush();
    }

    public void run() {
        try {
            URI u = new URI(url);
            boolean tls = "wss".equals(u.getScheme());
            String host = u.getHost();
            int port = u.getPort() > 0 ? u.getPort() : (tls ? 443 : 80);
            String path = u.getRawPath() == null || u.getRawPath().length() == 0 ? "/" : u.getRawPath();
            if (u.getRawQuery() != null) path += "?" + u.getRawQuery();
            Socket plain = new Socket();
            sock = plain;
            String proxy = tls ? proxyFor(host) : null;
            if (proxy != null) {
                // a computer that reaches the internet through an HTTP proxy (phones don't): tunnel through it
                URI pu = new URI(proxy.contains("://") ? proxy : "http://" + proxy);
                plain.connect(new InetSocketAddress(pu.getHost(), pu.getPort() > 0 ? pu.getPort() : 80), 15000);
                plain.setSoTimeout(15000);
                OutputStream po = plain.getOutputStream();
                po.write(("CONNECT " + host + ":" + port + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\n\r\n").getBytes("ISO-8859-1"));
                po.flush();
                InputStream pi = plain.getInputStream();
                String st = line(pi);
                if (st == null || !(st.startsWith("HTTP/1.1 200") || st.startsWith("HTTP/1.0 200"))) throw new IOException("proxy refused: " + st);
                for (String h = line(pi); h != null && h.length() > 0; h = line(pi)) {
                    // skip the proxy's headers
                }
            } else {
                plain.connect(new InetSocketAddress(host, port), 15000);
            }
            Socket s = plain;
            if (tls) {
                SSLSocket ss = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);
                sock = ss;
                // check the certificate is for this host: during the handshake where the platform can (Java 7+,
                // Android 7+), otherwise with Android's own host name check afterwards
                boolean checked = false;
                try {
                    SSLParameters sp = ss.getSSLParameters();
                    // by reflection: the Android SDK the APK is compiled against predates the method
                    SSLParameters.class.getMethod("setEndpointIdentificationAlgorithm", String.class).invoke(sp, "HTTPS");
                    ss.setSSLParameters(sp);
                    checked = true;
                } catch (Throwable old) {
                    // older Android: no endpoint identification
                }
                ss.startHandshake();
                if (!checked && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ss.getSession())) {
                    throw new IOException("the certificate is not for " + host);
                }
                s = ss;
            }
            s.setTcpNoDelay(true);
            s.setSoTimeout(15000);
            out = s.getOutputStream();
            InputStream in = new BufferedInputStream(s.getInputStream());
            byte[] keyBytes = new byte[16];
            random.nextBytes(keyBytes);
            String key = base64(keyBytes);
            String req = "GET " + path + " HTTP/1.1\r\nHost: " + host + (u.getPort() > 0 ? ":" + port : "")
                    + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key
                    + "\r\nSec-WebSocket-Version: 13\r\n\r\n";
            out.write(req.getBytes("ISO-8859-1"));
            out.flush();
            String status = line(in);
            if (status == null || !status.startsWith("HTTP/1.1 101")) throw new IOException("refused: " + status);
            String accept = null;
            for (String h = line(in); h != null && h.length() > 0; h = line(in)) {
                if (Text.lower(h).startsWith("sec-websocket-accept:")) accept = h.substring(21).trim();
            }
            String want = base64(MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes("ISO-8859-1")));
            if (!want.equals(accept)) throw new IOException("bad handshake");
            s.setSoTimeout(0);   // the game pings now and then and notices a dead connection itself
            state = OPEN;
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            while (state == OPEN) {
                int b0 = in.read(), b1 = in.read();
                if (b0 < 0 || b1 < 0) break;
                int op = b0 & 0x0F;
                long n = b1 & 0x7F;
                if (n == 126) n = (byteOf(in) << 8) | byteOf(in);
                else if (n == 127) {
                    n = 0;
                    for (int i = 0; i < 8; i++) n = (n << 8) | byteOf(in);
                }
                if (n > (8 << 20)) throw new IOException("message too large");
                byte[] mask = null;
                if ((b1 & 0x80) != 0) {
                    mask = new byte[4];
                    readFully(in, mask);
                }
                byte[] p = new byte[(int) n];
                readFully(in, p);
                if (mask != null) for (int i = 0; i < p.length; i++) p[i] ^= mask[i & 3];
                if (op == 8) break;                                   // close
                if (op == 9) {                                        // ping
                    frame(10, p);
                    continue;
                }
                if (op != 0 && op != 1 && op != 2) continue;          // pong and anything unknown
                msg.write(p, 0, p.length);
                if ((b0 & 0x80) != 0) {                               // the last frame of a message
                    received.add(new String(msg.toByteArray(), "UTF-8"));
                    msg.reset();
                }
            }
        } catch (Exception e) {
            // the game sees the link closed and decides what to do
        }
        shut();
    }

    /** The HTTPS proxy to use for this host, from HTTPS_PROXY and NO_PROXY, or null (always null on a phone). */
    private static String proxyFor(String host) {
        try {
            String p = System.getenv("HTTPS_PROXY");
            if (p == null) p = System.getenv("https_proxy");
            if (p == null || p.length() == 0) return null;
            String no = System.getenv("NO_PROXY");
            if (no == null) no = System.getenv("no_proxy");
            if (no != null) {
                for (String n : Text.split(no, ',')) {
                    n = n.trim();
                    if (n.startsWith("*.")) n = n.substring(1);
                    if (n.length() > 0 && (host.equals(n) || (n.startsWith(".") && host.endsWith(n)))) return null;
                }
            }
            return p;
        } catch (Throwable e) {
            return null;
        }
    }

    private static long byteOf(InputStream in) throws IOException {
        int v = in.read();
        if (v < 0) throw new IOException("closed");
        return v;
    }

    private static void readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new IOException("closed");
            off += n;
        }
    }

    /** One header line, without its line ending. */
    private static String line(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        for (int c = in.read(); c >= 0; c = in.read()) {
            if (c == '\n') return b.toString();
            if (c != '\r') b.append((char) c);
            if (b.length() > 8192) throw new IOException("header too long");
        }
        return b.length() > 0 ? b.toString() : null;
    }

    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    static String base64(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length; i += 3) {
            int n = (d[i] & 0xFF) << 16 | (i + 1 < d.length ? (d[i + 1] & 0xFF) << 8 : 0) | (i + 2 < d.length ? d[i + 2] & 0xFF : 0);
            b.append(B64[n >> 18 & 63]).append(B64[n >> 12 & 63]);
            b.append(i + 1 < d.length ? B64[n >> 6 & 63] : '=').append(i + 2 < d.length ? B64[n & 63] : '=');
        }
        return b.toString();
    }
}
