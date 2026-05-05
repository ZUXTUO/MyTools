package com.olsc.mytools.network;

import com.olsc.mytools.ai.ChatMessage;
import java.util.List;

public interface ChatClient {
    public interface Callback {
        void onStart();
        void onChunk(String chunk, String thinkingChunk);
        void onSuccess(String fullResponse);
        void onError(String error);
    }

    void sendMessage(String userMessage, List<ChatMessage> history, Callback callback);
}
