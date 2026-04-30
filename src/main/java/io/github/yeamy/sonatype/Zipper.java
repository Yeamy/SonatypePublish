package io.github.yeamy.sonatype;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zipper {
    private static final Logger logger = Logging.getLogger(Zipper.class);

    public static File zip(File dir) {
        File[] fs = listFiles(dir);
        String dirPath = dir.getPath();
        int subIndex = dirPath.length();
        if (!dirPath.endsWith(File.separator)) {
            subIndex++;
        }
        boolean replaceSeparatorChar = File.separatorChar != '/';
        File zip = new File(dir, zipFileName(fs));
        try (FileOutputStream os = new FileOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
            byte[] buf = new byte[4096];
            int len;
            for (File f : fs) {
                String path = f.getPath().substring(subIndex);
                if (replaceSeparatorChar) {
                    path = path.replace(File.separatorChar, '/');
                }
                zos.putNextEntry(new ZipEntry(path));
                try (FileInputStream fis = new FileInputStream(f)) {
                    while ((len = fis.read(buf)) != -1) {
                        zos.write(buf, 0, len);
                    }
                    zos.closeEntry();
                }
            }
            logger.lifecycle("[Sonatype-publish] zipping files output: {}", zip);
        } catch (Exception e) {
            logger.error("[Sonatype-publish] error zipping files", e);
        }
        return zip;
    }

    private static String zipFileName(File[] fs) {
        String name = fs[0].getName();
        int l = name.length();
        for (File f : fs) {
            String n = f.getName();
            if (n.length() < l) {
                name = n;
                l = n.length();
            }
        }
        return name.substring(0, name.lastIndexOf('.')) + ".zip";
    }

    private static File[] listFiles(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return new File[0];
        for (File f : fs) {
            if (f.isDirectory()) return listFiles(f);
        }
        return fs;
    }

}
