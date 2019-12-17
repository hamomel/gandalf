package server

import com.pi4j.io.gpio.*
import com.pi4j.platform.Platform
import com.pi4j.platform.PlatformManager
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

private const val MM_PER_STEP = 5.7 * 13 / 200
private val dir = System.getProperty("user.dir")
private val ARM_OPEN_COMMAND = listOf("python", "$dir/pythonpwm/arm.py", "o")
private val ARM_CLOSE_COMMAND = listOf("python", "$dir/pythonpwm/arm.py", "c")
private val ARM_UP_COMMAND = listOf("python", "$dir/pythonpwm/knee.py", "up")
private val ARM_DOWN_COMMAND = listOf("python", "$dir/pythonpwm/knee.py", "down")
private const val HAND_ANGLE = 165
private const val HAND_STEPS = (HAND_ANGLE / 1.8).toInt()

class Gate {

    private val handStepPin: GpioPinDigitalOutput
    private val handDirectionPin: GpioPinDigitalOutput
    private val bedStepPin: GpioPinDigitalOutput
    private val bedDirectionPin: GpioPinDigitalOutput
    private val endstopPin: GpioPinDigitalInput
    private val queue = ConcurrentLinkedQueue<Int>()
    private val processBuilder = ProcessBuilder()

    init {
        PlatformManager.setPlatform(Platform.ORANGEPI)
        val gpio = GpioFactory.getInstance()

        handStepPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_00, PinState.LOW)
        handStepPin.setShutdownOptions(true, PinState.LOW)
        handDirectionPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_01, PinState.LOW)
        handStepPin.setShutdownOptions(true, PinState.LOW)
        bedStepPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_02, PinState.LOW)
        bedStepPin.setShutdownOptions(true, PinState.LOW)
        bedDirectionPin = gpio.provisionDigitalOutputPin(OrangePiPin.GPIO_03, PinState.LOW)
        bedDirectionPin.setShutdownOptions(true, PinState.LOW)
        endstopPin = gpio.provisionDigitalInputPin(OrangePiPin.GPIO_12, "endstop")
        endstopPin.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF)

        upArm()
        homeBed()

        thread {
            while (true) {
                Thread.sleep(100)
                if (!queue.isEmpty()) {
                    doOpen(queue.poll())
                }
            }
        }
    }

    fun open(userId: String) {
        getCardPosition(userId)?.let {
            queue.add(it)
        }
    }

    fun doOpen(position: Int) {
        upArm()
        homeBed()
        Thread.sleep(500)
        val distance = position * 16
        moveBed(distance)
        openArm()
        downArm()
        closeArm()
        upArm()
        homeBed()
        Thread.sleep(500)
        downHand()
        Thread.sleep(3000)
        riseHand()
        moveBed(distance)
        downArm()
        openArm()
        upArm()
    }

    private fun openArm() {
        processBuilder.command(ARM_OPEN_COMMAND).start().waitFor()
    }

    private fun closeArm() {
        processBuilder.command(ARM_CLOSE_COMMAND).start().waitFor()
    }

    private fun upArm() {
        processBuilder.command(ARM_UP_COMMAND).start().waitFor()
    }

    private fun downArm() {
        processBuilder.command(ARM_DOWN_COMMAND).start().waitFor()
    }

    private fun riseHand() {
        handDirectionPin.high()
        for (i in 0 until HAND_STEPS) {
            stepHand()
        }
    }

    private fun downHand() {
        handDirectionPin.low()
        for (i in 0 until HAND_STEPS) {
            stepHand()
        }
    }

    private fun moveBed(distance: Int) {
        bedDirectionPin.low()
        val steps = distance / MM_PER_STEP
        println("move distance: ${steps.toInt()}")
        for (i in 0 until steps.toInt()) {
            stepBed()
        }
    }

    private fun homeBed() {
        bedDirectionPin.high()

        while (endstopPin.isHigh) {
            println("bed home")
            stepBed()
        }
    }

    private fun stepBed() {
        bedStepPin.state = PinState.HIGH
        Thread.sleep(5)
        bedStepPin.state = PinState.LOW
        Thread.sleep(5)
    }

    private fun stepHand() {
        handStepPin.state = PinState.HIGH
        Thread.sleep(10)
        handStepPin.state = PinState.LOW
        Thread.sleep(10)
    }
}