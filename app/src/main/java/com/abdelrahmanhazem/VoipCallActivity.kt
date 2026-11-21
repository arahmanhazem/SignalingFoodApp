
package com.abdelrahmanhazem

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors


class VoipCallActivity : AppCompatActivity() {

    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var callButton: Button

    private lateinit var listenButton: Button
    private lateinit var endCallButton: Button
    private lateinit var callStatus: TextView

    // Stores the port selected by the user for incoming calls.
    private var listeningPort: Int? = null

    private val executor = Executors.newCachedThreadPool()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    // Prevents multiple incoming call requests from being handled simultaneously.
    @Volatile
    private var incomingCallPending = false

    @Volatile
    private var running = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voip_call)

        ipInput = findViewById(R.id.ipAddressInput)
        portInput = findViewById(R.id.portInput)
        callButton = findViewById(R.id.callButton)
        listenButton = findViewById(R.id.listenButton)
        endCallButton = findViewById(R.id.endCallButton)
        callStatus = findViewById(R.id.callStatus)

        listenButton.setOnClickListener {
            startListening()
        }

        callButton.setOnClickListener {
            startCall()
        }

        endCallButton.setOnClickListener {
            endCall()
        }
    }

    // Start listening for incoming calls on the port entered by the user.
    private fun startListening() {
        val portText = portInput.text.toString().trim()
        val port = portText.toIntOrNull()

        if (port == null || port !in 1024..65535) {
            Toast.makeText(
                this,
                "Enter a valid port between 1024 and 65535",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (serverSocket != null && serverSocket?.isClosed == false) {
            Toast.makeText(
                this,
                "Already listening for calls",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        listeningPort = port

        executor.execute {
            try {
                serverSocket = ServerSocket(port)

                runOnUiThread {
                    callStatus.text = "Ready - Waiting for calls on port $port"
                }

                while (running) {
                    val client = serverSocket?.accept() ?: break

                    if (!running) {
                        client.close()
                        break
                    }

                    handleIncomingCall(client)
                }

            } catch (e: Exception) {
                if (running) {
                    runOnUiThread {
                        callStatus.text = "Server error: ${e.message}"
                    }
                }
            }
        }
    }

    // Handle a call request from another user.
    private fun handleIncomingCall(client: Socket) {
        executor.execute {
            try {
                val reader = BufferedReader(
                    InputStreamReader(client.getInputStream())
                )

                val writer = PrintWriter(
                    client.getOutputStream(), true
                )

                val request = reader.readLine()

                if (request != "CALL_REQUEST") {
                    client.close()
                    return@execute
                }
                synchronized(this@VoipCallActivity) {
                    if (incomingCallPending || activeSocket != null) {
                        writer.println("REJECT")
                        client.close()
                        return@execute
                    }

                    incomingCallPending = true
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        client.close()
                        return@runOnUiThread
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Incoming VoIP Call")
                        .setMessage("Someone is calling you. Do you want to accept?")
                        .setPositiveButton("Accept") { _, _ ->
                            activeSocket = client
                            incomingCallPending = false
                            writer.println("ACCEPT")

                            callStatus.text = "Call connected"
                            callButton.isEnabled = false
                            endCallButton.isEnabled = true

                            monitorCall(client, reader)
                        }
                        .setNegativeButton("Reject") { _, _ ->
                            writer.println("REJECT")
                            incomingCallPending = false
                            client.close()
                            callStatus.text = "Call rejected"
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                client.close()
            }
        }
    }

    // Connect to another user's IP address and port.
    private fun startCall() {
        val ip = ipInput.text.toString().trim()
        val portText = portInput.text.toString().trim()

        if (ip.isEmpty() || portText.isEmpty()) {
            Toast.makeText(
                this,
                "Please enter IP address and port",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val port = portText.toIntOrNull()

        if (port == null || port !in 1024..65535) {
            Toast.makeText(
                this,
                "\"Port must be between 1024 and 65535\"",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        callButton.isEnabled = false
        callStatus.text = "Connecting..."

        executor.execute {
            var socket: Socket? = null

            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 15000)
                activeSocket = socket

                val writer = PrintWriter(
                    socket.getOutputStream(), true
                )

                val reader = BufferedReader(
                    InputStreamReader(socket.getInputStream())
                )

                writer.println("CALL_REQUEST")

                val response = reader.readLine()

                runOnUiThread {
                    when (response) {
                        "ACCEPT" -> {
                            callStatus.text = "Call connected"
                            endCallButton.isEnabled = true
                        }

                        "REJECT" -> {
                            callStatus.text = "Call rejected"
                            callButton.isEnabled = true
                        }

                        else -> {
                            callStatus.text = "Connection failed"
                            callButton.isEnabled = true
                        }
                    }
                }

                if (response == "ACCEPT") {
                    monitorCall(socket, reader)
                } else {
                    socket.close()
                    activeSocket = null
                }

            } catch (e: Exception) {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }

                runOnUiThread {
                    callStatus.text = "Connection failed: ${e.message}"
                    callButton.isEnabled = true
                }
            }
        }
    }

    // Monitor the connection for the other user's hang-up.
    private fun monitorCall(
        socket: Socket,
        reader: BufferedReader
    ) {
        executor.execute {
            try {
                val message = reader.readLine()

                if (message == "END") {
                    runOnUiThread {
                        if (activeSocket === socket) {
                            activeSocket = null
                            callStatus.text = "Other user ended the call"
                            callButton.isEnabled = true
                            endCallButton.isEnabled = false
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (activeSocket === socket) {
                        activeSocket = null
                        callStatus.text = "Connection lost"
                        callButton.isEnabled = true
                        endCallButton.isEnabled = false
                    }
                }
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    // End the current call.
    private fun endCall() {
        val socket = activeSocket
        activeSocket = null

        if (socket != null) {
            executor.execute {
                try {
                    PrintWriter(socket.getOutputStream(), true)
                        .println("END")
                } catch (_: Exception) {
                }

                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }

        callStatus.text = "Call ended"
        callButton.isEnabled = true
        endCallButton.isEnabled = false
    }

    override fun onDestroy() {
        running = false

        try {
            activeSocket?.close()
            serverSocket?.close()
        } catch (_: Exception) {
        }

        executor.shutdownNow()

        super.onDestroy()
    }
}
