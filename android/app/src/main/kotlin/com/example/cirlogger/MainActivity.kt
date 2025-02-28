package com.example.cirlogger

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class MainActivity : FlutterActivity() {
    private val AUDIO_CHANNEL = "audio_experiment/control"
    private val RECORD_CHANNEL = "audio_experiment/record"
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private lateinit var tempFile: File

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // 音频播放通道
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AUDIO_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "playTone" -> {
                    val frequency = call.argument<Int>("frequency") ?: 15000
                    val durationMs = call.argument<Int>("durationMs") ?: 100
                    val useSpeaker = call.argument<Boolean>("useSpeaker") ?: false
                    playTone(frequency, durationMs, useSpeaker)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        // 录音控制通道
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, RECORD_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startRecord" -> {
                    val path = call.argument<String>("path")
                    startRecording(path)
                    result.success(null)
                }
                "stopRecord" -> {
                    stopRecording()
                    result.success(tempFile.absolutePath)
                }
                "openFile" -> {
                    val path = call.argument<String>("path")
                    openFile(path)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun playTone(freq: Int, durationMs: Int, useSpeaker: Boolean) {
        // 设置音频模式
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = useSpeaker

        // 生成正弦波
        val sampleRate = 44100
        val duration = durationMs / 1000.0
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ByteBuffer.allocate(numSamples * 2)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val sample = sin(2.0 * Math.PI * i / (sampleRate / freq))
            buffer.putShort((sample * Short.MAX_VALUE).toShort())
        }

        // 播放音频
        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.array().size,
            AudioTrack.MODE_STATIC
        ).apply {
            write(buffer.array(), 0, buffer.array().size)
            play()
        }
    }

    private fun startRecording(path: String?) {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        tempFile = File(path ?: "${getExternalFilesDir(null)}/temp.pcm")
        tempFile.parentFile?.mkdirs()

        isRecording = true
        recordThread = Thread {
            val buffer = ByteArray(bufferSize)
            val fos = FileOutputStream(tempFile)

            audioRecord?.startRecording()
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (bytesRead > 0) {
                    fos.write(buffer, 0, bytesRead)
                }
            }

            fos.close()
            audioRecord?.stop()
        }.apply { start() }
    }

    private fun stopRecording() {
        isRecording = false
        recordThread?.join()
        audioRecord?.release()
        addWavHeader(tempFile) // 添加WAV文件头
    }

    private fun openFile(path: String?) {
        val file = File(path ?: return)
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            file
        )

        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/wav")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun addWavHeader(pcmFile: File) {
        // WAV 文件头生成逻辑（需要自行实现）
    }

    override fun onDestroy() {
        audioTrack?.release()
        super.onDestroy()
    }
}