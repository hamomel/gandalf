package server

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parametersOf
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*


private const val ID = "6e30b36c-fc53-4488-abf7-935daa3241b3"
private const val SECRET = "tJIdWLjoWcDKUWHJbKXSRQ8NaIOqoZAa5YhheKgdt80Kvc2tf0Uh42zu1SPxix2R"
private const val BASE_URL = "https://app.digital-id.kz/api/v1/oauth/"

private val guard = Guard()
private val client = HttpClient(OkHttp) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

fun main(args: Array<String>) = runBlocking {
    val cameraIP = if (args.isEmpty()) null else args[0]
    guard.start(cameraIP)
    embeddedServer(Netty, 8081, watchPaths = listOf("getToken"), module = Application::module).start()

    return@runBlocking
}

private fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        get("/getToken") {
            val accessCode = call.request.queryParameters["userId"]
            if (accessCode == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val didToken = withContext(Dispatchers.Default) {
                getToken(accessCode)
            }

            if (didToken == null) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val document = withContext(Dispatchers.Default) {
                getDocument(didToken)
            }

            if (getCardPosition(document) == null) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val token = UUID.randomUUID().toString()
            val validTill = guard.addToken(token, document)
            val json = "{ \"token\": \"$token\", \"valid_till\": $validTill }"

            call.respondText(json, ContentType.Application.Json)
        }
    }
}

private suspend fun getToken(accessCode: String): String? =
    try {
        client.post<String> {
            headers {
                headersOf("http basic auth", "$ID:$SECRET")
            }
            url("${BASE_URL}token")
            parametersOf(
                "grant_type" to listOf("authorization_code"),
                "grant_type" to listOf("authorization_code"),
                "code" to listOf(accessCode),
                "redirect_uri" to listOf("http://mobile_sdk")
            )
        }
    } catch (e: Throwable) {
        null
    }

private suspend fun getDocument(token: String): Passport =
    client.get {
        headersOf("Authorization", "Bearer $token")
        url("${BASE_URL}documents")
    }

fun getCardPosition(passport: Passport): Int? {
    val hash = guard.getHash(passport)
    val dir = System.getProperty("user.dir")
    val file = File(dir, "users.csv")
    file.readLines().forEach {
        val parts = it.split(",")
        if (parts[0] == hash) {
            return parts[1].toInt()
        }
    }

    return null
}
