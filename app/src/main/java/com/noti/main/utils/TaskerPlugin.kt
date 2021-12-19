package com.noti.main.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup

import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.radiobutton.MaterialRadioButton

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError

import com.noti.main.R

import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.noti.main.ui.ToastHelper

class TaskerConfigActionHelper(config: TaskerPluginConfig<GetConfigInput>) : TaskerPluginConfigHelperNoOutput<GetConfigInput,TaskerConfigActionRunner>(config) {
    override val runnerClass: Class<TaskerConfigActionRunner> get() = TaskerConfigActionRunner::class.java
    override val inputClass: Class<GetConfigInput> get() = GetConfigInput::class.java
}

@TaskerInputRoot
class GetConfigInput @JvmOverloads constructor(
    @field:TaskerInputField("taskType") var taskType: String = ""
)

class TaskerConfigAction : Activity(), TaskerPluginConfig<GetConfigInput> {
    private var taskResult : String = ""
    override val context: Context get() = applicationContext
    private val taskerHelper by lazy { TaskerConfigActionHelper(this) }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App_Palette)
        recreate()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker)
        taskerHelper.onCreate()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val waringLayout = findViewById<LinearLayout>(R.id.waring_layout)
        val radioLayout = findViewById<RadioGroup>(R.id.radio_layout)
        val saveButton = findViewById<ExtendedFloatingActionButton>(R.id.save_button)

        val toggleOn = findViewById<MaterialRadioButton>(R.id.toggleOn)
        val toggleOff = findViewById<MaterialRadioButton>(R.id.toggleOff)
        val toggleAuto = findViewById<MaterialRadioButton>(R.id.toggleAuto)

        val prefs = context.getSharedPreferences("com.noti.main_preferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean("UseTaskerExtension", false)) {
            waringLayout.visibility = View.GONE

            var toReturn = ""
            saveButton.setOnClickListener {
                when (true) {
                    toggleOn.isChecked -> toReturn = toggleOn.text as String
                    toggleOff.isChecked -> toReturn = toggleOff.text as String
                    toggleAuto.isChecked -> toReturn = toggleAuto.text as String

                    else -> ToastHelper.show(this, "Please choose config option!", "OK", ToastHelper.LENGTH_SHORT, saveButton)
                }

                if (toReturn.isNotEmpty()) {
                    taskResult = toReturn
                    taskerHelper.finishForTasker()
                }
            }
        } else {
            radioLayout.visibility = View.GONE
            saveButton.visibility = View.GONE
        }
    }

    override fun assignFromInput(input: TaskerInput<GetConfigInput>) {
        //not needed because all process is handled at onCreate()
    }

    override val inputForTasker: TaskerInput<GetConfigInput> get() = TaskerInput(GetConfigInput(taskResult))
}

class TaskerConfigActionRunner : TaskerPluginRunnerActionNoOutput<GetConfigInput>() {
    override fun run(context: Context, input: TaskerInput<GetConfigInput>): TaskerPluginResult<Unit> {
        val prefs = context.getSharedPreferences("com.noti.main_preferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean("UseTaskerExtension", false)) {
            val stateToChange : Boolean = when(input.regular.taskType) {
                "Enable Service Toggle" -> true
                "Disable Service Toggle" -> false
                "Automatically Enable/Disable" -> !prefs.getBoolean("serviceToggle", false)

                else -> return TaskerPluginResultError(IllegalArgumentException("Task type is not selected!"))
            }

            prefs.edit().putBoolean("serviceToggle", stateToChange).apply()
            return TaskerPluginResultSucess()
        }

        return TaskerPluginResultError(IllegalArgumentException("Tasker extension option not enabled"))
    }
}
