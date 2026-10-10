package ir.p2payam.app;

public class DhtPeer {

    // Internal network identity
    public String nodeId;

    // User identity
    public String userId;

    // Human-readable username
    public String username;

    // Public key used for encryption/signature
    public String publicKey;

    // Internal transport information
    public String host;
    public int port;

    public long lastSeen;

    public DhtPeer(
            String nodeId,
            String userId,
            String username,
            String publicKey,
            String host,
            int port
    ) {

        this.nodeId = nodeId;

        this.userId = userId;

        this.username = username;

        this.publicKey = publicKey;

        this.host = host;

        this.port = port;

        this.lastSeen =
                System.currentTimeMillis();
    }
}