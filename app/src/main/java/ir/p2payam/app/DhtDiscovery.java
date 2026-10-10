package ir.p2payam.app;

import android.content.Context;
import android.util.Base64;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DhtDiscovery {

    private static final int DISCOVERY_PORT = 8989;

    private static final long ANNOUNCEMENT_INTERVAL = 3000;

    private static final String PREFIX =
            "P2PAYAM_DHT|";

    private static final int MAX_PACKET_SIZE = 4096;


    private final Context context;

    private final IdentityManager identityManager;

    private final UserIdentityManager userIdentityManager;

    private final DhtRoutingTable routingTable;

    private final TcpTransport tcpTransport;


    private final ExecutorService executor =
            Executors.newCachedThreadPool();


    private volatile boolean running = false;

    private DatagramSocket socket;

    private Thread listenerThread;


    public DhtDiscovery(
            Context context,
            IdentityManager identityManager,
            UserIdentityManager userIdentityManager,
            DhtRoutingTable routingTable,
            TcpTransport tcpTransport
    ) {

        this.context =
                context.getApplicationContext();

        this.identityManager =
                identityManager;

        this.userIdentityManager =
                userIdentityManager;

        this.routingTable =
                routingTable;

        this.tcpTransport =
                tcpTransport;
    }


    // =====================================================
    // Start
    // =====================================================

    public synchronized void start()
            throws Exception {

        if (running) {
            return;
        }


        /*
         * Create UDP socket BEFORE starting the
         * announcement thread.
         *
         * This prevents the first announcement
         * from being sent before our listener
         * is actually ready.
         */
        socket =
                createListenerSocket();


        running = true;


        startListener();


        /*
         * Give listener thread a moment to enter
         * receive().
         */
        try {

            Thread.sleep(100);

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();
        }


        startAnnouncement();
    }


    // =====================================================
    // Create UDP listener socket
    // =====================================================

    private DatagramSocket createListenerSocket()
            throws Exception {

        DatagramSocket result =
                new DatagramSocket(
                        null
                );


        result.setReuseAddress(
                true
        );


        result.setBroadcast(
                true
        );


        result.bind(
                new InetSocketAddress(
                        DISCOVERY_PORT
                )
        );


        return result;
    }


    // =====================================================
    // UDP Listener
    // =====================================================

    private void startListener() {

        listenerThread =
                new Thread(
                        () -> {

                            try {

                                byte[] buffer =
                                        new byte[
                                                MAX_PACKET_SIZE
                                                ];


                                while (running &&
                                        socket != null &&
                                        !socket.isClosed()) {

                                    DatagramPacket packet =
                                            new DatagramPacket(
                                                    buffer,
                                                    buffer.length
                                            );


                                    socket.receive(
                                            packet
                                    );


                                    if (!running) {
                                        break;
                                    }


                                    String data =
                                            new String(
                                                    packet.getData(),
                                                    packet.getOffset(),
                                                    packet.getLength(),
                                                    StandardCharsets.UTF_8
                                            );


                                    String host =
                                            packet.getAddress()
                                                    .getHostAddress();


                                    handleAnnouncement(
                                            data,
                                            host
                                    );
                                }


                            } catch (Exception e) {

                                if (running) {

                                    e.printStackTrace();
                                }
                            }

                        },
                        "P2P-DHT-Listener"
                );


        listenerThread.start();
    }


    // =====================================================
    // Announcement loop
    // =====================================================

    private void startAnnouncement() {

        executor.execute(
                () -> {

                    while (running) {

                        try {

                            sendAnnouncement();

                        } catch (Exception e) {

                            if (running) {

                                e.printStackTrace();
                            }
                        }


                        try {

                            Thread.sleep(
                                    ANNOUNCEMENT_INTERVAL
                            );

                        } catch (InterruptedException e) {

                            Thread.currentThread()
                                    .interrupt();

                            break;
                        }
                    }
                }
        );
    }


    // =====================================================
    // Send Announcement
    // =====================================================

    private void sendAnnouncement()
            throws Exception {

        String nodeId =
                identityManager
                        .getNodeId();


        String userId =
                userIdentityManager
                        .getUserId();


        String username =
                userIdentityManager
                        .getUsername();


        String publicKey =
                Base64.encodeToString(
                        identityManager
                                .getPublicKey()
                                .getEncoded(),
                        Base64.NO_WRAP
                );


        int port =
                TcpTransport.DEFAULT_PORT;


        String announcement =
                PREFIX +
                        nodeId +
                        "|" +
                        userId +
                        "|" +
                        username +
                        "|" +
                        publicKey +
                        "|" +
                        port;


        byte[] data =
                announcement.getBytes(
                        StandardCharsets.UTF_8
                );


        if (data.length >
                MAX_PACKET_SIZE) {

            return;
        }


        /*
         * Send to global broadcast first.
         */
        sendBroadcastToAddress(
                data,
                InetAddress.getByName(
                        "255.255.255.255"
                )
        );


        /*
         * Also send to each IPv4 interface
         * broadcast address.
         *
         * This is important because some routers
         * do not properly forward 255.255.255.255
         * on the local Wi-Fi interface.
         */
        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();


        if (interfaces == null) {
            return;
        }


        while (interfaces.hasMoreElements()) {

            NetworkInterface networkInterface =
                    interfaces.nextElement();


            try {

                if (!networkInterface.isUp() ||
                        networkInterface.isLoopback()) {

                    continue;
                }

            } catch (Exception ignored) {

                continue;
            }


            for (InterfaceAddressHolder holder :
                    getBroadcastAddresses(
                            networkInterface
                    )) {

                try {

                    sendBroadcastToAddress(
                            data,
                            holder.address
                    );

                } catch (Exception ignored) {
                }
            }
        }
    }


    // =====================================================
    // Broadcast sender
    // =====================================================

    private void sendBroadcastToAddress(
            byte[] data,
            InetAddress address
    ) throws Exception {

        if (address == null) {
            return;
        }


        DatagramSocket sendSocket =
                new DatagramSocket();


        try {

            sendSocket.setBroadcast(
                    true
            );


            DatagramPacket packet =
                    new DatagramPacket(
                            data,
                            data.length,
                            address,
                            DISCOVERY_PORT
                    );


            sendSocket.send(
                    packet
            );

        } finally {

            sendSocket.close();
        }
    }


    // =====================================================
    // Get IPv4 broadcast addresses
    // =====================================================

    private Iterable<InterfaceAddressHolder>
    getBroadcastAddresses(
            NetworkInterface networkInterface
    ) {

        java.util.List<InterfaceAddressHolder>
                result =
                new java.util.ArrayList<>();


        try {

            java.util.List<java.net.InterfaceAddress>
                    addresses =
                    networkInterface
                            .getInterfaceAddresses();


            for (java.net.InterfaceAddress address :
                    addresses) {

                InetAddress broadcast =
                        address.getBroadcast();


                if (broadcast == null) {
                    continue;
                }


                if (!(broadcast instanceof Inet4Address)) {
                    continue;
                }


                result.add(
                        new InterfaceAddressHolder(
                                broadcast
                        )
                );
            }

        } catch (Exception ignored) {
        }


        return result;
    }


    // =====================================================
    // Handle Announcement
    // =====================================================

    private void handleAnnouncement(
            String data,
            String host
    ) {

        try {

            if (data == null ||
                    !data.startsWith(PREFIX)) {

                return;
            }


            String payload =
                    data.substring(
                            PREFIX.length()
                    );


            String[] parts =
                    payload.split(
                            "\\|",
                            5
                    );


            if (parts.length != 5) {
                return;
            }


            String nodeId =
                    parts[0];


            String userId =
                    parts[1];


            String username =
                    parts[2];


            String publicKey =
                    parts[3];


            int port =
                    Integer.parseInt(
                            parts[4]
                    );


            if (nodeId.isEmpty() ||
                    userId.isEmpty() ||
                    username.isEmpty() ||
                    publicKey.isEmpty()) {

                return;
            }


            if (userId.length() != 64) {
                return;
            }


            if (port <= 0 ||
                    port > 65535) {

                return;
            }


            /*
             * Verify Node ID -> Public Key.
             */
            String calculatedNodeId =
                    calculateNodeId(
                            publicKey
                    );


            if (!nodeId.equals(
                    calculatedNodeId
            )) {

                return;
            }


            String localNodeId =
                    identityManager
                            .getNodeId();


            /*
             * Ignore our own broadcast.
             */
            if (nodeId.equals(
                    localNodeId
            )) {

                return;
            }


            String normalizedUsername =
                    normalizeUsername(
                            username
                    );


            if (normalizedUsername.isEmpty()) {
                return;
            }


            DhtPeer peer =
                    new DhtPeer(
                            nodeId,
                            userId,
                            normalizedUsername,
                            publicKey,
                            host,
                            port
                    );


            routingTable.addPeer(
                    peer
            );


            /*
             * Do not repeatedly create connections.
             *
             * TcpTransport itself protects against
             * duplicate connection attempts.
             */
            tcpTransport.connect(
                    peer
            );


        } catch (Exception ignored) {

            /*
             * Discovery packets are untrusted input.
             *
             * Never allow malformed UDP data to
             * affect the application.
             */
        }
    }


    // =====================================================
    // Calculate Node ID
    // =====================================================

    private String calculateNodeId(
            String publicKeyBase64
    ) throws Exception {

        byte[] publicKey =
                Base64.decode(
                        publicKeyBase64,
                        Base64.NO_WRAP
                );


        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );


        byte[] hash =
                digest.digest(
                        publicKey
                );


        StringBuilder result =
                new StringBuilder();


        for (byte b : hash) {

            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }


        return result.toString();
    }


    // =====================================================
    // Username normalization
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


    // =====================================================
    // Stop
    // =====================================================

    public synchronized void stop() {

        running = false;


        if (socket != null) {

            socket.close();

            socket = null;
        }


        if (listenerThread != null) {

            listenerThread.interrupt();

            listenerThread = null;
        }


        executor.shutdownNow();
    }


    // =====================================================
    // Small wrapper
    // =====================================================

    private static class InterfaceAddressHolder {

        final InetAddress address;


        InterfaceAddressHolder(
                InetAddress address
        ) {

            this.address =
                    address;
        }
    }
}