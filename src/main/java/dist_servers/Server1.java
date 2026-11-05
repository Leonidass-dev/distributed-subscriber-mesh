package dist_servers;

/**
 * Server Node 1 implementation.
 * Listens on Admin: 7001, Client: 6001, Peer: 5001.
 */
public class Server1 extends DistributedServer {
    public Server1() {
        super(1, 7001, 6001, 5001);
    }

    public static void main(String[] args) {
        Server1 server = new Server1();
        server.start();
        server.waitForShutdown();
    }
}
