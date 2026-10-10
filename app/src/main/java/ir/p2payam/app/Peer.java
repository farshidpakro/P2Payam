package ir.p2payam.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "peers")
public class Peer {

    @PrimaryKey
    @NonNull
    public String nodeId = "";

    public String publicKey;

    public long lastSeen;

    public boolean online;
}

