package com.olsc.mytools.network;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.olsc.mytools.ai.ChatMessage;
import com.olsc.mytools.util.AppConfig;
import okhttp3.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class BaseChatClient implements ChatClient {
    protected final OkHttpClient httpClient;
    protected final Gson gson;
    protected final AppConfig config;
    protected final Context context;

    public BaseChatClient(Context context, AppConfig config) {
        this.context = context;
        this.config = config;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    protected abstract String getApiUrl();
    protected abstract Request buildRequest(String userMessage, List<ChatMessage> history);

    @Override
    public void sendMessage(String userMessage, List<ChatMessage> history, Callback callback) {
        Request request = buildRequest(userMessage, history);
        callback.onStart();

        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMsg = context.getString(com.olsc.mytools.R.string.error_http) + response.code();
                    if (response.code() == 429) errorMsg += " (频率过快或额度不足)";
                    callback.onError(errorMsg);
                    return;
                }

                handleStreamResponse(response, callback);
            }
        });
    }

    protected void handleStreamResponse(Response response, Callback callback) throws IOException {
        try (ResponseBody responseBody = response.body()) {
            if (responseBody == null) return;
            okio.BufferedSource source = responseBody.source();
            StringBuilder fullContent = new StringBuilder();
            boolean isThinkingState = false;
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) continue;
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if (data.equals("[DONE]")) break;

                    try {
                        JsonObject json = gson.fromJson(data, JsonObject.class);
                        JsonArray choices = json.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                            if (delta != null) {
                                String content = delta.has("content") && !delta.get("content").isJsonNull() ? delta.get("content").getAsString() : "";
                                String reasoning = delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull() ? delta.get("reasoning_content").getAsString() : "";
                                
                                if (!reasoning.isEmpty()) {
                                    callback.onChunk("", reasoning);
                                }

                                if (!content.isEmpty()) {
                                    // Handle cases where <think> tags are inside the content (common in local models)
                                    if (content.contains("<think>")) {
                                        isThinkingState = true;
                                        String[] parts = content.split("<think>", 2);
                                        if (!parts[0].isEmpty()) {
                                            fullContent.append(parts[0]);
                                            callback.onChunk(parts[0], "");
                                        }
                                        if (parts.length > 1 && !parts[1].isEmpty()) {
                                            callback.onChunk("", parts[1]);
                                        }
                                        continue;
                                    }
                                    
                                    if (content.contains("</think>")) {
                                        isThinkingState = false;
                                        String[] parts = content.split("</think>", 2);
                                        if (!parts[0].isEmpty()) {
                                            callback.onChunk("", parts[0]);
                                        }
                                        if (parts.length > 1 && !parts[1].isEmpty()) {
                                            fullContent.append(parts[1]);
                                            callback.onChunk(parts[1], "");
                                        }
                                        continue;
                                    }

                                    if (isThinkingState) {
                                        callback.onChunk("", content);
                                    } else {
                                        fullContent.append(content);
                                        callback.onChunk(content, "");
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            callback.onSuccess(fullContent.toString());
        } catch (Exception e) {
            callback.onError(context.getString(com.olsc.mytools.R.string.error_parse));
        }
    }

    protected JsonArray buildMessagesArray(String userMessage, List<ChatMessage> history, boolean includeSystem) {
        JsonArray messagesArr = new JsonArray();
        if (includeSystem) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", config.getSystemPrompt());
            messagesArr.add(systemMsg);
        }

        if (config.isContextEnabled()) {
            for (ChatMessage msg : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.getType() == ChatMessage.TYPE_USER ? "user" : "assistant");
                String fullContent = msg.hasThink() ? "<think>" + msg.getThink() + "</think>\n" + msg.getContent() : msg.getContent();
                m.addProperty("content", fullContent);
                messagesArr.add(m);
            }
        }

        JsonObject currentMsg = new JsonObject();
        currentMsg.addProperty("role", "user");
        currentMsg.addProperty("content", userMessage);
        messagesArr.add(currentMsg);
        return messagesArr;
    }
}
