package server

import org.slf4j.LoggerFactory

object Log {
    private val loger =  LoggerFactory.getLogger("gandalf")

    fun d(message: String) {
        loger.debug(message)
    }
}