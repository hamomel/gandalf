package server

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File
import java.util.*

private var guard = Guard()

fun main(args: Array<String>) {
    val cameraIP = if (args.isEmpty()) null else args[0]
    guard.init(cameraIP)
    embeddedServer(Netty, 8081, watchPaths = listOf("getToken"), module = Application::module).start()
}

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        get("/getToken") {
            val userId = call.request.queryParameters["userId"]
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                if (getCardPosition(userId) == null) {
                    call.respond(HttpStatusCode.Forbidden)
                } else {
                    val token = UUID.randomUUID().toString()
                    val validTill = guard.addToken(token, userId)
                    val json = "{ \"token\": \"$token\", \"valid_till\": $validTill }"

                    call.respondText(json, ContentType.Application.Json)
                }
            }
        }
    }
}

fun getCardPosition(userId: String): Int? {
    val dir = System.getProperty("user.dir")
    val file = File(dir, "users.csv")
    file.readLines().forEach {
        val parts = it.split(",")
        if (parts[0] == userId) {
            return parts[1].toInt()
        }
    }

    return null
}