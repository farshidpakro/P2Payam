package ir.p2payam.app;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpTransport {

    private static final String TAG = "P2TCP";

    public static final int DEFAULT_PORT = 8988;

    private static final int CONNECT_TIMEOUT = 5000;

    private static final int MAX_STRING_LENGTH = 4096;

    private static final int MAX_PACKET_SIZE =
            1024 * 1024;

    private static final int MAX_PENDING_PER_PEER = 100;

    private final Context context;

    private final IdentityManager identityManager;

    private final UserIdentityManager userIdentityManager;

    private final DhtRoutingTable routingTable;

    private final ExecutorService executor =
            Executors.newCachedThreadPool();

    private final Map<String, PeerConnection> connections =
            new ConcurrentHashMap<>();

    private final Map<String, Boolean> pendingConnections =
            new ConcurrentHashMap<>();

    private final Map<String, ConcurrentLinkedQueue<byte[]>>
            pendingPackets =
            new ConcurrentHashMap<>();

    private volatile boolean running = false;

    private ServerSocket serverSocket;

    private Thread acceptThread;

    private Listener listener;

    public TcpTransport(
            Context context,
            IdentityManager identityManager,
            UserIdentityManager userIdentityManager,
            DhtRoutingTable routingTable
    ) {

        this.context =
                context.getApplicationContext();

        this.identityManager =
                identityManager;

        this.userIdentityManager =
                userIdentityManager;

        this.routingTable =
                routingTable;
    }

    // =====================================================
    // Listener
    // =====================================================

    public interface Listener {

        void onConnected(
                DhtPeer peer
        );

        void onReceived(
                String nodeId,
                byte[] data
        );

        void onDisconnected(
                String nodeId
        );

        void onError(
                String nodeId,
                Exception exception
        );
    }

    public void setListener(
            Listener listener
    ) {

        this.listener = listener;
    }

    // =====================================================
    // Start
    // =====================================================

    public synchronized void start()
            throws Exception {

        if (running) {

            Log.d(
                    TAG,
                    "START_IGNORED already running"
            );

            return;
        }

        Log.d(
                TAG,
                "STARTING TCP transport on port " +
                        DEFAULT_PORT
        );

        running = true;

        serverSocket =
                new ServerSocket(
                        DEFAULT_PORT
                );

        Log.d(
                TAG,
                "SERVER_SOCKET_STARTED port=" +
                        DEFAULT_PORT
        );

        acceptThread =
                new Thread(
                        this::acceptLoop,
                        "P2P-TCP-Accept"
                );

        acceptThread.start();

        Log.d(
                TAG,
                "ACCEPT_THREAD_STARTED"
        );
    }

    // =====================================================
    // Accept incoming connections
    // =====================================================

    private void acceptLoop() {

        Log.d(
                TAG,
                "ACCEPT_LOOP_STARTED"
        );

        while (running) {

            try {

                Socket socket =
                        serverSocket.accept();

                Log.d(
                        TAG,
                        "INCOMING_CONNECTION from=" +
                                socket.getInetAddress()
                                        .getHostAddress()
                );

                configureSocket(
                        socket
                );

                executor.execute(
                        () -> handleIncoming(
                                socket
                        )
                );

            } catch (Exception e) {

                if (running) {

                    Log.e(
                            TAG,
                            "ACCEPT_ERROR",
                            e
                    );
                }
            }
        }

        Log.d(
                TAG,
                "ACCEPT_LOOP_STOPPED"
        );
    }

    // =====================================================
    // Incoming connection
    // =====================================================

    private void handleIncoming(
            Socket socket
    ) {

        PeerConnection connection =
                null;

        try {

            Log.d(
                    TAG,
                    "INCOMING_AUTH_START"
            );

            connection =
                    authenticateConnection(
                            socket,
                            null
                    );

            if (connection == null) {

                Log.d(
                        TAG,
                        "INCOMING_AUTH_NO_CONNECTION"
                );

                return;
            }

            Log.d(
                    TAG,
                    "INCOMING_AUTH_SUCCESS node=" +
                            connection.peer.nodeId
            );

            runConnection(
                    connection
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "INCOMING_CONNECTION_ERROR",
                    e
            );

            closeQuietly(
                    socket
            );
        }
    }

    // =====================================================
    // Connect
    // =====================================================

    public void connect(
            DhtPeer peer
    ) {

        if (peer == null) {

            Log.e(
                    TAG,
                    "CONNECT_FAILED peer=null"
            );

            return;
        }

        Log.d(
                TAG,
                "CONNECT_REQUEST node=" +
                        peer.nodeId +
                        " host=" +
                        peer.host +
                        " port=" +
                        peer.port
        );

        if (peer.nodeId == null ||
                peer.host == null ||
                peer.port <= 0) {

            Log.e(
                    TAG,
                    "CONNECT_FAILED invalid peer information"
            );

            return;
        }

        if (peer.nodeId.equals(
                getLocalNodeId()
        )) {

            Log.d(
                    TAG,
                    "CONNECT_IGNORED self"
            );

            return;
        }

        if (connections.containsKey(
                peer.nodeId
        )) {

            Log.d(
                    TAG,
                    "CONNECT_ALREADY_CONNECTED node=" +
                            peer.nodeId
            );

            flushPending(
                    peer.nodeId
            );

            return;
        }

        if (pendingConnections.putIfAbsent(
                peer.nodeId,
                true
        ) != null) {

            Log.d(
                    TAG,
                    "CONNECT_ALREADY_PENDING node=" +
                            peer.nodeId
            );

            return;
        }

        Log.d(
                TAG,
                "CONNECT_THREAD_START node=" +
                        peer.nodeId
        );

        executor.execute(
                () -> {

                    Socket socket =
                            null;

                    try {

                        socket =
                                new Socket();

                        configureSocket(
                                socket
                        );

                        Log.d(
                                TAG,
                                "SOCKET_CONNECT_START " +
                                        peer.host +
                                        ":" +
                                        peer.port
                        );

                        socket.connect(
                                new InetSocketAddress(
                                        peer.host,
                                        peer.port
                                ),
                                CONNECT_TIMEOUT
                        );

                        Log.d(
                                TAG,
                                "SOCKET_CONNECTED node=" +
                                        peer.nodeId
                        );

                        PeerConnection connection =
                                authenticateConnection(
                                        socket,
                                        peer
                                );

                        if (connection == null) {

                            Log.d(
                                    TAG,
                                    "AUTH_RETURNED_NULL node=" +
                                            peer.nodeId
                            );

                            closeQuietly(
                                    socket
                            );

                            return;
                        }

                        Log.d(
                                TAG,
                                "OUTGOING_AUTH_SUCCESS node=" +
                                        peer.nodeId
                        );

                        runConnection(
                                connection
                        );

                    } catch (Exception e) {

                        Log.e(
                                TAG,
                                "CONNECT_FAILED node=" +
                                        peer.nodeId +
                                        " host=" +
                                        peer.host +
                                        " port=" +
                                        peer.port,
                                e
                        );

                        closeQuietly(
                                socket
                        );

                        notifyError(
                                peer.nodeId,
                                e
                        );

                    } finally {

                        pendingConnections.remove(
                                peer.nodeId
                        );

                        Log.d(
                                TAG,
                                "CONNECT_THREAD_FINISHED node=" +
                                        peer.nodeId
                        );
                    }
                }
        );
    }

    // =====================================================
    // Send immediately OR queue
    // =====================================================

    public boolean sendOrQueue(
            DhtPeer peer,
            byte[] data
    ) {

        if (peer == null) {

            Log.e(
                    TAG,
                    "SEND_QUEUE_FAILED peer=null"
            );

            return false;
        }

        if (peer.nodeId == null) {

            Log.e(
                    TAG,
                    "SEND_QUEUE_FAILED nodeId=null"
            );

            return false;
        }

        if (data == null ||
                data.length == 0) {

            Log.e(
                    TAG,
                    "SEND_QUEUE_FAILED empty packet"
            );

            return false;
        }

        if (data.length > MAX_PACKET_SIZE) {

            Log.e(
                    TAG,
                    "SEND_QUEUE_FAILED packet too large size=" +
                            data.length
            );

            return false;
        }

        Log.d(
                TAG,
                "SEND_REQUEST node=" +
                        peer.nodeId +
                        " bytes=" +
                        data.length
        );

        /*
         * Already connected.
         */

        if (isConnected(
                peer.nodeId
        )) {

            Log.d(
                    TAG,
                    "SEND_DIRECT node=" +
                            peer.nodeId
            );

            boolean result =
                    sendTo(
                            peer.nodeId,
                            data
                    );

            Log.d(
                    TAG,
                    "SEND_DIRECT_RESULT node=" +
                            peer.nodeId +
                            " result=" +
                            result
            );

            /*
             * If direct send failed, queue the packet
             * and reconnect.
             */

            if (!result) {

                Log.w(
                        TAG,
                        "SEND_DIRECT_FAILED_QUEUEING node=" +
                                peer.nodeId
                );

                queuePacket(
                        peer,
                        data
                );

                connect(
                        peer
                );

                return true;
            }

            return true;
        }

        /*
         * Not connected.
         */

        Log.d(
                TAG,
                "SEND_NOT_CONNECTED_QUEUE node=" +
                        peer.nodeId
        );

        boolean queued =
                queuePacket(
                        peer,
                        data
                );

        if (!queued) {

            return false;
        }

        Log.d(
                TAG,
                "SEND_QUEUED node=" +
                        peer.nodeId +
                        " queueSize=" +
                        getQueueSize(
                                peer.nodeId
                        )
        );

        connect(
                peer
        );

        return true;
    }

    // =====================================================
    // Queue packet
    // =====================================================

    private boolean queuePacket(
            DhtPeer peer,
            byte[] data
    ) {

        ConcurrentLinkedQueue<byte[]> queue =
                pendingPackets.computeIfAbsent(
                        peer.nodeId,
                        key ->
                                new ConcurrentLinkedQueue<>()
                );

        if (queue.size() >=
                MAX_PENDING_PER_PEER) {

            Log.e(
                    TAG,
                    "QUEUE_FULL node=" +
                            peer.nodeId
            );

            return false;
        }

        queue.offer(
                data.clone()
        );

        Log.d(
                TAG,
                "QUEUE_ADD node=" +
                        peer.nodeId +
                        " size=" +
                        queue.size()
        );

        return true;
    }

    // =====================================================
    // Flush pending packets
    // =====================================================

    private void flushPending(
            String nodeId
    ) {

        if (nodeId == null) {
            return;
        }

        Log.d(
                TAG,
                "FLUSH_START node=" +
                        nodeId +
                        " queueSize=" +
                        getQueueSize(nodeId)
        );

        PeerConnection connection =
                connections.get(
                        nodeId
                );

        if (connection == null ||
                connection.isClosed()) {

            Log.w(
                    TAG,
                    "FLUSH_ABORT_NO_CONNECTION node=" +
                            nodeId
            );

            return;
        }

        ConcurrentLinkedQueue<byte[]> queue =
                pendingPackets.get(
                        nodeId
                );

        if (queue == null) {

            Log.d(
                    TAG,
                    "FLUSH_NO_QUEUE node=" +
                            nodeId
            );

            return;
        }

        while (true) {

            byte[] data =
                    queue.poll();

            if (data == null) {
                break;
            }

            Log.d(
                    TAG,
                    "FLUSH_SEND node=" +
                            nodeId +
                            " bytes=" +
                            data.length
            );

            boolean sent =
                    sendTo(
                            nodeId,
                            data
                    );

            if (!sent) {

                Log.e(
                        TAG,
                        "FLUSH_SEND_FAILED node=" +
                                nodeId
                );

                /*
                 * Put packet back.
                 */

                queue.offer(
                        data
                );

                /*
                 * Try reconnecting.
                 */

                DhtPeer peer =
                        routingTable.getPeer(
                                nodeId
                        );

                if (peer != null) {

                    Log.d(
                            TAG,
                            "FLUSH_RECONNECT node=" +
                                    nodeId
                    );

                    connect(
                            peer
                    );
                }

                break;
            }

            Log.d(
                    TAG,
                    "FLUSH_SEND_SUCCESS node=" +
                            nodeId
            );
        }

        if (queue.isEmpty()) {

            pendingPackets.remove(
                    nodeId,
                    queue
            );

            Log.d(
                    TAG,
                    "FLUSH_COMPLETE_EMPTY node=" +
                            nodeId
            );

        } else {

            Log.d(
                    TAG,
                    "FLUSH_REMAINING node=" +
                            nodeId +
                            " queueSize=" +
                            queue.size()
            );
        }
    }

    // =====================================================
    // Authenticate
    // =====================================================

    private PeerConnection authenticateConnection(
            Socket socket,
            DhtPeer expectedPeer
    ) throws Exception {

        Log.d(
                TAG,
                "AUTH_START expectedNode=" +
                        (expectedPeer == null
                                ? "INCOMING"
                                : expectedPeer.nodeId)
        );

        DataInputStream input =
                new DataInputStream(
                        socket.getInputStream()
                );

        DataOutputStream output =
                new DataOutputStream(
                        socket.getOutputStream()
                );

        /*
         * Send HELLO.
         */

        Log.d(
                TAG,
                "AUTH_SEND_HELLO"
        );

        writeHello(
                output
        );

        /*
         * Read remote HELLO.
         */

        Log.d(
                TAG,
                "AUTH_WAIT_HELLO"
        );

        Hello hello =
                readHello(
                        input
                );

        if (hello == null) {

            throw new IOException(
                    "Invalid HELLO"
            );
        }

        Log.d(
                TAG,
                "AUTH_HELLO_RECEIVED node=" +
                        hello.nodeId +
                        " user=" +
                        hello.username
        );

        if (hello.nodeId.equals(
                getLocalNodeId()
        )) {

            throw new IOException(
                    "Self connection"
            );
        }

        String calculatedNodeId =
                calculateNodeId(
                        hello.publicKey
                );

        if (!hello.nodeId.equals(
                calculatedNodeId
        )) {

            throw new IOException(
                    "Invalid Node ID"
            );
        }

        if (expectedPeer != null) {

            if (!hello.nodeId.equals(
                    expectedPeer.nodeId
            )) {

                throw new IOException(
                        "Unexpected Node ID"
                );
            }

            if (!hello.publicKey.equals(
                    expectedPeer.publicKey
            )) {

                throw new IOException(
                        "Unexpected public key"
                );
            }

            if (expectedPeer.userId != null &&
                    !expectedPeer.userId.equals(
                            hello.userId
                    )) {

                throw new IOException(
                        "Unexpected User ID"
                );
            }
        }

        if (hello.userId == null ||
                hello.userId.length() != 64) {

            throw new IOException(
                    "Invalid User ID"
            );
        }

        if (hello.username == null ||
                hello.username.isEmpty()) {

            throw new IOException(
                    "Invalid username"
            );
        }

        String normalizedUsername =
                normalizeUsername(
                        hello.username
                );

        DhtPeer peer =
                new DhtPeer(
                        hello.nodeId,
                        hello.userId,
                        normalizedUsername,
                        hello.publicKey,
                        socket.getInetAddress()
                                .getHostAddress(),
                        hello.port
                );

        Log.d(
                TAG,
                "AUTH_PEER_VALID node=" +
                        peer.nodeId +
                        " user=" +
                        peer.username +
                        " host=" +
                        peer.host +
                        " port=" +
                        peer.port
        );

        routingTable.addPeer(
                peer
        );

        PeerConnection connection =
                new PeerConnection(
                        peer,
                        socket,
                        input,
                        output
                );

        PeerConnection existing =
                connections.putIfAbsent(
                        peer.nodeId,
                        connection
                );

        if (existing != null) {

            Log.d(
                    TAG,
                    "AUTH_DUPLICATE_CONNECTION node=" +
                            peer.nodeId
            );

            connection.close();

            executor.execute(
                    () ->
                            flushPending(
                                    peer.nodeId
                            )
            );

            return null;
        }

        Log.d(
                TAG,
                "CONNECTION_REGISTERED node=" +
                        peer.nodeId
        );

        notifyConnected(
                peer
        );

        executor.execute(
                () ->
                        flushPending(
                                peer.nodeId
                        )
        );

        Log.d(
                TAG,
                "AUTH_COMPLETE node=" +
                        peer.nodeId
        );

        return connection;
    }

    // =====================================================
    // HELLO writer
    // =====================================================

    private void writeHello(
            DataOutputStream output
    ) throws Exception {

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

        output.writeUTF(
                nodeId
        );

        output.writeUTF(
                userId
        );

        output.writeUTF(
                username
        );

        output.writeUTF(
                publicKey
        );

        output.writeInt(
                DEFAULT_PORT
        );

        output.flush();

        Log.d(
                TAG,
                "HELLO_SENT node=" +
                        nodeId +
                        " user=" +
                        username
        );
    }

    // =====================================================
    // HELLO reader
    // =====================================================

    private Hello readHello(
            DataInputStream input
    ) throws Exception {

        String nodeId =
                input.readUTF();

        String userId =
                input.readUTF();

        String username =
                input.readUTF();

        String publicKey =
                input.readUTF();

        int port =
                input.readInt();

        if (!isValidString(nodeId)) {
            return null;
        }

        if (!isValidString(userId)) {
            return null;
        }

        if (!isValidString(username)) {
            return null;
        }

        if (!isValidString(publicKey)) {
            return null;
        }

        if (port <= 0 ||
                port > 65535) {

            return null;
        }

        Hello hello =
                new Hello();

        hello.nodeId =
                nodeId;

        hello.userId =
                userId;

        hello.username =
                username;

        hello.publicKey =
                publicKey;

        hello.port =
                port;

        return hello;
    }

    // =====================================================
    // Connection loop
    // =====================================================

    private void runConnection(
            PeerConnection connection
    ) {

        Log.d(
                TAG,
                "CONNECTION_LOOP_STARTED node=" +
                        connection.peer.nodeId
        );

        try {

            while (running &&
                    !connection.isClosed()) {

                int length;

                try {

                    length =
                            connection.input
                                    .readInt();

                } catch (IOException e) {

                    Log.d(
                            TAG,
                            "CONNECTION_READ_CLOSED node=" +
                                    connection.peer.nodeId +
                                    " message=" +
                                    e.getMessage()
                    );

                    break;
                }

                Log.d(
                        TAG,
                        "PACKET_LENGTH_RECEIVED node=" +
                                connection.peer.nodeId +
                                " bytes=" +
                                length
                );

                if (length <= 0 ||
                        length > MAX_PACKET_SIZE) {

                    throw new IOException(
                            "Invalid packet length"
                    );
                }

                byte[] data =
                        new byte[length];

                connection.input.readFully(
                        data
                );

                Log.d(
                        TAG,
                        "PACKET_RECEIVED node=" +
                                connection.peer.nodeId +
                                " bytes=" +
                                length
                );

                notifyReceived(
                        connection.peer.nodeId,
                        data
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "CONNECTION_LOOP_ERROR node=" +
                            connection.peer.nodeId,
                    e
            );

            notifyError(
                    connection.peer.nodeId,
                    e
            );

        } finally {

            Log.d(
                    TAG,
                    "CONNECTION_LOOP_END node=" +
                            connection.peer.nodeId
            );

            removeConnection(
                    connection
            );
        }
    }

    // =====================================================
    // Send to peer
    // =====================================================

    public boolean sendTo(
            String nodeId,
            byte[] data
    ) {

        if (nodeId == null) {

            Log.e(
                    TAG,
                    "SEND_TO_FAILED nodeId=null"
            );

            return false;
        }

        if (data == null ||
                data.length == 0) {

            Log.e(
                    TAG,
                    "SEND_TO_FAILED empty packet node=" +
                            nodeId
            );

            return false;
        }

        if (data.length > MAX_PACKET_SIZE) {

            Log.e(
                    TAG,
                    "SEND_TO_FAILED packet too large node=" +
                            nodeId
            );

            return false;
        }

        PeerConnection connection =
                connections.get(
                        nodeId
                );

        if (connection == null ||
                connection.isClosed()) {

            Log.e(
                    TAG,
                    "SEND_TO_FAILED no connection node=" +
                            nodeId
            );

            return false;
        }

        try {

            synchronized (connection.output) {

                connection.output.writeInt(
                        data.length
                );

                connection.output.write(
                        data
                );

                connection.output.flush();
            }

            Log.d(
                    TAG,
                    "PACKET_SENT node=" +
                            nodeId +
                            " bytes=" +
                            data.length
            );

            return true;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "SEND_EXCEPTION node=" +
                            nodeId,
                    e
            );

            removeConnection(
                    connection
            );

            notifyError(
                    nodeId,
                    e
            );

            return false;
        }
    }

    // =====================================================
    // Check connection
    // =====================================================

    public boolean isConnected(
            String nodeId
    ) {

        PeerConnection connection =
                connections.get(
                        nodeId
                );

        boolean result =
                connection != null &&
                        !connection.isClosed();

        Log.d(
                TAG,
                "IS_CONNECTED node=" +
                        nodeId +
                        " result=" +
                        result
        );

        return result;
    }

    // =====================================================
    // Queue size
    // =====================================================

    private int getQueueSize(
            String nodeId
    ) {

        ConcurrentLinkedQueue<byte[]> queue =
                pendingPackets.get(
                        nodeId
                );

        if (queue == null) {
            return 0;
        }

        return queue.size();
    }

    // =====================================================
    // Remove connection
    // =====================================================

    private void removeConnection(
            PeerConnection connection
    ) {

        if (connection == null) {
            return;
        }

        boolean removed =
                connections.remove(
                        connection.peer.nodeId,
                        connection
                );

        connection.close();

        if (removed) {

            Log.d(
                    TAG,
                    "CONNECTION_REMOVED node=" +
                            connection.peer.nodeId
            );

            notifyDisconnected(
                    connection.peer.nodeId
            );
        }
    }

    // =====================================================
    // Socket configuration
    // =====================================================

    private void configureSocket(
            Socket socket
    ) throws Exception {

        socket.setKeepAlive(
                true
        );

        socket.setTcpNoDelay(
                true
        );

        socket.setSoTimeout(
                0
        );
    }

    // =====================================================
    // Node ID
    // =====================================================

    private String getLocalNodeId() {

        try {

            return identityManager
                    .getNodeId();

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "GET_LOCAL_NODE_ID_FAILED",
                    e
            );

            return "";
        }
    }

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
    // Username
    // =====================================================

    private String normalizeUsername(
            String username
    ) {

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
    // Validation
    // =====================================================

    private boolean isValidString(
            String value
    ) {

        return value != null &&
                !value.isEmpty() &&
                value.length() <= MAX_STRING_LENGTH;
    }

    // =====================================================
    // Notifications
    // =====================================================

    private void notifyConnected(
            DhtPeer peer
    ) {

        Log.d(
                TAG,
                "NOTIFY_CONNECTED node=" +
                        peer.nodeId +
                        " user=" +
                        peer.username
        );

        if (listener != null) {

            listener.onConnected(
                    peer
            );
        }
    }

    private void notifyReceived(
            String nodeId,
            byte[] data
    ) {

        Log.d(
                TAG,
                "NOTIFY_RECEIVED node=" +
                        nodeId +
                        " bytes=" +
                        data.length
        );

        if (listener != null) {

            listener.onReceived(
                    nodeId,
                    data
            );
        }
    }

    private void notifyDisconnected(
            String nodeId
    ) {

        Log.d(
                TAG,
                "NOTIFY_DISCONNECTED node=" +
                        nodeId
        );

        if (listener != null) {

            listener.onDisconnected(
                    nodeId
            );
        }
    }

    private void notifyError(
            String nodeId,
            Exception exception
    ) {

        Log.e(
                TAG,
                "NOTIFY_ERROR node=" +
                        nodeId,
                exception
        );

        if (listener != null) {

            listener.onError(
                    nodeId,
                    exception
            );
        }
    }

    // =====================================================
    // Stop
    // =====================================================

    public synchronized void stop() {

        Log.d(
                TAG,
                "STOP"
        );

        running = false;

        try {

            if (serverSocket != null) {

                serverSocket.close();
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "SERVER_SOCKET_CLOSE_ERROR",
                    e
            );
        }

        for (PeerConnection connection :
                connections.values()) {

            connection.close();
        }

        connections.clear();

        pendingConnections.clear();

        pendingPackets.clear();

        if (acceptThread != null) {

            acceptThread.interrupt();

            acceptThread = null;
        }

        executor.shutdownNow();
    }

    // =====================================================
    // Close socket quietly
    // =====================================================

    private void closeQuietly(
            Socket socket
    ) {

        if (socket == null) {
            return;
        }

        try {

            socket.close();

        } catch (Exception ignored) {
        }
    }

    // =====================================================
    // HELLO model
    // =====================================================

    private static class Hello {

        String nodeId;

        String userId;

        String username;

        String publicKey;

        int port;
    }

    // =====================================================
    // Peer Connection
    // =====================================================

    private static class PeerConnection {

        final DhtPeer peer;

        final Socket socket;

        final DataInputStream input;

        final DataOutputStream output;

        PeerConnection(
                DhtPeer peer,
                Socket socket,
                DataInputStream input,
                DataOutputStream output
        ) {

            this.peer =
                    peer;

            this.socket =
                    socket;

            this.input =
                    input;

            this.output =
                    output;
        }

        boolean isClosed() {

            return socket.isClosed();
        }

        void close() {

            try {

                socket.close();

            } catch (Exception ignored) {
            }
        }
    }
}
