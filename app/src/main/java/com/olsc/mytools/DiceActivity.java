package com.olsc.mytools;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class DiceActivity extends AppCompatActivity {

    private SeekBar seekbarFaces;
    private SeekBar seekbarCount;
    private TextView labelFaces;
    private TextView labelCount;
    private TextView textTotal;
    private TextView textEmptyHint;
    private ChipGroup resultsChipGroup;
    private LinearLayout historyContainer;
    private Button btnRoll;
    
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "DicePrefs";
    private static final String KEY_FACES = "faces";
    private static final String KEY_COUNT = "count";
    private static final String KEY_FACES_MAX = "faces_max";
    private static final String KEY_COUNT_MAX = "count_max";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_LAST_RESULT = "last_result";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        androidx.core.view.WindowInsetsControllerCompat controller = androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!isDarkMode);

        setContentView(R.layout.activity_dice);

        // Handle window insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dice_layout), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupListeners();
        loadSettings();
    }

    private void initViews() {
        seekbarFaces = findViewById(R.id.seekbar_faces);
        seekbarCount = findViewById(R.id.seekbar_count);
        labelFaces = findViewById(R.id.label_faces);
        labelCount = findViewById(R.id.label_count);
        textTotal = findViewById(R.id.text_total);
        textEmptyHint = findViewById(R.id.text_empty_hint);
        resultsChipGroup = findViewById(R.id.results_chip_group);
        historyContainer = findViewById(R.id.history_container);
        btnRoll = findViewById(R.id.btn_roll);

        updateLabels();
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        seekbarFaces.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabels();
                if (fromUser) saveSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabels();
                if (fromUser) saveSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btn_edit_faces_max).setOnClickListener(v -> showMaxInputDialog(true));
        findViewById(R.id.btn_edit_count_max).setOnClickListener(v -> showMaxInputDialog(false));

        btnRoll.setOnClickListener(v -> rollDice());
    }

    private void showMaxInputDialog(boolean isFaces) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dice_set_max_title);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(isFaces ? seekbarFaces.getMax() : seekbarCount.getMax()));
        builder.setView(input);

        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            try {
                int newVal = Integer.parseInt(input.getText().toString());
                if (newVal < 1) newVal = 1;
                if (isFaces) {
                    seekbarFaces.setMax(newVal);
                    prefs.edit().putInt(KEY_FACES_MAX, newVal).apply();
                } else {
                    seekbarCount.setMax(newVal);
                    prefs.edit().putInt(KEY_COUNT_MAX, newVal).apply();
                }
                updateLabels();
            } catch (Exception ignored) {}
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateLabels() {
        int faces = seekbarFaces.getProgress();
        int count = seekbarCount.getProgress();
        labelFaces.setText(getString(R.string.dice_faces_label, faces));
        labelCount.setText(getString(R.string.dice_count_label, count));
    }

    private void loadSettings() {
        int facesMax = prefs.getInt(KEY_FACES_MAX, 20);
        int countMax = prefs.getInt(KEY_COUNT_MAX, 20);
        seekbarFaces.setMax(facesMax);
        seekbarCount.setMax(countMax);

        seekbarFaces.setProgress(prefs.getInt(KEY_FACES, 6));
        seekbarCount.setProgress(prefs.getInt(KEY_COUNT, 1));
        
        String lastRes = prefs.getString(KEY_LAST_RESULT, "");
        if (!lastRes.isEmpty()) {
            String[] parts = lastRes.split(",");
            List<Integer> results = new ArrayList<>();
            int total = 0;
            for (String p : parts) {
                int val = Integer.parseInt(p);
                results.add(val);
                total += val;
            }
            displayResults(results, total, false);
        }
        
        displayHistory();
    }

    private void saveSettings() {
        prefs.edit()
                .putInt(KEY_FACES, seekbarFaces.getProgress())
                .putInt(KEY_COUNT, seekbarCount.getProgress())
                .apply();
    }

    private void rollDice() {
        int faces = seekbarFaces.getProgress();
        int count = seekbarCount.getProgress();

        btnRoll.setEnabled(false);
        resultsChipGroup.setVisibility(View.GONE);
        textTotal.setVisibility(View.GONE);
        textEmptyHint.setVisibility(View.VISIBLE);
        textEmptyHint.setText(R.string.dice_rolling);

        handler.postDelayed(() -> {
            List<Integer> results = new ArrayList<>();
            int total = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                int res = random.nextInt(faces) + 1;
                results.add(res);
                total += res;
                sb.append(res).append(i == count - 1 ? "" : ",");
            }

            displayResults(results, total, true);
            addToHistory(results, total);
            
            prefs.edit().putString(KEY_LAST_RESULT, sb.toString()).apply();
            btnRoll.setEnabled(true);
        }, 600);
    }

    private void displayResults(List<Integer> results, int total, boolean animate) {
        textEmptyHint.setVisibility(View.GONE);
        resultsChipGroup.removeAllViews();
        resultsChipGroup.setVisibility(View.VISIBLE);

        for (int res : results) {
            Chip chip = new Chip(this);
            chip.setText(String.valueOf(res));
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setChipBackgroundColorResource(R.color.bg_surface);
            chip.setTextColor(getResources().getColor(R.color.text_primary));
            resultsChipGroup.addView(chip);
        }

        if (results.size() > 1) {
            textTotal.setText(getString(R.string.dice_total, total));
            textTotal.setVisibility(View.VISIBLE);
            if (animate) {
                textTotal.setAlpha(0f);
                textTotal.animate().alpha(1f).setDuration(300).start();
            }
        } else {
            textTotal.setVisibility(View.GONE);
        }
        
        if (animate) {
            resultsChipGroup.setAlpha(0f);
            resultsChipGroup.setScaleX(0.8f);
            resultsChipGroup.setScaleY(0.8f);
            resultsChipGroup.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    private void addToHistory(List<Integer> results, int total) {
        String history = prefs.getString(KEY_HISTORY, "");
        StringBuilder sb = new StringBuilder();
        sb.append(total).append(":").append(results.toString());
        
        List<String> records = new ArrayList<>();
        if (!history.isEmpty()) {
            records.addAll(Arrays.asList(history.split("\\|")));
        }
        records.add(0, sb.toString());
        
        if (records.size() > 20) {
            records = records.subList(0, 20);
        }
        
        StringBuilder historySb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            historySb.append(records.get(i)).append(i == records.size() - 1 ? "" : "|");
        }
        prefs.edit().putString(KEY_HISTORY, historySb.toString()).apply();
        displayHistory();
    }

    private void displayHistory() {
        historyContainer.removeAllViews();
        String history = prefs.getString(KEY_HISTORY, "");
        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.dice_history_empty);
            empty.setTextColor(getResources().getColor(R.color.text_secondary));
            empty.setTextSize(14);
            historyContainer.addView(empty);
            return;
        }

        String[] records = history.split("\\|");
        for (String record : records) {
            String[] parts = record.split(":");
            if (parts.length < 2) continue;
            
            View item = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, historyContainer, false);
            TextView text1 = item.findViewById(android.R.id.text1);
            TextView text2 = item.findViewById(android.R.id.text2);
            
            text1.setText(getString(R.string.dice_history_total, Integer.parseInt(parts[0])));
            text1.setTextColor(getResources().getColor(R.color.text_primary));
            text2.setText(parts[1].replace("[", "").replace("]", ""));
            text2.setTextColor(getResources().getColor(R.color.text_secondary));
            
            historyContainer.addView(item);
        }
    }
}
