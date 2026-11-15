package com.nzfrankwu.noteapp.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.nzfrankwu.noteapp.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes ONE picture of the whole screen, other apps included (PHASE-3.md 3.9), for the screenshot
 * tool when Noteapp shares the screen (split screen or a floating window).
 *
 * Android requires screen capture to run in a foreground service of type "mediaProjection"
 * (Android 14+), started right after the person agrees in Android's own prompt. This service
 * starts, grabs a single frame, and stops at once; the notification shows only while it runs.
 * Nothing is recorded or kept: the frame goes straight to the crop screen.
 */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (data == null) {
            finish(null)
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val p = manager.getMediaProjection(resultCode, data) ?: run { finish(null); return START_NOT_STICKY }
            projection = p
            capture(p)
        } catch (e: Exception) {
            finish(null)
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Screenshot tool", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_crop)
            .setContentTitle("Taking a screenshot for your notebook")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun capture(p: MediaProjection) {
        val wm = getSystemService(WindowManager::class.java)
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.maximumWindowMetrics.bounds else null
        val metrics = resources.displayMetrics
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels
        val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = r
        // Android 14+ requires a callback before the virtual display is made.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { finish(null) }
        }, handler)
        display = p.createVirtualDisplay(
            "noteapp-screenshot", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler,
        )
        // Give Android's prompt a moment to disappear, then take the newest frame.
        var tries = 0
        val grab = object : Runnable {
            override fun run() {
                if (finished) return
                val image = r.acquireLatestImage()
                if (image == null) {
                    if (++tries < MAX_TRIES) handler.postDelayed(this, RETRY_MS) else finish(null)
                    return
                }
                val bitmap = try {
                    val plane = image.planes[0]
                    val rowPixels = plane.rowStride / plane.pixelStride
                    val full = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888)
                    full.copyPixelsFromBuffer(plane.buffer)
                    if (rowPixels == width) full else Bitmap.createBitmap(full, 0, 0, width, height).also { full.recycle() }
                } catch (e: Exception) {
                    null
                } finally {
                    image.close()
                }
                finish(bitmap)
            }
        }
        handler.postDelayed(grab, FIRST_FRAME_DELAY_MS)
    }

    private fun finish(bitmap: Bitmap?) {
        if (finished) return
        finished = true
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        ScreenCapture.deliver(bitmap)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL = "screenshot"
        private const val NOTIFICATION_ID = 7301
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private const val FIRST_FRAME_DELAY_MS = 450L
        private const val RETRY_MS = 100L
        private const val MAX_TRIES = 30
    }
}

/** Starts [ScreenCaptureService] and waits for its one picture of the screen. */
object ScreenCapture {
    @Volatile private var pending: CompletableDeferred<Bitmap?>? = null

    internal fun deliver(bitmap: Bitmap?) {
        pending?.complete(bitmap)
        pending = null
    }

    /**
     * After the person agreed in Android's capture prompt ([resultCode], [data]): the whole screen
     * as a picture, or null if it couldn't be taken.
     */
    suspend fun capture(context: Context, resultCode: Int, data: Intent): Bitmap? {
        val result = CompletableDeferred<Bitmap?>()
        pending = result
        val intent = Intent(context, ScreenCaptureService::class.java)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenCaptureService.EXTRA_DATA, data)
        return try {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            withTimeoutOrNull(8_000) { result.await() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * [bitmap] as an ordinary bitmap whose pixels can be read (a GPU "hardware" bitmap, as a
     * captured Compose layer gives, can't be): a copy if needed.
     */
    fun readable(bitmap: Bitmap): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

    /** True when (nearly) every pixel is black: the app on screen doesn't allow screenshots. */
    fun isAllBlack(picture: Bitmap): Boolean {
        val bitmap = readable(picture)
        val stepX = maxOf(1, bitmap.width / 80)
        val stepY = maxOf(1, bitmap.height / 80)
        var total = 0
        var black = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                total++
                if (((c shr 16) and 0xFF) < 14 && ((c shr 8) and 0xFF) < 14 && (c and 0xFF) < 14) black++
                x += stepX
            }
            y += stepY
        }
        return total > 0 && black >= total * 0.98
    }
}
