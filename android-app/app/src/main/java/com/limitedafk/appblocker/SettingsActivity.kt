package com.limitedafk.appblocker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.limitedafk.appblocker.data.AppDatabase
import com.limitedafk.appblocker.data.BlockedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvTargetApp: TextView
    private lateinit var etDuration: EditText
    private lateinit var rgMode: RadioGroup
    private lateinit var rbCloseAfter: RadioButton
    private lateinit var rbCloseImmediate: RadioButton
    private lateinit var btnSave: Button
    private lateinit var database: AppDatabase

    private var currentPackage: String? = null
    private var blockedApp: BlockedApp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        database = AppDatabase.getDatabase(this)
        tvTargetApp = findViewById(R.id.tv_target_app)
        etDuration = findViewById(R.id.et_duration)
        rgMode = findViewById(R.id.rg_mode)
        rbCloseAfter = findViewById(R.id.rb_close_after)
        rbCloseImmediate = findViewById(R.id.rb_close_immediate)
        btnSave = findViewById(R.id.btn_save_settings)

        // For simplicity in single activity configuration flow
        loadFirstBlockedAppDetails()

        btnSave.setOnClickListener {
            saveAppSettings()
        }
    }

    private fun loadFirstBlockedAppDetails() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { database.blockedAppDao().getAll() }
            if (apps.isNotEmpty()) {
                val app = apps.first()
                blockedApp = app
                currentPackage = app.packageName
                tvTargetApp.text = "Configuring constraints for: ${app.appName}"
                etDuration.setText(app.limitMinutes.toString())
                if (app.mode == "CLOSE_IMMEDIATELY") {
                    rbCloseImmediate.isChecked = true
                } else {
                    rbCloseAfter.isChecked = true
                }
            } else {
                tvTargetApp.text = "Please select an app on the Main screen first."
                btnSave.isEnabled = false
            }
        }
    }

    private fun saveAppSettings() {
        val app = blockedApp ?: return
        val pkg = currentPackage ?: return
        val durationText = etDuration.text.toString()
        val duration = durationText.toIntOrNull()

        if (duration == null || duration <= 0) {
            Toast.makeText(this, "Please enter a valid duration limit in minutes", Toast.LENGTH_SHORT).show()
            return
        }

        val mode = if (rbCloseImmediate.isChecked) "CLOSE_IMMEDIATELY" else "CLOSE_AFTER_TIMEOUT"

        lifecycleScope.launch(Dispatchers.IO) {
            val updated = BlockedApp(
                packageName = pkg,
                appName = app.appName,
                limitMinutes = duration,
                remainingSeconds = duration * 60,
                mode = mode,
                isBlocked = false
            )
            database.blockedAppDao().update(updated)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, "App settings updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
