/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/


import java.util.ArrayList;
import java.util.Scanner;

public class Cleanup1
{
	public static void main(String[] args) throws Exception {

		System.out.println("Starting worker...");
		TriangleWorker worker = new TriangleWorker();
		worker.setDaemon(args.length <= 0 || !args[0].equals("nd"));
		worker.start();
		
		System.out.println("Press enter for report, x + enter to exit");
		Scanner scanner = new Scanner(System.in);
		while (!scanner.nextLine().equals("x")) worker.report();
		
		System.out.println("Exiting...");
	}
	
	public static class TriangleWorker extends Thread {

		public void run() {
			System.out.println("Worker started...");
			while (true) next();
			// System.out.println("Final report:");
			// report();
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
	}
}
