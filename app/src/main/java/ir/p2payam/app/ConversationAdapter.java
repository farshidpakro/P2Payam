package ir.p2payam.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter
        extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(
                Conversation conversation
        );
    }

    private final List<Conversation> conversations;

    private final OnConversationClickListener listener;

    public ConversationAdapter(
            List<Conversation> conversations,
            OnConversationClickListener listener
    ) {
        this.conversations = conversations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view =
                LayoutInflater.from(
                        parent.getContext()
                ).inflate(
                        R.layout.item_conversation,
                        parent,
                        false
                );

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {

        Conversation conversation =
                conversations.get(position);

        String username =
                conversation.peerUsername;

        if (username == null ||
                username.trim().isEmpty()) {

            username = "Unknown";
        }

        username =
                normalizeUsername(username);

        holder.usernameText.setText(
                "@" + username
        );

        holder.avatarText.setText(
                username.substring(0, 1).toUpperCase()
        );

        String lastMessage =
                conversation.lastMessage;

        if (lastMessage == null ||
                lastMessage.trim().isEmpty()) {

            lastMessage = "No messages yet";
        }

        holder.lastMessageText.setText(
                lastMessage
        );

        if (conversation.lastMessageAt > 0) {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                    );

            holder.timeText.setText(
                    format.format(
                            new Date(
                                    conversation.lastMessageAt
                            )
                    )
            );

        } else {

            holder.timeText.setText("");
        }

        holder.itemView.setOnClickListener(
                view ->
                        listener.onConversationClick(
                                conversation
                        )
        );
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void updateData(
            List<Conversation> newData
    ) {

        conversations.clear();

        conversations.addAll(
                newData
        );

        notifyDataSetChanged();
    }

    private String normalizeUsername(
            String username
    ) {

        String value =
                username.trim();

        if (value.startsWith("@")) {
            value = value.substring(1);
        }

        if (value.isEmpty()) {
            return "U";
        }

        return value;
    }

    static class ViewHolder
            extends RecyclerView.ViewHolder {

        TextView avatarText;
        TextView usernameText;
        TextView lastMessageText;
        TextView timeText;

        ViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);

            avatarText =
                    itemView.findViewById(
                            R.id.avatarText
                    );

            usernameText =
                    itemView.findViewById(
                            R.id.usernameText
                    );

            lastMessageText =
                    itemView.findViewById(
                            R.id.lastMessageText
                    );

            timeText =
                    itemView.findViewById(
                            R.id.timeText
                    );
        }
    }
}