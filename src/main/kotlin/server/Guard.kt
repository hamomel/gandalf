package server

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry
import com.github.sarxos.webcam.ds.ipcam.IpCamDriver
import com.github.sarxos.webcam.ds.ipcam.IpCamMode
import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val TOKEN_LIFETIME = 30000

class Guard {
    private val tokensWithLifetime = ConcurrentHashMap<String, Long>()
    private val tokensWithId = ConcurrentHashMap<String, String>()
    private val gate = Gate()

    init {
        thread {
            while (true) {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {

                }

                val iterator = tokensWithLifetime.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    if (next.value < System.currentTimeMillis()) {
                        iterator.remove()
                        tokensWithId.remove(next.key)
                    }
                }
            }
        }
    }

    fun addToken(token: String, userId: String): Long {
        val validTill = System.currentTimeMillis() + TOKEN_LIFETIME
        tokensWithLifetime[token] = validTill
        tokensWithId[token] = userId
        Log.d("added token: $token")
        return validTill
    }

    fun init(cameraIP: String?) {
        cameraIP?.let {
            Webcam.setDriver(IpCamDriver())
            IpCamDeviceRegistry.register("DroidCam", "http://$it:4747/mjpegfeed?640x480", IpCamMode.PUSH)
        }
        val webcam = Webcam.getWebcams()[0]
        webcam.viewSize = WebcamResolution.VGA.size

        thread {
            do {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                var result: Result? = null
                var image: BufferedImage? = null
                if (webcam.isOpen) {
                    image = webcam.image ?: continue
                    val source: LuminanceSource = BufferedImageLuminanceSource(image)
                    val bitmap = BinaryBitmap(HybridBinarizer(source))
                    try {
                        result = MultiFormatReader().decode(bitmap)
                    } catch (e: NotFoundException) {
                        // fall thru, it means there is no QR code in image
                        continue
                    }
                } else {
                    webcam.open()
                    continue
                }

                result?.let { processCode(it.text) }

            } while (true)
        }
    }

    private fun processCode(code: String) {
        Log.d("captured code: $code")
        if (tokensWithLifetime[code]?.let { it > System.currentTimeMillis() } == true ) {
            tokensWithId[code]?.let {
                Log.d("code is valid: $code")
                tokensWithLifetime.remove(code)
                tokensWithId.remove(code)
                gate.open(it)
            }
        }
    }
}