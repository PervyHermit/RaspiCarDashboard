package nl.roy.raspicardashboard;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Recursively scans a user-selected document tree without broad storage permission. */
public final class LocalMediaLibrary {
    private static final int MAX_TRACKS = 2500;
    private static final int MAX_DEPTH = 12;

    private LocalMediaLibrary() { }

    public static List<LocalTrack> scan(Context context, Uri treeUri) {
        ArrayList<LocalTrack> tracks = new ArrayList<>();
        if (treeUri == null) return tracks;
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            scanChildren(context, treeUri, rootId, tracks, 0);
        } catch (RuntimeException ignored) { }
        tracks.sort(Comparator.comparing(track -> track.displayName.toLowerCase(Locale.ROOT)));
        return tracks;
    }

    private static void scanChildren(Context context, Uri treeUri, String parentDocumentId,
                                     List<LocalTrack> out, int depth) {
        if (depth > MAX_DEPTH || out.size() >= MAX_TRACKS) return;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection,
                null, null, null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext() && out.size() < MAX_TRACKS) {
                String id = cursor.getString(idColumn);
                String name = cursor.getString(nameColumn);
                String mime = cursor.getString(mimeColumn);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanChildren(context, treeUri, id, out, depth + 1);
                } else if (isAudio(mime, name)) {
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    out.add(new LocalTrack(documentUri, name == null ? "Onbekend nummer" : name));
                }
            }
        } catch (RuntimeException ignored) { }
    }

    private static boolean isAudio(String mime, String name) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/")) return true;
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")
                || lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".opus")
                || lower.endsWith(".wav") || lower.endsWith(".amr") || lower.endsWith(".mid")
                || lower.endsWith(".midi");
    }
}
