package com.kuhlmann.hubcapcontrol

import android.content.Context
import android.media.effect.EffectContext
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.kuhlmann.hubcapcontrol.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.NumberFormatException
import java.net.InetAddress
import java.net.Socket
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var socket: Socket? = null
    private val timer: Timer = Timer()

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.connectButton.setOnClickListener{ scope.launch { connectToServer() } }
        binding.sendbutton.setOnClickListener{ scope.launch { sendMessage() } }
        report("Ready")
    }

    private suspend fun connectToServer() {
        withContext(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(binding.serverEditText.text.toString())
                val port = binding.portEditText.text.toString().toInt()
                socket = Socket(address, port)
                timer.scheduleAtFixedRate(PulseTask(), 1000, 1000)
            } catch (e: Exception) {
                closeSocket()
                runOnUiThread {
                    report("Error connecting to server: " + e.toString() + ": " + e.message)
                }
            }
            runOnUiThread {
                updateConnectionStatus()
            }
        }
    }

    fun closeSocket() {
        if (socket != null) {
            socket!!.close()
            socket = null
        }
    }

    fun updateConnectionStatus() {
        if (socket != null && socket!!.isConnected) {
            binding.statusTextView.text = getString(R.string.connected)
        } else {
            binding.statusTextView.text = getString(R.string.not_connected)
        }

    }

    private suspend fun sendMessage() {
        try {
            val b0 = binding.byte0EditText.text.toString().toInt()
            val b1 = binding.byte1EditText.text.toString().toInt()
            val b2 = binding.byte2EditText.text.toString().toInt()
            val b3 = binding.byte3EditText.text.toString().toInt()
            if (b0 !in 0..255) {
                report("Byte value $b0 out of range.")
                return
            }
            if (b1 !in 0..255) {
                report("Byte value $b1 out of range.")
                return
            }
            if (b2 !in 0..255) {
                report("Byte value $b2 out of range.")
                return
            }
            if (b3 !in 0..255) {
                report("Byte value $b3 out of range.")
                return
            }
            withContext(Dispatchers.IO) {
                try {
                    val stream = socket!!.getOutputStream()
                    stream.write(b0)
                    stream.write(b1)
                    stream.write(b2)
                    stream.write(b3)
                } catch (e: Exception) {
                    closeSocket()
                    runOnUiThread {
                        updateConnectionStatus()
                        report("Error sending message: " + e.toString() + ": " + e.message)
                    }
                }
            }
        } catch (e: NumberFormatException) {
            report("Invalid byte value: " + e.message)
        }
    }

    fun report(line: String) {
        binding.outputTextView.append(line + "\n")
    }

    private fun getWifiManager(): WifiManager {
        return applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    inner class PulseTask: TimerTask() {

        override fun run() {
            if (socket != null) {
                try {
                    val stream = socket!!.getOutputStream()
                    for (i in 1..4) {
                        stream.write(0)
                    }
                    stream.flush()
                    runOnUiThread { report("Sent pulse.") }
                } catch (e: Exception) {
                    closeSocket()
                    runOnUiThread {
                        updateConnectionStatus()
                        report("Error sending pulse: " + e.message)
                    }
                }
            }
        }

    }
}
