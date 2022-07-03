package com.kuhlmann.hubcapcontrol

import android.content.Context
import android.media.effect.EffectContext
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
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
    private val socketLock = Any()

    private val timer: Timer = Timer()

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.connectButton.setOnClickListener{ scope.launch { connectToServer() } }
        binding.sendbutton.setOnClickListener{ scope.launch { sendMessage() } }
        report("Ready")
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun connectToServer() {
        withContext(Dispatchers.IO) {
            synchronized(socketLock) {
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
            }
            runOnUiThread {
                updateConnectionStatus()
            }
        }
    }

    fun closeSocket() {
        synchronized(socketLock) {
            if (socket != null) {
                socket!!.close()
                socket = null
            }
        }
    }

    fun updateConnectionStatus() {
        synchronized(socketLock) {
            if (socket != null && socket!!.isConnected) {
                binding.statusTextView.text = getString(R.string.connected)
            } else {
                binding.statusTextView.text = getString(R.string.not_connected)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun sendMessage() {
        try {
            val b0 = getByteFromControl(binding.byte0EditText)
            val b1 = getByteFromControl(binding.byte1EditText)
            val b2 = getByteFromControl(binding.byte2EditText)
            val b3 = getByteFromControl(binding.byte3EditText)
            withContext(Dispatchers.IO) {
                try {
                    synchronized(socketLock) {
                        val stream = socket!!.getOutputStream()
                        stream.write(b0)
                        stream.write(b1)
                        stream.write(b2)
                        stream.write(b3)
                    }
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

    private fun getByteFromControl(control: EditText): Int {
        val b = control.text.toString().toInt()
        if (b !in 0..255) {
            throw NumberFormatException("Byte value $b out of range.")
        }
        return b
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
                    synchronized(socketLock) {
                        val stream = socket!!.getOutputStream()
                        for (i in 1..4) {
                            stream.write(0)
                        }
                        stream.flush()
                    }
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
