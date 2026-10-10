package ir.p2payam.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PeerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Peer peer);

    @Query("SELECT * FROM peers")
    List<Peer> getAll();

    @Query("SELECT * FROM peers WHERE nodeId = :nodeId LIMIT 1")
    Peer getByNodeId(String nodeId);

    @Query("DELETE FROM peers")
    void deleteAll();

    @Query("DELETE FROM peers WHERE nodeId = :nodeId")
    void deleteByNodeId(String nodeId);
}

