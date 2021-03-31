/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

import java.io.OutputStream;
import java.lang.Runtime;
import java.lang.Thread;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeBot
{
	private static int LISTEN_PORT = 9999;
	private static int POOL_SIZE = 3;
	
	public static void main(String[] args) throws Exception {

		System.out.println("^C to exit");

		ServerSocket socket = new ServerSocket(LISTEN_PORT);
		ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				System.out.println("Shutting down");
				pool.shutdown();
				pool.awaitTermination(5, TimeUnit.SECONDS);
				pool.shutdownNow();
				pool.awaitTermination(5, TimeUnit.SECONDS);
			}
			catch (Exception e) {
				// nothihg
			}
		}));

		while (true) {
			pool.execute(new ResponseBot(socket.accept()));
		}
	}

	public static class ResponseBot implements Runnable
	{
		public ResponseBot(Socket socket) {
			this.socket = socket;
		}

		public void run() {

			try {
				System.out.println("Handling " + socket.getRemoteSocketAddress().toString());
				OutputStream stream = socket.getOutputStream();

				for (int i = 0; i < 10; ++i) {
					stream.write(new Date().toString().getBytes());
					stream.write("\n".getBytes());
					stream.flush();
					Thread.sleep(2000);
				}
				
				stream.close();
				socket.close();
			}
			catch (Exception e) {
				// nothing
			}
		}

		private Socket socket;
	}
}
