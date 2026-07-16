package com.limitedafk.appblocker.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.limitedafk.appblocker.R
import com.limitedafk.appblocker.data.AppDatabase
import com.limitedafk.appblocker.data.BlockedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLimitsFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var rvAllApps: RecyclerView
    private lateinit var etLimit: EditText
    private lateinit var spinnerMode: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnResetAll: Button
    private lateinit var tvSelectedApp: TextView

    private var selectedPackage: String? = null
    private var selectedName: String? = null
    private var allInstalledApps = listOf<InstalledApp>()
    private var selectedMode = "CLOSE_AFTER_TIMEOUT"

    data class InstalledApp(val packageName: String, val name: String)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_app_limits, container, false)
        database = AppDatabase.getDatabase(requireContext())

        rvAllApps = root.findViewById(R.id.rv_all_apps)
        etLimit = root.findViewById(R.id.et_limit_minutes)
        spinnerMode = root.findViewById(R.id.spinner_mode)
        btnSave = root.findViewById(R.id.btn_save_limit)
        btnResetAll = root.findViewById(R.id.btn_reset_all)
        tvSelectedApp = root.findViewById(R.id.tv_selected_app)

        rvAllApps.layoutManager = LinearLayoutManager(requireContext())

        ArrayAdapter.createFromResource(
            requireContext(), R.array.blocking_modes, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMode.adapter = adapter
        }

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedMode = if (pos == 0) "CLOSE_AFTER_TIMEOUT" else "CLOSE_IMMEDIATELY"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener { saveLimit() }
        btnResetAll.setOnClickListener { resetAll() }

        loadApps()
        return root
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val installed = withContext(Dispatchers.IO) {
                val pm = requireContext().packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                apps.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }
                    .sortedBy { it.name }
            }
            allInstalledApps = installed

            val blocked = withContext(Dispatchers.IO) { database.blockedAppDao().getAll() }
            val blockedPkgs = blocked.map { it.packageName }.toSet()

            rvAllApps.adapter = AppListAdapter(installed, blockedPkgs) { pkg, name ->
                selectedPackage = pkg
                selectedName = name
                tvSelectedApp.text = "Configure: $name"
                // Pre-fill
                val existing = blocked.find { it.packageName == pkg }
                if (existing != null) {
                    etLimit.setText(existing.limitMinutes.toString())
                    spinnerMode.setSelection(if (existing.mode == "CLOSE_IMMEDIATELY") 1 else 0)
                } else {
                    etLimit.setText("")
                    spinnerMode.setSelection(0)
                }
            }
        }
    }

    private fun saveLimit() {
        val pkg = selectedPackage ?: run {
            Toast.makeText(requireContext(), "Select an app first", Toast.LENGTH_SHORT).show()
            return
        }
        val name = selectedName ?: pkg
        val minutes = etLimit.text.toString().toIntOrNull()
        if (minutes == null || minutes <= 0) {
            Toast.makeText(requireContext(), "Enter valid minutes", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val app = BlockedApp(
                packageName = pkg,
                appName = name,
                limitMinutes = minutes,
                remainingSeconds = minutes * 60,
                mode = selectedMode,
                isBlocked = false,
                enabled = true
            )
            database.blockedAppDao().insert(app)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Limit saved for $name", Toast.LENGTH_SHORT).show()
                loadApps()
            }
        }
    }

    private fun resetAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            database.blockedAppDao().resetAllRemainingTime()
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "All limits reset", Toast.LENGTH_SHORT).show()
                loadApps()
            }
        }
    }

    inner class AppListAdapter(
        private val apps: List<InstalledApp>,
        private val blockedPkgs: Set<String>,
        private val onClick: (String, String) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_icon)
            val tvName: TextView = view.findViewById(R.id.tv_name)
            val tvPkg: TextView = view.findViewById(R.id.tv_package)
            val tvTime: TextView = view.findViewById(R.id.tv_time)
            val btnSet: Button = view.findViewById(R.id.btn_set_limit)
            val btnRemove: Button = view.findViewById(R.id.btn_remove_limit)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_limit, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.tvName.text = app.name
            holder.tvPkg.text = app.packageName
            try {
                val ai = requireContext().packageManager.getApplicationInfo(app.packageName, 0)
                holder.ivIcon.setImageDrawable(ai.loadIcon(requireContext().packageManager))
            } catch (_: Exception) {
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            val isBlocked = app.packageName in blockedPkgs
            holder.tvTime.text = if (isBlocked) "Limited" else "No limit"
            holder.btnSet.visibility = if (isBlocked) View.GONE else View.VISIBLE
            holder.btnRemove.visibility = if (isBlocked) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onClick(app.packageName, app.name) }
            holder.btnSet.setOnClickListener { onClick(app.packageName, app.name) }
            holder.btnRemove.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val existing = database.blockedAppDao().getApp(app.packageName)
                    if (existing != null) database.blockedAppDao().delete(existing)
                    withContext(Dispatchers.Main) { loadApps() }
                }
            }
        }

        override fun getItemCount(): Int = apps.size
    }
}
