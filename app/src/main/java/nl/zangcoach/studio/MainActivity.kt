package nl.zangcoach.studio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * De app zelf is de webpagina in assets/index.html (dezelfde code als in de browser).
 * Deze klasse is de "schil" eromheen: microfoon, opslaan, delen, bestanden kiezen
 * en blijven spelen op de achtergrond.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val PICK_FILE = 42
        // Zelfde adres als in de oude app: zo blijven je bewaarde nummers en zangopnames zichtbaar.
        private const val BASE_URL = "https://zangcoach.app/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), 1)

        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            // <input type="file"> in de pagina: de bestandskiezer van Android openen
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                return try {
                    startActivityForResult(intent, PICK_FILE); true
                } catch (e: Exception) {
                    fileCallback = null; false
                }
            }
        }
        web.addJavascriptInterface(Bridge(this), "ZangcoachApp")

        val html = assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        web.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null)
        setContentView(web)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    // Terug-knop: de app naar de achtergrond sturen in plaats van af te sluiten,
    // zodat de muziek kan blijven spelen.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        PlaybackService.stop(this)
        web.destroy()
        super.onDestroy()
    }
}
