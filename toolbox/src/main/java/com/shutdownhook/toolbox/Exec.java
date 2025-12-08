//
// EXEC.JAVA
// 

package com.shutdownhook.toolbox;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Exec implements Closeable
{
	// +-------+
	// | Setup |
	// +-------+

	public final static int CACHED_THREADPOOL = -1;
	
	public Exec() {
		this(CACHED_THREADPOOL);
	}

	public Exec(int threads) {
		this.pool = (threads == CACHED_THREADPOOL
					 ? Executors.newCachedThreadPool()
					 : Executors.newFixedThreadPool(threads));
	}

	public void close() {
		shutdownPool();
	}
	
	// +---------+
	// | getPool |
	// +---------+
	
	public ExecutorService getPool() { return(pool); }

	// +----------+
	// | runAsync |
	// +----------+

	public interface AsyncOperation<T> {
		public T execute() throws Exception;
		default public T exceptionResult() { return(null); }
	}
	
	public <T> CompletableFuture<T> runAsync(String label, AsyncOperation<T> op) {
		
		CompletableFuture<T> future = new CompletableFuture<T>();

		getPool().submit(() -> {

			T result = null;
				
			try {
				result = op.execute();
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "runAsync (" + label + ")", true));
				result = op.exceptionResult();
			}
			
			future.complete(result);
		});

		return(future);
	}

	// +----------+
	// | Shutdown |
	// +----------+

	private final static int SHUTDOWN_FIRSTWAIT_SECONDS = 1;
	private final static int SHUTDOWN_SECONDWAIT_SECONDS = 2;

	public void shutdownPool() {
		
		if (pool == null) return;
		
		try {
			pool.shutdown();
			pool.awaitTermination(SHUTDOWN_FIRSTWAIT_SECONDS, TimeUnit.SECONDS);
			pool.shutdownNow();
			pool.awaitTermination(SHUTDOWN_SECONDWAIT_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			/* eat it */
		}
		finally {
			pool = null;
		}
		
	}

	// +---------+
	// | Members |
	// +---------+

	private ExecutorService pool;
	
	private final static Logger log = Logger.getLogger(Exec.class.getName());

}
