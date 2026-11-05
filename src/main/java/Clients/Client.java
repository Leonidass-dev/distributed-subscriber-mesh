package Clients;

import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.SubscriberProto.Status;
import com.hasup.proto.MessageProto.Message;

import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static final Map<Integer, ServerInfo> SERVERS = new HashMap<>();
    static {
        SERVERS.put(1, new ServerInfo("localhost", 6001));
        SERVERS.put(2, new ServerInfo("localhost", 6002));
        SERVERS.put(3, new ServerInfo("localhost", 6003));
    }
    
    private final String clientId;
    private Socket currentSocket;
    private int currentServerId;
    private InputStream input;
    private OutputStream output;
    private boolean connected;
    
    public Client(String clientId) {
        this.clientId = clientId;
    }
    
    // Sunucuya bağlan
    public boolean connect(int serverId) {
        ServerInfo server = SERVERS.get(serverId);
        if (server == null) return false;
        
        try {
            if (currentSocket != null) {
                currentSocket.close();
            }
            
            currentSocket = new Socket(server.host, server.port);
            input = currentSocket.getInputStream();
            output = currentSocket.getOutputStream();
            currentServerId = serverId;
            this.connected = true;
            
            System.out.println("Sunucu " + serverId + "'e bağlanıldı");
            return true;
        } catch (IOException e) {
            System.err.println("Sunucuya bağlanılamadı: " + e.getMessage());
            this.connected = false;
            return false;
        }
    }
    
    // Başka bir sunucuya geç
    private boolean switchServer() {
        List<Integer> otherServers = new ArrayList<>(SERVERS.keySet());
        otherServers.remove(currentServerId);
        
        for (int serverId : otherServers) {
            if (connect(serverId)) {
                return true;
            }
        }
        return false;
    }
    
    // Abone ol
    public boolean subscribe(Subscriber subscriber) {
        try {
            // Subscriber nesnesini doğrudan gönder
            subscriber.writeDelimitedTo(output);
            
            // Yanıtı bekle
            Subscriber response = Subscriber.parseDelimitedFrom(input);
            
            if (response != null) {
                System.out.println("İşlem başarılı: " + response.getNameSurname() + 
                    " (ID: " + response.getId() + ", Status: " + response.getStatus() + ")");
                return true;
            }
            
            System.out.println("İşlem başarısız");
            return false;
        } catch (IOException e) {
            System.err.println("İletişim hatası: " + e.getMessage());
            
            // Sunucu hatası durumunda başka sunucuya geç
            if (switchServer()) {
                return subscribe(subscriber);
            }
            return false;
        }
    }
    
    // Bağlantıyı kapat
    public void close() {
        try {
            if (currentSocket != null) {
                currentSocket.close();
                this.connected = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Yardımcı sınıf
    private static class ServerInfo {
        String host;
        int port;
        
        ServerInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
    
    // Test için main metodu
    public static void main(String[] args) {
        Client client = new Client("test-client-1");
        
        if (client.connect(1)) {
            Subscriber subscriber = Subscriber.newBuilder()
                .setStatus(Status.SUBS)
                .setNameSurname("Test User")
                .setLastAccessed(System.currentTimeMillis())
                .build();
            client.subscribe(subscriber);
        }
        
        client.close();
    }
    
    public boolean isConnected() {
        return this.connected;
    }
    
    public String getClientId() {
        return this.clientId;
    }
}
