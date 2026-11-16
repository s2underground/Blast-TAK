import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds and writes the class index TAK-X makes when it scans a plugin jar
 * (TAK-X 5.8 uses Jandex 2.4.3). If writing fails, TAK-X can't scan the
 * jar and silently leaves the plugin out of its list. Exits 1 on failure,
 * naming the classes that break it.
 */
public final class IndexCheck {

    private IndexCheck() {
    }

    public static void main(String[] args) throws Exception {
        List<String> bad = new ArrayList<>();
        Indexer all = new Indexer();
        try (ZipFile z = new ZipFile(args[0])) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (!e.getName().endsWith(".class"))
                    continue;
                byte[] b;
                try (InputStream in = z.getInputStream(e)) {
                    b = in.readAllBytes();
                }
                try {
                    Indexer one = new Indexer();
                    one.index(new ByteArrayInputStream(b));
                    new IndexWriter(new ByteArrayOutputStream())
                            .write(one.complete());
                    all.index(new ByteArrayInputStream(b));
                } catch (Exception | Error ex) {
                    bad.add(e.getName() + ": " + ex);
                }
            }
        }
        if (bad.isEmpty()) {
            new IndexWriter(new ByteArrayOutputStream()).write(all.complete());
            System.out.println("TAK-X class index OK: " + args[0]);
            return;
        }
        System.err.println("TAK-X could not index " + args[0] + ":");
        for (String s : bad)
            System.err.println("  " + s);
        System.err.println("Build with a JDK 17 compiler (javac 21+ writes "
                + "unnamed enum-constructor parameters Jandex 2.4 can't "
                + "store).");
        System.exit(1);
    }
}
