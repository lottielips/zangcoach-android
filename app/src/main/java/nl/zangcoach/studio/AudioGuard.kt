package nl.zangcoach.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Zolang er muziek speelt, vraagt de app de "audiofocus" aan Android, zoals elke muziekspeler.
 * - Komt er een oproep binnen of start een andere app muziek: de muziek stopt (anders speelt ze door je gesprek).
 * - Wordt de koptelefoon losgekoppeld (ook Bluetooth): de muziek stopt, zodat ze niet plots luid
 *   door de luidspreker van de gsm klinkt.
 * Korte meldingen (navigatie, berichtjes) laten de muziek gewoon doorspelen.
 */
class AudioGuard(private val activity: MainActivity) {

    private val am: AudioManager = activity.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var noisyRegistered = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) activity.interrupt("noisy")
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            activity.interrupt("focus")
        }
    }

    fun setPlaying(on: Boolean) {
        if (on) {
            if (request == null) {
                try {
                    val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setOnAudioFocusChangeListener(focusListener)
                        .setWillPauseWhenDucked(false)
                        .build()
                    am.requestAudioFocus(r)
                    request = r
                } catch (_: Exception) { }
            }
            if (!noisyRegistered) {
                try {
                    val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        activity.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        activity.registerReceiver(noisy, filter)
                    }
                    noisyRegistered = true
                } catch (_: Exception) { }
            }
        } else {
            request?.let { try { am.abandonAudioFocusRequest(it) } catch (_: Exception) { } }
            request = null
            if (noisyRegistered) {
                try { activity.unregisterReceiver(noisy) } catch (_: Exception) { }
                noisyRegistered = false
            }
        }
    }
}
