package com.olsc.mytools.network;

import android.content.Context;
import com.google.gson.JsonObject;
import com.olsc.mytools.ai.ChatMessage;
import com.olsc.mytools.util.AppConfig;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import java.util.List;

public class LMStudioClient extends BaseChatClient {
    public LMStudioClient(Context context, AppConfig config) {
        super(context, config);
    }

    @Override
    protected String getApiUrl() {
        return config.getApiUrl();
    }

    @Override
    protected Request buildRequest(String userMessage, List<ChatMessage> history) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.getModel());
        root.addProperty("stream", true);
        
        // Native v1 API uses 'input'. We concatenate history to maintain context in this stateless-style call to a stateful API
        StringBuilder fullInput = new StringBuilder();
        fullInput.append("System: ").append(config.getSystemPrompt()).append("\n\n");
        for (ChatMessage msg : history) {
            String role = msg.getType() == ChatMessage.TYPE_USER ? "User" : "Assistant";
            fullInput.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        fullInput.append("User: ").append(userMessage);
        
        root.addProperty("input", fullInput.toString());
        root.addProperty("max_output_tokens", 4096);
        root.addProperty("temperature", 0.7);

        RequestBody body = RequestBody.create(root.toString(), MediaType.get("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder()
                .url(getApiUrl())
                .post(body);

        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }
        
        return builder.build();
    }

    @Override
    protected void handleStreamResponse(okhttp3.Response response, Callback callback) throws java.io.IOException {
        // Native v1 API response format is different: data: {"content": "...", "done": false}
        try (okhttp3.ResponseBody responseBody = response.body()) {
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
                        JsonObject json = new com.google.gson.Gson().fromJson(data, JsonObject.class);
                        String content = "";
                        
                        // Native v1 format has 'content' at root
                        if (json.has("content")) {
                            content = json.get("content").getAsString();
                        } else if (json.has("choices")) {
                            // Fallback to OpenAI format
                            JsonObject delta = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("delta");
                            if (delta != null && delta.has("content")) {
                                content = delta.get("content").getAsString();
                            }
                        }

                        if (!content.isEmpty()) {
                            // Thinking tag logic
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
                    } catch (Exception ignored) {}
                }
            }
            callback.onSuccess(fullContent.toString());
        } catch (Exception e) {
            callback.onError("LM Studio Parse Error: " + e.getMessage());
        }
    }
}
