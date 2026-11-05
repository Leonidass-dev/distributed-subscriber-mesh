package dist_servers;

import com.hasup.proto.CapacityProto.Capacity;
import com.hasup.proto.ConfigurationProto.Configuration;
import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.SubscriberProto.Status;
import com.hasup.proto.MessageProto.Message;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core implementation of a Fault-Tolerant Distributed Server node.
 * Handles Admin connections, Client subscriptions, and Peer-to-Peer synchronization.
 */
public class DistributedServer implements ServerInterface {
    protected final int serverId;
    protected final int adminPort;
    protected final int clientPort;
    protected final int peerPort;

    protected Configuration config;
    protected final ConcurrentHashMap<String, Subscriber> subscribers;
    protected final ReentrantLock subscriberLock;
    protected volatile boolean isRunning;
    protected final ConcurrentHashMap<Integer, Socket> peerConnections;
    protected final CountDownLatch shutdownLatch = new CountDownLatch(1);
    protected final AtomicInteger nextId = new AtomicInteger(1);

    public DistributedServer(int serverId, int adminPort, int clientPort, int peerPort) {
        this.serverId = serverId;
        this.adminPort = adminPort;
        this.clientPort = clientPort;
        this.peerPort = peerPort;
        this.subscribers = new ConcurrentHashMap<>();
        this.subscriberLock = new ReentrantLock();
        this.peerConnections = new ConcurrentHashMap<>();
    }

    public void start() {
        this.isRunning = true;
        System.out.println("==================================================");
        System.out.printf("Starting DistributedServer #%d%n", serverId);
        System.out.printf("  Admin Port:  %d%n", adminPort);
        System.out.printf("  Client Port: %d%n", clientPort);
        System.out.printf("  Peer Port:   %d%n", peerPort);
        System.out.println("==================================================");

        // Admin connection listener thread
        CompletableFuture.runAsync(() -> {
            try (ServerSocket adminSocket = new ServerSocket(adminPort)) {
                System.out.printf("[Server %d] Admin listener active on port %d%n", serverId, adminPort);
                while (isRunning) {
                    Socket socket = adminSocket.accept();
                    new Thread(new AdminHandler(socket, this)).start();
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.printf("[Server %d] Admin socket error: %s%n", serverId, e.getMessage());
                }
            }
        });

        // Client connection listener thread
        CompletableFuture.runAsync(() -> {
            try (ServerSocket clientSocket = new ServerSocket(clientPort)) {
                System.out.printf("[Server %d] Client listener active on port %d%n", serverId, clientPort);
                while (isRunning) {
                    Socket socket = clientSocket.accept();
                    new Thread(new ClientHandler(socket, this)).start();
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.printf("[Server %d] Client socket error: %s%n", serverId, e.getMessage());
                }
            }
        });

        // Peer-to-Peer replication listener thread
        CompletableFuture.runAsync(() -> {
            try (ServerSocket peerSocket = new ServerSocket(peerPort)) {
                System.out.printf("[Server %d] Peer replication listener active on port %d%n", serverId, peerPort);
                while (isRunning) {
                    Socket socket = peerSocket.accept();
                    CompletableFuture.runAsync(() -> handleIncomingPeer(socket));
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.printf("[Server %d] Peer socket error: %s%n", serverId, e.getMessage());
                }
            }
        });
    }

    protected void handleIncomingPeer(Socket peerSocket) {
        try {
            InputStream input = peerSocket.getInputStream();
            while (isRunning && !peerSocket.isClosed()) {
                Message message = Message.parseDelimitedFrom(input);
                if (message != null && message.getType() == Message.Type.SYNC) {
                    Subscriber subscriber = Subscriber.parseFrom(message.getPayload());
                    updateSubscriber(subscriber);
                    System.out.printf("[Server %d] Replicated subscriber from Peer: %s (ID: %d)%n",
                            serverId, subscriber.getNameSurname(), subscriber.getId());
                }
            }
        } catch (IOException e) {
            // Peer disconnected cleanly or interrupted
        }
    }

    public void stop() {
        this.isRunning = false;
        shutdownLatch.countDown();
    }

    public void waitForShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int getServerId() {
        return serverId;
    }

    public int getAdminPort() { return adminPort; }
    public int getClientPort() { return clientPort; }
    public int getPeerPort() { return peerPort; }

    @Override
    public Capacity getCapacity() {
        return Capacity.newBuilder()
            .setServerId(serverId)
            .setServerStatus(getActiveSubscriberCount())
            .setTimestamp(System.currentTimeMillis())
            .build();
    }

    @Override
    public boolean addSubscriber(Subscriber subscriber) {
        subscriberLock.lock();
        try {
            String subId = String.valueOf(subscriber.getId());
            if (!subscribers.containsKey(subId)) {
                subscribers.put(subId, subscriber);
                syncWithPeers(subscriber);
                return true;
            }
            return false;
        } finally {
            subscriberLock.unlock();
        }
    }

    @Override
    public void setConfiguration(Configuration config) {
        this.config = config;
        if (config.getFaultTolerance() > 0) {
            CompletableFuture.runAsync(this::connectToPeers);
        }
    }

    protected void connectToPeers() {
        if (config == null) return;
        int ft = config.getFaultTolerance();
        
        for (int id = 1; id <= 3; id++) {
            if (id != serverId && ft >= 1) {
                connectToPeer(id, "localhost", 5000 + id);
            }
        }
    }

    protected void connectToPeer(int peerId, String host, int port) {
        try {
            Socket peerSocket = new Socket(host, port);
            peerConnections.put(peerId, peerSocket);
            CompletableFuture.runAsync(() -> handleIncomingPeer(peerSocket));
            System.out.printf("[Server %d] Successfully linked to Peer #%d (%s:%d)%n", serverId, peerId, host, port);
        } catch (IOException e) {
            System.err.printf("[Server %d] Link to Peer #%d pending (%s:%d)%n", serverId, peerId, host, port);
        }
    }

    protected void updateSubscriber(Subscriber subscriber) {
        subscriberLock.lock();
        try {
            subscribers.put(String.valueOf(subscriber.getId()), subscriber);
        } finally {
            subscriberLock.unlock();
        }
    }

    protected void syncWithPeers(Subscriber subscriber) {
        if (config != null && config.getFaultTolerance() > 0) {
            Message syncMessage = Message.newBuilder()
                .setType(Message.Type.SYNC)
                .setSenderId(String.valueOf(serverId))
                .setPayload(subscriber.toByteString())
                .setTimestamp(System.currentTimeMillis())
                .build();

            for (Map.Entry<Integer, Socket> peer : peerConnections.entrySet()) {
                sendToPeer(peer.getKey(), syncMessage);
            }
        }
    }

    protected void sendToPeer(int peerId, Message message) {
        Socket peerSocket = peerConnections.get(peerId);
        if (peerSocket != null && !peerSocket.isClosed()) {
            try {
                message.writeDelimitedTo(peerSocket.getOutputStream());
                peerSocket.getOutputStream().flush();
            } catch (IOException e) {
                System.err.printf("[Server %d] Sync error to Peer #%d: %s%n", serverId, peerId, e.getMessage());
                peerConnections.remove(peerId);
            }
        }
    }

    @Override
    public int getSubscriberCount() {
        return subscribers.size();
    }

    @Override
    public int getActiveSubscriberCount() {
        return (int) subscribers.values().stream()
            .filter(s -> s.getStatus() == Status.ONLINE)
            .count();
    }

    public static void main(String[] args) {
        int id = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        int adminPort = 7000 + id;
        int clientPort = 6000 + id;
        int peerPort = 5000 + id;

        DistributedServer server = new DistributedServer(id, adminPort, clientPort, peerPort);
        server.start();
        server.waitForShutdown();
    }
}
