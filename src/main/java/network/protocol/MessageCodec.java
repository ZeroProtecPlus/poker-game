package network.protocol;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class MessageCodec {

    private MessageCodec() {}

    /**
     * @deprecated Use {@link #send(Socket, BufferedWriter, LanEnvelope)} with a cached writer
     *             to avoid allocating a new {@link BufferedWriter} on every call.
     */
    @Deprecated
    public static void send(Socket socket, LanEnvelope envelope) throws IOException {
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
        );
        send(socket, writer, envelope);
    }

    /** Send using a pre-opened BufferedWriter to avoid per-message allocation. */
    public static void send(Socket socket, BufferedWriter writer, LanEnvelope envelope) throws IOException {
        synchronized (socket) {
            writer.write(envelope.toJson().toString());
            writer.write('\n');
            writer.flush();
        }
    }

    public static BufferedWriter openWriter(Socket socket) throws IOException {
        return new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
        );
    }

    public static LanEnvelope receive(Socket socket, BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.isBlank()) {
            throw new IOException("connection closed");
        }
        return LanEnvelope.fromJson(new JSONObject(line));
    }

    public static BufferedReader openReader(Socket socket) throws IOException {
        return new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
    }
}
