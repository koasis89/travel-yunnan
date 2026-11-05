package kr.koasis.travelyunnan.tts

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 중국어 음성 재생.
 * 1순위: assets/audio/phrases/{id}.mp3 (S5에서 생성·내장)
 * 2순위: 기기 TTS 엔진(zh-CN)
 */
class TtsPlayer(private val context: Context) {
    enum class Status { Initializing, Ready, MissingData, Unavailable }

    var status: Status = Status.Initializing
        private set
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private val audioIds: Set<String> by lazy {
        runCatching { context.assets.list("audio/phrases")?.map { it.substringBeforeLast('.') }?.toSet() }
            .getOrNull().orEmpty()
    }

    init {
        tts = TextToSpeech(context.applicationContext) { code ->
            status = if (code != TextToSpeech.SUCCESS) Status.Unavailable else {
                when (tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)) {
                    TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> Status.MissingData
                    else -> Status.Ready
                }
            }
        }
    }

    fun hasAudio(id: String) = id in audioIds

    /** 재생. 반환값 false 이면 재생 불가(음성 데이터 설치 안내 필요). */
    fun speak(id: String, text: String, slow: Boolean, repeat: Int = 1): Boolean {
        stop()
        if (hasAudio(id)) return playAsset(id, slow, repeat)
        val engine = tts ?: return false
        if (status != Status.Ready) return false
        engine.setSpeechRate(if (slow) 0.7f else 1.0f)
        repeat(repeat.coerceIn(1, 3)) { i ->
            engine.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$id-$i")
            if (i < repeat - 1) engine.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, "$id-gap-$i")
        }
        return true
    }

    private fun playAsset(id: String, slow: Boolean, repeat: Int): Boolean = runCatching {
        var left = repeat.coerceIn(1, 3)
        val fd = context.assets.openFd("audio/phrases/$id.mp3")
        val mp = MediaPlayer()
        mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()
        mp.prepare()
        if (slow && Build.VERSION.SDK_INT >= 23) mp.playbackParams = PlaybackParams().setSpeed(0.7f)
        mp.setOnCompletionListener { if (--left > 0) it.start() else it.release().also { player = null } }
        mp.start()
        player = mp
        true
    }.getOrDefault(false)

    fun stop() {
        tts?.stop()
        player?.runCatching { stop(); release() }
        player = null
    }

    fun installDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
