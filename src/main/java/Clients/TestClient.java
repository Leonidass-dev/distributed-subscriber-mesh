package Clients;

import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.SubscriberProto.Status;

public class TestClient {
    public static void main(String[] args) {
        try {
            System.out.println("Test başlatılıyor...");
            Thread.sleep(2000);
            
            // İlk istemci - Abone olma (SUBS)
            System.out.println("Client1 oluşturuluyor...");
            Client client1 = new Client("test-client-1");
            System.out.println("Client1 Server1'e bağlanmaya çalışıyor...");
            if (client1.connect(1)) {
                Subscriber subscriber = Subscriber.newBuilder()
                    .setStatus(Status.SUBS)
                    .setNameSurname("John DOE")
                    .setLastAccessed(System.currentTimeMillis())
                    .build();
                
                if (client1.subscribe(subscriber)) {
                    System.out.println("Client1 başarıyla abone oldu");
                }
            }

            Thread.sleep(1000);

            // İkinci istemci - Online olma (ONLN)
            System.out.println("\nClient2 oluşturuluyor...");
            Client client2 = new Client("test-client-2");
            System.out.println("Client2 Server2'ye bağlanmaya çalışıyor...");
            if (client2.connect(2)) {
                Subscriber subscriber = Subscriber.newBuilder()
                    .setStatus(Status.ONLN)
                    .setNameSurname("Jane DOE")
                    .setLastAccessed(System.currentTimeMillis())
                    .build();
                
                if (client2.subscribe(subscriber)) {
                    System.out.println("Client2 başarıyla online oldu");
                }
            }

            // Biraz bekle ve client2'yi offline yap
            Thread.sleep(2000);
            if (client2.isConnected()) {
                Subscriber offlineRequest = Subscriber.newBuilder()
                    .setStatus(Status.OFFL)
                    .setNameSurname("Jane DOE")
                    .setLastAccessed(System.currentTimeMillis())
                    .build();
                
                if (client2.subscribe(offlineRequest)) {
                    System.out.println("Client2 başarıyla offline oldu");
                }
            }

            // Bağlantıları kapat
            client1.close();
            client2.close();
            System.out.println("\nTest tamamlandı.");
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
} 