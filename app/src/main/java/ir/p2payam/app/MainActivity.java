package ir.p2payam.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Intent;


import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity
        implements TcpTransport.Listener {

    private RecyclerView chatListRecyclerView;
    private LinearLayout emptyChatLayout;
    private IdentityManager identityManager;
    private UserIdentityManager userIdentityManager;

    private static DhtRoutingTable routingTable;
    private static TcpTransport tcpTransport;
    private DhtDiscovery dhtDiscovery;

    private P2PayamDatabase database;

    private final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    private TextView usernameText;
    private TextView statusText;
    private TextView messagesText;

    private EditText targetUsernameInput;
    private EditText messageInput;

    private Button sendButton;

    private String localUsername;
    private String localUserId;

    private boolean initialized = false;

    private static final long MESSAGE_TTL =
            48L * 60L * 60L * 1000L;

    private static final int DEFAULT_HOP_LIMIT = 8;
//
//    private final Context context;
//
//    public MainActivity(Context context) {
//        this.context = context.getApplicationContext();
//    }
private final BroadcastReceiver newMessageReceiver =
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                loadConversationList(
                        chatListRecyclerView,
                        emptyChatLayout
                );
            }
        };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeApplication();
    }


    private void initializeApplication() {

        try {

            identityManager =
                    new IdentityManager(this);

            identityManager.getIdentity();


            userIdentityManager =
                    new UserIdentityManager(this);

            userIdentityManager.initialize(
                    identityManager
            );


            database =
                    P2PayamDatabase.getInstance(
                            getApplicationContext()
                    );


            boolean hasUsername =
                    userIdentityManager.hasUsername();


            if (hasUsername) {

                localUsername =
                        userIdentityManager.getUsername();

                localUserId =
                        userIdentityManager.getUserId();


                runOnUiThread(
                        this::startMainApplication
                );

            } else {

                runOnUiThread(
                        this::showUsernameSetup
                );
            }


        } catch (Exception e) {

            runOnUiThread(() ->
                    showFatalError(
                            "Initialization error:\n" +
                                    e.getMessage()
                    )
            );
        }
    }


    private void showUsernameSetup() {

        ScrollView scrollView =
                new ScrollView(this);

        scrollView.setFillViewport(true);


        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                30,
                30,
                30,
                30
        );


        TextView title =
                new TextView(this);

        title.setText(
                getString(
                        R.string.username_setup
                )
        );

        title.setTextSize(26);

        title.setGravity(
                Gravity.CENTER
        );


        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );


        TextView description =
                new TextView(this);

        description.setText(
                getString(
                        R.string.username_description
                )
        );

        description.setTextSize(16);


        root.addView(
                description,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );


        EditText usernameInput =
                new EditText(this);

        usernameInput.setHint(
                getString(
                        R.string.username_hint
                )
        );

        usernameInput.setSingleLine(true);

        usernameInput.setInputType(
                InputType.TYPE_CLASS_TEXT
        );


        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        inputParams.topMargin = 10;


        root.addView(
                usernameInput,
                inputParams
        );


        Button continueButton =
                new Button(this);

        continueButton.setText(
                getString(
                        R.string.continue_text
                )
        );


        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.topMargin = 20;


        root.addView(
                continueButton,
                buttonParams
        );


        TextView errorText =
                new TextView(this);

        errorText.setTextSize(14);


        LinearLayout.LayoutParams errorParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        errorParams.topMargin = 10;


        root.addView(
                errorText,
                errorParams
        );


        continueButton.setOnClickListener(v -> {

            String username =
                    usernameInput
                            .getText()
                            .toString()
                            .trim();
            android.util.Log.d(
                    "P2PAYAM",
                    "OPEN NEW CHAT"
            );

            try {

                userIdentityManager.setUsername(
                        username
                );


                localUsername =
                        userIdentityManager
                                .getUsername();

                localUserId =
                        userIdentityManager
                                .getUserId();


                startMainApplication();
                android.util.Log.d(
                        "P2PAYAM",
                        "START ACTIVITY CALLED"
                );



            } catch (Exception e) {

                errorText.setText(
                        e.getMessage()
                );
            }
        });


        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );


        setContentView(
                scrollView
        );


        usernameInput.requestFocus();
    }


    private void startMainApplication() {

        if (initialized) {
            return;
        }


        try {

            String localNodeId =
                    identityManager.getNodeId();


            routingTable =
                    new DhtRoutingTable(
                            localNodeId
                    );


            tcpTransport =
                    new TcpTransport(
                            this,
                            identityManager,
                            userIdentityManager,
                            routingTable
                    );


            tcpTransport.setListener(
                    this
            );


            tcpTransport.start();


            dhtDiscovery =
                    new DhtDiscovery(
                            this,
                            identityManager,
                            userIdentityManager,
                            routingTable,
                            tcpTransport
                    );


            dhtDiscovery.start();


            initialized = true;


            buildMainScreen();


            setStatus(
                    getString(
                            R.string.online
                    )
            );


        } catch (Exception e) {

            showFatalError(
                    "Network initialization error:\n" +
                            e.getMessage()
            );
        }
    }


    private void buildMainScreen() {

        setContentView(
                R.layout.activity_main
        );
        TextView myUsernameText =
                findViewById(
                        R.id.myUsernameText
                );

        myUsernameText.setText(
                "@" + userIdentityManager.getUsername()
        );
        chatListRecyclerView =
                findViewById(R.id.chatListRecyclerView);

        emptyChatLayout =
                findViewById(R.id.emptyChatLayout);

        FloatingActionButton newChatButton =
                findViewById(
                        R.id.newChatButton
                );

        newChatButton.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    MainActivity.this,
                                    NewChatActivity.class
                            );

                    startActivity(intent);
                }
        );

        loadConversationList(
                chatListRecyclerView,
                emptyChatLayout
        );
    }

    private void loadConversationList(
            RecyclerView recyclerView,
            LinearLayout emptyLayout
    ) {

        new Thread(() -> {

            List<Conversation> conversations =
                    database
                            .conversationDao()
                            .getAll();

            runOnUiThread(() -> {

                ConversationAdapter adapter =
                        new ConversationAdapter(
                                new ArrayList<>(
                                        conversations
                                ),
                                conversation -> {

                                    Intent intent =
                                            new Intent(
                                                    MainActivity.this,
                                                    ChatActivity.class
                                            );

                                    intent.putExtra(
                                            ChatActivity.EXTRA_CONVERSATION_ID,
                                            conversation.conversationId
                                    );

                                    startActivity(intent);
                                }
                        );

                recyclerView.setLayoutManager(
                        new LinearLayoutManager(
                                MainActivity.this
                        )
                );

                recyclerView.setAdapter(
                        adapter
                );

                if (conversations.isEmpty()) {

                    emptyLayout.setVisibility(
                            View.VISIBLE
                    );

                    recyclerView.setVisibility(
                            View.GONE
                    );

                } else {

                    emptyLayout.setVisibility(
                            View.GONE
                    );

                    recyclerView.setVisibility(
                            View.VISIBLE
                    );
                }
            });

        }).start();
    }
    private void sendMessage() {

        String targetUsername =
                targetUsernameInput
                        .getText()
                        .toString()
                        .trim();


        String text =
                messageInput
                        .getText()
                        .toString()
                        .trim();


        if (targetUsername.isEmpty()) {

            targetUsernameInput.setError(
                    getString(
                            R.string.enter_recipient
                    )
            );

            return;
        }


        if (text.isEmpty()) {

            messageInput.setError(
                    getString(
                            R.string.enter_message
                    )
            );

            return;
        }


        DhtPeer targetPeer =
                routingTable.getPeerByUsername(
                        targetUsername
                );


        if (targetPeer == null) {

            setStatus(
                    getString(
                            R.string.recipient_not_found_dht
                    )
            );


            Toast.makeText(
                    this,
                    getString(
                            R.string.recipient_not_found
                    ),
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        sendButton.setEnabled(
                false
        );


        databaseExecutor.execute(() -> {

            try {

                long now =
                        System.currentTimeMillis();


                MessagePacket packet =
                        new MessagePacket();


                packet.type =
                        MessagePacket.TYPE_MESSAGE;


                packet.messageId =
                        MessageIdGenerator.generate();


                packet.senderUserId =
                        localUserId;


                packet.recipientUserId =
                        targetPeer.userId;


                packet.senderUsername =
                        localUsername;


                packet.recipientUsername =
                        normalizeUsername(
                                targetUsername
                        );


                packet.senderPublicKey =
                        Base64.encodeToString(
                                identityManager
                                        .getPublicKey()
                                        .getEncoded(),
                                Base64.NO_WRAP
                        );


                packet.createdAt =
                        now;


                packet.expiresAt =
                        now + MESSAGE_TTL;


                packet.hopLimit =
                        DEFAULT_HOP_LIMIT;


                PublicKey recipientPublicKey =
                        CryptoManager.base64ToPublicKey(
                                targetPeer.publicKey
                        );


                packet.encryptedPayload =
                        CryptoManager.encrypt(
                                text,
                                recipientPublicKey
                        );


                packet.signature =
                        MessageSigner.sign(
                                packet,
                                identityManager
                                        .getPrivateKey()
                        );


                byte[] encodedPacket =
                        MessagePacketCodec.encode(
                                packet
                        );


                Message message =
                        new Message();


                message.messageId =
                        packet.messageId;


                message.senderId =
                        packet.senderUserId;


                message.recipientId =
                        packet.recipientUserId;


                message.encryptedPayload =
                        packet.encryptedPayload
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );


                message.createdAt =
                        packet.createdAt;


                message.expiresAt =
                        packet.expiresAt;


                message.status =
                        Message.STATUS_PENDING;


                database
                        .messageDao()
                        .insert(
                                message
                        );


                boolean sent =
                        sendPacket(
                                targetPeer,
                                encodedPacket
                        );


                runOnUiThread(() -> {

                    sendButton.setEnabled(
                            true
                    );


                    if (sent) {

                        appendMessage(
                                "You → @" +
                                        packet.recipientUsername +
                                        "\n" +
                                        text +
                                        "\n"
                        );


                        messageInput.setText(
                                ""
                        );


                        setStatus(
                                getString(
                                        R.string.message_sent
                                )
                        );


                    } else {

                        setStatus(
                                getString(
                                        R.string.message_stored
                                )
                        );


                        appendMessage(
                                getString(
                                        R.string.stored_for_delivery,
                                        packet.recipientUsername
                                ) + "\n"
                        );
                    }
                });


            } catch (Exception e) {

                runOnUiThread(() -> {

                    sendButton.setEnabled(
                            true
                    );


                    setStatus(
                            getString(
                                    R.string.send_error,
                                    e.getMessage()
                            )
                    );
                });
            }
        });
    }


    private boolean sendPacket(
            DhtPeer targetPeer,
            byte[] data
    ) {

        if (targetPeer == null ||
                data == null) {

            return false;
        }

        /*
         * TcpTransport خودش:
         *
         * 1. اگر TCP وصل باشد، فوراً ارسال می‌کند.
         *
         * 2. اگر وصل نباشد، پیام را در Pending Queue
         *    قرار می‌دهد.
         *
         * 3. اتصال TCP را برقرار می‌کند.
         *
         * 4. بعد از اتصال، پیام Pending را ارسال می‌کند.
         */
        return tcpTransport.sendOrQueue(
                targetPeer,
                data
        );
    }


    @Override
    public void onConnected(
            DhtPeer peer
    ) {

        runOnUiThread(() ->
                setStatus(
                        getString(
                                R.string.connected,
                                peer.username
                        )
                )
        );
    }



    @Override
    public void onReceived(
            String nodeId,
            byte[] data
    ) {

        android.util.Log.d(
                "P2RECV",
                "========== PACKET RECEIVED =========="
        );

        android.util.Log.d(
                "P2RECV",
                "FROM_NODE=" + nodeId
        );

        android.util.Log.d(
                "P2RECV",
                "DATA_SIZE=" +
                        (data == null ? "NULL" : data.length)
        );

        databaseExecutor.execute(() -> {

            try {

                // -------------------------------------------------
                // 1. DECODE
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_1_DECODE_START"
                );

                MessagePacket packet =
                        MessagePacketCodec.decode(
                                data
                        );

                android.util.Log.d(
                        "P2RECV",
                        "STEP_1_DECODE_OK"
                );

                android.util.Log.d(
                        "P2RECV",
                        "TYPE=" + packet.type
                );

                android.util.Log.d(
                        "P2RECV",
                        "VERSION=" + packet.version
                );

                android.util.Log.d(
                        "P2RECV",
                        "MESSAGE_ID=" + packet.messageId
                );

                android.util.Log.d(
                        "P2RECV",
                        "SENDER_USER_ID=" +
                                packet.senderUserId
                );

                android.util.Log.d(
                        "P2RECV",
                        "SENDER_USERNAME=" +
                                packet.senderUsername
                );

                android.util.Log.d(
                        "P2RECV",
                        "RECIPIENT_USER_ID=" +
                                packet.recipientUserId
                );

                android.util.Log.d(
                        "P2RECV",
                        "LOCAL_USER_ID=" +
                                localUserId
                );


                // -------------------------------------------------
                // 2. EXPIRATION
                // -------------------------------------------------

                if (packet.isExpired()) {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_2_EXPIRED=true"
                    );

                    return;
                }

                android.util.Log.d(
                        "P2RECV",
                        "STEP_2_EXPIRED=false"
                );


                // -------------------------------------------------
                // 3. ACK
                // -------------------------------------------------

                if (packet.type ==
                        MessagePacket.TYPE_ACK) {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_3_ACK_RECEIVED"
                    );

                    handleAck(
                            packet
                    );

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_3_ACK_HANDLED"
                    );

                    return;
                }


                // -------------------------------------------------
                // 4. MESSAGE TYPE
                // -------------------------------------------------

                if (packet.type !=
                        MessagePacket.TYPE_MESSAGE) {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_4_IGNORED_TYPE=" +
                                    packet.type
                    );

                    return;
                }

                android.util.Log.d(
                        "P2RECV",
                        "STEP_4_MESSAGE_TYPE_OK"
                );


                // -------------------------------------------------
                // 5. RECIPIENT CHECK
                // -------------------------------------------------

                if (packet.recipientUserId == null) {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_5_RECIPIENT_NULL"
                    );

                    return;
                }

                if (!packet.recipientUserId.equals(
                        localUserId
                )) {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_5_NOT_FOR_THIS_USER"
                    );

                    android.util.Log.d(
                            "P2RECV",
                            "PACKET_RECIPIENT=" +
                                    packet.recipientUserId
                    );

                    android.util.Log.d(
                            "P2RECV",
                            "LOCAL_USER=" +
                                    localUserId
                    );

                    forwardPacket(
                            packet
                    );

                    return;
                }

                android.util.Log.d(
                        "P2RECV",
                        "STEP_5_RECIPIENT_OK"
                );


                // -------------------------------------------------
                // 6. SIGNATURE
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_6_VERIFY_START"
                );

                boolean verified =
                        verifyPacket(
                                packet
                        );

                android.util.Log.d(
                        "P2RECV",
                        "STEP_6_VERIFY_RESULT=" +
                                verified
                );

                if (!verified) {

                    android.util.Log.e(
                            "P2RECV",
                            "SIGNATURE_VERIFICATION_FAILED"
                    );

                    return;
                }


                // -------------------------------------------------
                // 7. DUPLICATE CHECK
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_7_DUPLICATE_CHECK"
                );

                Message existing =
                        database
                                .messageDao()
                                .getById(
                                        packet.messageId
                                );

                if (existing != null) {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_7_DUPLICATE=true"
                    );

                    sendAck(
                            packet,
                            nodeId
                    );

                    return;
                }

                android.util.Log.d(
                        "P2RECV",
                        "STEP_7_DUPLICATE=false"
                );


                // -------------------------------------------------
                // 8. SENDER PUBLIC KEY
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_8_PUBLIC_KEY_START"
                );

                CryptoManager.base64ToPublicKey(
                        packet.senderPublicKey
                );

                android.util.Log.d(
                        "P2RECV",
                        "STEP_8_PUBLIC_KEY_OK"
                );


                // -------------------------------------------------
                // 9. DECRYPT
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_9_DECRYPT_START"
                );

                String decryptedText =
                        CryptoManager.decrypt(
                                packet.encryptedPayload,
                                identityManager
                                        .getPrivateKey()
                        );

                android.util.Log.d(
                        "P2RECV",
                        "STEP_9_DECRYPT_OK"
                );

                android.util.Log.d(
                        "P2RECV",
                        "PLAINTEXT=" +
                                decryptedText
                );


                // -------------------------------------------------
                // 10. CONVERSATION ID
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_10_CONVERSATION_ID_START"
                );

                String conversationId =
                        ConversationIdGenerator
                                .fromUserIds(
                                        packet.senderUserId,
                                        packet.recipientUserId
                                );

                android.util.Log.d(
                        "P2RECV",
                        "STEP_10_CONVERSATION_ID=" +
                                conversationId
                );


                // -------------------------------------------------
                // 11. CREATE MESSAGE
                // -------------------------------------------------

                Message message =
                        new Message();

                message.messageId =
                        packet.messageId;

                message.conversationId =
                        conversationId;

                message.senderId =
                        packet.senderUserId;

                message.recipientId =
                        packet.recipientUserId;

                message.plainText =
                        decryptedText;

                message.encryptedPayload =
                        packet.encryptedPayload
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                message.createdAt =
                        packet.createdAt;

                message.expiresAt =
                        packet.expiresAt;

                message.status =
                        Message.STATUS_DELIVERED;


                // -------------------------------------------------
                // 12. ROOM INSERT
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_12_ROOM_INSERT_START"
                );

                database
                        .messageDao()
                        .insert(
                                message
                        );

                android.util.Log.d(
                        "P2RECV",
                        "STEP_12_ROOM_INSERT_OK"
                );


                // -------------------------------------------------
                // 13. CONVERSATION
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_13_CONVERSATION_START"
                );

                Conversation conversation =
                        database
                                .conversationDao()
                                .getById(
                                        conversationId
                                );

                if (conversation == null) {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_13_CONVERSATION_CREATE"
                    );

                    conversation =
                            new Conversation();

                    conversation.conversationId =
                            conversationId;

                    conversation.peerUserId =
                            packet.senderUserId;

                    conversation.peerUsername =
                            packet.senderUsername;

                    conversation.createdAt =
                            System.currentTimeMillis();

                    conversation.lastMessage =
                            decryptedText;

                    conversation.lastMessageAt =
                            packet.createdAt;

                    conversation.unreadCount =
                            1;

                    database
                            .conversationDao()
                            .insert(
                                    conversation
                            );

                } else {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_13_CONVERSATION_EXISTS"
                    );

                    conversation.lastMessage =
                            decryptedText;

                    conversation.lastMessageAt =
                            packet.createdAt;

                    conversation.unreadCount =
                            conversation.unreadCount + 1;

                    database
                            .conversationDao()
                            .update(
                                    conversation
                            );
                }

                android.util.Log.d(
                        "P2RECV",
                        "STEP_13_CONVERSATION_OK"
                );


                // -------------------------------------------------
                // 14. ACK
                // -------------------------------------------------

                android.util.Log.d(
                        "P2RECV",
                        "STEP_14_ACK_START"
                );

                sendAck(
                        packet,
                        nodeId
                );

                android.util.Log.d(
                        "P2RECV",
                        "STEP_14_ACK_SENT"
                );


                // -------------------------------------------------
                // 15. UI
                // -------------------------------------------------

                runOnUiThread(() -> {

                    android.util.Log.d(
                            "P2RECV",
                            "STEP_15_UI_UPDATE"
                    );

//                    appendMessage(
//                            "@" +
//                                    packet.senderUsername +
//                                    " → You\n" +
//                                    decryptedText +
//                                    "\n"
//                    );
                });

                Intent intent =
                        new Intent("ir.p2payam.NEW_MESSAGE");

                intent.putExtra(
                        "conversation_id",
                        conversationId
                );

                //context.
                MainActivity.this.sendBroadcast(intent);
                android.util.Log.d(
                        "P2RECV",
                        "========== RECEIVE COMPLETE ==========");

            } catch (Exception e) {

                android.util.Log.e(
                        "P2RECV",
                        "========== RECEIVE ERROR ==========",
                        e
                );

                android.util.Log.e(
                        "P2RECV",
                        "ERROR_MESSAGE=" +
                                e.getMessage()
                );
            }
        });
    }



    private boolean verifyPacket(
            MessagePacket packet
    ) {

        try {

            if (packet.senderUserId == null ||
                    packet.senderPublicKey == null) {

                return false;
            }


            byte[] publicKeyBytes =
                    Base64.decode(
                            packet.senderPublicKey,
                            Base64.NO_WRAP
                    );


            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );


            byte[] hash =
                    digest.digest(
                            publicKeyBytes
                    );


            String calculatedUserId =
                    bytesToHex(
                            hash
                    );


            if (!calculatedUserId.equals(
                    packet.senderUserId
            )) {

                return false;
            }


            PublicKey publicKey =
                    CryptoManager.base64ToPublicKey(
                            packet.senderPublicKey
                    );


            return MessageSigner.verify(
                    packet,
                    publicKey
            );


        } catch (Exception e) {

            return false;
        }
    }


    private void forwardPacket(
            MessagePacket packet
    ) {

        if (!packet.canForward()) {
            return;
        }


        packet.decreaseHop();


        DhtPeer targetPeer =
                routingTable.getPeerByUserId(
                        packet.recipientUserId
                );


        if (targetPeer != null) {

            byte[] encoded =
                    safeEncode(packet);


            if (encoded != null &&
                    tcpTransport.sendTo(
                            targetPeer.nodeId,
                            encoded
                    )) {

                return;
            }
        }


        DhtPeer nextPeer =
                routingTable.findClosestPeer(
                        packet.recipientUserId
                );


        if (nextPeer == null) {
            return;
        }


        if (!tcpTransport.isConnected(
                nextPeer.nodeId
        )) {

            tcpTransport.connect(
                    nextPeer
            );

            return;
        }


        byte[] encoded =
                safeEncode(packet);


        if (encoded != null) {

            tcpTransport.sendTo(
                    nextPeer.nodeId,
                    encoded
            );
        }
    }


    private void sendAck(
            MessagePacket original,
            String receivedFromNodeId
    ) {

        try {

            MessagePacket ack =
                    new MessagePacket();


            ack.type =
                    MessagePacket.TYPE_ACK;


            ack.messageId =
                    MessageIdGenerator.generate();


            ack.ackMessageId =
                    original.messageId;


            ack.senderUserId =
                    localUserId;


            ack.recipientUserId =
                    original.senderUserId;


            ack.senderUsername =
                    localUsername;


            ack.recipientUsername =
                    original.senderUsername;


            ack.senderPublicKey =
                    Base64.encodeToString(
                            identityManager
                                    .getPublicKey()
                                    .getEncoded(),
                            Base64.NO_WRAP
                    );


            long now =
                    System.currentTimeMillis();


            ack.createdAt =
                    now;


            ack.expiresAt =
                    now + MESSAGE_TTL;


            ack.hopLimit =
                    DEFAULT_HOP_LIMIT;


            ack.encryptedPayload =
                    "";


            ack.signature =
                    MessageSigner.sign(
                            ack,
                            identityManager
                                    .getPrivateKey()
                    );


            byte[] data =
                    MessagePacketCodec.encode(
                            ack
                    );


            DhtPeer senderPeer =
                    routingTable.getPeerByUserId(
                            original.senderUserId
                    );


            if (senderPeer != null &&
                    tcpTransport.isConnected(
                            senderPeer.nodeId
                    )) {

                tcpTransport.sendTo(
                        senderPeer.nodeId,
                        data
                );

                return;
            }


            DhtPeer nextPeer =
                    routingTable.findClosestPeer(
                            original.senderUserId
                    );


            if (nextPeer != null) {

                if (!tcpTransport.isConnected(
                        nextPeer.nodeId
                )) {

                    tcpTransport.connect(
                            nextPeer
                    );

                    return;
                }


                tcpTransport.sendTo(
                        nextPeer.nodeId,
                        data
                );
            }


        } catch (Exception ignored) {
            // ACK failure should not crash the application.
        }
    }


    private void handleAck(
            MessagePacket packet
    ) {

        if (packet.ackMessageId == null) {
            return;
        }


        try {

            if (!verifyPacket(
                    packet
            )) {

                return;
            }


            Message message =
                    database
                            .messageDao()
                            .getById(
                                    packet.ackMessageId
                            );


            if (message == null) {
                return;
            }


            database
                    .messageDao()
                    .updateStatus(
                            packet.ackMessageId,
                            Message.STATUS_DELIVERED
                    );


            runOnUiThread(() ->
                    setStatus(
                            getString(
                                    R.string.message_delivered
                            )
                    )
            );


        } catch (Exception ignored) {
            // Invalid ACK.
        }
    }


    private byte[] safeEncode(
            MessagePacket packet
    ) {

        try {

            return MessagePacketCodec.encode(
                    packet
            );

        } catch (Exception e) {

            return null;
        }
    }


    @Override
    public void onDisconnected(
            String nodeId
    ) {

        runOnUiThread(() ->
                setStatus(
                        getString(
                                R.string.peer_disconnected
                        )
                )
        );
    }


    @Override
    public void onError(
            String nodeId,
            Exception exception
    ) {

        runOnUiThread(() ->
                setStatus(
                        getString(
                                R.string.network_error
                        )
                )
        );
    }


    private void setStatus(
            String text
    ) {

        if (statusText != null) {

            statusText.setText(
                    text
            );
        }
    }

    private void appendMessage(
            String text
    ) {

        android.util.Log.d(
                "P2UI",
                "APPEND_MESSAGE called"
        );

        android.util.Log.d(
                "P2UI",
                "TEXT=" + text
        );

        if (messagesText == null) {

            android.util.Log.e(
                    "P2UI",
                    "messagesText is NULL"
            );

            return;
        }

        String old =
                messagesText
                        .getText()
                        .toString();

        if (old.isEmpty()) {

            messagesText.setText(
                    text
            );

        } else {

            messagesText.setText(
                    old +
                            "\n" +
                            text
            );
        }

        android.util.Log.d(
                "P2UI",
                "APPEND_MESSAGE DONE"
        );
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


    private String getLocalNodeIdSafely() {

        try {

            return identityManager.getNodeId();

        } catch (Exception e) {

            return "";
        }
    }


    private String bytesToHex(
            byte[] bytes
    ) {

        StringBuilder result =
                new StringBuilder();


        for (byte b : bytes) {

            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }


        return result.toString();
    }


    private void showFatalError(
            String message
    ) {

        TextView text =
                new TextView(this);


        text.setText(
                message
        );


        text.setTextSize(
                18
        );


        text.setPadding(
                40,
                40,
                40,
                40
        );


        ScrollView scrollView =
                new ScrollView(this);

        scrollView.addView(
                text
        );


        setContentView(
                scrollView
        );
    }


    @Override
    protected void onDestroy() {

        if (dhtDiscovery != null) {

            dhtDiscovery.stop();
        }


        if (tcpTransport != null) {

            tcpTransport.stop();
        }


        databaseExecutor.shutdownNow();


        super.onDestroy();
    }


    public static DhtRoutingTable getRoutingTable() {
        return routingTable;
    }
    public static TcpTransport getTcpTransport() {
        return tcpTransport;
    }
    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter =
                new IntentFilter("ir.p2payam.NEW_MESSAGE");

        registerReceiver(
                newMessageReceiver,
                filter,
                Context.RECEIVER_EXPORTED
        );
    }
    @Override
    protected void onStop() {
        super.onStop();

        try {
            unregisterReceiver(newMessageReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

}
