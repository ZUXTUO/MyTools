package com.olsc.mytools.network;

import android.content.Context;
import com.google.gson.JsonObject;
import com.olsc.mytools.ai.ChatMessage;
import com.olsc.mytools.util.AppConfig;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import java.util.List;

public class GeminiClient extends BaseChatClient {
    public GeminiClient(Context context, AppConfig config) {
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
        root.add("messages", buildMessagesArray(userMessage, history, true));
        root.addProperty("max_tokens", 4096);

        RequestBody body = RequestBody.create(root.toString(), MediaType.get("application/json; charset=utf-8"));
        return new Request.Builder()
                .url(getApiUrl())
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .post(body)
                .build();
    }
}
