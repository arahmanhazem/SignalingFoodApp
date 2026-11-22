package com.abdelrahmanhazem

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Milestone 4 VoIP screen.
 * Users manually enter IP + port, exchange a short text handshake (CALL_REQUEST /
 * ACCEPT / REJECT / END), then stream live PCM microphone audio both ways.
 */
class VoipCallActivity : AppCompatActivity() {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var callButton: Button
    private lateinit var listenButton: Button
    private lateinit var endCallButton: Button
    private lateinit var callStatus: TextView

    private var listeningPort: Int? = null
    private val executor = Executors.newCachedThreadPool()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null

    @Volatile
    private var incomingCallPending = false

    @Volatile
    private var running = true

    private val audioRunning = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Microphone permission is required for VoIP", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voip_call)

        ipInput = findViewById(R.id.ipAddressInput)
        portInput = findViewById(R.id.portInput)
        callButton = findViewById(R.id.callButton)
        listenButton = findViewById(R.id.listenButton)
        endCallButton = findViewById(R.id.endCallButton)
        callStatus = findViewById(R.id.callStatus)

        askMicPermission()

        listenButton.setOnClickListener { startListening() }
        callButton.setOnClickListener { startCall() }
        endCallButton.setOnClickListener { endCall() }
    }

    private fun askMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // Start listening for incoming calls on the port entered by the user.
    private fun startListening() {
        if (!hasMicPermission()) {
            askMicPermission()
            Toast.makeText(this, "Allow microphone access to receive calls", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val portText = portInput.text.toString().trim()
        val port = portText.toIntOrNull()

        if (port == null || port !in 1024..65535) {
            Toast.makeText(this, "Enter a valid port between 1024 and 65535", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (serverSocket != null && serverSocket?.isClosed == false) {
            Toast.makeText(this, "Already listening for calls", Toast.LENGTH_SHORT).show()
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
                    runOnUiThread { callStatus.text = "Server error: ${e.message}" }
                }
            }
        }
    }

    // Handle a call request from another user.
    private fun handleIncomingCall(client: Socket) {
        executor.execute {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)
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
                            callStatus.text = "Call connected — speaking"
                            callButton.isEnabled = false
                            endCallButton.isEnabled = true
                            startAudio(client)
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
            } catch (_: Exception) {
                try {
                    client.close()
                } catch (_: Exception) {
                }
                incomingCallPending = false
            }
        }
    }

    // Connect to another user's IP address and port.
    private fun startCall() {
        if (!hasMicPermission()) {
            askMicPermission()
            Toast.makeText(this, "Allow microphone access to place calls", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val ip = ipInput.text.toString().trim()
        val portText = portInput.text.toString().trim()

        if (ip.isEmpty() || portText.isEmpty()) {
            Toast.makeText(this, "Please enter IP address and port", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portText.toIntOrNull()
        if (port == null || port !in 1024..65535) {
            Toast.makeText(this, "Port must be between 1024 and 65535", Toast.LENGTH_SHORT).show()
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

                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println("CALL_REQUEST")
                val response = reader.readLine()

                runOnUiThread {
                    when (response) {
                        "ACCEPT" -> {
                            callStatus.text = "Call connected — speaking"
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
                    startAudio(socket)
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

    /** After ACCEPT: stream mic PCM out and play remote PCM in. */
    private fun startAudio(socket: Socket) {
        if (!audioRunning.compareAndSet(false, true)) return

        val minRec = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val minPlay = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val bufferSize = maxOf(minRec, minPlay, 2048)

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                audioRunning.set(false)
                runOnUiThread { callStatus.text = "Microphone permission missing" }
                return
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                bufferSize
            )
            val track = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_OUT,
                ENCODING,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioRecord = recorder
            audioTrack = track
            recorder.startRecording()
            track.play()

            // Mic → network
            executor.execute {
                val buffer = ByteArray(bufferSize)
                val out = socket.getOutputStream()
                try {
                    while (audioRunning.get() && !socket.isClosed) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) out.write(buffer, 0, read)
                    }
                } catch (_: Exception) {
                }
            }

            // Network → speaker
            executor.execute {
                val buffer = ByteArray(bufferSize)
                val input = socket.getInputStream()
                try {
                    while (audioRunning.get() && !socket.isClosed) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) track.write(buffer, 0, read)
                    }
                } catch (_: Exception) {
                } finally {
                    runOnUiThread {
                        if (activeSocket === socket) {
                            stopAudio()
                            activeSocket = null
                            callStatus.text = "Call ended"
                            callButton.isEnabled = true
                            endCallButton.isEnabled = false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            audioRunning.set(false)
            runOnUiThread {
                callStatus.text = "Audio error: ${e.message}"
            }
        }
    }

    private fun stopAudio() {
        audioRunning.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        audioTrack = null
    }

    // End the current call.
    private fun endCall() {
        val socket = activeSocket
        activeSocket = null
        stopAudio()

        if (socket != null) {
            executor.execute {
                try {
                    PrintWriter(socket.getOutputStream(), true).println("END")
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
        stopAudio()
        try {
            activeSocket?.close()
            serverSocket?.close()
        } catch (_: Exception) {
        }
        executor.shutdownNow()
        super.onDestroy()
    }
}
