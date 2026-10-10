package ir.p2payam.app;

public class MessagePacket {

    public static final int VERSION = 2;

    public static final int TYPE_HELLO = 1;
    public static final int TYPE_MESSAGE = 2;
    public static final int TYPE_ACK = 3;


    // =====================================================
    // Packet
    // =====================================================

    public int type;

    public int version;


    // =====================================================
    // Message identity
    // =====================================================

    public String messageId;


    // Used by ACK
    public String ackMessageId;


    // =====================================================
    // User identity
    // =====================================================

    public String senderUserId;

    public String recipientUserId;


    // Human-readable sender name
    public String senderUsername;

    // Human-readable recipient name
    public String recipientUsername;


    // =====================================================
    // Cryptographic identity
    // =====================================================

    public String senderPublicKey;


    // =====================================================
    // Message lifetime
    // =====================================================

    public long createdAt;

    public long expiresAt;


    // =====================================================
    // Routing metadata
    //
    // NOT signed.
    // =====================================================

    public int hopLimit;


    // =====================================================
    // Encrypted content
    // =====================================================

    public String encryptedPayload;


    // =====================================================
    // Signature
    // =====================================================

    public byte[] signature;


    public MessagePacket() {

        version =
                VERSION;
    }


    public boolean isExpired() {

        return System.currentTimeMillis()
                >= expiresAt;
    }


    public boolean canForward() {

        return hopLimit > 0 &&
                !isExpired();
    }


    public void decreaseHop() {

        if (hopLimit > 0) {

            hopLimit--;
        }
    }
}