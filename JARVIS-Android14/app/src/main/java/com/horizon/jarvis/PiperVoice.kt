package com.horizon.jarvis

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * Offline neural Russian Piper Dmitri (int8) via Apache-2.0 sherpa-onnx.
 * Files originate from k2-fsa release tts-models; generated WAV is private.
 */
object PiperVoice {
    private const val DIRECTORY = "piper-dmitri-v1"
    private val guard = Any()
    private var engine: OfflineTts? = null

    @JvmStatic fun enabled(context: Context): Boolean =
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getBoolean("piper_voice", true)

    @JvmStatic fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("piper_voice", enabled).apply()
    }

    private fun copyAssets(context: Context, assetPath: String, destination: File) {
        val files = context.assets.list(assetPath) ?: emptyArray()
        if (files.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { source ->
                FileOutputStream(destination).use { target -> source.copyTo(target, 65536) }
            }
            return
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Cannot create offline TTS directory")
        }
        for (name in files) copyAssets(context, "$assetPath/$name", File(destination, name))
    }

    private fun prepare(context: Context): File {
        val folder = File(context.filesDir, DIRECTORY)
        val marker = File(folder, ".installed-v1")
        if (marker.exists()) return folder
        val partial = File(context.filesDir, "$DIRECTORY.partial")
        partial.deleteRecursively()
        copyAssets(context, "piper", partial)
        val model = partial.listFiles()?.firstOrNull { it.extension == "onnx" }
        if (model == null || model.length() < 10_000_000) {
            partial.deleteRecursively()
            throw IOException("Piper model not found inside APK")
        }
        val tokens = File(partial, "tokens.txt")
        val espeak = File(partial, "espeak-ng-data")
        if (!tokens.isFile || !espeak.isDirectory) {
            partial.deleteRecursively()
            throw IOException("Piper language files are missing")
        }
        folder.deleteRecursively()
        if (!partial.renameTo(folder)) throw IOException("Cannot initialize offline voice")
        File(folder, ".installed-v1").writeText("ready")
        return folder
    }

    /**
     * Blocking call: NEVER invoke on Android's UI thread.
     * Returns absolute WAV path suitable for MediaPlayer.
     */
    @JvmStatic fun synthesize(context: Context, text: String): String {
        synchronized(guard) {
            val folder = prepare(context)
            var tts = engine
            if (tts == null) {
                val onnx = folder.listFiles()?.firstOrNull { it.extension == "onnx" }
                    ?: throw IOException("Piper ONNX model not found")
                val vits = OfflineTtsVitsModelConfig(
                    model = onnx.absolutePath,
                    tokens = File(folder, "tokens.txt").absolutePath,
                    dataDir = File(folder, "espeak-ng-data").absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.06f
                )
                val model = OfflineTtsModelConfig(vits = vits, numThreads = 2, provider = "cpu")
                tts = OfflineTts(config = OfflineTtsConfig(model = model))
                engine = tts
            }
            val speech = text.take(1000)
            val audio = tts.generate(speech, sid = 0, speed = 0.93f)
            if (audio.samples.isEmpty()) throw IOException("Piper produced empty audio")
            val output = File(context.cacheDir, "jarvis_piper_reply.wav")
            if (!audio.save(output.absolutePath) || output.length() < 512) {
                throw IOException("Failed to save Piper audio")
            }
            return output.absolutePath
        }
    }
}
