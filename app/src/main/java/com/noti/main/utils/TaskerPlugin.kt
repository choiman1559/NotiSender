package com.noti.main.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*

import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess

import com.noti.main.R
import com.noti.main.service.pair.DataProcess
import com.noti.main.utils.ui.ToastHelper

class TaskerConfigActionHelper(config: TaskerPluginConfig<GetConfigInput>) :
    TaskerPluginConfigHelperNoOutput<GetConfigInput, TaskerConfigActionRunner>(config) {
    override val runnerClass: Class<TaskerConfigActionRunner> get() = TaskerConfigActionRunner::class.java
    override val inputClass: Class<GetConfigInput> get() = GetConfigInput::class.java
}

@TaskerInputRoot
class GetConfigInput @JvmOverloads constructor(
    @field:TaskerInputField("taskType") var taskType: String = ""
)

class TaskerConfigAction : Activity(), TaskerPluginConfig<GetConfigInput> {
    private var taskResult: String = ""
    override val context: Context get() = applicationContext
    private val taskerHelper by lazy { TaskerConfigActionHelper(this) }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
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
        val togglePair = findViewById<MaterialRadioButton>(R.id.togglePair)

        var deviceSelection = 0
        val actionConfigLayout = findViewById<LinearLayout>(R.id.actionConfigLayout)
        val deviceSelectSpinner = findViewById<MaterialAutoCompleteTextView>(R.id.deviceSelectSpinner)
        val taskSelectSpinner = findViewById<MaterialAutoCompleteTextView>(R.id.taskSelectSpinner)
        val taskArgs0 = findViewById<TextInputEditText>(R.id.taskArgs0)
        val taskArgs1 = findViewById<TextInputEditText>(R.id.taskArgs1)

        val prefs = context.getSharedPreferences("com.noti.main_preferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean("UseTaskerExtension", false)) {
            waringLayout.visibility = View.GONE
            actionConfigLayout.visibility = View.GONE
            taskArgs0.visibility = View.GONE
            taskArgs1.visibility = View.GONE

            val pairPrefs = context.getSharedPreferences("com.noti.main_pair", Context.MODE_PRIVATE)
            val rawList = ArrayList<String>()
            val nameList = ArrayList<String>()
            for (str in pairPrefs.getStringSet("paired_list", HashSet<String>())!!) {
                rawList.add(str)
            }
            for (str in rawList) {
                nameList.add(str.split("|")[0])
            }

            deviceSelectSpinner.setAdapter<ArrayAdapter<String>>(
                ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, nameList)
            )

            taskSelectSpinner.setAdapter<ArrayAdapter<String>>(
                ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    resources.getStringArray(R.array.pairAction)
                )
            )

            var toReturn = ""
            saveButton.setOnClickListener {
                when (true) {
                    toggleOn.isChecked -> toReturn = toggleOn.text as String
                    toggleOff.isChecked -> toReturn = toggleOff.text as String
                    toggleAuto.isChecked -> toReturn = toggleAuto.text as String
                    togglePair.isChecked -> {
                        if (deviceSelectSpinner.text.toString().isEmpty()) {
                            deviceSelectSpinner.error = "Please select target device"
                        } else if (taskSelectSpinner.text.toString().isEmpty()) {
                            taskSelectSpinner.error = "Please select task to run"
                        } else if (taskArgs0.visibility == View.VISIBLE && taskArgs0.text.toString().isEmpty()) {
                            taskArgs0.error = "Please type argument"
                        } else if (taskArgs1.visibility == View.VISIBLE && taskArgs1.text.toString().isEmpty()) {
                            taskArgs1.error = "Please type argument"
                        } else {
                            toReturn = "pairTask|"
                            toReturn += rawList[deviceSelection] + "|"
                            toReturn += taskSelectSpinner.text.toString() + "|"
                            if (taskArgs0.visibility == View.VISIBLE) toReturn += taskArgs0.text.toString().replace("|", "").trim() + "|"
                            if (taskArgs1.visibility == View.VISIBLE) toReturn += taskArgs1.text.toString().replace("|", "").trim()
                        }
                    }

                    else -> ToastHelper.show(
                        this,
                        "Please choose config option!",
                        "OK",
                        ToastHelper.LENGTH_SHORT,
                        saveButton
                    )
                }

                if (toReturn.isNotEmpty()) {
                    taskResult = toReturn
                    taskerHelper.finishForTasker()
                }
            }

            radioLayout.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.togglePair) {
                    actionConfigLayout.visibility = View.VISIBLE
                } else {
                    actionConfigLayout.visibility = View.GONE
                }
            }

            deviceSelectSpinner.setOnItemClickListener { _, _, position, _ ->
                deviceSelection = position
            }

            taskSelectSpinner.setOnItemClickListener { _, _, position, _ ->
                when (position) {
                    0 -> {
                        taskArgs0.visibility = View.VISIBLE
                        taskArgs1.visibility = View.VISIBLE
                        taskArgs0.hint = "Notification Title"
                        taskArgs1.hint = "Notification Content"
                    }

                    1 -> {
                        taskArgs0.visibility = View.VISIBLE
                        taskArgs1.visibility = View.GONE
                        taskArgs0.hint = "Text to send"
                    }

                    2 -> {
                        taskArgs0.visibility = View.VISIBLE
                        taskArgs1.visibility = View.GONE
                        taskArgs0.hint = "Url to open in browser"
                    }

                    3 -> {
                        taskArgs0.visibility = View.GONE
                        taskArgs1.visibility = View.GONE
                    }

                    4 -> {
                        taskArgs0.visibility = View.VISIBLE
                        taskArgs1.visibility = View.GONE
                        taskArgs0.hint = "app's package name to open"
                    }

                    5 -> {
                        taskArgs0.visibility = View.VISIBLE
                        taskArgs1.visibility = View.GONE
                        taskArgs0.hint = "Type terminal command to run"
                    }
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

    override val inputForTasker: TaskerInput<GetConfigInput>
        get() = TaskerInput(
            GetConfigInput(
                taskResult
            )
        )
}

class TaskerConfigActionRunner : TaskerPluginRunnerActionNoOutput<GetConfigInput>() {
    override fun run(
        context: Context,
        input: TaskerInput<GetConfigInput>
    ): TaskerPluginResult<Unit> {
        val prefs = context.getSharedPreferences("com.noti.main_preferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean("UseTaskerExtension", false)) {
            val taskType = input.regular.taskType
            if (taskType.startsWith("pairTask")) {
                val args = taskType.split("|")
                when(args.size) {
                    6 -> DataProcess.requestAction(context, args[1], args[2], args[3], args[4], args[5])
                    5 -> DataProcess.requestAction(context, args[1], args[2], args[3], args[4])
                    else -> DataProcess.requestAction(context, args[1], args[2], args[3])
                }
            } else {
                val stateToChange: Boolean = when (taskType) {
                    "Enable Service Toggle" -> true
                    "Disable Service Toggle" -> false
                    "Automatically Enable/Disable" -> !prefs.getBoolean("serviceToggle", false)

                    else -> return TaskerPluginResultError(IllegalArgumentException("Task type is not selected!"))
                }
                prefs.edit().putBoolean("serviceToggle", stateToChange).apply()
            }
            return TaskerPluginResultSucess()
        }

        return TaskerPluginResultError(IllegalStateException("Tasker extension option not enabled"))
    }
}
