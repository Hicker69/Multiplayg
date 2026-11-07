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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val adapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    private val uuid = UUID.fromString(
        "8b7a9d10-1c2e-4f30-9a50-4d554c544950"
    )

    private var socket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private var host = false
    private var localWord = ""
    private var remoteWord = ""
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
    private lateinit var nameInput: EditText

    private var playerName = "Player"

    private val bg = 0xFF0B1020.toInt()
    private val panel = 0xFF151F35.toInt()
    private val accent = 0xFF647BFF.toInt()
    private val muted = 0xFF9AA8C7.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
        requestBtPermissions()
    }

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            val missing = permissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 7)
            }
        }
    }

    private fun rounded(
        color: Int,
        radius: Float = 18f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun text(
        s: String,
        size: Float = 16f
    ): TextView {
        return TextView(this).apply {
            text = s
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
            )
            setPadding(14, 12, 14, 12)
        }
    }

    private fun button(
        s: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = s
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(accent, 16f)
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.BOLD
            )
            elevation = 3f
            setPadding(16, 8, 16, 8)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            lp.setMargins(0, 8, 0, 8)
            layoutParams = lp

            setOnClickListener { action() }
        }
    }

    private fun edit(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setSingleLine(true)
            textSize = 17f
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            background = rounded(panel, 14f)
            setPadding(18, 14, 18, 14)
        }
    }

    private fun gap(height: Int = 10) {
        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, height)
        )
    }

    private fun page(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 24, 22, 24)

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(bg, 0xFF17264A.toInt(), bg)
            )
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        }

        setContentView(scroll)

        root.addView(
            text("MULTIPLAY", 13f).apply {
                setTextColor(0xFF8FA3FF.toInt())
                letterSpacing = 0.18f
            }
        )

        root.addView(text(title, 27f))
        gap(8)
    }

    private fun buildHome() {
        page("MULTIPLAY")

        root.addView(
            text("OFFLINE • BLUETOOTH • TWO PLAYERS", 13f)
        )

        nameInput = edit("Your player name")
        root.addView(nameInput)

        root.addView(button("CREATE GAME • HOST") {
            startHost()
        })

        root.addView(button("JOIN GAME • GUEST") {
            showPairedDevices()
        })

        root.addView(button("HOW TO CONNECT") {
            page("CONNECT TWO PHONES")

            root.addView(
                text(
                    "1. Turn Bluetooth on on both phones.\n" +
                    "2. Pair the phones in Android Settings.\n" +
                    "3. Player 1 taps Create Game.\n" +
                    "4. Player 2 joins the paired phone.\n" +
                    "5. Each player selects ONE secret word."
                )
            )

            root.addView(button("BACK") {
                buildHome()
            })
        })

        root.addView(
            text("No internet or account required.", 13f)
        )
    }

    private fun startHost() {
        if (!readyBt()) return

        host = true
        mySide = 0

        playerName = nameInput.text.toString()
            .ifBlank { "Host" }

        page("Waiting for player 2…")

        status = text(
            "Keep this screen open. The other phone should join."
        )

        root.addView(status)

        root.addView(button("CANCEL") {
            closeConnection()
            buildHome()
        })

        thread {
            try {
                val server: BluetoothServerSocket =
                    adapter!!.listenUsingRfcommWithServiceRecord(
                        "MULTIPLAY",
                        uuid
                    )

                val connection = server.accept()
                server.close()

                attach(connection)

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

        playerName = nameInput.text.toString()
            .ifBlank { "Guest" }

        val devices = try {
            adapter!!.bondedDevices.toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        page("Choose paired phone")

        if (devices.isEmpty()) {
            root.addView(
                text(
                    "No paired devices found. Pair both phones in Bluetooth Settings."
                )
            )
        }

        devices.forEach { device ->
            root.addView(
                button(device.name ?: device.address) {
                    connect(device)
                }
            )
        }

        root.addView(button("BACK") {
            buildHome()
        })
    }

    private fun connect(device: BluetoothDevice) {
        page("Connecting…")

        status = text("Connecting to ${device.name ?: "phone"}")
        root.addView(status)

        thread {
            try {
                adapter?.cancelDiscovery()

                val connection =
                    device.createRfcommSocketToServiceRecord(uuid)

                connection.connect()
                attach(connection)

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

        if (
            Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
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

    private fun attach(connection: BluetoothSocket) {
        socket = connection
        writer = PrintWriter(connection.outputStream, true)
        reader = BufferedReader(
            InputStreamReader(connection.inputStream)
        )

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
        page("CHOOSE YOUR SECRET WORD")

        root.addView(
            text(
                "Each player selects ONE word. Keep it secret!",
                15f
            )
        )

        gap(12)

        wordOne = edit("Secret word (3–12 letters)")
        root.addView(wordOne)

        root.addView(button("SEND SECRET WORD") {
            val chosen = wordOne.text.toString()
                .trim()
                .uppercase()

            if (!chosen.matches(Regex("[A-Z]{3,12}"))) {
                toast("Enter one word using 3–12 letters.")
                return@button
            }

            localWord = chosen
            send("WORD|$chosen")

            status = text(
                "Word sent. Waiting for the other player…",
                15f
            )

            root.addView(status)

            if (remoteWord.isNotBlank()) {
                startRound()
            }
        })
    }

    private fun receive(line: String) {
        val parts = line.split("|")

        when (parts.firstOrNull()) {

            "WORD" -> {
                if (parts.size == 2) {
                    remoteWord = parts[1]

                    if (localWord.isNotBlank()) {
                        startRound()
                    }
                }
            }

            "GUESS" -> {
                if (parts.size == 2 && parts[1].length == 1) {
                    applyIncomingGuess(parts[1][0])
                }
            }

            "WIN" -> {
                showOpponentWinner()
            }

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
        if (
            c !in 'A'..'Z' ||
            c in guessed ||
            turn != mySide
        ) return

        guessed.add(c)

        if (c !in remoteWord) {
            wrong++
        }

        if (transmit) {
            send("GUESS|$c")
        }

        if (remoteWord.all { it in guessed }) {
            score[mySide]++
            send("WIN")
            showWinner()
        } else {
            turn = 1 - turn
            showBoard()
        }
    }

    // FIXED: Incoming guesses are checked against THIS player's secret word.
    private fun applyIncomingGuess(c: Char) {
        if (c !in 'A'..'Z' || c in guessed) return

        guessed.add(c)

        if (c !in localWord) {
            wrong++
        }

        if (localWord.all { it in guessed }) {
            score[1 - mySide]++
            showOpponentWinner()
        } else {
            turn = 1 - turn
            showBoard()
        }
    }

    private fun showOpponentWinner() {
        page("OPPONENT WINS!")

        root.addView(
            text("Score: ${score[0]} — ${score[1]}", 22f)
        )

        root.addView(button("NEXT ROUND") {
            guessed.clear()
            wrong = 0
            turn = 0
            round++

            if (host) {
                send("RESET")
            }

            showBoard()
        })

        root.addView(button("HOME") {
            closeConnection()
            buildHome()
        })
    }

    private fun showBoard() {
        page("WORD GUESS • ROUND $round")

        root.addView(
            text(
                "You: ${if (mySide == 0) "HOST" else "GUEST"}\n" +
                "Score: ${score[0]} — ${score[1]}"
            )
        )

        root.addView(
            text(
                if (turn == mySide) {
                    "YOUR TURN — CHOOSE A LETTER"
                } else {
                    "OPPONENT'S TURN"
                },
                17f
            )
        )

        root.addView(
            text(
                "OPPONENT'S SECRET WORD\n${mask(remoteWord)}",
                25f
            ).apply {
                background = rounded(panel, 18f)
                setPadding(18, 24, 18, 24)
            }
        )

        root.addView(text("Wrong guesses: $wrong / 6"))

        keyboard = GridLayout(this).apply {
            columnCount = 7
        }

        ('A'..'Z').forEach { c ->
            val keyButton = Button(this).apply {
                text = if (c in guessed) "·" else c.toString()
                isEnabled = c !in guessed && turn == mySide

                setOnClickListener {
                    applyGuess(c, true)
                }
            }

            keyboard.addView(
                keyButton,
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

        root.addView(button("DISCONNECT / HOME") {
            closeConnection()
            buildHome()
        })
    }

    private fun mask(word: String): String {
        return word.map {
            if (it in guessed) it else '_'
        }.joinToString(" ")
    }

    private fun showWinner() {
        page("YOU WIN!")

        root.addView(
            text(
                "Secret word guessed!\n" +
                "Score: ${score[0]} — ${score[1]}",
                22f
            )
        )

        root.addView(button("NEXT ROUND") {
            guessed.clear()
            wrong = 0
            turn = 0
            round++
            send("RESET")
            showBoard()
        })

        root.addView(button("HOME") {
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

    private fun toast(s: String) {
        runOnUiThread {
            Toast.makeText(
                this,
                s,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        closeConnection()
        super.onDestroy()
    }
}
