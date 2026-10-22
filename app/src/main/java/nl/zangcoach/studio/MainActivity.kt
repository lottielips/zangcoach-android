package nl.zangcoach.studio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * De app zelf is de webpagina in assets/index.html (dezelfde code als in de browser).
 * Deze klasse is de "schil" eromheen: microfoon, opslaan, delen, bestanden kiezen
 * en blijven spelen op de achtergrond.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    // Eigen microfoon (zie NativeMic): zo blijft een Bluetooth-koptelefoon in muziekkwaliteit spelen tijdens het opnemen.
    lateinit var mic: NativeMic
    // Telefoon of ander geluid neemt het over, koptelefoon los: dan stopt de muziek (zie AudioGuard).
    lateinit var audioGuard: AudioGuard
    // Bestanden die de pagina in stukjes doorstuurt (zie Bridge.fileOpen).
    val files = FileSink(this)

    companion object {
        private const val PICK_FILE = 42
        // Zelfde adres als in de oude app: zo blijven je bewaarde nummers en zangopnames zichtbaar.
        private const val BASE_URL = "https://zangcoach.app/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mic = NativeMic(this)
        audioGuard = AudioGuard(this)

        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), 1)

        web = makeWebView()
        setContentView(web)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(): WebView {
        val w = WebView(this)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        // De pagina krijgt dezelfde voorrang als de app, ook als het scherm uit is:
        // zo legt Android het geluid niet als eerste stil om geheugen vrij te maken.
        w.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        w.webViewClient = object : WebViewClient() {
            // Loopt de pagina vast of wordt ze door Android afgesloten, dan bouwen we ze opnieuw op
            // in plaats van dat de hele app crasht. Je werk staat veilig in de opslag van de pagina.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (view !== web) return true
                recoverWebView()
                return true
            }

            // Links naar buiten openen in de browser, nooit in de app zelf.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "http" || url.scheme == "https") {
                    if (url.host == "zangcoach.app") return false
                    try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) { }
                    return true
                }
                return false
            }
        }
        w.webChromeClient = object : WebChromeClient() {
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
        w.addJavascriptInterface(Bridge(this), "ZangcoachApp")

        val html = assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        w.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null)
        return w
    }

    private fun recoverWebView() {
        mic.stop()
        audioGuard.setPlaying(false)
        PlaybackService.stop(this)
        files.closeAll()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        val old = web
        (old.parent as? ViewGroup)?.removeView(old)
        try { old.destroy() } catch (_: Exception) { }
        web = makeWebView()
        setContentView(web)
    }

    /** Vanuit Android iets laten weten aan de pagina (altijd op de UI-thread). */
    fun tellPage(js: String) {
        runOnUiThread {
            try { web.evaluateJavascript(js, null) } catch (_: Exception) { }
        }
    }

    /** De muziek moet stoppen: "focus" (ander geluid, bv. een oproep) of "noisy" (koptelefoon los). */
    fun interrupt(why: String) {
        tellPage("window.zcAudioInterrupt&&window.zcAudioInterrupt('$why')")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    // Naar de achtergrond: de pagina alles laten wegschrijven, voor het geval Android de app later afsluit.
    override fun onPause() {
        super.onPause()
        tellPage("window.zcFlush&&window.zcFlush()")
    }

    // Terug-knop: de app naar de achtergrond sturen in plaats van af te sluiten,
    // zodat de muziek kan blijven spelen.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        mic.stop()
        audioGuard.setPlaying(false)
        files.closeAll()
        PlaybackService.stop(this)
        web.destroy()
        super.onDestroy()
    }
}
