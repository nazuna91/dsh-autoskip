package com.dsh.autoskip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var open: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "DSH AutoSkip"
            textSize = 24f
            gravity = Gravity.CENTER_HORIZONTAL
        })

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 32, 0, 8)
        }
        root.addView(status)

        stats = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        root.addView(stats)

        open = Button(this).apply {
            text = "去开启无障碍服务"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(open)

        root.addView(TextView(this).apply {
            text = "开启后在「已下载的应用」里找到 DSH AutoSkip 并启用。\n" +
                "它只监听 com.clggjv.xcjfmd.ffo 的界面，看到「暂时跳过」就点一下。"
            textSize = 12f
            setPadding(0, 40, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.text = if (on) "状态：已开启" else "状态：未开启"
        stats.text = "已自动跳过 ${SkipService.clicks} 次" +
            (if (SkipService.lastHit.isNotEmpty()) "（最近：${SkipService.lastHit}）" else "")
    }

    private fun isServiceEnabled(): Boolean {
        val expected = "$packageName/${SkipService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
