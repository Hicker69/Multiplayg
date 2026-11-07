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
import android.view.View
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
    private var turn = 0 // 0 = host, 1 = guest
    private var mySide = 0
    private var round = 1
    private var score = intArrayOf(0, 0)
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var board: TextView
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
            val p = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            val missing = p.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 7)
        }
    }

    private fun text(s: String, size: Float = 16f): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(0xFFF8FAFC.toInt()); gravity = Gravity.CENTER
        setPadding(12, 10, 12, 10)
    }
    private fun button(s: String, action: () -> Unit): Button = Button(this).apply {
        text = s; isAllCaps = false; setOnClickListener { action() }
    }
    private fun edit(hintText: String): EditText = EditText(this).apply {
        hint = hintText; setSingleLine(true); textSize = 17f; setPadding(14, 8, 14, 8)
    }
    private fun page(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18, 18, 18, 18); setBackgroundColor(0xFF111827.toInt())
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(text(title, 28f))
    }
    private fun buildHome() {
        page("MULTIPLAY")
        root.addView(text("OFFLINE • BLUETOOTH • TWO PLAYERS", 13f))
        nameInput = edit("Your player name")
        root.addView(nameInput)
        root.addView(button("Create game (Host)") { startHost() })
        root.addView(button("Join paired phone (Guest)") { showPairedDevices() })
        root.addView(button("How to connect") {
            page("CONNECT TWO PHONES")
            root.addView(text("1. Turn Bluetooth on on both phones.\n2. Pair the phones in Android Bluetooth Settings.\n3. On phone 1 tap Create game.\n4. On phone 2 tap Join paired phone and select phone 1.\n5. Both players enter two secret words and start."))
            root.addView(button("Back") { buildHome() })
        })
        root.addView(text("No internet or account required. Pair the phones in Android Settings first.", 13f))
    }
    private fun startHost() {
        if (!readyBt()) return
        host = true; mySide = 0
        playerName = nameInput.text.toString().ifBlank { "Host" }
        page("Waiting for player 2…")
