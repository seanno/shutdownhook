/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/


import java.util.ArrayList;
import java.util.Scanner;

public class Cleanup3
{
	// args[0] not present or == 0: be a good citizen
	// 1: respond to interrupt
	// 2: ignore all shutdown asks
	
	public static void main(String[] args) throws Exception {

		System.out.println("Starting worker...");
		int rudeness = (args.length == 0 ? 0 : Integer.parseInt(args[0]));
		TriangleWorker worker = new TriangleWorker(rudeness);
		worker.go();
		
		System.out.println("Press enter for report, x + enter to exit");
		Scanner scanner = new Scanner(System.in);
		while (!scanner.nextLine().equals("x")) worker.report();
		
		System.out.println("Exiting...");
		if (!worker.waitForStop()) {
			System.out.println("I mean it folks this is bad. \u0007\u0007\u0007");
		}
	}
	
	public static abstract class Worker extends Thread {

		// +-----------------+
		// | For Imlementors |
		// +-----------------+

		abstract void work() throws Exception;
		abstract void cleanup(Exception e);

		protected synchronized boolean shouldStop() { return(stop); }

		// +-------------+
		// | For Callers |
		// +-------------+
		
		public void go() {
			setDaemon(true);
			start();
		}
		
		public synchronized void signalStop() {
			stop = true;
		}

		public boolean waitForStop() {
			return(waitForStop(DEFAULT_WAIT_SECONDS));
		}
		
		public boolean waitForStop(int waitSeconds) {

			System.out.println("Waiting for clean shutdown...");
			signalStop(); // just in case
			safeJoin(waitSeconds * 1000 / 2); 
			if (isAlive()) {

				System.out.println("Stop ignored, sending interrupt...");
				interrupt();
				safeJoin(waitSeconds * 1000 / 2);
				if (isAlive()) {

					// BETTER THAN NOTHING
					System.out.println("SHUTDOWN FAILURE; thread will be killed!");
					return(false);
				}
			}

			return(true);
		}

		private void safeJoin(int waitSeconds) {
			try { join(waitSeconds); }
			catch (InterruptedException e) { }
		}

		// +----------+
		// | Internal |
		// +----------+
		
		public void run() {

			Exception e = null;
			
			try {
				work();
			}
			catch (InterruptedException ie) {
				// nothing
			}
			catch (Exception ex) {
				e = ex;
			}
			finally {
				cleanup(e);
			}
		}

		private boolean stop = false;
		private static int DEFAULT_WAIT_SECONDS = 10;
	}

	public static class TriangleWorker extends Worker {

		public TriangleWorker(int rudeness) {
			this.rudeness = rudeness;
		}
		
		public void work() throws Exception {
			System.out.println("Worker started...");
			while (!shouldStop() || rudeness > 0) {
				next();
				try {
					sleep(1000);
				}
				catch (InterruptedException e) {
					if (rudeness == 1) throw e;
				}
			}
		}

		public void cleanup(Exception e) {
			if (e == null) {
				System.out.println("Final report:");
				report();
			}
			else {
				System.out.println("Didn't stop cleanly: " + e.toString());
			}
		}

		protected synchronized void next() {
			long newBase = base + 1; long newCount = count + newBase;
			if (newCount > 0) {	base = newBase;	count = newCount; }
		}

		public synchronized void report() {
			System.out.println(String.format("base = %,d\ncount = %,d",
											 base, count));
		}

		private long base = 1;
		private long count = 1;
		private int rudeness;
	}
}
