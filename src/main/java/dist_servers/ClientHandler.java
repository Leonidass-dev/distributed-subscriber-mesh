package dist_servers;

import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.MessageProto.Message;
import java.io.*;
import java.net.Socket;
import com.hasup.proto.SubscriberProto.Status;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerInterface server;
    private static final AtomicInteger idGenerator = new AtomicInteger(1);

    public ClientHandler(Socket socket, ServerInterface server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            while (!socket.isClosed()) {
                // İstemciden gelen Subscriber nesnesini oku
                Subscriber subscriber = Subscriber.parseDelimitedFrom(input);
                
                if (subscriber != null) {
                    handleSubscriberRequest(subscriber);
                }
            }
        } catch (IOException e) {
            System.err.println("Client bağlantısı koptu: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleSubscriberRequest(Subscriber request) throws IOException {
        System.out.println("Server" + server.getServerId() + ": Yeni istek alındı - " + request.getNameSurname());
        
        // Yeni ID ata ve son erişim zamanını güncelle
        Subscriber subscriber = Subscriber.newBuilder()
            .mergeFrom(request)
            .setId(generateNextId())  // ID üretme metodu eklenecek
            .setLastAccessed(System.currentTimeMillis())
            .setLastServerId(String.valueOf(server.getServerId()))
            .build();
        
        boolean success = server.addSubscriber(subscriber);
        
        // Yanıt gönder
        Subscriber response = Subscriber.newBuilder()
            .mergeFrom(subscriber)
            .setStatus(success ? request.getStatus() : Status.UNKNOWN)
            .build();
            
        response.writeDelimitedTo(socket.getOutputStream());
    }

    private int generateNextId() {
        return idGenerator.getAndIncrement();
    }
} 