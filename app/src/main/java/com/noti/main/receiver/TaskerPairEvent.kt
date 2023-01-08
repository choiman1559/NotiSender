package com.noti.main.receiver

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat

import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView

import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.noti.main.Application

import com.noti.main.R

class EventStateChangedRunner : TaskerPluginRunnerConditionEvent<GetConfigInput,GetConfigInput,GetConfigInput>() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<GetConfigInput>, update: GetConfigInput?) : TaskerPluginResultCondition<GetConfigInput> {
        if (update != null) {
            if((input.regular.deviceName == "Every device" && input.regular.deviceId == "none") || (input.regular.deviceId == update.deviceId && input.regular.deviceName == update.deviceName))
                return TaskerPluginResultConditionSatisfied(context, update)
        }
        return TaskerPluginResultConditionUnsatisfied()
    }
}

class EventTriggerHelper(config: TaskerPluginConfig<GetConfigInput>) : TaskerPluginConfigHelper<GetConfigInput,GetConfigInput, EventStateChangedRunner>(config) {
    override val outputClass = GetConfigInput::class.java
    override val inputClass = GetConfigInput::class.java
    override val runnerClass get() = EventStateChangedRunner::class.java
    override fun addToStringBlurb(input: TaskerInput<GetConfigInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("\n\nThis event will be triggered when another device executes the \"Trigger tasker event\" action through the Noti Sender.")
    }
}

@TaskerInputRoot
class GetConfigInput @JvmOverloads constructor(
    @field:TaskerInputField("deviceName") var deviceName: String = "",
    @field:TaskerInputField("deviceId") var deviceId: String = ""
)

class TaskerPairEvent : Activity(), TaskerPluginConfig<GetConfigInput> {
    override val context: Context get() = applicationContext
    private val taskerHelper by lazy { EventTriggerHelper(this) }

    private var deviceName = "unknown"
    private var deviceId = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pair_tasker)
        taskerHelper.onCreate()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val infoIcon = findViewById<ImageView>(R.id.infoIcon)
        val waringLayout = findViewById<LinearLayout>(R.id.waring_layout)
        val actionConfigLayout = findViewById<LinearLayout>(R.id.actionConfigLayout)
        val deviceSelectSpinner = findViewById<MaterialAutoCompleteTextView>(R.id.deviceSelectSpinner)
        val saveButton = findViewById<ExtendedFloatingActionButton>(R.id.save_button)

        val prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("UseTaskerExtension", false)) {
            waringLayout.visibility = View.GONE
            infoIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_info_outline_black_24dp, theme))

            var deviceSelection = 0
            val pairPrefs = context.getSharedPreferences("com.noti.main_pair", Context.MODE_PRIVATE)
            val rawList = ArrayList<String>()
            val nameList = ArrayList<String>()
            for (str in pairPrefs.getStringSet("paired_list", HashSet<String>())!!) {
                rawList.add(str)
            }

            nameList.add("Every device")
            for (str in rawList) {
                nameList.add(str.split("|")[0])
            }

            deviceSelectSpinner.setAdapter<ArrayAdapter<String>>(
                ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, nameList)
            )

            deviceSelectSpinner.setOnItemClickListener { _, _, position, _ ->
                deviceSelection = position
            }

            saveButton.setOnClickListener {
                if(deviceSelectSpinner.text.toString().isEmpty()) {
                    deviceSelectSpinner.error = "Please select target device"
                } else {
                    if(deviceSelection == 0) {
                        deviceName = "Every device"
                        deviceId = "none"
                    } else {
                        val array = rawList[deviceSelection - 1].split("|")
                        deviceName = array[0]
                        deviceId = array[1]
                    }
                    EventTriggerHelper(this).finishForTasker()
                }
            }
        } else {
            saveButton.visibility = View.GONE
            actionConfigLayout.visibility = View.GONE
        }
    }

    override val inputForTasker get() = TaskerInput(GetConfigInput(deviceName, deviceId))
    override fun assignFromInput(input: TaskerInput<GetConfigInput>) {
        //not needed because all process is handled at onCreate()
    }
}

fun callTaskerEvent(deviceName : String, deviceId: String, context: Context) {
    val obj = GetConfigInput(deviceName, deviceId)
    TaskerPairEvent::class.java.requestQuery(context, obj)
}

