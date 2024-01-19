package com.noti.main.utils.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.noti.main.R
import kotlin.properties.Delegates

@Suppress("unused")
class PrefsCard(context: Context?, attrs: AttributeSet?) : LinearLayoutCompat(context!!, attrs) {

    private lateinit var itemTitle: TextView
    private lateinit var itemDescription: TextView
    private lateinit var itemIcon: ImageView
    private lateinit var itemSwitch: SwitchMaterial
    private lateinit var itemParent: LinearLayoutCompat

    private lateinit var itemTitleStr: String
    private lateinit var itemDescriptionStr: String

    private var iconAlignEnd by Delegates.notNull<Boolean>()
    private var checked by Delegates.notNull<Boolean>()
    private var itemType by Delegates.notNull<Int>()
    private var imageSrc by Delegates.notNull<Int>()

    init {
        if (attrs != null) {
            initAttrs(attrs)
        }
        initView()
    }

    private fun initAttrs(attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.PrefsCard,
            0, 0
        ).apply {
            try {
                itemTitleStr = getString(R.styleable.PrefsCard_cardTitle) ?: ""
                itemDescriptionStr = getString(R.styleable.PrefsCard_cardDescription) ?: ""
                iconAlignEnd = getBoolean(R.styleable.PrefsCard_cardIconAlignEnd, false)
                checked = getBoolean(R.styleable.PrefsCard_cardSwitchChecked, false)
                imageSrc = getResourceId(R.styleable.PrefsCard_cardIconDrawable, TYPE_NONE_DEFAULT)
                itemType = getInt(R.styleable.PrefsCard_cardType, TYPE_NONE_DEFAULT)
            } finally {
                recycle()
            }
        }
    }

    private fun initView() {
        inflate(context, R.layout.item_prefs_card, this)

        itemTitle = findViewById(R.id.itemTitle)
        itemDescription = findViewById(R.id.itemDescription)
        itemSwitch = findViewById(R.id.switchCompat)
        itemParent = findViewById(R.id.itemParent)

        if (!containsTypeFlag(TYPE_SWITCH) && iconAlignEnd) {
            itemIcon = findViewById(R.id.itemIconRight)
            (findViewById<ImageView>(R.id.itemIcon)).visibility = View.GONE
        } else {
            itemIcon = findViewById(R.id.itemIcon)
            (findViewById<ImageView>(R.id.itemIconRight)).visibility = View.GONE
        }

        itemTitle.visibility = View.GONE
        itemDescription.visibility = View.GONE
        itemIcon.visibility = View.GONE
        itemSwitch.visibility = View.GONE

        if (containsTypeFlag(TYPE_TITLE)) {
            itemTitle.visibility = View.VISIBLE
            itemTitle.text = itemTitleStr
        }

        if (containsTypeFlag(TYPE_DESCRIPTION)) {
            itemDescription.visibility = View.VISIBLE
            itemDescription.text = itemDescriptionStr
        }

        if (containsTypeFlag(TYPE_ICON)) {
            itemIcon.visibility = View.VISIBLE
            itemIcon.setImageDrawable(AppCompatResources.getDrawable(context, imageSrc))
        }

        if (containsTypeFlag(TYPE_SWITCH)) {
            itemSwitch.visibility = View.VISIBLE
            itemSwitch.isChecked = checked
        }
    }

    fun setSwitchChecked(checked: Boolean) {
        if (containsTypeFlag(TYPE_SWITCH)) {
            itemSwitch.isChecked = checked
            requestLayout()
        }
    }

    fun setTitle(string: String) {
        if (containsTypeFlag(TYPE_TITLE)) {
            itemTitleStr = string
            itemTitle.text = itemTitleStr
            requestLayout()
        }
    }

    fun setDescription(string: String) {
        if (containsTypeFlag(TYPE_DESCRIPTION)) {
            itemDescriptionStr = string
            itemDescription.text = itemDescriptionStr
            requestLayout()
        }
    }

    fun setIconDrawable(resId: Int) {
        if (containsTypeFlag(TYPE_ICON)) {
            imageSrc = resId
            itemIcon.setImageDrawable(AppCompatResources.getDrawable(context, resId))
            requestLayout()
        }
    }

    fun setCardType(itemType: Int) {
        this.itemType = itemType
        initView()
        requestLayout()
    }

    fun getTitleString(): String {
        return itemTitleStr
    }

    fun getDescriptionString(): String {
        return itemDescriptionStr
    }

    fun getIconDrawable(): Int {
        return imageSrc
    }

    fun getCardType(): Int {
        return this.itemType
    }

    fun isChecked(): Boolean {
        return itemSwitch.isChecked
    }

    override fun setOnClickListener(l: OnClickListener?) {
        itemParent.setOnClickListener(l)
    }

    private fun containsTypeFlag(flag: Int): Boolean {
        return itemType or flag == itemType
    }

    companion object {
        private const val TYPE_NONE_DEFAULT = 0
        private const val TYPE_TITLE = 1
        private const val TYPE_DESCRIPTION = 2
        private const val TYPE_SWITCH = 4
        private const val TYPE_ICON = 8
    }
}
