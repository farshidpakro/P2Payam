package ir.p2payam.app;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerManager {

    private final P2PayamDatabase database;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();


    public PeerManager(Context context) {

        database =
                P2PayamDatabase.getInstance(
                        context.getApplicationContext()
                );
    }


    // =====================================================
    // Add / Update Peer
    // =====================================================

    public void savePeer(
            String nodeId,
            String publicKey,
            boolean online,
            PeerCallback callback
    ) {

        executor.execute(() -> {

            try {

                Peer peer =
                        database.peerDao()
                                .getByNodeId(nodeId);


                if (peer == null) {

                    peer =
                            new Peer();

                    peer.nodeId =
                            nodeId;
                }


                peer.publicKey =
                        publicKey;

                peer.online =
                        online;

                peer.lastSeen =
                        System.currentTimeMillis();


                database.peerDao()
                        .insert(peer);


                if (callback != null) {

                    callback.onSuccess(peer);
                }


            } catch (Exception e) {

                if (callback != null) {

                    callback.onError(e);
                }
            }
        });
    }


    // =====================================================
    // Get Peer
    // =====================================================

    public void getPeer(
            String nodeId,
            PeerCallback callback
    ) {

        executor.execute(() -> {

            try {

                Peer peer =
                        database.peerDao()
                                .getByNodeId(nodeId);


                if (callback != null) {

                    callback.onSuccess(peer);
                }


            } catch (Exception e) {

                if (callback != null) {

                    callback.onError(e);
                }
            }
        });
    }


    // =====================================================
    // Get All Peers
    // =====================================================

    public void getAllPeers(
            PeersCallback callback
    ) {

        executor.execute(() -> {

            try {

                List<Peer> peers =
                        database.peerDao()
                                .getAll();


                if (callback != null) {

                    callback.onSuccess(peers);
                }


            } catch (Exception e) {

                if (callback != null) {

                    callback.onError(e);
                }
            }
        });
    }


    // =====================================================
    // Mark Online
    // =====================================================

    public void markOnline(
            String nodeId
    ) {

        executor.execute(() -> {

            try {

                Peer peer =
                        database.peerDao()
                                .getByNodeId(nodeId);


                if (peer != null) {

                    peer.online =
                            true;

                    peer.lastSeen =
                            System.currentTimeMillis();


                    database.peerDao()
                            .insert(peer);
                }


            } catch (Exception e) {

                e.printStackTrace();
            }
        });
    }


    // =====================================================
    // Mark Offline
    // =====================================================

    public void markOffline(
            String nodeId
    ) {

        executor.execute(() -> {

            try {

                Peer peer =
                        database.peerDao()
                                .getByNodeId(nodeId);


                if (peer != null) {

                    peer.online =
                            false;


                    database.peerDao()
                            .insert(peer);
                }


            } catch (Exception e) {

                e.printStackTrace();
            }
        });
    }


    // =====================================================
    // Shutdown
    // =====================================================

    public void shutdown() {

        executor.shutdown();
    }


    // =====================================================
    // Callbacks
    // =====================================================

    public interface PeerCallback {

        void onSuccess(Peer peer);

        void onError(Exception e);
    }


    public interface PeersCallback {

        void onSuccess(List<Peer> peers);

        void onError(Exception e);
    }
}