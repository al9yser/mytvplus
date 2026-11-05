package com.mytv.player

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun load(): Account? {
        val host = sp.getString("host", null) ?: return null
        val user = sp.getString("user", null) ?: return null
        val pass = sp.getString("pass", null) ?: return null
        val ua = sp.getString("ua", DEFAULT_UA) ?: DEFAULT_UA
        return Account(host, user, pass, ua)
    }

    fun save(a: Account) {
        sp.edit()
            .putString("host", a.host)
            .putString("user", a.user)
            .putString("pass", a.pass)
            .putString("ua", a.ua)
            .apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }
}
