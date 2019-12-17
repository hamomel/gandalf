package server

import com.pi4j.io.gpio.*
import com.pi4j.io.gpio.event.GpioPinListenerDigital
import com.pi4j.platform.Platform
import com.pi4j.platform.PlatformManager
import com.pi4j.util.CommandArgumentParser.getPin
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread


class Gate {

    private val handStepPin: GpioPinDigitalOutput
        private val handDirectionPin: GpioPinDigitalOutput
    private val buttonPin: GpioPinDigitalInput
    private val armPwmPin: GpioPinDigitalOutput
    private val queue = ConcurrentLinkedQueue<Int>()
    private val processBuilder = ProcessBuilder()
    private val dir = System.getProperty("user.dir")
    private var isClampOpen = true
    private var timeout = System.currentTimeMillis()

    init {
//        if (PlatformManager.getPlatform() == null) {
        PlatformManager.setPlatform(Platform.ORANGEPI)
//        }
        val gpio = GpioFactory.getInstance()
        handStepPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_00, PinState.LOW)
        handStepPin.setShutdownOptions(true, PinState.LOW)
        handDirectionPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_01, PinState.LOW)
        handStepPin.setShutdownOptions(true, PinState.LOW)
        buttonPin = gpio.provisionDigitalInputPin(OrangePiPin.GPIO_02, "button", PinPullResistance.PULL_UP)
        buttonPin.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF)
        armPwmPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_05, "", PinState.LOW)

        gpio.addListener(GpioPinListenerDigital {
            if (it.state == PinState.LOW) {
                if (System.currentTimeMillis() - timeout < 500) return@GpioPinListenerDigital
                doOpen(50)
                move(3)
                timeout = System.currentTimeMillis()
            }
        }, buttonPin)
        thread {
            while (true) {
//                if (buttonPin.state == PinState.LOW) {
//                    doOpen(200)
//                }
                Thread.sleep(100)
            }
        }

//        thread {
//            while (true) {
//                if (!queue.isEmpty()) {
//                    doOpen(queue.poll())
//                }
//            }
//        }
    }

    fun move(angle: Int) {
        println("move")
        val address = "$dir/pythonpwm/testg.py"
        println(address)
        val arg = if (isClampOpen) "c" else "o"
        isClampOpen = !isClampOpen
        processBuilder.command("python", address, arg).start().waitFor()
        println("command sent")
    }

    fun open(userId: String) {
        getCardPosition(userId)?.let {
            Log.d("adding to queue: $it")
            queue.add(it)
        }
    }

    private fun doOpen(position: Int) {
        Log.d("openinig $position")
        handDirectionPin.toggle()
        for (i in 0 until position) {
            handStepPin.state = PinState.HIGH
            Thread.sleep(5)
            handStepPin.state = PinState.LOW
            Thread.sleep(5)
        }
    }
}