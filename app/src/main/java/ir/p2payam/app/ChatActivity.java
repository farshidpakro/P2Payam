package ir.p2payam.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID =
            "conversation_id";

    private static final long MESSAGE_TTL =
            48L * 60L * 60L * 1000L;

    private static final int DEFAULT_HOP_LIMIT = 8;

    private P2PayamDatabase database;

    private Conversation conversation;

    private String conversationId;

    private RecyclerView messagesRecyclerView;

    private EditText messageText;

    private TextView chatUsernameText;

    private MessageAdapter messageAdapter;

    private final List<Message> messages =
            new ArrayList<>();
    private final BroadcastReceiver newMessageReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    String receivedConversationId =
                            intent.getStringExtra(
                                    "conversation_id"
                            );

                    if (receivedConversationId == null) {
                        return;
                    }

                    /*
                     * Only refresh if the received message
                     * belongs to the conversation currently open.
                     */

                    if (!receivedConversationId.equals(
                            conversationId
                    )) {
                        return;
                    }

                    android.util.Log.d(
                            "P2CHAT",
                            "NEW_MESSAGE_BROADCAST_RECEIVED"
                    );

                    loadConversation();
                }
            };

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        Log.e("P2TEST", "CHAT ACTIVITY ONCREATE");

        setContentView(
                R.layout.activity_chat
        );

        /*
         * -------------------------------------------------
         * 1. Get conversation ID
         * -------------------------------------------------
         */

        conversationId =
                getIntent().getStringExtra(
                        EXTRA_CONVERSATION_ID
                );

        if (conversationId == null ||
                conversationId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Conversation not found",
                    Toast.LENGTH_SHORT
            ).show();

            finish();

            return;
        }

        /*
         * -------------------------------------------------
         * 2. Database
         * -------------------------------------------------
         */

        database =
                P2PayamDatabase.getInstance(
                        getApplicationContext()
                );

        /*
         * -------------------------------------------------
         * 3. Views
         * -------------------------------------------------
         */

        chatUsernameText =
                findViewById(
                        R.id.chatUsernameText
                );

        messageText =
                findViewById(
                        R.id.chatMessageText
                );

        messagesRecyclerView =
                findViewById(
                        R.id.messagesRecyclerView
                );

        ImageButton backButton =
                findViewById(
                        R.id.backButton
                );

        ImageButton sendButton =
                findViewById(
                        R.id.sendMessageButton
                );

        /*
         * Make sure the input does not show the
         * old English "Message" hint.
         */

        messageText.setHint("پیام");

        /*
         * -------------------------------------------------
         * 4. RecyclerView
         * -------------------------------------------------
         */

        messagesRecyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        IdentityManager identityManager =
                new IdentityManager(this);

        try {
            identityManager.getIdentity();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UserIdentityManager userIdentityManager =
                new UserIdentityManager(this);

        try {
            userIdentityManager.initialize(
                    identityManager
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        messageAdapter =
                new MessageAdapter(
                        messages,
                        userIdentityManager.getUserId()
                );

        messagesRecyclerView.setAdapter(
                messageAdapter
        );

        /*
         * -------------------------------------------------
         * 5. Buttons
         * -------------------------------------------------
         */

        backButton.setOnClickListener(
                view -> finish()
        );

        sendButton.setOnClickListener(
                view -> sendMessage()
        );

        /*
         * -------------------------------------------------
         * 6. Load conversation
         * -------------------------------------------------
         */

        loadConversation();
    }

    private void loadConversation() {

        new Thread(() -> {

            try {

                Conversation loadedConversation =
                        database
                                .conversationDao()
                                .getById(
                                        conversationId
                                );

                if (loadedConversation == null) {

                    runOnUiThread(() -> {

                        Toast.makeText(
                                ChatActivity.this,
                                "Conversation not found",
                                Toast.LENGTH_SHORT
                        ).show();

                        finish();
                    });

                    return;
                }

                conversation =
                        loadedConversation;

                List<Message> loadedMessages =
                        database
                                .messageDao()
                                .getConversationMessages(
                                        conversationId
                                );

                runOnUiThread(() -> {

                    messages.clear();

                    if (loadedMessages != null) {
                        messages.addAll(
                                loadedMessages
                        );
                    }

                    /*
                     * Mark conversation as read.
                     */

                    new Thread(() ->
                            database
                                    .conversationDao()
                                    .markAsRead(
                                            conversationId
                                    )
                    ).start();

                    /*
                     * Username
                     */

                    String username =
                            conversation.peerUsername;

                    if (username == null ||
                            username.trim().isEmpty()) {

                        username = "Unknown";
                    }

                    username =
                            normalizeUsername(
                                    username
                            );

                    chatUsernameText.setText(
                            "@" + username
                    );

                    messageAdapter.notifyDataSetChanged();

                    if (!messages.isEmpty()) {

                        messagesRecyclerView
                                .scrollToPosition(
                                        messages.size() - 1
                                );
                    }
                });

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(
                                ChatActivity.this,
                                "Unable to load conversation",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }

        }).start();
    }

    private void sendMessage() {

        String text =
                messageText
                        .getText()
                        .toString()
                        .trim();

        if (text.isEmpty()) {
            return;
        }

        if (conversation == null) {

            Toast.makeText(
                    this,
                    "Conversation is not loaded yet",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        final String messageTextValue = text;

        messageText.setEnabled(false);

        new Thread(() -> {

            try {

                /*
                 * -------------------------------------------------
                 * 1. Check network objects
                 * -------------------------------------------------
                 */

                if (MainActivity.getRoutingTable() == null) {

                    throw new Exception(
                            "Routing table is not initialized"
                    );
                }

                if (MainActivity.getTcpTransport() == null) {

                    throw new Exception(
                            "TCP transport is not initialized"
                    );
                }

                /*
                 * -------------------------------------------------
                 * 2. Identity
                 * -------------------------------------------------
                 */

                IdentityManager identityManager =
                        new IdentityManager(
                                ChatActivity.this
                        );

                identityManager.getIdentity();

                UserIdentityManager userIdentityManager =
                        new UserIdentityManager(
                                ChatActivity.this
                        );

                userIdentityManager.initialize(
                        identityManager
                );

                String localUserId =
                        userIdentityManager.getUserId();

                String localUsername =
                        userIdentityManager.getUsername();


                /*
                 * -------------------------------------------------
                 * 3. DEBUG LOG
                 * -------------------------------------------------
                 */

                android.util.Log.d(
                        "P2CHAT",
                        "LOCAL_USER_ID=" +
                                localUserId
                );

                android.util.Log.d(
                        "P2CHAT",
                        "LOCAL_USERNAME=" +
                                localUsername
                );

                android.util.Log.d(
                        "P2CHAT",
                        "CONVERSATION_ID=" +
                                conversationId
                );

                android.util.Log.d(
                        "P2CHAT",
                        "CONVERSATION_PEER_USER_ID=" +
                                conversation.peerUserId
                );

                android.util.Log.d(
                        "P2CHAT",
                        "CONVERSATION_PEER_USERNAME=" +
                                conversation.peerUsername
                );


                /*
                 * -------------------------------------------------
                 * 4. Print all DHT peers
                 * -------------------------------------------------
                 */

                java.util.List<DhtPeer> allPeers =
                        MainActivity
                                .getRoutingTable()
                                .getAllPeers();

                android.util.Log.d(
                        "P2CHAT",
                        "DHT_PEER_COUNT=" +
                                allPeers.size()
                );

                for (DhtPeer p : allPeers) {

                    android.util.Log.d(
                            "P2CHAT",
                            "DHT_PEER " +
                                    "node=" +
                                    p.nodeId +
                                    " user=" +
                                    p.userId +
                                    " username=" +
                                    p.username +
                                    " host=" +
                                    p.host +
                                    " port=" +
                                    p.port
                    );
                }


                /*
                 * -------------------------------------------------
                 * 5. Find recipient
                 * -------------------------------------------------
                 */

                DhtPeer peer =
                        MainActivity
                                .getRoutingTable()
                                .getPeerByUserId(
                                        conversation.peerUserId
                                );

                android.util.Log.d(
                        "P2CHAT",
                        "PEER_LOOKUP_RESULT=" +
                                (peer == null
                                        ? "NULL"
                                        : "FOUND")
                );

                if (peer == null) {

                    throw new Exception(
                            "User is not currently available"
                    );
                }

                android.util.Log.d(
                        "P2CHAT",
                        "TARGET_NODE=" +
                                peer.nodeId
                );

                android.util.Log.d(
                        "P2CHAT",
                        "TARGET_USER=" +
                                peer.userId
                );

                android.util.Log.d(
                        "P2CHAT",
                        "TARGET_USERNAME=" +
                                peer.username
                );

                android.util.Log.d(
                        "P2CHAT",
                        "TARGET_HOST=" +
                                peer.host
                );

                android.util.Log.d(
                        "P2CHAT",
                        "TARGET_PORT=" +
                                peer.port
                );


                /*
                 * -------------------------------------------------
                 * 6. Validate peer
                 * -------------------------------------------------
                 */

                if (peer.nodeId == null ||
                        peer.nodeId.trim().isEmpty()) {

                    throw new Exception(
                            "Recipient node ID is unavailable"
                    );
                }

                if (peer.publicKey == null ||
                        peer.publicKey.trim().isEmpty()) {

                    throw new Exception(
                            "Recipient public key is unavailable"
                    );
                }


                /*
                 * -------------------------------------------------
                 * 7. Message ID / timestamps
                 * -------------------------------------------------
                 */

                String messageId =
                        UUID.randomUUID().toString();

                long createdAt =
                        System.currentTimeMillis();

                long expiresAt =
                        createdAt + MESSAGE_TTL;


                /*
                 * -------------------------------------------------
                 * 8. Encrypt
                 * -------------------------------------------------
                 */

                PublicKey recipientPublicKey =
                        CryptoManager.base64ToPublicKey(
                                peer.publicKey
                        );

                String encryptedPayload =
                        CryptoManager.encrypt(
                                messageTextValue,
                                recipientPublicKey
                        );


                /*
                 * -------------------------------------------------
                 * 9. Create packet
                 * -------------------------------------------------
                 */

                MessagePacket packet =
                        new MessagePacket();

                packet.type =
                        MessagePacket.TYPE_MESSAGE;

                packet.version =
                        MessagePacket.VERSION;

                packet.messageId =
                        messageId;

                packet.senderUserId =
                        localUserId;

                packet.recipientUserId =
                        conversation.peerUserId;

                packet.senderUsername =
                        localUsername;

                packet.recipientUsername =
                        normalizeUsername(
                                conversation.peerUsername
                        );

                packet.senderPublicKey =
                        Base64.encodeToString(
                                identityManager
                                        .getPublicKey()
                                        .getEncoded(),
                                Base64.NO_WRAP
                        );

                packet.createdAt =
                        createdAt;

                packet.expiresAt =
                        expiresAt;

                packet.hopLimit =
                        DEFAULT_HOP_LIMIT;

                packet.encryptedPayload =
                        encryptedPayload;


                /*
                 * -------------------------------------------------
                 * 10. Sign
                 * -------------------------------------------------
                 */

                PrivateKey privateKey =
                        identityManager
                                .getPrivateKey();

                packet.signature =
                        MessageSigner.sign(
                                packet,
                                privateKey
                        );


                /*
                 * -------------------------------------------------
                 * 11. Encode
                 * -------------------------------------------------
                 */

                byte[] packetData =
                        MessagePacketCodec.encode(
                                packet
                        );


                /*
                 * -------------------------------------------------
                 * 12. Save local message
                 * -------------------------------------------------
                 */

                Message message =
                        new Message();

                message.messageId =
                        messageId;

                message.conversationId =
                        conversationId;

                message.senderId =
                        localUserId;

                message.recipientId =
                        conversation.peerUserId;

                message.plainText =
                        messageTextValue;

                message.encryptedPayload =
                        encryptedPayload.getBytes(
                                StandardCharsets.UTF_8
                        );

                message.createdAt =
                        createdAt;

                message.expiresAt =
                        expiresAt;

                message.status =
                        Message.STATUS_PENDING;

                database
                        .messageDao()
                        .insert(
                                message
                        );


                /*
                 * -------------------------------------------------
                 * 13. Update conversation
                 * -------------------------------------------------
                 */

                conversation.lastMessage =
                        messageTextValue;

                conversation.lastMessageAt =
                        createdAt;

                database
                        .conversationDao()
                        .update(
                                conversation
                        );


                /*
                 * -------------------------------------------------
                 * 14. TCP
                 * -------------------------------------------------
                 */

                android.util.Log.d(
                        "P2CHAT",
                        "CALLING_TCP_SEND"
                );

                boolean sentOrQueued =
                        MainActivity
                                .getTcpTransport()
                                .sendOrQueue(
                                        peer,
                                        packetData
                                );

                android.util.Log.d(
                        "P2CHAT",
                        "TCP_SEND_RESULT=" +
                                sentOrQueued
                );

                if (!sentOrQueued) {

                    throw new Exception(
                            "TCP could not queue the message"
                    );
                }


                /*
                 * -------------------------------------------------
                 * 15. UI
                 * -------------------------------------------------
                 */

                runOnUiThread(() -> {

                    messageText.setText("");

                    messageText.setEnabled(true);

                    loadConversation();

                    Toast.makeText(
                            ChatActivity.this,
                            "پیام در صف ارسال قرار گرفت",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() -> {

                    messageText.setEnabled(true);

                    messageText.setError(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "Unable to send message"
                    );
                });
            }

        }).start();
    }

    private String normalizeUsername(
            String username
    ) {

        if (username == null) {
            return "";
        }

        String value =
                username
                        .trim()
                        .toLowerCase();

        if (value.startsWith("@")) {

            value =
                    value.substring(1);
        }

        return value;
    }

    @Override
    protected void onResume() {

        super.onResume();

        /*
         * Register receiver while this chat is visible.
         */

        IntentFilter filter =
                new IntentFilter(
                        "ir.p2payam.NEW_MESSAGE"
                );

        registerReceiver(
                newMessageReceiver,
                filter,
                Context.RECEIVER_EXPORTED
        );

        /*
         * Refresh messages when entering
         * or returning to this conversation.
         */

        if (conversationId != null &&
                !conversationId.isEmpty() &&
                database != null) {

            loadConversation();
        }
    }
    @Override
    protected void onPause() {

        super.onPause();

        try {

            unregisterReceiver(
                    newMessageReceiver
            );

        } catch (IllegalArgumentException ignored) {
            /*
             * Receiver was already unregistered.
             */
        }
    }
//@Override
//protected void onStart() {
//    super.onStart();
//
//    IntentFilter filter =
//            new IntentFilter("ir.p2payam.NEW_MESSAGE");
//
//    registerReceiver(
//            newMessageReceiver,
//            filter,
//            Context.RECEIVER_NOT_EXPORTED
//    );
//}
//
//    @Override
//    protected void onStop() {
//        super.onStop();
//
//        try {
//            unregisterReceiver(newMessageReceiver);
//        } catch (IllegalArgumentException ignored) {
//        }
//    }
}