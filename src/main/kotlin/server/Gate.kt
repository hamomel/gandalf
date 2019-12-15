package server

import com.pi4j.io.gpio.*
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent
import com.pi4j.io.gpio.event.GpioPinListener
import com.pi4j.io.gpio.event.GpioPinListenerDigital
import com.pi4j.platform.Platform
import com.pi4j.platform.PlatformManager
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

class Gate {

    private val ledPin: GpioPinDigitalOutput
    private val queue = ConcurrentLinkedQueue<Int>()

    init {
//        if (PlatformManager.getPlatform() == null) {
            PlatformManager.setPlatform(Platform.ORANGEPI)
//        }
        val gpio = GpioFactory.getInstance()
        ledPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_02, PinState.LOW)
        thread {
            while (true) {
                if (!queue.isEmpty()) {
                    doOpen(queue.poll())
                }
            }
        }
    }

    fun open(userId: String) {
        getCardPosition(userId)?.let {
            Log.d("adding to queue: $it")
            queue.add(it)
        }
    }

    private fun doOpen(position: Int) {
        Log.d("openinig $position")
        for (i in 0 until position) {
            ledPin.state = PinState.HIGH
            Thread.sleep(500)
            ledPin.state = PinState.LOW
            Thread.sleep(500)
        }
    }
}