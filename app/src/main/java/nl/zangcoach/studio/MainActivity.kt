package nl.zangcoach.studio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
    private lateinit var audio: AudioManager

    // Bluetooth-koptelefoon + microfoon: Android schakelt dan over naar "bellen-modus"
    // (lage kwaliteit, haperend geluid, mic van de koptelefoon). Wij zetten dat meteen terug:
    // muziek blijft in goede kwaliteit naar de koptelefoon, opnemen gebeurt met de mic van de gsm.
    private val modeListener = AudioManager.OnModeChangedListener { keepMusicQuality() }
    private val commDeviceListener = AudioManager.OnCommunicationDeviceChangedListener { keepMusicQuality() }

    companion object {
        private const val PICK_FILE = 42
        // Zelfde adres als in de oude app: zo blijven je bewaarde nummers en zangopnames zichtbaar.
        private const val BASE_URL = "https://zangcoach.app/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            audio.addOnModeChangedListener(mainExecutor, modeListener)
            audio.addOnCommunicationDeviceChangedListener(mainExecutor, commDeviceListener)
        }

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
                // net na het openen van de microfoon nog eens nakijken
                web.postDelayed({ keepMusicQuality() }, 300)
                web.postDelayed({ keepMusicQuality() }, 1500)
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

    private fun isBluetooth(d: AudioDeviceInfo?): Boolean = d != null && (
        d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        )

    /** Bellen-modus via Bluetooth uitzetten, zodat de muziek niet hapert. */
    @Suppress("DEPRECATION")
    private fun keepMusicQuality() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val comm = audio.communicationDevice
                if (isBluetooth(comm) || (audio.mode == AudioManager.MODE_IN_COMMUNICATION &&
                        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetooth(it) })
                ) {
                    audio.clearCommunicationDevice()
                    if (audio.mode != AudioManager.MODE_NORMAL) audio.mode = AudioManager.MODE_NORMAL
                }
            } else if (audio.isBluetoothScoOn) {
                audio.stopBluetoothSco()
                audio.isBluetoothScoOn = false
                audio.mode = AudioManager.MODE_NORMAL
            }
        } catch (_: Exception) { }
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
        if (Build.VERSION.SDK_INT >= 31) {
            audio.removeOnModeChangedListener(modeListener)
            audio.removeOnCommunicationDeviceChangedListener(commDeviceListener)
        }
        PlaybackService.stop(this)
        web.destroy()
        super.onDestroy()
    }
}
