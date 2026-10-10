package ir.p2payam.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter
        extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final List<Message> messages;

    /*
     * User ID of the current local user.
     *
     * If message.senderId equals this value,
     * the message belongs to the local user.
     */
    private final String localUserId;

    public MessageAdapter(
            List<Message> messages,
            String localUserId
    ) {
        this.messages = messages;
        this.localUserId = localUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        LinearLayout container =
                new LinearLayout(
                        parent.getContext()
                );

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setGravity(
                Gravity.START
        );

        container.setPadding(
                8,
                4,
                8,
                4
        );

        container.setLayoutParams(
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {

        Message message =
                messages.get(position);

        /*
         * -------------------------------------------------
         * Create message bubble
         * -------------------------------------------------
         */

        TextView text =
                new TextView(
                        holder.container.getContext()
                );

        /*
         * Get real message text.
         */

        String messageText =
                message.plainText;

        if (messageText == null) {
            messageText = "";
        }

        text.setText(
                messageText
        );

        text.setTextSize(
                16
        );

        text.setTextColor(
                Color.DKGRAY
        );

        text.setPadding(
                16,
                10,
                16,
                10
        );

        /*
         * -------------------------------------------------
         * Determine message owner
         * -------------------------------------------------
         *
         * Own message:
         *     message.senderId == localUserId
         *
         * Received message:
         *     message.senderId != localUserId
         */

        boolean isMine =
                localUserId != null &&
                        message.senderId != null &&
                        localUserId.equals(
                                message.senderId
                        );

        /*
         * -------------------------------------------------
         * Message alignment
         * -------------------------------------------------
         *
         * Mine     -> RIGHT
         * Received -> LEFT
         */

        holder.container.setGravity(
                isMine
                        ? Gravity.END
                        : Gravity.START
        );

        /*
         * -------------------------------------------------
         * Bubble layout
         * -------------------------------------------------
         */

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        /*
         * Keep some space from screen edges.
         */

        textParams.setMargins(
                8,
                2,
                8,
                2
        );

        /*
         * Prevent extremely wide message bubbles.
         */

        textParams.width =
                ViewGroup.LayoutParams.WRAP_CONTENT;

        text.setLayoutParams(
                textParams
        );

        /*
         * -------------------------------------------------
         * Bubble background
         * -------------------------------------------------
         */

        GradientDrawable bubble =
                new GradientDrawable();

        bubble.setCornerRadius(
                18
        );

        if (isMine) {

            /*
             * Own message
             * Green bubble
             */

            bubble.setColor(
                    Color.rgb(
                            220,
                            248,
                            198
                    )
            );

        } else {

            /*
             * Received message
             * White bubble
             */

            bubble.setColor(
                    Color.WHITE
            );
        }

        text.setBackground(
                bubble
        );

        /*
         * -------------------------------------------------
         * Refresh ViewHolder
         * -------------------------------------------------
         */

        holder.container.removeAllViews();

        holder.container.addView(
                text
        );
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    /*
     * Update adapter data if needed.
     */

    public void updateData(
            List<Message> newMessages
    ) {

        messages.clear();

        if (newMessages != null) {
            messages.addAll(
                    newMessages
            );
        }

        notifyDataSetChanged();
    }

    /*
     * ViewHolder
     */

    static class ViewHolder
            extends RecyclerView.ViewHolder {

        LinearLayout container;

        ViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);

            container =
                    (LinearLayout) itemView;
        }
    }
}
