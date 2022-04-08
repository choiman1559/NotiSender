package com.noti.main.utils.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import com.noti.main.R

class SwitchedPreference(context: Context?, attrs: AttributeSet?) :
    SwitchPreference(context!!, attrs) {

    private var defaultChecked: Boolean = false
    private var switchCompat: SwitchCompat? = null

    init {
        widgetLayoutResource = R.layout.item_switch
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        defaultChecked = checked
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        switchCompat = holder.findViewById(R.id.switchCompat) as SwitchCompat?
        switchCompat?.apply {
            this.isChecked = defaultChecked
        }
    }
}