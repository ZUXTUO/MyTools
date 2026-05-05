package com.olsc.mytools.network;

import android.content.Context;
import com.olsc.mytools.util.AppConfig;

public class ChatClientFactory {
    public static ChatClient create(Context context, AppConfig config) {
        int providerIndex = config.getProviderIndex();
        switch (providerIndex) {
            case 1: // OpenAI
                return new OpenAiClient(context, config);
            case 2: // Zhipu
                return new ZhipuClient(context, config);
            case 3: // Claude
                return new AnthropicClient(context, config);
            case 4: // Gemini
                return new GeminiClient(context, config);
            case 5: // LM Studio / Local
                return new LMStudioClient(context, config);
            case 6: // Groq
                return new OpenAiClient(context, config); // Use standard OpenAI for these
            default:
                return new OpenAiClient(context, config);
        }
    }
}
