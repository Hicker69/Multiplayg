                runOnUiThread { showWordEntry() }
            } catch (e: Exception) { runOnUiThread { status.text = "Could not connect: ${e.message}" } }
        }
    }
    private fun readyBt(): Boolean {
        if (adapter == null) { toast("This phone does not support Bluetooth."); return false }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBtPermissions(); toast("Allow Bluetooth permissions, then try again."); return false
        }
        if (adapter?.isEnabled != true) { toast("Please turn Bluetooth on in Android Settings."); return false }
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
                    runOnUiThread { receive(line) }
                }
            } catch (_: Exception) { runOnUiThread { toast("Bluetooth disconnected.") } }
        }
    }
    private fun send(line: String) { try { writer?.println(line) } catch (_: Exception) { toast("Send failed.") } }
    private fun showWordEntry() {
        page("Set your secret words")
        root.addView(text("Your opponent will guess these. Keep them secret!"))
        wordOne = edit("Secret word 1 (3–12 letters)")
        wordTwo = edit("Secret word 2 (3–12 letters)")
        root.addView(wordOne); root.addView(wordTwo)
        root.addView(button("Ready / Send words") {
            val a = wordOne.text.toString().trim().uppercase()
            val b = wordTwo.text.toString().trim().uppercase()
            if (!a.matches(Regex("[A-Z]{3,12}")) || !b.matches(Regex("[A-Z]{3,12}"))) {
                toast("Enter two words using 3–12 letters each."); return@button
            }
            localWords = listOf(a, b)
            send("WORDS|$a|$b")
            status = text("Words sent. Waiting for the other player…")
            root.addView(status)
            if (remoteWords.all { it.isNotBlank() }) startRound()
        })
    }
    private fun receive(line: String) {
        val p = line.split("|")
        when (p.firstOrNull()) {
            "WORDS" -> if (p.size == 3) { remoteWords = listOf(p[1], p[2]); if (localWords.all { it.isNotBlank() }) startRound() }
            "GUESS" -> if (p.size == 2 && p[1].length == 1) applyIncomingGuess(p[1][0])
            "WIN" -> showOpponentWinner()
            "RESET" -> { guessed.clear(); wrong = 0; turn = 0; round++; showBoard() }
        }
    }
    private fun startRound() {
        guessed.clear(); wrong = 0; turn = 0; round = 1
        showBoard()
    }
    private fun applyGuess(c: Char, transmit: Boolean) {
        if (c !in 'A'..'Z' || c in guessed || turn != mySide) return
        guessed.add(c)
        if (c !in remoteWords.joinToString("")) wrong++
        if (transmit) send("GUESS|$c")
        if (remoteWords.all { w -> w.all { it in guessed } }) {
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
        if (c !in remoteWords.joinToString("")) wrong++
        if (remoteWords.all { w -> w.all { it in guessed } }) {
            score[1 - mySide]++
            showOpponentWinner()
        } else {
            turn = 1 - turn
            showBoard()
        }
    }
    private fun showOpponentWinner() {
        page("OPPONENT WINS THIS ROUND")
        root.addView(text("Score: ${score[0]} — ${score[1]}", 22f))
        root.addView(button("Next round") {
            guessed.clear(); wrong = 0; turn = 0; round++
            if (host) send("RESET")
            showBoard()
        })
        root.addView(button("Home") { closeConnection(); buildHome() })
    }
    private fun showBoard() {
        page("WORD GUESS • ROUND $round")
        root.addView(text("You: ${if (mySide == 0) "HOST" else "GUEST"}     Score  ${score[0]} — ${score[1]}"))
        root.addView(text(if (turn == mySide) "YOUR TURN — choose one letter" else "OPPONENT'S TURN"))
        root.addView(text("Word 1\n${mask(remoteWords.getOrElse(0) { "" })}", 24f))
        root.addView(text("Word 2\n${mask(remoteWords.getOrElse(1) { "" })}", 24f))
        root.addView(text("Wrong guesses: $wrong / 6"))
        keyboard = GridLayout(this).apply { columnCount = 7 }
        ('A'..'Z').forEach { c ->
            val b = Button(this).apply {
                text = if (c in guessed) "·" else c.toString()
                isEnabled = c !in guessed && turn == mySide
                setOnClickListener { applyGuess(c, true) }
            }
            keyboard.addView(b, GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
        }
        root.addView(keyboard)
        root.addView(button("Disconnect / Home") { closeConnection(); buildHome() })
    }
    private fun mask(w: String): String = w.map { if (it in guessed) it else '_' }.joinToString(" ")
    private fun showWinner() {
        page("YOU WIN THIS ROUND!")
        root.addView(text("Both words guessed. Score: ${score[0]} — ${score[1]}", 22f))
        root.addView(button("Next round") {
            guessed.clear(); wrong = 0; turn = 0; round++
            send("RESET")
            showBoard()
        })
        root.addView(button("Home") { closeConnection(); buildHome() })
    }
    private fun closeConnection() { try { socket?.close() } catch (_: Exception) {}; socket = null; writer = null; reader = null }
    private fun toast(s: String) = runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    override fun onDestroy() { closeConnection(); super.onDestroy() }
}
