package ir.p2payam.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(
            Conversation conversation
    );

    @Update
    void update(
            Conversation conversation
    );

    @Query(
            "SELECT * FROM conversations " +
                    "ORDER BY lastMessageAt DESC"
    )
    List<Conversation> getAll();

    @Query(
            "SELECT * FROM conversations " +
                    "WHERE conversationId = :conversationId " +
                    "LIMIT 1"
    )
    Conversation getById(
            String conversationId
    );

    @Query(
            "SELECT * FROM conversations " +
                    "WHERE peerUserId = :peerUserId " +
                    "LIMIT 1"
    )
    Conversation getByPeerUserId(
            String peerUserId
    );

    @Query(
            "DELETE FROM conversations " +
                    "WHERE conversationId = :conversationId"
    )
    void delete(
            String conversationId
    );

    @Query(
            "UPDATE conversations " +
                    "SET unreadCount = 0 " +
                    "WHERE conversationId = :conversationId"
    )
    void markAsRead(
            String conversationId
    );
}