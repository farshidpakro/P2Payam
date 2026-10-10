package ir.p2payam.app;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DhtRoutingTable {

    private final String localNodeId;

    private final Map<String, DhtPeer> peersByNodeId =
            new HashMap<>();

    private final Map<String, DhtPeer> peersByUserId =
            new HashMap<>();

    private final Map<String, DhtPeer> peersByUsername =
            new HashMap<>();

    public DhtRoutingTable(
            String localNodeId
    ) {

        this.localNodeId = localNodeId;
    }


    // =====================================================
    // Add Peer
    // =====================================================

    public synchronized void addPeer(
            DhtPeer peer
    ) {

        if (peer == null ||
                peer.nodeId == null ||
                peer.nodeId.equals(localNodeId)) {

            return;
        }

        peer.lastSeen =
                System.currentTimeMillis();


        peersByNodeId.put(
                peer.nodeId,
                peer
        );


        if (peer.userId != null) {

            peersByUserId.put(
                    peer.userId,
                    peer
            );
        }


        if (peer.username != null) {

            peersByUsername.put(
                    normalizeUsername(
                            peer.username
                    ),
                    peer
            );
        }
    }


    // =====================================================
    // Find by Node ID
    // =====================================================

    public synchronized DhtPeer getPeer(
            String nodeId
    ) {

        return peersByNodeId.get(
                nodeId
        );
    }


    // =====================================================
    // Find by User ID
    // =====================================================

    public synchronized DhtPeer getPeerByUserId(
            String userId
    ) {

        return peersByUserId.get(
                userId
        );
    }


    // =====================================================
    // Find by Username
    // =====================================================

    public synchronized DhtPeer getPeerByUsername(
            String username
    ) {

        if (username == null) {
            return null;
        }

        return peersByUsername.get(
                normalizeUsername(
                        username
                )
        );
    }


    // =====================================================
    // All Peers
    // =====================================================

    public synchronized List<DhtPeer> getAllPeers() {

        return new ArrayList<>(
                peersByNodeId.values()
        );
    }


    // =====================================================
    // Closest Peer to User ID
    // =====================================================

    public synchronized DhtPeer findClosestPeer(
            String targetUserId
    ) {

        return findClosestPeerExcept(
                targetUserId,
                null
        );
    }


    public synchronized DhtPeer findClosestPeerExcept(
            String targetUserId,
            String excludedNodeId
    ) {

        if (targetUserId == null) {
            return null;
        }


        List<DhtPeer> list =
                new ArrayList<>(
                        peersByNodeId.values()
                );


        list.removeIf(peer ->
                peer.nodeId == null ||
                        peer.nodeId.equals(localNodeId) ||
                        (
                                excludedNodeId != null &&
                                        peer.nodeId.equals(
                                                excludedNodeId
                                        )
                        )
        );


        if (list.isEmpty()) {
            return null;
        }


        list.sort(
                Comparator.comparing(
                        peer ->
                                xorDistance(
                                        peer.userId,
                                        targetUserId
                                )
                )
        );


        return list.get(0);
    }


    // =====================================================
    // Remove Peer
    // =====================================================

    public synchronized void removePeer(
            String nodeId
    ) {

        if (nodeId == null) {
            return;
        }


        DhtPeer peer =
                peersByNodeId.remove(
                        nodeId
                );


        if (peer == null) {
            return;
        }


        if (peer.userId != null) {

            DhtPeer current =
                    peersByUserId.get(
                            peer.userId
                    );

            if (current == peer) {

                peersByUserId.remove(
                        peer.userId
                );
            }
        }


        if (peer.username != null) {

            String username =
                    normalizeUsername(
                            peer.username
                    );

            DhtPeer current =
                    peersByUsername.get(
                            username
                    );

            if (current == peer) {

                peersByUsername.remove(
                        username
                );
            }
        }
    }


    // =====================================================
    // XOR Distance
    // =====================================================

    private BigInteger xorDistance(
            String a,
            String b
    ) {

        if (a == null ||
                b == null) {

            return BigInteger.valueOf(
                    Long.MAX_VALUE
            );
        }


        BigInteger x =
                new BigInteger(
                        a,
                        16
                );


        BigInteger y =
                new BigInteger(
                        b,
                        16
                );


        return x.xor(y);
    }


    // =====================================================
    // Normalize Username
    // =====================================================

    private String normalizeUsername(
            String username
    ) {

        if (username == null) {
            return "";
        }

        String value =
                username.trim()
                        .toLowerCase();

        if (value.startsWith("@")) {

            value =
                    value.substring(1);
        }

        return value;
    }
}