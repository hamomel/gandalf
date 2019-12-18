package server

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry
import com.github.sarxos.webcam.ds.ipcam.IpCamDriver
import com.github.sarxos.webcam.ds.ipcam.IpCamMode
import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private const val TOKEN_LIFETIME = 30000

class Guard {
    private val tokensWithLifetime = ConcurrentHashMap<String, Long>()
    private val tokensWithId = ConcurrentHashMap<String, Passport>()
    private val gate = Gate()

    init {
        GlobalScope.launch {
            while (true) {
                delay(500)

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

    fun start(cameraIP: String?) {
        cameraIP?.let {
            Webcam.setDriver(IpCamDriver())
            IpCamDeviceRegistry.register("DroidCam", "http://$it:4747/mjpegfeed?640x480", IpCamMode.PUSH)
        }
        val webcam = Webcam.getWebcams()[0]
        webcam.viewSize = WebcamResolution.VGA.size

        GlobalScope.launch {
            do {
                delay(100)

                var result: Result?
                var image: BufferedImage?
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

    fun addToken(token: String, document: Passport): Long {
        val validTill = System.currentTimeMillis() + TOKEN_LIFETIME
        tokensWithLifetime[token] = validTill
        tokensWithId[token] = document
        println("added token: $token")
        return validTill
    }

    fun getHash(passport: Passport): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val string = passport.toString()
        val bytes = digest.digest(string.toByteArray(StandardCharsets.UTF_8))
        return String(bytes)
    }

    private fun processCode(code: String) {
        println("captured code: $code")
        if (tokensWithLifetime[code]?.let { it > System.currentTimeMillis() } == true) {
            tokensWithId[code]?.let {
                println("code is valid: $code")
                tokensWithLifetime.remove(code)
                tokensWithId.remove(code)
                gate.open(getHash(it))
            }
        }
    }
}