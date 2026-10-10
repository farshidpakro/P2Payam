package ir.p2payam.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

public class MessagePacketCodec {

    private static final int MAX_PACKET_SIZE =
            1024 * 1024;

    private static final int MAX_STRING_LENGTH =
            1024;


    // =====================================================
    // Encode complete packet
    // =====================================================

    public static byte[] encode(
            MessagePacket packet
    ) throws Exception {

        if (packet == null) {

            throw new IllegalArgumentException(
                    "Packet is null"
            );
        }


        byte[] signedData =
                encodeForSigning(
                        packet
                );


        ByteArrayOutputStream byteStream =
                new ByteArrayOutputStream();


        DataOutputStream output =
                new DataOutputStream(
                        byteStream
                );


        /*
         * Signed section
         */
        output.writeInt(
                signedData.length
        );

        output.write(
                signedData
        );


        /*
         * Routing metadata.
         *
         * NOT signed.
         */
        output.writeByte(
                packet.hopLimit
        );


        /*
         * Signature
         */
        if (packet.signature == null) {

            output.writeInt(
                    0
            );

        } else {

            output.writeInt(
                    packet.signature.length
            );

            output.write(
                    packet.signature
            );
        }


        output.flush();


        byte[] result =
                byteStream.toByteArray();


        if (result.length >
                MAX_PACKET_SIZE) {

            throw new IllegalArgumentException(
                    "Packet too large"
            );
        }


        return result;
    }


    // =====================================================
    // Data used for signature
    // =====================================================

    public static byte[] encodeForSigning(
            MessagePacket packet
    ) throws Exception {

        ByteArrayOutputStream byteStream =
                new ByteArrayOutputStream();


        DataOutputStream output =
                new DataOutputStream(
                        byteStream
                );


        output.writeInt(
                packet.version
        );


        output.writeInt(
                packet.type
        );


        writeString(
                output,
                packet.messageId
        );


        writeString(
                output,
                packet.ackMessageId
        );


        /*
         * User identities
         */
        writeString(
                output,
                packet.senderUserId
        );


        writeString(
                output,
                packet.recipientUserId
        );


        /*
         * Usernames are also signed so a node
         * cannot modify the displayed sender or
         * recipient name without invalidating
         * the packet signature.
         */
        writeString(
                output,
                packet.senderUsername
        );


        writeString(
                output,
                packet.recipientUsername
        );


        /*
         * Cryptographic public key
         */
        writeString(
                output,
                packet.senderPublicKey
        );


        output.writeLong(
                packet.createdAt
        );


        output.writeLong(
                packet.expiresAt
        );


        writeString(
                output,
                packet.encryptedPayload
        );


        /*
         * IMPORTANT:
         *
         * hopLimit is intentionally NOT written here.
         *
         * Every forwarding node is allowed to decrease
         * hopLimit without breaking the signature.
         */


        output.flush();


        return byteStream.toByteArray();
    }


    // =====================================================
    // Decode packet
    // =====================================================

    public static MessagePacket decode(
            byte[] data
    ) throws Exception {

        if (data == null ||
                data.length == 0 ||
                data.length > MAX_PACKET_SIZE) {

            throw new IllegalArgumentException(
                    "Invalid packet"
            );
        }


        ByteArrayInputStream byteStream =
                new ByteArrayInputStream(
                        data
                );


        DataInputStream input =
                new DataInputStream(
                        byteStream
                );


        int signedDataLength =
                input.readInt();


        if (signedDataLength <= 0 ||
                signedDataLength >
                        MAX_PACKET_SIZE) {

            throw new IllegalArgumentException(
                    "Invalid signed data length"
            );
        }


        if (signedDataLength >
                input.available()) {

            throw new IllegalArgumentException(
                    "Truncated packet"
            );
        }


        byte[] signedData =
                new byte[signedDataLength];


        input.readFully(
                signedData
        );


        MessagePacket packet =
                decodeSignedData(
                        signedData
                );


        /*
         * Routing metadata.
         */
        int hopLimit =
                input.readUnsignedByte();


        packet.hopLimit =
                hopLimit;


        /*
         * Signature.
         */
        int signatureLength =
                input.readInt();


        if (signatureLength < 0 ||
                signatureLength >
                        input.available() ||
                signatureLength >
                        MAX_PACKET_SIZE) {

            throw new IllegalArgumentException(
                    "Invalid signature length"
            );
        }


        if (signatureLength > 0) {

            byte[] signature =
                    new byte[signatureLength];


            input.readFully(
                    signature
            );


            packet.signature =
                    signature;

        } else {

            packet.signature =
                    null;
        }


        return packet;
    }


    // =====================================================
    // Decode signed data
    // =====================================================

    private static MessagePacket decodeSignedData(
            byte[] data
    ) throws Exception {

        ByteArrayInputStream byteStream =
                new ByteArrayInputStream(
                        data
                );


        DataInputStream input =
                new DataInputStream(
                        byteStream
                );


        MessagePacket packet =
                new MessagePacket();


        packet.version =
                input.readInt();


        if (packet.version <= 0 ||
                packet.version > 100) {

            throw new IllegalArgumentException(
                    "Unsupported packet version"
            );
        }


        packet.type =
                input.readInt();


        if (packet.type <
                MessagePacket.TYPE_HELLO ||
                packet.type >
                        MessagePacket.TYPE_ACK) {

            throw new IllegalArgumentException(
                    "Invalid packet type"
            );
        }


        packet.messageId =
                readString(
                        input
                );


        packet.ackMessageId =
                readString(
                        input
                );


        /*
         * User IDs
         */
        packet.senderUserId =
                readString(
                        input
                );


        packet.recipientUserId =
                readString(
                        input
                );


        /*
         * Usernames
         */
        packet.senderUsername =
                readString(
                        input
                );


        packet.recipientUsername =
                readString(
                        input
                );


        /*
         * Sender public key
         */
        packet.senderPublicKey =
                readString(
                        input
                );


        packet.createdAt =
                input.readLong();


        packet.expiresAt =
                input.readLong();


        packet.encryptedPayload =
                readString(
                        input
                );


        return packet;
    }


    // =====================================================
    // Write String
    // =====================================================

    private static void writeString(
            DataOutputStream output,
            String value
    ) throws Exception {

        if (value == null) {

            output.writeInt(
                    -1
            );

            return;
        }


        byte[] bytes =
                value.getBytes(
                        StandardCharsets.UTF_8
                );


        if (bytes.length >
                MAX_STRING_LENGTH) {

            throw new IllegalArgumentException(
                    "String too long"
            );
        }


        output.writeInt(
                bytes.length
        );


        output.write(
                bytes
        );
    }


    // =====================================================
    // Read String
    // =====================================================

    private static String readString(
            DataInputStream input
    ) throws Exception {

        int length =
                input.readInt();


        if (length == -1) {

            return null;
        }


        if (length < 0 ||
                length > MAX_STRING_LENGTH) {

            throw new IllegalArgumentException(
                    "Invalid string length"
            );
        }


        if (length >
                input.available()) {

            throw new IllegalArgumentException(
                    "Truncated string"
            );
        }


        byte[] bytes =
                new byte[length];


        input.readFully(
                bytes
        );


        return new String(
                bytes,
                StandardCharsets.UTF_8
        );
    }
}