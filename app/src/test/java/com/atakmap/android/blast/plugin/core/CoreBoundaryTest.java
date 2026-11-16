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
 *
 * <p>It also holds the core to two more rules (audit, Nov 16, 2026, as the
 * WX and HAZ guards do):
 * <ul>
 * <li><b>Same output on every machine:</b> no number or date formatting,
 * case change or calendar without an explicit Locale / time zone, no
 * default locale or zone, and no clock reads (time comes in through the
 * Clock port or as arguments).</li>
 * <li><b>Runs on Android 5 (API 21, Blast's minSdk):</b> none of the Java 8
 * library additions Android only has from 7 or 8 (java.util.function,
 * streams, java.time, java.nio.file, Base64, Optional, the new default
 * methods on Map / List / Iterable, String.join, ...). Lambdas and method
 * references are fine (the build desugars them) as long as their types
 * are the core's own or older Java interfaces.</li>
 * </ul>
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

    /** Machine-setting rules: [pattern, what to do instead]. */
    static final String[][] MACHINE = {
            { "String\\.format\\((?!\\s*(?:java\\.util\\.)?Locale\\.)",
                    "String.format needs a Locale first" },
            { "\\.to(?:Upper|Lower)Case\\(\\s*\\)",
                    "toUpperCase/toLowerCase need a Locale" },
            { "new\\s+(?:java\\.text\\.)?SimpleDateFormat\\((?![^;]*Locale\\.)",
                    "SimpleDateFormat needs a Locale (and a UTC zone)" },
            { "Calendar\\.getInstance\\(\\s*\\)",
                    "Calendar.getInstance needs a zone and Locale" },
            { "(?:Locale|TimeZone)\\.getDefault\\(",
                    "no default locale or zone" },
            { "System\\.currentTimeMillis\\(|System\\.nanoTime\\("
                    + "|new\\s+(?:java\\.util\\.)?Date\\(\\s*\\)",
                    "no clock reads: use the Clock port or an argument" } };

    /** Java 8 library additions missing before Android 7/8. */
    static final String[][] ANDROID5 = {
            { "java\\.util\\.function\\.|java\\.util\\.stream\\."
                    + "|java\\.time\\.|java\\.nio\\.file\\."
                    + "|java\\.util\\.Base64|java\\.util\\.Optional"
                    + "|java\\.util\\.StringJoiner"
                    + "|java\\.util\\.concurrent\\.CompletableFuture",
                    "package or class newer than Android 5" },
            { "\\.(?:stream|parallelStream|chars)\\(\\s*\\)",
                    "streams need Android 7" },
            { "\\.(?:forEach|getOrDefault|computeIfAbsent|computeIfPresent"
                    + "|putIfAbsent|removeIf|forEachRemaining)\\(",
                    "Map/Collection default methods need Android 7" },
            { "(?<!Collections|Arrays)\\.sort\\(",
                    "List.sort needs Android 7: use Collections.sort" },
            { "String\\.join\\(|Comparator\\.(?:comparing|naturalOrder"
                    + "|reverseOrder|nullsFirst|nullsLast)",
                    "needs Android 7/8" },
            { "Math\\.(?:floorMod|floorDiv|\\w+Exact)\\("
                    + "|(?:Double|Long|Integer|Boolean|Float)\\.hashCode\\("
                    + "|Double\\.isFinite\\(|(?:List|Set|Map)\\.of\\(",
                    "needs Android 7 (or Java 9)" } };

    /** Rule violations in code (comments and strings already removed). */
    static List<String> ruleViolations(String name, String code,
            String[][] rules) {
        List<String> out = new ArrayList<>();
        for (String[] r : rules) {
            Matcher m = Pattern.compile(r[0]).matcher(code);
            while (m.find()) {
                int line = 1;
                for (int i = 0; i < m.start(); i++)
                    if (code.charAt(i) == '\n')
                        line++;
                out.add(name + ":" + line + " " + r[1]);
            }
        }
        return out;
    }

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
        List<File> files = new ArrayList<>();
        collect(dir, files);
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (File f : files) {
            checked++;
            String name = dir.toPath().relativize(f.toPath()).toString();
            bad.addAll(violations(name, new String(Files.readAllBytes(
                    f.toPath()), StandardCharsets.UTF_8)));
        }
        assertTrue("no core sources checked", checked > 10);
        if (!bad.isEmpty())
            fail("core must stay platform-free:\n  " + String.join("\n  ",
                    bad));
    }

    @Test
    public void coreGivesTheSameOutputEverywhereAndRunsOnAndroid5()
            throws Exception {
        File dir = coreDir();
        List<File> files = new ArrayList<>();
        collect(dir, files);
        List<String> bad = new ArrayList<>();
        for (File f : files) {
            String name = dir.toPath().relativize(f.toPath()).toString();
            String code = codeOnly(new String(Files.readAllBytes(
                    f.toPath()), StandardCharsets.UTF_8));
            bad.addAll(ruleViolations(name, code, MACHINE));
            bad.addAll(ruleViolations(name, code, ANDROID5));
        }
        if (!bad.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String b : bad)
                sb.append("\n  ").append(b);
            fail("core rules broken:" + sb);
        }
    }

    /** The rule checks catch what they should and nothing else. */
    @Test
    public void ruleCheckerWorks() {
        String ok = codeOnly("class Ok { void f() {\n"
                + "String.format(Locale.US, \"%d\", 1);\n"
                + "s.toUpperCase(Locale.ROOT);\n"
                + "new SimpleDateFormat(\"yyyy\",\n Locale.US);\n"
                + "Calendar.getInstance(TimeZone.getTimeZone(\"UTC\"), "
                + "Locale.US);\n"
                + "Collections.sort(l, c); Arrays.sort(a);\n"
                + "// String.format(\"%d\", 1); list.stream()\n"
                + "String t = \"System.currentTimeMillis() x.forEach(y)\";\n"
                + "}}");
        assertTrue(ruleViolations("Ok", ok, MACHINE).toString(),
                ruleViolations("Ok", ok, MACHINE).isEmpty());
        assertTrue(ruleViolations("Ok", ok, ANDROID5).toString(),
                ruleViolations("Ok", ok, ANDROID5).isEmpty());
        String bad = codeOnly("import java.util.function.Consumer;\n"
                + "class Bad { void f() {\n"
                + "String.format(\"%d\", 1);\n"
                + "s.toLowerCase();\n"
                + "new SimpleDateFormat(\"yyyy\");\n"
                + "Locale.getDefault(); System.currentTimeMillis();\n"
                + "new Date();\n"
                + "list.stream(); m.getOrDefault(k, v); list.sort(c);\n"
                + "String.join(\",\", l); Math.floorMod(a, b);\n"
                + "}}");
        List<String> m = ruleViolations("Bad", bad, MACHINE);
        List<String> a = ruleViolations("Bad", bad, ANDROID5);
        assertTrue(m.toString(), m.size() == 6);
        assertTrue(a.toString(), a.size() == 6);
        assertTrue(m.get(0).startsWith("Bad:3 "));
    }

    /** All .java files under dir, subpackages included. */
    static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null)
            return;
        for (File f : fs) {
            if (f.isDirectory())
                collect(f, out);
            else if (f.getName().endsWith(".java"))
                out.add(f);
        }
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
