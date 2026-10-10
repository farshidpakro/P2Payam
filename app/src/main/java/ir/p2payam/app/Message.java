package ir.p2payam.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class Message {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_EXPIRED = 2;

    @PrimaryKey
    @NonNull
    public String messageId = "";

    /*
     * Conversation that this message belongs to.
     */
    @NonNull
    public String conversationId = "";

    /*
     * User identities, NOT node IDs.
     */
    public String senderId;

    public String recipientId;

    /*
     * Plain text used for local display.
     */
    public String plainText;

    /*
     * Encrypted payload used for network transmission.
     */
    public byte[] encryptedPayload;

    public long createdAt;

    public long expiresAt;

    public int status;
}