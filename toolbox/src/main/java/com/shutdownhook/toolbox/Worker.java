/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

public abstract class Worker extends Thread {

	// +-----------------+
	// | For Imlementors |
	// +-----------------+

	public abstract void work() throws Exception;
	public abstract void cleanup(Exception e);

	protected synchronized boolean shouldStop() { return(stop); }

	protected boolean sleepyStop(int seconds) {
		try {
			long msRemaining = seconds * 1000;
			while (msRemaining > 0) {
				Thread.sleep(500);
				if (shouldStop()) return(true);
				msRemaining -= 500;
			}

			return(false);
		}
		catch (InterruptedException e) {
			return(true);
		}
	}
	
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
