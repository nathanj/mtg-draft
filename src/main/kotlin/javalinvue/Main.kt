package javalinvue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.Javalin
import io.javalin.plugin.rendering.vue.JavalinVue
import io.javalin.plugin.rendering.vue.VueComponent
import java.io.File
import kotlin.random.Random
import kotlin.random.nextUInt

private fun loadCards(): List<Card> {
    val start = System.currentTimeMillis()
    val json = jacksonObjectMapper()
    val cards = json.readValue<Array<Card>>(File("scryfall-oracle-cards.json"))
            .filter { c-> c.set_type != "token" }
            .sorted()
    val delta = System.currentTimeMillis() - start
    println("Loaded ${cards.size} cards in ${delta}ms")
    return cards
}

val CARDS = loadCards()

private fun findCard(name: String): Card {
    return (CARDS.find { card -> card.name == name }
        ?: CARDS.find { card -> card.name.startsWith("$name //") }
        ?: throw CardException("could not find card '$name'"))
}

private class CardException(reason: String) : RuntimeException(reason)

object Main {
    @JvmStatic
    fun main(args: Array<String>) {

        println("CARDS = ${CARDS.size}")

        val app = Javalin.create { config ->
            config.showJavalinBanner = false
            config.enableWebjars()
            config.requestLogger { ctx, ms ->
                println("${ctx.req.pathInfo} - ${ms.toInt()}ms")
            }
            config.addStaticFiles("public")
            JavalinVue.stateFunction = { ctx -> mapOf("currentUser" to ctx.basicAuthCredentials()?.username) }
        }.start(7001)

        app.post("/create") { ctx ->
            try {
                val cardList = ctx.formParam("cards")!!.replace("\r", "").split("\n")
                val cube = cardList.filter { it.trim().isNotEmpty() }.map(::findCard).shuffled()
                val requiredGridSize = 15 * 9
                if (cube.size < requiredGridSize)
                    throw CardException("Cube not large enough. Size is ${cube.size} but needs to be at least $requiredGridSize")
                val session = Random.nextUInt().toString()
                val room = DraftRoom(session, cube)
                println("Creating empty room '${room.session}'")
                rooms[session] = room
                ctx.redirect("/draft/$session")
            } catch (ex: CardException) {
                ctx.res.status = 500
                println("Error: ${ex.message}")
                ctx.result("Error: ${ex.message}")
            }
        }

        app.get("/draft/:session") { ctx ->
            val session = ctx.pathParam("session")
            var name = ctx.sessionAttribute<String>("name")
            if (name == null) {
                name = "User-${nextUserNumber++}"
                ctx.sessionAttribute("name", name)
            }
            val room = rooms[session]
            if (room == null) {
                ctx.redirect("/")
                return@get
            }
            println("got a room session=$session")
            VueComponent("<draft></draft>").handle(ctx)
        }

        app.get("/", VueComponent("<hello-world></hello-world>"))

        app.ws("/ws") { ws ->
            ws.onConnect { ctx ->
                contextData[ctx] = WsContextData()
                println("$ctx connect data=${contextData[ctx]}")
            }
            ws.onClose { ctx ->
                val data = contextData[ctx]!!
                println("$ctx close data=$data")
                data.room?.let { room ->
                    room.getPlayer(ctx)?.let { player ->
                        if (room.removePlayer(player))
                            room.chat("${player.name} has left")
                    }
                }
                contextData.remove(ctx)
            }
            ws.onMessage { ctx ->
                val data = contextData[ctx]!!
                val msg = ctx.message<List<String>>()
                println("$ctx message data=$data msg=$msg")
                when {
                    msg[0] == "chat" -> {
                        println("got chat")
                        data.room?.let { room ->
                            room.getPlayer(ctx)?.let { player ->
                                room.chat("${player.name}: ${msg[1]}")
                            }
                        }
                    }
                    msg[0] == "join" -> {
                        println("got join ${msg[1]}")
                        val username = "User" + nextUserNumber++
                        val player = Player(username, ctx)
                        val session = msg[1]
                        val room = rooms[session]
                        room?.apply {
                            addPlayer(player)
                            chat("${player.name} has joined")
                            sendGrid()
                            sendTurn()
                        }
                        data.room = room
                    }
                    msg[0] == "take" -> {
                        println("got take ${msg[1]}")
                        val room = data.room!!
                        val player = room.getPlayer(ctx)!!
                        val grid = room.grid
                        var tookSomething = false
                        INDEXES[msg[1]]?.forEach { v ->
                            grid[v].let { c ->
                                if (c != null) {
                                    println("$player: adding card '$c'")
                                    player.cards.add(c)
                                    player.cards.sort()
                                    tookSomething = true
                                    room.chat("${player.name} takes ${c.name}")
                                }
                            }
                            grid[v] = null
                        }
                        if (tookSomething) {
                            if (grid.count { c -> c != null } < 5) {
                                room.nextGrid()
                            } else {
                                room.turn = room.turn xor true
                            }
                            println("$player: sending cards")
                            player.send("cards", player.cards.map { c -> c.imageUri ?: "" })
                            player.send("cards_text", player.cards.map { c -> c.name })
                            room.sendGrid()
                            room.sendTurn()
                        }
                    }
                }
            }
        }
    }
}

