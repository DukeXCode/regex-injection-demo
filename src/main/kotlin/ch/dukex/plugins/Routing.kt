package ch.dukex.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        post("/email") {
            val receivedEmail = call.receive<String>()
            val regex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}""")
            if (regex.containsMatchIn(receivedEmail)) {
                call.respondText("Contains email", status = HttpStatusCode.Created)
            } else {
                call.respondText("Does not contain email", status = HttpStatusCode.BadRequest)
            }
        }
        get("/check") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
    }
}
