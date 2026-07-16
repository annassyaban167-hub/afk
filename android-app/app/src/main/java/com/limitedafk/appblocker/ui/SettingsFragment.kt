package com.limitedafk.appblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.limitedafk.appblocker.R
import com.limitedafk.appblocker.utils.SecurityUtils

class SettingsFragment : Fragment() {

    private lateinit var etPinGate: EditText
    private lateinit var etNewPin: EditText
    private lateinit var btnUnlock: Button
    private lateinit var btnSavePin: Button
    private lateinit var tvPinError: TextView
    private lateinit var layoutPinGate: View
    private lateinit var layoutContent: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)

        etPinGate = root.findViewById(R.id.et_pin_gate)
        etNewPin = root.findViewById(R.id.et_new_pin)
        btnUnlock = root.findViewById(R.id.btn_unlock)
        btnSavePin = root.findViewById(R.id.btn_save_pin)
        tvPinError = root.findViewById(R.id.tv_pin_error)
        layoutPinGate = root.findViewById(R.id.layout_pin_gate)
        layoutContent = root.findViewById(R.id.layout_settings_content)

        if (!SecurityUtils.hasPin(requireContext())) {
            layoutPinGate.visibility = View.GONE
            layoutContent.visibility = View.VISIBLE
        }

        btnUnlock.setOnClickListener {
            val pin = etPinGate.text.toString()
            if (SecurityUtils.verifyPin(requireContext(), pin)) {
                layoutPinGate.visibility = View.GONE
                layoutContent.visibility = View.VISIBLE
                tvPinError.visibility = View.GONE
            } else {
                tvPinError.visibility = View.VISIBLE
            }
        }

        btnSavePin.setOnClickListener {
            val newPin = etNewPin.text.toString()
            if (SecurityUtils.savePin(requireContext(), newPin)) {
                Toast.makeText(requireContext(), "PIN updated successfully", Toast.LENGTH_SHORT).show()
                etNewPin.setText("")
            } else {
                Toast.makeText(requireContext(), "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show()
            }
        }

        return root
    }
}
