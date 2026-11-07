package com.multiplay.offline

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val adapter: BluetoothAdapter? get() = BluetoothAdapter.getDefaultAdapter()
    private val uuid: UUID = UUID.fromString("8b7a9d10-1c2e-4f30-9a50-4d554c544950")
    private var socket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var host = false
    private var localWords = listOf("", "")
    private var remoteWords = listOf("", "")
    private var guessed = mutableSetOf<Char>()
    private var wrong = 0
    private var turn = 0
    private var mySide = 0
    private var round = 1
    private var score = intArrayOf(0, 0)
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var keyboard: GridLayout
    private lateinit var wordOne: EditText
    private lateinit var wordTwo: EditText
    private lateinit var nameInput: EditText
    private var playerName = "Player"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
        requestBtPermissions()
    }

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val p = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = p.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 7)
            }
        }
    }

    private fun text(s: String, size: Float = 16f): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(0xFFF8FAFC.toInt())
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 10)
        }

    private fun button(s: String, action: () -> Unit): Button =
        Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun edit(hintText: String): EditText =
        EditText(this).apply {
            hint = hintText
            setSingleLine(true)
            textSize = 17f
            setPadding(14, 8, 14, 8)
        }

    private fun page(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF111827.toInt())
        }
        val scroll = ScrollView(this).apply {
            addView(root)
        }
        setContentView(scroll)
        root.addView(text(title, 28f))
    }

    private fun buildHome() {
        page("MULTIPLAY")
        root.addView(text("OFFLINE • BLUETOOTH • TWO PLAYERS", 13f))
        nameInput = edit("Your player name")
        root.addView(nameInput)

        root.addView(button("Create game (Host)") {
            startHost()
        })

        root.addView(button("Join paired phone (Guest)") {
            showPairedDevices()
        })

        root.addView(button("How to connect") {
            page("CONNECT TWO PHONES")
            root.addView(text(
                "1. Turn Bluetooth on on both phones.\n" +
                "2. Pair the phones in Android Bluetooth Settings.\n" +
                "3. On phone 1 tap Create game.\n" +
                "4. On phone 2 tap Join paired phone and select phone 1.\n" +
                "5. Both players enter two secret words and start."
            ))
            root.addView(button("Back") {
                buildHome()
            })
        })

        root.addView(text(
            "No internet or account required. Pair the phones in Android Settings first.",
            13f
        ))
    }

    private fun startHost() {
        if (!readyBt()) return

        host = true
        mySide = 0
        playerName = nameInput.text.toString().ifBlank { "Host" }

        page("Waiting for player 2…")
        status = text(
            "Keep this screen open. The other phone should join from its paired-device list."
        )
        root.addView(status)

        root.addView(button("Cancel") {
            closeConnection()
            buildHome()
        })

        thread {
            try {
                val server: BluetoothServerSocket =
                    adapter!!.listenUsingRfcommWithServiceRecord("MULTIPLAY", uuid)

                val s = server.accept()
                server.close()
                attach(s)

                runOnUiThread {
                    showWordEntry()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Connection failed: ${e.message}"
                }
            }
        }
    }

    private fun showPairedDevices() {
        if (!readyBt()) return

        host = false
        mySide = 1
        playerName = nameInput.text.toString().ifBlank { "Guest" }

        val devices = try {
            adapter!!.bondedDevices.toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        page("Choose paired phone")

        if (devices.isEmpty()) {
            root.addView(text(
                "No paired devices found. Pair both phones in Android Bluetooth Settings first."
            ))
        }

        devices.forEach { d ->
            root.addView(button(d.name ?: d.address) {
                connect(d)
            })
        }

        root.addView(button("Back") {
            buildHome()
        })
    }

    private fun connect(d: BluetoothDevice) {
        page("Connecting…")
        status = text("Connecting to ${d.name ?: "phone"}")
        root.addView(status)

        thread {
            try {
                adapter?.cancelDiscovery()
                val s = d.createRfcommSocketToServiceRecord(uuid)
                s.connect()
                attach(s)

                runOnUiThread {
                    showWordEntry()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Could not connect: ${e.message}"
                }
            }
        }
    }

    private fun readyBt(): Boolean {
        if (adapter == null) {
            toast("This phone does not support Bluetooth.")
            return false
        }

        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBtPermissions()
            toast("Allow Bluetooth permissions, then try again.")
            return false
        }

        if (adapter?.isEnabled != true) {
            toast("Please turn Bluetooth on in Android Settings.")
            return false
        }

        return true
    }

    private fun attach(s: BluetoothSocket) {
        socket = s
        writer = PrintWriter(s.outputStream, true)
        reader = BufferedReader(InputStreamReader(s.inputStream))

        thread {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    runOnUiThread {
                        receive(line)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    toast("Bluetooth disconnected.")
                }
            }
        }
    }

    private fun send(line: String) {
        try {
            writer?.println(line)
        } catch (_: Exception) {
            toast("Send failed.")
        }
    }

    private fun showWordEntry() {
        page("Set your secret words")
        root.addView(text(
            "Your opponent will guess these. Keep them secret!"
        ))

        wordOne = edit("Secret word 1 (3–12 letters)")
        wordTwo = edit("Secret word 2 (3–12 letters)")

        root.addView(wordOne)
        root.addView(wordTwo)

        root.addView(button("Ready / Send words") {
            val a = wordOne.text.toString().trim().uppercase()
            val b = wordTwo.text.toString().trim().uppercase()

            if (!a.matches(Regex("[A-Z]{3,12}")) ||
                !b.matches(Regex("[A-Z]{3,12}"))
            ) {
                toast("Enter two words using 3–12 letters each.")
                return@button
            }

            localWords = listOf(a, b)
            send("WORDS|$a|$b")

            status = text("Words sent. Waiting for the other player…")
            root.addView(status)

            if (remoteWords.all { it.isNotBlank() }) {
                startRound()
            }
        })
    }

    private fun receive(line: String) {
        val p = line.split("|")

        when (p.firstOrNull()) {
            "WORDS" -> if (p.size == 3) {
                remoteWords = listOf(p[1], p[2])
                if (localWords.all { it.isNotBlank() }) {
                    startRound()
                }
            }

            "GUESS" -> if (p.size == 2 && p[1].length == 1) {
                applyIncomingGuess(p[1][0])
            }

            "WIN" -> showOpponentWinner()

            "RESET" -> {
                guessed.clear()
                wrong = 0
                turn = 0
                round++
                showBoard()
            }
        }
    }

    private fun startRound() {
        guessed.clear()
        wrong = 0
        turn = 0
        round = 1
        showBoard()
    }

    private fun applyGuess(c: Char, transmit: Boolean) {
        if (c !in 'A'..'Z' || c in guessed || turn != mySide) return

        guessed.add(c)

        if (c !in remoteWords.joinToString("")) {
            wrong++
        }

        if (transmit) {
            send("GUESS|$c")
        }

        if (remoteWords.all { w ->
                w.all { it in guessed }
            }
        ) {
            score[mySide]++
            send("WIN")
            showWinner()
        } else {
            turn = 1 - turn
            showBoard()
        }
    }

    private fun applyIncomingGuess(c: Char) {
        if (c !in 'A'..'Z' || c in guessed) return

        guessed.add(c)

        if (c !in remoteWords.joinToString("")) {
            wrong++
        }

        if (remoteWords.all { w ->
                w.all { it in guessed }
            }
        ) {
            score[1 - mySide]++
            showOpponentWinner()
        } else {
            turn = 1 - turn
            showBoard()
        }
    }

    private fun showOpponentWinner() {
        page("OPPONENT WINS THIS ROUND")
        root.addView(text(
            "Score: ${score[0]} — ${score[1]}",
            22f
        ))

        root.addView(button("Next round") {
            guessed.clear()
            wrong = 0
            turn = 0
            round++

            if (host) {
                send("RESET")
            }

            showBoard()
        })

        root.addView(button("Home") {
            closeConnection()
            buildHome()
        })
    }

    private fun showBoard() {
        page("WORD GUESS • ROUND $round")

        root.addView(text(
            "You: ${if (mySide == 0) "HOST" else "GUEST"}     " +
            "Score  ${score[0]} — ${score[1]}"
        ))

        root.addView(text(
            if (turn == mySide) {
                "YOUR TURN — choose one letter"
            } else {
                "OPPONENT'S TURN"
            }
        ))

        root.addView(text(
            "Word 1\n${mask(remoteWords.getOrElse(0) { "" })}",
            24f
        ))

        root.addView(text(
            "Word 2\n${mask(remoteWords.getOrElse(1) { "" })}",
            24f
        ))

        root.addView(text("Wrong guesses: $wrong / 6"))

        keyboard = GridLayout(this).apply {
            columnCount = 7
        }

        ('A'..'Z').forEach { c ->
            val b = Button(this).apply {
                text = if (c in guessed) "·" else c.toString()
                isEnabled = c !in guessed && turn == mySide
                setOnClickListener {
                    applyGuess(c, true)
                }
            }

            keyboard.addView(
                b,
                GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                    )
                }
            )
        }

        root.addView(keyboard)

        root.addView(button("Disconnect / Home") {
            closeConnection()
            buildHome()
        })
    }

    private fun mask(w: String): String =
        w.map {
            if (it in guessed) it else '_'
        }.joinToString(" ")

    private fun showWinner() {
        page("YOU WIN THIS ROUND!")

        root.addView(text(
            "Both words guessed. Score: ${score[0]} — ${score[1]}",
            22f
        ))

        root.addView(button("Next round") {
            guessed.clear()
            wrong = 0
            turn = 0
            round++
            send("RESET")
            showBoard()
        })

        root.addView(button("Home") {
            closeConnection()
            buildHome()
        })
    }

    private fun closeConnection() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }

        socket = null
        writer = null
        reader = null
    }

    private fun toast(s: String) =
        runOnUiThread {
            Toast.makeText(
                this,
                s,
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onDestroy() {
        closeConnection()
        super.onDestroy()
    }
}
