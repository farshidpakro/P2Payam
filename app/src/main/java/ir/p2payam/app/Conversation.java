package ir.p2payam.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversations")
public class Conversation {

    @PrimaryKey
    @NonNull
    public String conversationId = "";

    /*
     * Permanent identity of the other user.
     * This is the real identifier of the conversation.
     */
    @NonNull
    public String peerUserId = "";

    /*
     * Username is only for display.
     * The conversation must NOT depend on username
     * as its permanent identifier.
     */
    public String peerUsername;

    /*
     * Last message preview shown in chat list.
     */
    public String lastMessage;

    public long lastMessageAt;

    public long createdAt;

    /*
     * Number of unread messages.
     */
    public int unreadCount;
}