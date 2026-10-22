package nl.zangcoach.studio

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/**
 * Bestanden die de pagina in stukjes doorstuurt (back-ups, MP3's). Vroeger ging alles in één
 * grote tekst door de brug; bij een back-up van tientallen MB kon dat het geheugen doen vollopen.
 * Nu: open → stukje per stukje schrijven → sluiten.
 *
 * dest "download": naar Download/Zangcoach (zichtbaar in Bestanden).
 * dest "share": naar een tijdelijk bestand, en bij het sluiten het deel-menu openen.
 */
class FileSink(private val activity: MainActivity) {

    private class Open(val out: OutputStream, val uri: Uri?, val file: File?, val mime: String, val name: String)

    private val open = HashMap<String, Open>()
    private var counter = 0

    @Synchronized
    fun open(name: String, mime: String, dest: String): String {
        return try {
            val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "zangcoach" }
            val o = if (dest == "share") {
                val dir = File(activity.cacheDir, "share").apply { mkdirs() }
                // oude gedeelde bestanden opruimen
                dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000L) it.delete() }
                val f = File(dir, safeName)
                Open(f.outputStream().buffered(), null, f, mime, safeName)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Zangcoach")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "fout: geen plek om op te slaan"
                val out = resolver.openOutputStream(uri) ?: return "fout: kan niet schrijven"
                Open(out.buffered(), uri, null, mime, safeName)
            }
            counter++
            val id = "f$counter"
            open[id] = o
            id
        } catch (e: Throwable) {
            "fout: $e"
        }
    }

    @Synchronized
    fun write(id: String, b64: String): String {
        val o = open[id] ?: return "bestand is niet open"
        return try {
            o.out.write(Base64.decode(b64, Base64.DEFAULT))
            "ok"
        } catch (e: Throwable) {
            close(id, false)
            e.toString()
        }
    }

    @Synchronized
    fun close(id: String, keep: Boolean): String {
        val o = open.remove(id) ?: return "bestand is niet open"
        return try {
            o.out.flush()
            o.out.close()
            if (o.uri != null) {
                val resolver = activity.contentResolver
                if (keep) {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(o.uri, done, null, null)
                } else {
                    resolver.delete(o.uri, null, null)
                }
            }
            if (o.file != null) {
                if (!keep) {
                    o.file.delete()
                } else {
                    val uri = FileProvider.getUriForFile(activity, "nl.zangcoach.studio.files", o.file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = o.mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.runOnUiThread { activity.startActivity(Intent.createChooser(send, "Delen")) }
                }
            }
            "ok"
        } catch (e: Throwable) {
            e.toString()
        }
    }

    @Synchronized
    fun closeAll() {
        for (id in open.keys.toList()) close(id, false)
    }
}
