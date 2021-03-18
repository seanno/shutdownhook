
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.lang.InterruptedException;
import java.lang.Runtime;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Main
{
	private final static Logger log = Logger.getLogger(Main.class.getName());
		
	public static void main(String[] args) throws Exception {

		if (args.length != 3) {
			log.info("Usage: java -cp '*':. Main PORT THREADS DATADIR");
			return;
		}
		
		System.setProperty("java.util.logging.SimpleFormatter.format",
						   "[%1$tF %1$tT] [%4$-7s] %5$s %n");

		int port = Integer.parseInt(args[0]);
		int threads = Integer.parseInt(args[1]);

		Store store = new Store(args[2]);
		Radio radio = new Radio(store);

		trigger = new Object();
		pool = Executors.newFixedThreadPool(threads);
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.setExecutor(pool);

		server.createContext("/channel", new Handlers.GetChannel(radio));
		server.createContext("/playlist", new Handlers.GetPlaylist(radio));
		server.createContext("/addVideo", new Handlers.AddVideo(radio));
		server.createContext("/", new Handlers.Static(radio));
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				log.info("Shutting down");
				server.stop(0);
				pool.shutdown();
				pool.awaitTermination(30, TimeUnit.SECONDS);
				pool.shutdownNow();
				pool.awaitTermination(30, TimeUnit.SECONDS);
			}
			catch (Exception e) {
				// nothihg
			}
			finally {
				synchronized (trigger) { trigger.notify(); }
			}
		}));

		server.start();
		log.info("Listening for requests; ^C to exit");
		
		synchronized (trigger) {
			try { trigger.wait(); }
			catch (InterruptedException e) { /* nut-n-honey */ }
		}
	}

	private static Object trigger;
	private static ExecutorService pool;
	private static HttpServer server;
}
