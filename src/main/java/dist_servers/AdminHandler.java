package dist_servers;

import com.hasup.proto.CapacityProto.Capacity;
import com.hasup.proto.ConfigurationProto.Configuration;
import java.io.*;
import java.net.Socket;

public class AdminHandler implements Runnable {
    private final Socket socket;
    private final ServerInterface server;

    public AdminHandler(Socket socket, ServerInterface server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            while (!socket.isClosed()) {
                // Ruby istemciden gelen 4 byte uzunluk bilgisini oku
                byte[] lengthBytes = new byte[4];
                input.read(lengthBytes);
                int length = java.nio.ByteBuffer.wrap(lengthBytes).getInt();
                
                // Mesaj içeriğini oku
                byte[] messageBytes = new byte[length];
                input.read(messageBytes);
                
                // Mesaj tipini belirle (ilk byte)
                if (messageBytes[0] == 1) {  // Capacity request
                    handleCapacityRequest(messageBytes);
                } else if (messageBytes[0] == 2) {  // Configuration request
                    handleConfigurationRequest(messageBytes);
                }
            }
        } catch (IOException e) {
            System.err.println("Admin bağlantısı koptu: " + e.getMessage());
        }
    }
    
    private void handleCapacityRequest(byte[] data) throws IOException {
        Capacity request = Capacity.parseFrom(data);
        if (request.getServerId() == server.getServerId()) {
            // Aktif (ONLN) abonelerin sayısını al
            int activeCount = server.getActiveSubscriberCount();
            
            Capacity response = Capacity.newBuilder()
                .setServerId(server.getServerId())
                .setServerStatus(activeCount)
                .setTimestamp(System.currentTimeMillis())
                .build();
            
            byte[] responseBytes = response.toByteArray();
            socket.getOutputStream().write(java.nio.ByteBuffer.allocate(4).putInt(responseBytes.length).array());
            socket.getOutputStream().write(responseBytes);
        }
    }
    
    private void handleConfigurationRequest(byte[] data) throws IOException {
        Configuration config = Configuration.parseFrom(data);
        if (config.getServerId() == server.getServerId()) {
            server.setConfiguration(config);
            System.out.println("Server" + server.getServerId() + ": Yeni konfigürasyon ayarlandı");
        }
    }
} 