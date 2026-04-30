package io.github.yeamy.sonatype;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.*;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

/*
 *
 * PUT /io/github/yeamy/restlite-core/1.0-RC10/restlite-core-1.0-RC10.jar
 * Content-Length: 32
 * Content-Type: application/octet-stream
 * Host: 127.0.0.1:8082
 * Connection: Keep-Alive
 * User-Agent: Gradle/9.4.0 (Mac OS X;15.7.3;aarch64) (BellSoft;17.0.13;17.0.13+12-LTS)
 * Accept-Encoding: gzip,deflate
 * Authorization: Basic MHFmNHBsOlFaemxIWlZROWtua0RxWHN3Yk5DMWZFMzdaYncyWGVNMA==
 */
public class BridgeHttpServer implements Closeable {
    private static final Logger logger = Logging.getLogger(BridgeHttpServer.class);
    private ServerSocket server;
    private Thread thread;
    private String auth;

    public void start(int port, File baseDir) {
        CountDownLatch latch = new CountDownLatch(1);
        thread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                server = serverSocket;
                logger.lifecycle("""
                        [Sonatype-publish] HTTP Server start at port: {}
                        If task crush you may run shell "lsof -i :{}" or cmd "netstat -ano | findstr :{}" to checkout whether server is still running, then stop it.-
                        """, port, port, port);
                latch.countDown();
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    handleRequest(baseDir, clientSocket);
                }
                logger.lifecycle("[Sonatype-publish] HTTP Server closed");
            } catch (BindException e) {
                logger.error("[Sonatype-publish] HTTP Server start failed: ", e);
            } catch (IOException e) {
                if (server != null) {
                    close();
                    logger.error("[Sonatype-publish] HTTP Server start failed: ", e);
                }
            } finally {
                if (thread != null) {
                    thread.interrupt();
                }
            }
        });
        thread.start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String getAuth() {
        return auth;
    }

    @Override
    public void close() {
        if (server == null) return;
        try {
            if (!server.isClosed()) {
                server.close();
                server = null;
            }
            logger.lifecycle("[Sonatype-publish] HTTP Server closed.");
        } catch (Exception e) {
            logger.error("[Sonatype-publish] HTTP Server close error: ", e);
        }
    }

    private void handleRequest(File baseDir, Socket client) {
        if (!"127.0.0.1".equals(client.getInetAddress().getHostAddress())) {
            try (OutputStream os = client.getOutputStream()) {
                String data = responseForbidden();
                os.write(data.getBytes());
            } catch (Exception ignored) {
            }
        }
        try (client; HttpRequest r = new HttpRequest(client.getInputStream())) {
            r.init();
            File file = new File(baseDir, r.uri);
            switch (r.method) {
                case "GET" -> doGet(client, file);
                case "PUT" -> doPut(client, r, file);
            }
        } catch (IOException e) {
            logger.error("[Sonatype-publish] HTTP Server running error: ", e);
        }
    }

    private class HttpRequest implements Closeable {
        private final InputStream is;
        private boolean EOH;
        private int contentLength = 0;
        private String method;
        private String uri;

        public HttpRequest(InputStream is) {
            this.is = is;
        }

        public void init() throws IOException {
            String s = readLine();
            assert s != null;
            String[] ss = s.split(" ");
            method = ss[0];
            uri = ss[1];
            boolean readBody = "PUT".equals(method) || "POST".equals(method);
            while (true) {
                s = readLine();
                if (s == null) return;
                if (auth == null && s.startsWith("Authorization:")) {
                    auth = s.split(":")[1].trim();
                    if (auth.startsWith("Basic ")) {
                        auth = auth.substring("Basic ".length());
                    }
                }
                if (readBody && (s.startsWith("Content-Length") || s.startsWith("content-length") || s.startsWith("CONTENT-LENGTH"))) {
                    String num = s.split(":")[1].trim();
                    contentLength = Integer.parseInt(num);
                }
                if (s.isEmpty()) return;
            }
        }

        private String readLine() throws IOException {
            if (EOH) return null;
            StringBuilder sb = new StringBuilder();
            while (true) {
                int i = is.read();
                if (i == -1) break;
                if (i == 0x0d) {// \r
                    int j = is.read();
                    if (j == 0x0a) {// \n
                        if (sb.isEmpty()) EOH = true;
                        return sb.toString();
                    } else if (j == -1) {
                        throw new IOException("Incomplete request");
                    }
                    sb.append('\r').append((char) j);
                }
                sb.append((char) i);
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            is.close();
        }

        public void writeToFile(FileOutputStream os) throws IOException {
            long total = 0;
            byte[] tmp = new byte[8192];
            while (true) {
                int l = is.read(tmp);
                if (l == -1) {
                    break;
                }
                os.write(tmp, 0, l);
                total += l;
                if (total == contentLength) break;
            }
            os.flush();
        }
    }

    private static void doGet(Socket client, File file) throws IOException {
        //结束 Bearer；
        if (file.exists()) {
            String ext = file.getPath().substring(file.getPath().length() - 3).toLowerCase();
            String data = switch (ext) {
                case "jar" -> responseJar(file.length());
                case "xml", "pom" -> responseXml(file.length());
                default -> responseText(file.length());
            };
            try (OutputStream os = client.getOutputStream(); FileInputStream is = new FileInputStream(file)) {
                os.write(data.getBytes());
                byte[] buf = new byte[8192];
                while (true) {
                    int l = is.read(buf);
                    if (l == -1) {
                        os.flush();
                        break;
                    }
                    os.write(buf, 0, l);
                }
            }
        } else {
            String data = responseNotFound();
            try (OutputStream os = client.getOutputStream()) {
                os.write(data.getBytes());
                os.flush();
            }
        }
    }

    private static void doPut(Socket client, HttpRequest r, File file) throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream os = new FileOutputStream(file)) {
            r.writeToFile(os);
        }
        String data = responseCreated();
        try (OutputStream os = client.getOutputStream()) {
            os.write(data.getBytes());
            os.flush();
        }
    }

    private static String responseJar(long length) {
        return "HTTP/1.1 200 OK\r\nDate:" + date()
                + "\r\nContent-Type: application/java-archive\r\nContent-Length: " + length + "\r\n\r\n";
    }

    private static String responseXml(long length) {
        return "HTTP/1.1 200 OK\r\nDate:" + date()
                + "\r\nContent-Type: application/xml\r\nContent-Length: " + length + "\r\n\r\n";
    }

    private static String responseText(long length) {
        return "HTTP/1.1 200 OK\r\nDate:" + date()
                + "\r\nContent-Type: text/plain\r\nContent-Length: " + length + "\r\n\r\n";
    }

    private static String responseCreated() {
        return "HTTP/1.1 201 Created\r\nDate:" + date() + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    }

    private static String responseForbidden() {
        return "HTTP/1.1 403 Forbidden\r\nDate:" + date() + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    }

    private static String responseNotFound() {
        return "HTTP/1.1 404 Not Found\r\nDate:" + date() + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    }

    private static final ZoneId zoneId = ZoneId.of("GMT");
    private static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(zoneId);

    private static String date() {
        return ZonedDateTime.now(ZoneId.of("GMT")).format(HTTP_DATE_FORMATTER);
    }

}
