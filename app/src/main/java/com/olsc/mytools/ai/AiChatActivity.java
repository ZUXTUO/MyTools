package com.olsc.mytools.ai;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;
import com.olsc.mytools.R;
import com.olsc.mytools.ai.ChatMessage;
import com.olsc.mytools.network.ChatClient;
import com.olsc.mytools.network.ChatClientFactory;
import com.olsc.mytools.util.AppConfig;

import io.noties.markwon.Markwon;

import java.util.ArrayList;
import java.util.List;

public class AiChatActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private EditText etMessage;
    private ImageView btnSend;
    private ImageView btnSettings;
    private ImageView btnBack;
    private ImageView btnClear;

    private AppConfig config;
    private com.olsc.mytools.network.ChatClient aiClient;
    private Markwon markwon;
    private boolean isGenerating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_ai_chat);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_root), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            
            // Adjust bottom padding to account for keyboard or navigation bar
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            
            return insets;
        });

        config = new AppConfig(this);
        aiClient = com.olsc.mytools.network.ChatClientFactory.create(this, config);
        markwon = Markwon.create(this);

        messages.addAll(config.getChatHistory());

        initViews();
        setupRecyclerView();
        setupListeners();
        if (!messages.isEmpty()) {
            recyclerView.scrollToPosition(messages.size() - 1);
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnSettings = findViewById(R.id.btn_settings);
        btnBack = findViewById(R.id.btn_back);
        btnClear = findViewById(R.id.btn_clear);
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter(messages, markwon, config);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnSend.setOnClickListener(v -> sendMessage());
        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.clear_history_title)
                    .setMessage(R.string.clear_history_confirm)
                    .setPositiveButton(R.string.confirm, (d, w) -> {
                        config.clearChatHistory();
                        messages.clear();
                        adapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void sendMessage() {
        if (isGenerating) return;
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        if (config.getApiKey().isEmpty() && config.getProviderIndex() != 5) {
            Toast.makeText(this, R.string.api_key_missing, Toast.LENGTH_SHORT).show();
            showSettingsDialog();
            return;
        }

        isGenerating = true;
        updateUIState();
        
        messages.add(new ChatMessage(text, ChatMessage.TYPE_USER));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.smoothScrollToPosition(messages.size() - 1);
        etMessage.setText("");

        aiClient.sendMessage(text, messages.subList(0, messages.size() - 1), new com.olsc.mytools.network.ChatClient.Callback() {
            private int aiMsgIndex = -1;

            @Override
            public void onStart() {
                runOnUiThread(() -> {
                    messages.add(new ChatMessage("", ChatMessage.TYPE_AI));
                    aiMsgIndex = messages.size() - 1;
                    adapter.notifyItemInserted(aiMsgIndex);
                    recyclerView.smoothScrollToPosition(aiMsgIndex);
                });
            }

            @Override
            public void onChunk(String chunk, String thinkingChunk) {
                runOnUiThread(() -> {
                    if (aiMsgIndex != -1) {
                        ChatMessage msg = messages.get(aiMsgIndex);
                        if (!chunk.isEmpty()) msg.setContent(msg.getContent() + chunk);
                        if (!thinkingChunk.isEmpty()) {
                            msg.setThink(msg.getThink() + thinkingChunk);
                        }
                        adapter.notifyItemChanged(aiMsgIndex);
                    }
                });
            }

            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    isGenerating = false;
                    updateUIState();
                    config.saveChatHistory(messages);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isGenerating = false;
                    updateUIState();
                    Toast.makeText(AiChatActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateUIState() {
        btnSend.setEnabled(!isGenerating);
        btnSend.setAlpha(isGenerating ? 0.5f : 1.0f);
        etMessage.setEnabled(!isGenerating);
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_chat_settings, null);
        EditText etUrl = dialogView.findViewById(R.id.settings_api_url);
        EditText etKey = dialogView.findViewById(R.id.settings_api_key);
        EditText etModel = dialogView.findViewById(R.id.settings_ai_model);
        EditText etSystem = dialogView.findViewById(R.id.settings_system_prompt);
        android.widget.Spinner spinnerProvider = dialogView.findViewById(R.id.settings_api_provider);
        SwitchCompat swContext = dialogView.findViewById(R.id.settings_context_toggle);
        SwitchCompat swThink = dialogView.findViewById(R.id.settings_think_toggle);

        // Setup Spinner
        android.widget.ArrayAdapter<CharSequence> spinnerAdapter = android.widget.ArrayAdapter.createFromResource(this,
                R.array.provider_options, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(spinnerAdapter);
        spinnerProvider.setSelection(mapProviderToPos(config.getProviderIndex()));

        etUrl.setText(config.getApiUrl());
        etKey.setText(config.getApiKey());
        etModel.setText(config.getModel());
        etSystem.setText(config.getSystemPrompt());
        swContext.setChecked(config.isContextEnabled());
        swThink.setChecked(config.isThinkEnabled());

        final boolean[] isUpdatingFields = {false};

        spinnerProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                isUpdatingFields[0] = true;
                int actualProviderIndex = mapPosToProvider(position);
                config.setProviderIndex(actualProviderIndex);
                etUrl.setText(config.getApiUrl(actualProviderIndex));
                etKey.setText(config.getApiKey(actualProviderIndex));
                etModel.setText(config.getModel(actualProviderIndex));
                etSystem.setText(config.getSystemPrompt(actualProviderIndex));
                swContext.setChecked(config.isContextEnabled(actualProviderIndex));
                swThink.setChecked(config.isThinkEnabled(actualProviderIndex));
                aiClient = com.olsc.mytools.network.ChatClientFactory.create(AiChatActivity.this, config);
                isUpdatingFields[0] = false;
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        android.text.TextWatcher autoSaveWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isUpdatingFields[0]) return;
                
                int pos = spinnerProvider.getSelectedItemPosition();
                int actualProviderIndex = mapPosToProvider(pos);
                config.setApiUrl(actualProviderIndex, etUrl.getText().toString().trim());
                config.setApiKey(actualProviderIndex, etKey.getText().toString().trim());
                config.setModel(actualProviderIndex, etModel.getText().toString().trim());
                config.setSystemPrompt(actualProviderIndex, etSystem.getText().toString().trim());
            }
        };

        etUrl.addTextChangedListener(autoSaveWatcher);
        etKey.addTextChangedListener(autoSaveWatcher);
        etModel.addTextChangedListener(autoSaveWatcher);
        etSystem.addTextChangedListener(autoSaveWatcher);

        swContext.setOnCheckedChangeListener((v, isChecked) -> {
            if (isUpdatingFields[0]) return;
            int pos = spinnerProvider.getSelectedItemPosition();
            config.setContextEnabled(mapPosToProvider(pos), isChecked);
        });
        swThink.setOnCheckedChangeListener((v, isChecked) -> {
            if (isUpdatingFields[0]) return;
            int pos = spinnerProvider.getSelectedItemPosition();
            config.setThinkEnabled(mapPosToProvider(pos), isChecked);
            adapter.notifyDataSetChanged();
        });

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(dialogView);

        dialogView.findViewById(R.id.btn_fetch_models).setOnClickListener(v -> {
            String baseUrl = etUrl.getText().toString().trim();
            String apiKey = etKey.getText().toString().trim();
            if (baseUrl.isEmpty()) return;

            // Simple model fetching logic
            String modelsUrl = baseUrl;
            if (modelsUrl.endsWith("/chat/completions")) {
                modelsUrl = modelsUrl.replace("/chat/completions", "/models");
            } else if (modelsUrl.endsWith("/chat")) {
                modelsUrl = modelsUrl.replace("/chat", "/models");
            } else if (!modelsUrl.contains("/models")) {
                modelsUrl = modelsUrl.replaceAll("/+$", "") + "/models";
            }

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(modelsUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("x-api-key", apiKey) // For Anthropic
                    .get()
                    .build();

            new okhttp3.OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(AiChatActivity.this, getString(R.string.error_http) + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    try (okhttp3.ResponseBody body = response.body()) {
                        if (body == null) return;
                        String json = body.string();
                        JsonObject root = new com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject.class);
                        com.google.gson.JsonArray data = root.getAsJsonArray("data");
                        if (data == null) data = root.getAsJsonArray("models"); // Native v1 key

                        if (data != null && data.size() > 0) {
                            String[] modelNames = new String[data.size()];
                            String[] displayNames = new String[data.size()];
                            for (int i = 0; i < data.size(); i++) {
                                com.google.gson.JsonObject m = data.get(i).getAsJsonObject();
                                // Support OpenAI 'id' and LM Studio 'key'
                                String id = m.has("id") ? m.get("id").getAsString() : 
                                           (m.has("key") ? m.get("key").getAsString() : "unknown");
                                String name = m.has("display_name") ? m.get("display_name").getAsString() : id;
                                
                                modelNames[i] = id;
                                displayNames[i] = name;
                            }

                            runOnUiThread(() -> {
                                new AlertDialog.Builder(AiChatActivity.this)
                                        .setTitle(R.string.select_model)
                                        .setItems(displayNames, (d, which) -> {
                                            etModel.setText(modelNames[which]);
                                        })
                                        .show();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(AiChatActivity.this, R.string.no_models_found, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(AiChatActivity.this, R.string.parse_error, Toast.LENGTH_SHORT).show());
                    }
                }
            });
        });

        dialog.setOnDismissListener(d -> {
            // Final sync and recreate client
            aiClient = com.olsc.mytools.network.ChatClientFactory.create(this, config);
        });

        dialog.show();
    }

    private int mapPosToProvider(int pos) {
        // 0 -> Zhipu (2), 1 -> LM Studio (5)
        return pos == 0 ? 2 : 5;
    }

    private int mapProviderToPos(int index) {
        if (index == 2) return 0;
        if (index == 5) return 1;
        return 0; // Default to Zhipu
    }
}
