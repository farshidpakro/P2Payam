package ir.p2payam.app;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                Peer.class,
                Message.class,
                Conversation.class
        },
        version = 2,
        exportSchema = false
)
public abstract class P2PayamDatabase
        extends RoomDatabase {

    public abstract PeerDao peerDao();

    public abstract MessageDao messageDao();

    public abstract ConversationDao conversationDao();

    private static volatile P2PayamDatabase INSTANCE;

    /*
     * Database migration:
     *
     * Version 1
     *      ↓
     * Version 2
     *
     * Add conversations table
     * Add conversationId to messages
     */
    private static final Migration MIGRATION_1_2 =
            new Migration(1, 2) {

                @Override
                public void migrate(
                        SupportSQLiteDatabase database
                ) {

                    /*
                     * Create conversations table.
                     */
                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS conversations (" +
                                    "conversationId TEXT NOT NULL, " +
                                    "peerUserId TEXT NOT NULL, " +
                                    "peerUsername TEXT, " +
                                    "lastMessage TEXT, " +
                                    "lastMessageAt INTEGER NOT NULL, " +
                                    "createdAt INTEGER NOT NULL, " +
                                    "unreadCount INTEGER NOT NULL, " +
                                    "PRIMARY KEY(conversationId)" +
                                    ")"
                    );

                    /*
                     * Add conversationId to existing messages.
                     *
                     * Existing messages don't belong to a
                     * conversation yet, so they receive an
                     * empty value.
                     */
                    database.execSQL(
                            "ALTER TABLE messages " +
                                    "ADD COLUMN conversationId TEXT NOT NULL DEFAULT ''"
                    );
                }
            };

    public static P2PayamDatabase getInstance(
            Context context
    ) {

        if (INSTANCE == null) {

            synchronized (
                    P2PayamDatabase.class
            ) {

                if (INSTANCE == null) {

                    INSTANCE =
                            Room.databaseBuilder(
                                            context.getApplicationContext(),
                                            P2PayamDatabase.class,
                                            "p2payam.db"
                                    )
                                    .addMigrations(
                                            MIGRATION_1_2
                                    )
                                    .build();
                }
            }
        }

        return INSTANCE;
    }
}