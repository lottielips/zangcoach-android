package nl.zangcoach.studio

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import java.io.File

/** Wat de webpagina via window.ZangcoachApp aan Android kan vragen. */
class Bridge(private val activity: MainActivity) {

    /** Bestand (base64) bewaren in Download/Zangcoach. Geeft "ok" of een foutmelding terug. */
    @JavascriptInterface
    fun saveAudio(b64: String, name: String, mime: String): String {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Zangcoach")
            }
            val resolver = activity.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "geen plek om op te slaan"
            resolver.openOutputStream(uri).use { it!!.write(Base64.decode(b64, Base64.DEFAULT)) }
            "ok"
        } catch (e: Throwable) {
            e.toString()
        }
    }

    /** Bestand (base64) delen via het deel-menu van Android (WhatsApp, Drive, ...). */
    @JavascriptInterface
    fun share(b64: String, name: String, mime: String): String {
        return try {
            val dir = File(activity.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, name)
            file.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            val uri = FileProvider.getUriForFile(activity, "nl.zangcoach.studio.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.runOnUiThread { activity.startActivity(Intent.createChooser(send, "Delen")) }
            "ok"
        } catch (e: Throwable) {
            e.toString()
        }
    }

    /** De pagina meldt of er muziek speelt: dan blijft de app actief op de achtergrond. */
    @JavascriptInterface
    fun playing(on: Boolean) {
        activity.runOnUiThread {
            if (on) PlaybackService.start(activity) else PlaybackService.stop(activity)
            activity.audioGuard.setPlaying(on)
        }
    }

    /** Eigen microfoon starten (ingebouwde mic, gewone modus). Geeft "ok" of een foutmelding. */
    @JavascriptInterface
    fun micStart(sampleRate: Int): String = activity.mic.start(sampleRate)

    /** Wat de microfoon sinds de vorige keer opnam: 16-bit PCM mono, base64. */
    @JavascriptInterface
    fun micRead(): String = activity.mic.read()

    @JavascriptInterface
    fun micStop() { activity.mic.stop() }

    /** Bestand in stukjes doorsturen: open (geeft een id of "fout: ..."), schrijven (base64), sluiten. dest = "download" of "share". */
    @JavascriptInterface
    fun fileOpen(name: String, mime: String, dest: String): String = activity.files.open(name, mime, dest)

    @JavascriptInterface
    fun fileWrite(id: String, b64: String): String = activity.files.write(id, b64)

    @JavascriptInterface
    fun fileClose(id: String, keep: Boolean): String = activity.files.close(id, keep)

    /** Laat de pagina weten welke extra's deze schil kan. */
    @JavascriptInterface
    fun features(): String = "save,share,playing,pick,mic,file,focus"
}
