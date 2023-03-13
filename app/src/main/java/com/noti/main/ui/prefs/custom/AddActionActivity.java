package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.noti.main.R;
import com.noti.main.utils.ui.ToastHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

@SuppressLint("SetTextI18n")
public class AddActionActivity extends AppCompatActivity {

    SharedPreferences regexPrefs;
    JSONObject jsonObject;

    TextView ringtoneSummary;
    TextView iconSummary;

    MaterialButton ringtoneReset;
    MaterialButton iconReset;

    ActivityResultLauncher<Intent> startOpenRingtone = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if(result.getData() != null) {
            try {
                Intent data = result.getData();
                Uri AudioMedia = result.getResultCode() == Activity.RESULT_CANCELED ? null : data.getData();
                if (AudioMedia == null) {
                    ToastHelper.show(this, "Please choose audio file!", "OK", ToastHelper.LENGTH_SHORT);
                } else {
                    DocumentFile file = DocumentFile.fromSingleUri(this, AudioMedia);
                    ringtoneSummary.setText("Now : " + (file == null ? "default setting" : file.getName()));
                    getContentResolver().takePersistableUriPermission(AudioMedia, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    ringtoneReset.setVisibility(View.VISIBLE);
                    jsonObject.put("ringtone", AudioMedia.toString());
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    });

    ActivityResultLauncher<Intent> startOpenIcon = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if(result.getData() != null) {
            try {
                Intent data = result.getData();
                Uri ImageMedia = result.getResultCode() == Activity.RESULT_CANCELED ? null : data.getData();
                if (ImageMedia == null) {
                    ToastHelper.show(this, "Please choose image file!", "OK", ToastHelper.LENGTH_SHORT);
                } else {
                    DocumentFile file = DocumentFile.fromSingleUri(this, ImageMedia);
                    iconSummary.setText("Now : " + (file == null ? "default setting" : file.getName()));
                    getContentResolver().takePersistableUriPermission(ImageMedia, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    iconReset.setVisibility(View.VISIBLE);
                    jsonObject.put("bitmap", ImageMedia.toString());
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    });

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_action);

        TextInputEditText regexValue = findViewById(R.id.regexValue);
        TextInputEditText labelValue = findViewById(R.id.labelValue);
        ExtendedFloatingActionButton saveButton = findViewById(R.id.saveButton);

        LinearLayout ringtoneLayout = findViewById(R.id.ringtoneLayout);
        LinearLayout iconLayout = findViewById(R.id.iconLayout);

        ringtoneSummary = findViewById(R.id.ringtoneSummary);
        ringtoneReset = findViewById(R.id.ringtoneReset);
        iconSummary = findViewById(R.id.iconSummary);
        iconReset = findViewById(R.id.iconReset);

        regexPrefs = getSharedPreferences("com.noti.main_regex", Context.MODE_PRIVATE);
        int index = getIntent().getIntExtra("index", -1);

        if(index != -1) {
            try {
                jsonObject = new JSONArray(regexPrefs.getString("RegexData", "")).getJSONObject(index);
                labelValue.setText(jsonObject.getString("label"));
                regexValue.setText(jsonObject.getString("regex"));

                if(jsonObject.has("ringtone")) {
                    DocumentFile AudioMedia = DocumentFile.fromSingleUri(this, Uri.parse(jsonObject.getString("ringtone")));
                    if (AudioMedia != null && AudioMedia.exists()) {
                        ringtoneSummary.setText("Now : " + AudioMedia.getName());
                        ringtoneReset.setVisibility(View.VISIBLE);
                    }
                }

                if(jsonObject.has("bitmap")) {
                    DocumentFile IconMedia = DocumentFile.fromSingleUri(this, Uri.parse(jsonObject.getString("bitmap")));
                    if (IconMedia != null && IconMedia.exists()) {
                        iconSummary.setText("Now : " + IconMedia.getName());
                        iconReset.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else jsonObject = new JSONObject();

        ringtoneLayout.setOnClickListener((v) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            startOpenRingtone.launch(intent);
        });

        ringtoneReset.setOnClickListener((v) -> {
            try {
                ringtoneSummary.setText("Now : default setting");
                getContentResolver()
                        .releasePersistableUriPermission(Uri.parse(jsonObject.getString("ringtone")),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                jsonObject.remove("ringtone");
                ToastHelper.show(this, "Selection reset done!", "OK", ToastHelper.LENGTH_SHORT);
                ringtoneReset.setVisibility(View.GONE);
            } catch(JSONException e) {
                ToastHelper.show(this, "Error occurred while reset selection!", "OK", ToastHelper.LENGTH_SHORT);
                e.printStackTrace();
            }
        });

        iconLayout.setOnClickListener((v) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startOpenIcon.launch(intent);
        });

        iconReset.setOnClickListener((v) -> {
            try {
                iconSummary.setText("Now : default setting");
                getContentResolver()
                        .releasePersistableUriPermission(Uri.parse(jsonObject.getString("bitmap")),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                jsonObject.remove("ringtone");
                ToastHelper.show(this, "Selection reset done!", "OK", ToastHelper.LENGTH_SHORT);
                iconReset.setVisibility(View.GONE);
            } catch(JSONException e) {
                ToastHelper.show(this, "Error occurred while reset selection!", "OK", ToastHelper.LENGTH_SHORT);
                e.printStackTrace();
            }
        });

        saveButton.setOnClickListener((v) -> {
            JSONArray array = new JSONArray();

            try {
                String data = regexPrefs.getString("RegexData", "");
                if (!data.isEmpty()) array = new JSONArray(data);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String label = Objects.requireNonNull(labelValue.getText()).toString().trim();
            String regex = Objects.requireNonNull(regexValue.getText()).toString().trim();

            if(label.isEmpty() || regex.isEmpty()) {
                if(labelValue.getText() == null) labelValue.setError("Please input value!");
                if(regexValue.getText() == null) regexValue.setError("Please input value!");
            } else {
                try {
                    jsonObject.put("label", label);
                    jsonObject.put("regex", regex);

                    if (index != -1) {
                        array.put(index, jsonObject);
                    } else {
                        array.put(jsonObject);
                    }

                    regexPrefs.edit().putString("RegexData", array.toString()).apply();
                    setResult(1, new Intent().putExtra("index", index));
                    AddActionActivity.this.finish();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}
