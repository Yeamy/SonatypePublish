package io.github.yeamy.sonatype;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Uploader {
    private static final Logger logger = Logging.getLogger(Uploader.class);
    private static final String BOUNDARY = "----NexusPublishBridge";
    private static final String CRLF = "\r\n";

    public static boolean upload(File zip, boolean autoPublish, String bearerAuth) {
        if (bearerAuth == null || bearerAuth.isEmpty()) {
            logger.error("[Sonatype-publish] auth is empty");
            return false;
        }
        if (zip == null || !zip.exists() || !zip.isFile()) {
            logger.error("[Sonatype-publish] zip file invalid");
            return false;
        }

        HttpURLConnection conn = null;
        try {
            String urlStr = "https://central.sonatype.com/api/v1/publisher/upload?publishingType="
                    + (autoPublish ? "AUTOMATIC" : "USER_MANAGED");

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();

            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + bearerAuth);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            conn.setDoOutput(true);
            conn.setDoInput(true);

            try (OutputStream os = conn.getOutputStream();
                 FileInputStream fis = new FileInputStream(zip)) {
                // 1. 写分隔头
                String header = "--" + BOUNDARY + CRLF +
                        "Content-Disposition: form-data; name=\"bundle\"; filename=\"" + zip.getName() + "\"" + CRLF +
                        "Content-Type: application/zip" + CRLF +
                        CRLF;
                os.write(header.getBytes(StandardCharsets.UTF_8));

                // 2. 写文件
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }

                // 写结束分隔符
                os.write(CRLF.getBytes(StandardCharsets.UTF_8));
                os.write(("--" + BOUNDARY + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 3. 读取响应（成功/失败都读）
            int responseCode = conn.getResponseCode();
            logger.lifecycle("Upload response code: {}", responseCode);

            try (InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                String result = baos.toString(StandardCharsets.UTF_8);
                logger.lifecycle("[Sonatype-publish] Upload result: {}", result);
            }
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            logger.error("[Sonatype-publish] upload failed", e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}