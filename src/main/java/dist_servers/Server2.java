package dist_servers;

/**
 * Server Node 2 implementation.
 * Listens on Admin: 7002, Client: 6002, Peer: 5002.
 */
public class Server2 extends DistributedServer {
    public Server2() {
        super(2, 7002, 6002, 5002);
    }

    public static void main(String[] args) {
        Server2 server = new Server2();
        server.start();
        server.waitForShutdown();
    }
}
