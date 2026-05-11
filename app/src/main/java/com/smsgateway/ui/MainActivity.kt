package com.smsgateway.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smsgateway.api.RetrofitClient
import com.smsgateway.service.SmsPollingService
import com.smsgateway.utils.Prefs
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val PERM_REQUEST = 101
    private val REQUIRED_PERMS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
    )

    // Views — using direct findViewById for simplicity
    private lateinit var etServerUrl: EditText
    private lateinit var etDeviceKey: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var layoutConnected: View
    private lateinit var layoutSetup: View
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Prefs.init(this)

        etServerUrl    = findViewById(R.id.et_server_url)
        etDeviceKey    = findViewById(R.id.et_device_key)
        btnConnect     = findViewById(R.id.btn_connect)
        btnDisconnect  = findViewById(R.id.btn_disconnect)
        tvStatus       = findViewById(R.id.tv_status)
        tvDeviceName   = findViewById(R.id.tv_device_name)
        layoutConnected = findViewById(R.id.layout_connected)
        layoutSetup    = findViewById(R.id.layout_setup)
        progressBar    = findViewById(R.id.progress_bar)

        // Restore saved values
        etServerUrl.setText(Prefs.serverUrl)
        etDeviceKey.setText(Prefs.deviceKey)

        if (Prefs.isConnected && Prefs.deviceKey.isNotEmpty()) showConnected()
        else showSetup()

        btnConnect.setOnClickListener { attemptConnect() }
        btnDisconnect.setOnClickListener { disconnect() }

        requestPermissions()
    }

    private fun attemptConnect() {
        val url = etServerUrl.text.toString().trim()
        val key = etDeviceKey.text.toString().trim()

        if (url.isEmpty() || key.isEmpty()) {
            toast("Please fill in Server URL and API Key")
            return
        }

        progressBar.visibility = View.VISIBLE
        btnConnect.isEnabled = false

        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.get(url).login(key)
                }
                progressBar.visibility = View.GONE
                btnConnect.isEnabled = true

                if (resp.isSuccessful && resp.body()?.success == true) {
                    val data = resp.body()!!.data!!
                    Prefs.serverUrl       = url
                    Prefs.deviceKey       = key
                    Prefs.deviceId        = data.deviceId
                    Prefs.deviceName      = data.deviceName
                    Prefs.pollingInterval = data.pollingInterval
                    Prefs.isConnected     = true

                    startService()
                    showConnected()
                    toast("Connected as: ${data.deviceName}")
                } else {
                    toast("Login failed: ${resp.body()?.message ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnConnect.isEnabled = true
                toast("Connection error: ${e.message}")
            }
        }
    }

    private fun disconnect() {
        stopService(Intent(this, SmsPollingService::class.java))
        Prefs.isConnected = false
        showSetup()
        toast("Disconnected")
    }

    private fun startService() {
        startForegroundService(Intent(this, SmsPollingService::class.java))
    }

    private fun showConnected() {
        layoutSetup.visibility      = View.GONE
        layoutConnected.visibility  = View.VISIBLE
        tvDeviceName.text = "Device: ${Prefs.deviceName}"
        tvStatus.text     = "🟢 Connected — polling every ${Prefs.pollingInterval}s"
    }

    private fun showSetup() {
        layoutSetup.visibility     = View.VISIBLE
        layoutConnected.visibility = View.GONE
    }

    private fun requestPermissions() {
        val missing = REQUIRED_PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST) {
            val denied = grantResults.filter { it != PackageManager.PERMISSION_GRANTED }
            if (denied.isNotEmpty()) toast("SMS permission required for gateway to work!")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
