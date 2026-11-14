package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refactor step 2: the core must stay plain Java so it can run in ATAK, in
 * TAK-X (desktop Java) and in standalone apps. This fails on any reference
 * to Android, ATAK, TAK-X, desktop UI, or the plugin's own platform layers
 * (atak/, ui/) from a file in core/.
 */
public class CoreBoundaryTest {

    static final String CORE_PKG = "com.atakmap.android.blast.plugin.core";

    /** Package prefixes the core may never use. */
    static final String[] FORBIDDEN = { "android.", "androidx.",
            "com.atakmap.", "gov.tak.", "gov.takx.", "transapps.",
            "javax.swing.", "java.awt.", "org.json." };

    static final Pattern REF = Pattern.compile(
            "(?<![\\w.])((?:android|androidx|com\\.atakmap|gov\\.tak|gov\\.takx"
                    + "|transapps|javax\\.swing|java\\.awt|org\\.json)"
                    + "(?:\\.\\w+)+)");

    static File coreDir() {
        String rel = "src/main/java/" + CORE_PKG.replace('.', '/');
        for (String base : new String[] { ".", "app" }) {
            File f = new File(base, rel);
            if (f.isDirectory())
                return f;
        }
        fail("core source folder not found from " + new File(".")
                .getAbsolutePath());
        return null;
    }

    /** Remove comments and string literals so only code is checked. */
    static String codeOnly(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n')
                    i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*'
                        && src.charAt(i + 1) == '/'))
                    i++;
                i += 2;
            } else if (c == '"' || c == '\'') {
                char q = c;
                sb.append(q);
                i++;
                while (i < n && src.charAt(i) != q) {
                    if (src.charAt(i) == '\\')
                        i++;
                    i++;
                }
                sb.append(q);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    static List<String> violations(String name, String src) {
        List<String> out = new ArrayList<>();
        String code = codeOnly(src);
        String[] lines = code.split("\n", -1);
        for (int ln = 0; ln < lines.length; ln++) {
            String line = lines[ln];
            if (line.trim().startsWith("package "))
                continue;
            Matcher m = REF.matcher(line);
            while (m.find()) {
                String ref = m.group(1);
                if (ref.startsWith(CORE_PKG + ".") || ref.equals(CORE_PKG))
                    continue;
                out.add(name + ":" + (ln + 1) + " uses " + ref);
            }
        }
        return out;
    }

    @Test
    public void coreUsesPlainJavaOnly() throws Exception {
        File dir = coreDir();
        File[] files = dir.listFiles();
        assertTrue(files != null && files.length > 10);
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (File f : files) {
            if (!f.getName().endsWith(".java"))
                continue;
            checked++;
            bad.addAll(violations(f.getName(), new String(Files.readAllBytes(
                    f.toPath()), StandardCharsets.UTF_8)));
        }
        assertTrue("no core sources checked", checked > 10);
        if (!bad.isEmpty())
            fail("core must stay platform-free:\n  " + String.join("\n  ",
                    bad));
    }

    /** The checker itself catches what it should and nothing else. */
    @Test
    public void checkerWorks() {
        String src = "package " + CORE_PKG + ";\n"
                + "import java.util.List;\n"
                + "import " + CORE_PKG + ".Geo;\n"
                + "// import android.util.Log;\n"
                + "/* com.atakmap.coremap.log.Log */\n"
                + "class X { String s = \"android.widget\"; }\n"
                + "import android.graphics.Color;\n"
                + "class Y { Object o = new com.atakmap.android.maps.Marker(); "
                + "gov.takx.api.shapes.IShape z; java.awt.Color c; }\n"
                + "import com.atakmap.android.blast.plugin.atak.BlastStore;\n";
        List<String> v = violations("X.java", src);
        assertTrue(v.toString(), v.size() == 5);
        assertTrue(v.get(0).contains("android.graphics.Color"));
        assertTrue(v.get(4).contains("plugin.atak.BlastStore"));
    }
}
