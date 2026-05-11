package com.smsgateway.utils

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF_NAME = "asg_prefs"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = _ctx?.let { prefs(it).getString("server_url", "") } ?: ""
        set(v) { _ctx?.let { prefs(it).edit().putString("server_url", v).apply() } }

    var deviceKey: String
        get() = _ctx?.let { prefs(it).getString("device_key", "") } ?: ""
        set(v) { _ctx?.let { prefs(it).edit().putString("device_key", v).apply() } }

    var deviceId: Int
        get() = _ctx?.let { prefs(it).getInt("device_id", 0) } ?: 0
        set(v) { _ctx?.let { prefs(it).edit().putInt("device_id", v).apply() } }

    var deviceName: String
        get() = _ctx?.let { prefs(it).getString("device_name", "") } ?: ""
        set(v) { _ctx?.let { prefs(it).edit().putString("device_name", v).apply() } }

    var pollingInterval: Int
        get() = _ctx?.let { prefs(it).getInt("polling_interval", 10) } ?: 10
        set(v) { _ctx?.let { prefs(it).edit().putInt("polling_interval", v).apply() } }

    var isConnected: Boolean
        get() = _ctx?.let { prefs(it).getBoolean("connected", false) } ?: false
        set(v) { _ctx?.let { prefs(it).edit().putBoolean("connected", v).apply() } }

    private var _ctx: Context? = null
    fun init(ctx: Context) { _ctx = ctx.applicationContext }
}
