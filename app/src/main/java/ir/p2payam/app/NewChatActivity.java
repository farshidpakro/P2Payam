package ir.p2payam.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class NewChatActivity
        extends AppCompatActivity {

    private EditText recipientUsernameText;

    private TextView errorText;

    private P2PayamDatabase database;

    private UserIdentityManager userIdentityManager;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_new_chat
        );

        database =
                P2PayamDatabase.getInstance(
                        this
                );

        userIdentityManager =
                new UserIdentityManager(
                        this
                );

        try {

            IdentityManager identityManager =
                    new IdentityManager(this);

            identityManager.getIdentity();

            userIdentityManager.initialize(
                    identityManager
            );

        } catch (Exception e) {

            e.printStackTrace();

            showError(
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Unable to initialize user identity"
            );
        }

        recipientUsernameText =
                findViewById(
                        R.id.recipientUsernameText
                );

        errorText =
                findViewById(
                        R.id.errorText
                );

        ImageButton backButton =
                findViewById(
                        R.id.backButton
                );

        Button startChatButton =
                findViewById(
                        R.id.startChatButton
                );

        backButton.setOnClickListener(
                view -> finish()
        );

        startChatButton.setOnClickListener(
                view -> startChat()
        );
    }

    private void startChat() {

        String username =
                recipientUsernameText
                        .getText()
                        .toString()
                        .trim()
                        .toLowerCase();

        if (username.startsWith("@")) {
            username =
                    username.substring(1);
        }

        if (!username.matches(
                "[a-z0-9_]{3,32}"
        )) {

            showError(
                    "Enter a valid username"
            );

            return;
        }

        final String finalUsername = username;

        new Thread(() -> {

            try {

                /*
                 * At this stage the routing table is still
                 * the source of known users.
                 *
                 * Later this lookup will be performed through
                 * the DHT.
                 */

                DhtPeer peer =
                        MainActivity
                                .getRoutingTable()
                                .getPeerByUsername(
                                        finalUsername
                                );

                if (peer == null) {

                    runOnUiThread(() ->
                            showError(
                                    "User not found in DHT"
                            )
                    );

                    return;
                }

                String localUserId =
                        userIdentityManager
                                .getUserId();

                String conversationId =
                        ConversationIdGenerator
                                .fromUserIds(
                                        localUserId,
                                        peer.userId
                                );

                /*
                 * All Room operations must run
                 * outside the UI thread.
                 */

                Conversation conversation =
                        database
                                .conversationDao()
                                .getById(
                                        conversationId
                                );

                if (conversation == null) {

                    conversation =
                            new Conversation();

                    conversation.conversationId =
                            conversationId;

                    conversation.peerUserId =
                            peer.userId;

                    conversation.peerUsername =
                            finalUsername;

                    conversation.createdAt =
                            System.currentTimeMillis();

                    conversation.lastMessageAt = 0;

                    conversation.lastMessage = "";

                    conversation.unreadCount = 0;

                    database
                            .conversationDao()
                            .insert(
                                    conversation
                            );

                } else {

                    /*
                     * Username can change.
                     * Keep the permanent conversation ID,
                     * but update the displayed username.
                     */

                    conversation.peerUsername =
                            finalUsername;

                    database
                            .conversationDao()
                            .update(
                                    conversation
                            );
                }

                runOnUiThread(() -> {

                    Intent intent =
                            new Intent(
                                    NewChatActivity.this,
                                    ChatActivity.class
                            );

                    intent.putExtra(
                            ChatActivity.EXTRA_CONVERSATION_ID,
                            conversationId
                    );

                    startActivity(intent);

                    finish();

                });

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        showError(
                                e.getMessage() != null
                                        ? e.getMessage()
                                        : "Unable to create chat"
                        )
                );
            }

        }).start();
    }

    private void showError(
            String message
    ) {

        errorText.setText(
                message
        );

        errorText.setVisibility(
                TextView.VISIBLE
        );
    }
}