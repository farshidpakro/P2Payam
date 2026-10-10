package ir.p2payam.app;

public interface Transport {

    void start();

    void stop();

    void send(
            byte[] data
    );

    boolean isConnected();
}