package im.commonplace.reader.fixtures;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-APK-only provider. Android starts it in the test APK's own process, whose
 * classpath does not include the target APK's shared Kotlin runtime. Keep this
 * component platform/Java-only; ordinary instrumentation still uses Kotlin.
 */
public final class TestDocumentProvider extends ContentProvider {
    private static final Set<String> FIXTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "reflowable.epub", "fixed-layout.epub", "hostile-remote.epub", "ordinary-text.pdf",
            "repeated-two-page.pdf", "rotated-cropped.pdf", "multilingual.pdf", "image-only-scan.pdf",
            "encrypted.pdf", "malformed.pdf")));
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    @Override public boolean onCreate() { return true; }
    // Deliberately spoof EPUB as PDF and report no length.
    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{"../../pretend.pdf", null});
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String key = uri.getLastPathSegment();
        if (key != null && (revoked.contains(key) || "revoked".equals(key))) {
            throw new SecurityException("Synthetic grant revoked");
        }
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only synthetic provider");
        String fixture = "failing".equals(key) || "revocable".equals(key) ? "reflowable.epub" : key;
        if (!FIXTURES.contains(fixture)) throw new FileNotFoundException("Unknown synthetic fixture");
        try {
            final byte[] bytes;
            try (InputStream input = getContext().getAssets().open("fixtures/" + fixture);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
                bytes = output.toByteArray();
            }
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            final boolean failing = "failing".equals(key);
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                        new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    if (failing) {
                        output.write(bytes, 0, Math.min(100, bytes.length));
                        pipe[1].closeWithError("Synthetic provider failure");
                    } else output.write(bytes);
                } catch (IOException ignored) {
                    // Import cancellation closes the read end; no document content is logged.
                }
            }, "synthetic-document-writer").start();
            return pipe[0];
        } catch (IOException failure) {
            throw new FileNotFoundException("Synthetic fixture could not be opened");
        }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("revoke".equals(method) && arg != null) revoked.add(arg);
        if ("reset".equals(method)) revoked.clear();
        return Bundle.EMPTY;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
}
