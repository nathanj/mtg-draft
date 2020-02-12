package javalinvue

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.javalin.websocket.WsContext
import java.util.concurrent.ConcurrentHashMap

val rooms = ConcurrentHashMap<String, DraftRoom>()

data class WsContextData(var room: DraftRoom? = null)

val contextData = ConcurrentHashMap<WsContext, WsContextData>()
var nextUserNumber = 1

@JsonIgnoreProperties(ignoreUnknown = true)
class Card(val name: String, val set_type: String, val image_uris: ImageUris?, val colors: Array<String>?, val card_faces: Array<CardFaces>?) : Comparable<Card> {
    @JsonIgnoreProperties(ignoreUnknown = true)
    class ImageUris(val normal: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    class CardFaces(val image_uris: ImageUris?)

    override fun compareTo(other: Card): Int {
        var ret = colorsString.length.compareTo(other.colorsString.length)
        if (ret != 0)
            return ret
        ret = colorsString.compareTo(other.colorsString)
        if (ret != 0)
            return ret
        ret = name.compareTo(other.name)
        return ret
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Card

        if (name != other.name) return false

        return true
    }

    override fun hashCode() = name.hashCode()
    override fun toString() = "Card { colors:$colorsString name:$name }"

    val imageUri
        get() = image_uris?.normal ?: card_faces?.get(0)?.image_uris?.normal
    private val colorsString
        get() = colors?.sorted()?.joinToString("") ?: ""
}

val INDEXES = mapOf(
    "row1" to listOf(0, 1, 2),
    "row2" to listOf(3, 4, 5),
    "row3" to listOf(6, 7, 8),
    "col1" to listOf(0, 3, 6),
    "col2" to listOf(1, 4, 7),
    "col3" to listOf(2, 5, 8)
)

class DraftRoom(val session: String, val cube: List<Card>) {
    var round = 0
    var turn = true
    var player1: Player? = null
    var player2: Player? = null
    val usernameMap = mutableMapOf<WsContext, Player>()
    lateinit var grid: MutableList<Card?>

    init {
        nextGrid()
    }

    fun nextGrid(): MutableList<Card?> {
        round++
        if (!finished()) {
            grid = cube.drop(round * 9).take(9).toMutableList()
        } else {
            grid = mutableListOf(null, null, null, null, null, null, null, null, null)
        }
        return grid
    }

    fun finished(): Boolean {
        return round == 16
    }

    fun addPlayer(player: Player) {
        usernameMap[player.socket] = player
        if (player1 == null) {
            player1 = player
            player.chat("You are now the first player in the draft.")
        } else if (player2 == null) {
            player2 = player
            player.chat("You are now the second player in the draft.")
        }
    }

    fun removePlayer(player: Player): Boolean {
        val existed = usernameMap.remove(player.socket) != null
        if (player1 == player)
            player1 = null
        else if (player2 == player)
            player2 = null
        return existed
    }

    fun empty(): Boolean {
        return player1 == null && player2 == null
    }

    private fun broadcast(command: String, args: Any) {
        usernameMap.forEach { (socket, _) ->
            val args = listOf(command, args)
            //println("args = ${args}")
            socket.send(args)
        }
    }

    fun chat(msg: String) {
        broadcast("chat", msg)
    }

    fun sendGrid() {
        broadcast("grid", grid.map { card -> card?.imageUri ?: "/img/back.jpg" })
    }

    fun sendTurn() {
        player1?.send("turn", listOf(round, turn, finished()))
        player2?.send("turn", listOf(round, !turn, finished()))
    }

    fun getPlayer(ctx: WsContext): Player? {
        return usernameMap[ctx]
    }

    override fun toString() = "Room { session:$session }"
}

class Player(var name: String, val socket: WsContext) {
    fun chat(msg: String) {
        send("chat", msg)
    }

    fun send(command: String, args: Any) {
        val args = listOf(command, args)
        //println("args = ${args}")
        socket.send(args)
    }

    val cards = mutableListOf<Card>()

    override fun toString() = "Player { name:$name }"
}

