package ir.p2payam.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Message message);

    @Query("   SELECT * FROM messages  WHERE conversationId = :conversationId   ORDER BY createdAt ASC")
    List<Message> getConversationMessages(
            String conversationId
    );

    @Query("   SELECT * FROM messages   WHERE recipientId = :recipientId  ORDER BY createdAt ASC")
    List<Message> getForRecipient(
            String recipientId
    );

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1 ")
    Message getById(
            String messageId
    );

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId ")
    void updateStatus(
            String messageId,
            int status
    );

    @Query(" DELETE FROM messages WHERE expiresAt <= :now")
    void deleteExpired(
            long now
    );
}