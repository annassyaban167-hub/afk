package com.limitedafk.appblocker.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.limitedafk.appblocker.R
import com.limitedafk.appblocker.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private lateinit var rvApps: RecyclerView
    private lateinit var tvActiveCount: TextView
    private lateinit var tvAppsCount: TextView
    private lateinit var tvDeviceStatus: TextView
    private lateinit var deviceStatusDot: View
    private lateinit var database: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        rvApps = root.findViewById(R.id.rv_apps)
        tvActiveCount = root.findViewById(R.id.tv_active_count)
        tvAppsCount = root.findViewById(R.id.tv_apps_count)
        tvDeviceStatus = root.findViewById(R.id.tv_device_status)
        deviceStatusDot = root.findViewById(R.id.device_status_dot)
        database = AppDatabase.getDatabase(requireContext())
        rvApps.layoutManager = GridLayoutManager(requireContext(), 2)
        return root
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { database.blockedAppDao().getAll() }
            val blockedCount = withContext(Dispatchers.IO) { database.blockedAppDao().getBlockedCount() }

            tvAppsCount.text = apps.size.toString()
            tvActiveCount.text = blockedCount.toString()

            // Check permissions status
            val accOk = isAccessibilityServiceEnabled()
            val dpmOk = isDeviceAdminEnabled()

            if (accOk && dpmOk) {
                tvDeviceStatus.text = "All permissions granted"
                deviceStatusDot.setBackgroundResource(R.drawable.dot_purple)
            } else {
                tvDeviceStatus.text = buildString {
                    if (!accOk) append("Enable Accessibility Service. ")
                    if (!dpmOk) append("Activate Device Admin. ")
                }
                deviceStatusDot.setBackgroundResource(R.drawable.dot_grey)
            }

            rvApps.adapter = AppGridAdapter(apps, requireContext().packageManager)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val enabled = android.provider.Settings.Secure.getString(
                requireContext().contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(requireContext().packageName)
        } catch (_: Exception) {
            return false
        }
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = requireContext().getSystemService(android.content.Context.DEVICE_POLICY_SERVICE)
                as android.app.admin.DevicePolicyManager
        val admin = android.content.ComponentName(requireContext(), com.limitedafk.appblocker.DeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    class AppGridAdapter(
        private val apps: List<com.limitedafk.appblocker.data.BlockedApp>,
        private val pm: PackageManager
    ) : RecyclerView.Adapter<AppGridAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_icon)
            val tvName: TextView = view.findViewById(R.id.tv_name)
            val tvBadge: TextView = view.findViewById(R.id.tv_badge)
            val tvPackage: TextView = view.findViewById(R.id.tv_package)
            val card: View = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.tvName.text = app.appName
            holder.tvPackage.text = app.packageName

            try {
                val ai = pm.getApplicationInfo(app.packageName, 0)
                holder.ivIcon.setImageDrawable(ai.loadIcon(pm))
            } catch (_: Exception) {
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            if (app.isBlocked) {
                holder.tvBadge.text = "BLOCKED"
                holder.tvBadge.setBackgroundResource(android.R.color.holo_red_dark)
            } else if (app.remainingSeconds > 0 && app.remainingSeconds < app.limitMinutes * 60) {
                holder.tvBadge.text = com.limitedafk.appblocker.utils.TimerUtils.formatTime(app.remainingSeconds)
            } else {
                holder.tvBadge.text = if (app.limitMinutes > 0) "${app.limitMinutes}m limit" else "No limit"
            }
        }

        override fun getItemCount(): Int = apps.size
    }
}
