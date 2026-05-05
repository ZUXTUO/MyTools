package com.olsc.mytools.ai;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;

    private String content;
    private int type;
    private String think;
    private boolean isThinkingExpanded = false;

    public ChatMessage(String content, int type) {
        this.content = content;
        this.type = type;
        parseThink();
    }

    private void parseThink() {
        if (type == TYPE_AI && content != null) {
            int start = content.indexOf("<think>");
            int end = content.indexOf("</think>");
            if (start != -1 && end != -1 && end > start) {
                this.think = content.substring(start + 7, end).trim();
                this.content = content.substring(end + 8).trim();
            }
        }
    }

    public String getContent() {
        return content;
    }

    public int getType() {
        return type;
    }

    public String getThink() {
        return think;
    }

    public boolean hasThink() {
        return think != null && !think.isEmpty();
    }

    public boolean isThinkingExpanded() {
        return isThinkingExpanded;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setThink(String think) {
        this.think = think;
    }

    public void setThinkingExpanded(boolean thinkingExpanded) {
        isThinkingExpanded = thinkingExpanded;
    }
}
