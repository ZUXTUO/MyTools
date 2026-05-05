package com.olsc.mytools.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.olsc.mytools.R;
import com.olsc.mytools.util.AppConfig;

import io.noties.markwon.Markwon;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages;
    private final Markwon markwon;
    private final AppConfig config;

    public ChatAdapter(List<ChatMessage> messages, Markwon markwon, AppConfig config) {
        this.messages = messages;
        this.markwon = markwon;
        this.config = config;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.tvMessage.setText(message.getContent());
            userHolder.btnCopy.setOnClickListener(v -> copyToClipboard(v.getContext(), message.getContent()));
        } else if (holder instanceof AiViewHolder) {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            markwon.setMarkdown(aiHolder.tvContent, message.getContent());

            if (message.hasThink() && config.isThinkEnabled()) {
                aiHolder.thinkContainer.setVisibility(View.VISIBLE);
                aiHolder.tvThink.setText(message.getThink());
                
                updateThinkState(aiHolder, message.isThinkingExpanded());

                aiHolder.btnToggleThink.setOnClickListener(v -> {
                    message.setThinkingExpanded(!message.isThinkingExpanded());
                    updateThinkState(aiHolder, message.isThinkingExpanded());
                });
            } else {
                aiHolder.thinkContainer.setVisibility(View.GONE);
            }

            aiHolder.btnCopy.setOnClickListener(v -> copyToClipboard(v.getContext(), message.getContent()));
        }
    }

    private void updateThinkState(AiViewHolder holder, boolean isExpanded) {
        ViewGroup.LayoutParams params = holder.thinkScroll.getLayoutParams();
        if (isExpanded) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.ivThinkArrow.setImageResource(R.drawable.ic_collapse);
        } else {
            params.height = (int) (80 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
            holder.ivThinkArrow.setImageResource(R.drawable.ic_expand);
            
            // Auto-scroll to bottom to show latest thinking progress
            holder.thinkScroll.post(() -> holder.thinkScroll.fullScroll(View.FOCUS_DOWN));
        }
        holder.thinkScroll.setLayoutParams(params);
    }

    private void copyToClipboard(android.content.Context context, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("AI Chat", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            android.widget.Toast.makeText(context, R.string.copied_to_clipboard, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        ImageView btnCopy;
        UserViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            btnCopy = itemView.findViewById(R.id.btn_copy);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        View thinkContainer;
        View btnToggleThink;
        androidx.core.widget.NestedScrollView thinkScroll;
        TextView tvThink;
        ImageView ivThinkArrow;
        ImageView btnCopy;
        AiViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            thinkContainer = itemView.findViewById(R.id.think_container);
            btnToggleThink = itemView.findViewById(R.id.btn_toggle_think);
            thinkScroll = itemView.findViewById(R.id.think_scroll);
            tvThink = itemView.findViewById(R.id.tv_think);
            ivThinkArrow = itemView.findViewById(R.id.iv_think_arrow);
            btnCopy = itemView.findViewById(R.id.btn_copy);
        }
    }
}
