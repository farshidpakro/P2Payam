package ir.p2payam.app;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.StandardCharsets;

public class NetworkManager {

    private static final String TAG =
            "P2PAYAM_NETWORK";


    // =====================================================
    // IMPORTANT
    // =====================================================

    private static final String SERVICE_ID =
            "ir.p2payam.app";


    private final Context context;

    private final ConnectionsClient connectionsClient;

    private final String localNodeId;


    public NetworkManager(
            Context context,
            String localNodeId
    ) {

        this.context =
                context.getApplicationContext();

        this.localNodeId =
                localNodeId;

        this.connectionsClient =
                Nearby.getConnectionsClient(
                        this.context
                );
    }


    // =====================================================
    // Start Advertising
    // =====================================================

    public void startAdvertising() {

        AdvertisingOptions options =
                new AdvertisingOptions.Builder()

                        .setStrategy(
                                Strategy.P2P_POINT_TO_POINT
                        )

                        .build();


        connectionsClient
                .startAdvertising(

                        localNodeId,

                        SERVICE_ID,

                        connectionLifecycleCallback,

                        options

                )

                .addOnSuccessListener(
                        unused -> {

                            Log.d(
                                    TAG,
                                    "Advertising started"
                            );
                        }
                )

                .addOnFailureListener(
                        e -> {

                            Log.e(
                                    TAG,
                                    "Advertising failed",
                                    e
                            );
                        }
                );
    }


    // =====================================================
    // Start Discovery
    // =====================================================

    public void startDiscovery() {

        DiscoveryOptions options =
                new DiscoveryOptions.Builder()

                        .setStrategy(
                                Strategy.P2P_POINT_TO_POINT
                        )

                        .build();


        connectionsClient
                .startDiscovery(

                        SERVICE_ID,

                        endpointDiscoveryCallback,

                        options

                )

                .addOnSuccessListener(
                        unused -> {

                            Log.d(
                                    TAG,
                                    "Discovery started"
                            );
                        }
                )

                .addOnFailureListener(
                        e -> {

                            Log.e(
                                    TAG,
                                    "Discovery failed",
                                    e
                            );
                        }
                );
    }


    // =====================================================
    // Stop
    // =====================================================

    public void stop() {

        connectionsClient
                .stopAdvertising();

        connectionsClient
                .stopDiscovery();

        connectionsClient
                .stopAllEndpoints();
    }


    // =====================================================
    // Endpoint Discovery
    // =====================================================

    private final EndpointDiscoveryCallback
            endpointDiscoveryCallback =

            new EndpointDiscoveryCallback() {

                @Override
                public void onEndpointFound(
                        String endpointId,
                        DiscoveredEndpointInfo info
                ) {

                    Log.d(
                            TAG,
                            "Endpoint found: " +
                                    endpointId
                    );


                    Log.d(
                            TAG,
                            "Remote name: " +
                                    info.getEndpointName()
                    );


                    // -------------------------------------
                    // Connect
                    // -------------------------------------

                    connectionsClient
                            .requestConnection(

                                    localNodeId,

                                    endpointId,

                                    connectionLifecycleCallback

                            )

                            .addOnSuccessListener(
                                    unused -> {

                                        Log.d(
                                                TAG,
                                                "Connection request sent"
                                        );
                                    }
                            )

                            .addOnFailureListener(
                                    e -> {

                                        Log.e(
                                                TAG,
                                                "Connection request failed",
                                                e
                                        );
                                    }
                            );
                }


                @Override
                public void onEndpointLost(
                        String endpointId
                ) {

                    Log.d(
                            TAG,
                            "Endpoint lost: " +
                                    endpointId
                    );
                }
            };


    // =====================================================
    // Connection Lifecycle
    // =====================================================

    private final ConnectionLifecycleCallback
            connectionLifecycleCallback =

            new ConnectionLifecycleCallback() {

                @Override
                public void onConnectionInitiated(

                        String endpointId,

                        ConnectionInfo connectionInfo

                ) {

                    Log.d(
                            TAG,
                            "Connection initiated: " +
                                    endpointId
                    );


                    Log.d(
                            TAG,
                            "Remote name: " +
                                    connectionInfo
                                            .getEndpointName()
                    );


                    Log.d(
                            TAG,
                            "Authentication token: " +
                                    connectionInfo
                                            .getAuthenticationToken()
                    );


                    // -------------------------------------
                    // TEMPORARY
                    //
                    // For first network test:
                    // automatically accept.
                    // -------------------------------------

                    connectionsClient
                            .acceptConnection(

                                    endpointId,

                                    payloadCallback
                            );
                }


                @Override
                public void onConnectionResult(

                        String endpointId,

                        ConnectionResolution result

                ) {

                    Log.d(
                            TAG,
                            "Connection result: " +
                                    endpointId
                    );


                    Log.d(
                            TAG,
                            "Status: " +
                                    result.getStatus()
                                            .getStatusCode()
                    );


                    if (
                            result.getStatus()
                                    .isSuccess()
                    ) {

                        Log.d(
                                TAG,
                                "CONNECTED!"
                        );


                        sendTestMessage(
                                endpointId
                        );
                    }
                }


                @Override
                public void onDisconnected(
                        String endpointId
                ) {

                    Log.d(
                            TAG,
                            "Disconnected: " +
                                    endpointId
                    );
                }
            };


    // =====================================================
    // Payload Callback
    // =====================================================

    private final PayloadCallback
            payloadCallback =

            new PayloadCallback() {

                @Override
                public void onPayloadReceived(

                        String endpointId,

                        Payload payload

                ) {

                    if (
                            payload.getType()
                                    != Payload.Type.BYTES
                    ) {

                        return;
                    }


                    byte[] bytes =
                            payload
                                    .asBytes();


                    if (bytes == null) {

                        return;
                    }


                    String message =
                            new String(
                                    bytes,
                                    StandardCharsets.UTF_8
                            );


                    Log.d(
                            TAG,
                            "MESSAGE RECEIVED: " +
                                    message
                    );
                }


                @Override
                public void onPayloadTransferUpdate(

                        String endpointId,

                        PayloadTransferUpdate update

                ) {

                    if (
                            update.getStatus()
                                    == PayloadTransferUpdate.Status.SUCCESS
                    ) {

                        Log.d(
                                TAG,
                                "Payload transfer complete"
                        );
                    }
                }
            };


    // =====================================================
    // TEST MESSAGE
    // =====================================================

    private void sendTestMessage(
            String endpointId
    ) {

        String text =
                "HELLO FROM P2PAYAM";


        Payload payload =
                Payload.fromBytes(
                        text.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        connectionsClient
                .sendPayload(
                        endpointId,
                        payload
                )

                .addOnSuccessListener(
                        unused -> {

                            Log.d(
                                    TAG,
                                    "TEST MESSAGE SENT"
                            );
                        }
                )

                .addOnFailureListener(
                        e -> {

                            Log.e(
                                    TAG,
                                    "SEND FAILED",
                                    e
                            );
                        }
                );
    }
}