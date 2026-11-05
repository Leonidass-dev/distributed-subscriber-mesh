package dist_servers;

/**
 * Server Node 3 implementation.
 * Listens on Admin: 7003, Client: 6003, Peer: 5003.
 */
public class Server3 extends DistributedServer {
    public Server3() {
        super(3, 7003, 6003, 5003);
    }

    public static void main(String[] args) {
        Server3 server = new Server3();
        server.start();
        server.waitForShutdown();
    }
}
