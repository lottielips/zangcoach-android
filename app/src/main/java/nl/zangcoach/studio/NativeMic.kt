package nl.zangcoach.studio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Base64

/**
 * Eigen microfoon, buiten de WebView om.
 *
 * Waarom: neemt de webpagina zelf op (getUserMedia), dan kiest Chromium bij een Bluetooth-koptelefoon
 * met microfoontje die microfoon en zet Android in "bellen-modus" (SCO): de muziek wordt dun en hapert.
 * Hier nemen we op in de gewone modus, met de ingebouwde microfoon van de gsm. De koptelefoon blijft
 * dan gewoon muziek spelen in goede kwaliteit (A2DP).
 *
 * De pagina haalt de opgenomen stukjes op met read() (16-bit PCM, mono, base64).
 */
class NativeMic(private val ctx: Context) {

    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val lock = Object()
    private var ring = ShortArray(0)
    private var w = 0
    private var r = 0
    private var count = 0
    var sampleRate = 48000
        private set

    fun isRunning(): Boolean = running

    @SuppressLint("MissingPermission")
    fun start(sr: Int): String {
        if (running) return "ok"
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return "geen toestemming voor de microfoon"
        }
        return try {
            sampleRate = if (sr in 8000..96000) sr else 48000
            val am = ctx.getSystemService(AudioManager::class.java)
            // zonder automatische volumeregeling of ruisonderdrukking: zingen moet natuurlijk klinken
            val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return "deze samplefrequentie werkt niet"
            val r0 = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(min * 2, sampleRate / 10 * 2))
                .build()
            if (r0.state != AudioRecord.STATE_INITIALIZED) { r0.release(); return "de microfoon start niet" }
            // speelt er een Bluetooth-koptelefoon: dan zeker de ingebouwde microfoon (niet die van de koptelefoon)
            val btOut = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetooth(it) }
            if (btOut) {
                am.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                    ?.let { r0.setPreferredDevice(it) }
            }
            synchronized(lock) { ring = ShortArray(sampleRate * 2); w = 0; r = 0; count = 0 }
            r0.startRecording()
            rec = r0
            running = true
            thread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val tmp = ShortArray(sampleRate / 200) // stukjes van 5 ms
                while (running) {
                    val n = r0.read(tmp, 0, tmp.size)
                    if (n > 0) push(tmp, n)
                }
            }.apply { name = "zangcoach-mic"; start() }
            "ok"
        } catch (e: Throwable) {
            stop(); e.message ?: e.toString()
        }
    }

    private fun push(a: ShortArray, n: Int) {
        synchronized(lock) {
            val size = ring.size
            for (i in 0 until n) {
                ring[w] = a[i]; w = (w + 1) % size
                if (count < size) count++ else r = (r + 1) % size // vol: het oudste valt weg
            }
        }
    }

    /** Alles wat sinds de vorige keer binnenkwam, als base64 (16-bit little-endian). Leeg als er niets is. */
    fun read(): String {
        val bytes: ByteArray? = synchronized(lock) {
            if (count == 0) {
                null
            } else {
                val b = ByteArray(count * 2)
                val size = ring.size
                for (i in 0 until count) {
                    val v = ring[(r + i) % size].toInt()
                    b[i * 2] = (v and 0xff).toByte()
                    b[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
                }
                r = w; count = 0
                b
            }
        }
        return if (bytes == null) "" else Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun stop() {
        running = false
        try { thread?.join(300) } catch (_: Exception) { }
        thread = null
        try { rec?.stop() } catch (_: Exception) { }
        try { rec?.release() } catch (_: Exception) { }
        rec = null
    }

    private fun isBluetooth(d: AudioDeviceInfo): Boolean =
        d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
}
