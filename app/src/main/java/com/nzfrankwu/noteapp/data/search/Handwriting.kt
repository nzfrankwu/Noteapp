package com.nzfrankwu.noteapp.data.search

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.nzfrankwu.noteapp.domain.ink.HandwritingLines
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads handwriting as text, one line at a time (PHASE-7.md 7.10). A fake in tests. */
interface HandwritingReader {
    /**
     * True when lines can be read now. May download what's needed first (on Wi-Fi only);
     * false if that isn't possible yet (the indexer tries again next time).
     */
    suspend fun prepare(): Boolean

    /**
     * The possible readings of one line, most likely first (empty if nothing could be read).
     * [before] is the text just before it (the line above), which helps the recogniser.
     */
    suspend fun read(line: HandwritingLines.Line, before: String = ""): List<String>
}

/**
 * Handwriting recognition with Google's ML Kit Digital Ink Recognition (the user's OK, 18 Nov 2026).
 * It runs on the tablet: the writing never leaves it. The English model (about 20 MB) is
 * downloaded once from Google, on Wi-Fi only, the first time it's needed.
 */
class MlKitHandwriting(
    private val languageTag: String = "en-US",
    /** Called when the model has finished downloading (e.g. to read handwriting now). */
    private val onModelReady: () -> Unit = {},
) : HandwritingReader {
    private val model: DigitalInkRecognitionModel? =
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)?.let { DigitalInkRecognitionModel.builder(it).build() }
    @Volatile private var recognizer: DigitalInkRecognizer? = null
    @Volatile private var downloading = false

    /**
     * True once the model is on the device. Otherwise it asks for the download (once at a time,
     * Wi-Fi only) and returns false straight away; [onModelReady] runs when it has arrived.
     */
    override suspend fun prepare(): Boolean {
        val m = model ?: return false
        if (recognizer != null) return true
        val manager = RemoteModelManager.getInstance()
        val have = runCatching { manager.isModelDownloaded(m).await() }.getOrDefault(false)
        if (!have) {
            if (!downloading) {
                downloading = true
                manager.download(m, DownloadConditions.Builder().requireWifi().build())
                    .addOnSuccessListener { onModelReady() }
                    .addOnCompleteListener { downloading = false }
            }
            return false
        }
        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
        return true
    }

    override suspend fun read(line: HandwritingLines.Line, before: String): List<String> {
        val r = recognizer ?: return emptyList()
        val ink = Ink.builder()
        var start = 0L
        for (stroke in line.strokes) {
            val s = Ink.Stroke.builder()
            for (i in 0 until stroke.pointCount) s.addPoint(Ink.Point.create(stroke.xs[i], stroke.ys[i], start + stroke.times[i]))
            ink.addStroke(s.build())
            start += (stroke.times.lastOrNull() ?: 0) + STROKE_GAP_MS
        }
        val area = WritingArea(line.right - line.left + 40f, line.bottom - line.top + 20f)
        // The line above as context (ML Kit uses up to the last 20 characters).
        val context = RecognitionContext.builder().setPreContext(before.takeLast(PRE_CONTEXT)).setWritingArea(area).build()
        val result = runCatching { r.recognize(ink.build(), context).await() }.getOrNull() ?: return emptyList()
        // Messy writing: the right reading is often 2nd or 3rd, so keep a few (search looks at all).
        return result.candidates.mapNotNull { it.text?.trim()?.ifEmpty { null } }.distinct().take(MAX_READINGS)
    }

    private companion object {
        const val STROKE_GAP_MS = 120L
        const val PRE_CONTEXT = 20
        const val MAX_READINGS = 5
    }
}

/** Waits for a Play Services task without blocking a thread. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
