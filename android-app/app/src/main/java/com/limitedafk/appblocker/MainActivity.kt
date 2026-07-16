package com.limitedafk.appblocker

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.limitedafk.appblocker.data.AppDatabase
import com.limitedafk.appblocker.data.BlockedApp
import com.limitedafk.appblocker.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var btnSettings: Button
    private lateinit var database: AppDatabase
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        rvApps = findViewById(R.id.rv_apps)
        btnSettings = findViewById(R.id.btn_settings)

        rvApps.layoutManager = LinearLayoutManager(this)

        btnSettings.setOnClickListener {
            checkPinAndProceed {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        checkPermissions()
        startTimerService()
        loadInstalledApps()
    }

    private fun checkPermissions() {
        // Usage access permission check
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        if (mode != AppOpsManager.MODE_ALLOWED) {
            Toast.makeText(this, "Please enable Usage Access", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // Device administrator policy check
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate device admin to secure application lifecycle.")
            }
            startActivity(intent)
        }
    }

    private fun startTimerService() {
        val intent = Intent(this, TimerForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkPinAndProceed(action: () -> Unit) {
        if (!SecurityUtils.hasPin(this)) {
            // First time setup - prompt to create PIN
            showSetupPinDialog(action)
        } else {
            // Prompt to verify PIN
            showVerifyPinDialog(action)
        }
    }

    private fun showSetupPinDialog(onSuccess: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null)
        val etPin = dialogView.findViewById<EditText>(R.id.et_pin)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tv_dialog_desc)

        tvTitle.text = "Setup access PIN"
        tvDesc.text = "Enter a 6-digit access PIN to secure your configuration settings."

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Create") { _, _ ->
                val pin = etPin.text.toString()
                if (SecurityUtils.savePin(this, pin)) {
                    onSuccess()
                } else {
                    Toast.makeText(this, "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show()
                    showSetupPinDialog(onSuccess)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVerifyPinDialog(onSuccess: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null)
        val etPin = dialogView.findViewById<EditText>(R.id.et_pin)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                val pin = etPin.text.toString()
                if (SecurityUtils.verifyPin(this, pin)) {
                    onSuccess()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    showVerifyPinDialog(onSuccess)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val installed = withContext(Dispatchers.IO) {
                val pm = packageManager
                val appsList = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val items = mutableListOf<AppItem>()
                for (app in appsList) {
                    // Only list launcher apps to exclude system background apps
                    if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                        items.add(
                            AppItem(
                                packageName = app.packageName,
                                name = app.loadLabel(pm).toString(),
                                icon = app.loadIcon(pm)
                            )
                        )
                    }
                }
                items.sortBy { it.name }
                items
            }

            val blockedApps = withContext(Dispatchers.IO) { database.blockedAppDao().getAll() }
            val blockedMap = blockedApps.associateBy { it.packageName }

            adapter = AppAdapter(installed, blockedMap) { appItem, isChecked ->
                toggleAppBlock(appItem, isChecked)
            }
            rvApps.adapter = adapter
        }
    }

    private fun toggleAppBlock(appItem: AppItem, isChecked: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = database.blockedAppDao()
            val existing = dao.getApp(appItem.packageName)
            if (isChecked) {
                if (existing == null) {
                    val newBlocked = BlockedApp(
                        packageName = appItem.packageName,
                        appName = appItem.name,
                        limitMinutes = 30, // Default duration limit
                        remainingSeconds = 1800,
                        mode = "CLOSE_AFTER_TIMEOUT"
                    )
                    dao.insert(newBlocked)
                }
            } else {
                if (existing != null) {
                    dao.delete(existing)
                }
            }
            withContext(Dispatchers.Main) {
                loadInstalledApps()
            }
        }
    }

    data class AppItem(val packageName: String, val name: String, val icon: Drawable)

    inner class AppAdapter(
        private val list: List<AppItem>,
        private val blockedMap: Map<String, BlockedApp>,
        private val onCheckedChange: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon = view.findViewById<ImageView>(R.id.iv_icon)
            val tvName = view.findViewById<TextView>(R.id.tv_name)
            val tvPackage = view.findViewById<TextView>(R.id.tv_package)
            val swBlocked = view.findViewById<SwitchCompat>(R.id.sw_blocked)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.app_list_item, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name
            holder.tvPackage.text = item.packageName
            holder.ivIcon.setImageDrawable(item.icon)

            holder.swBlocked.setOnCheckedChangeListener(null)
            holder.swBlocked.isChecked = blockedMap.containsKey(item.packageName)

            holder.swBlocked.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(item, isChecked)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
