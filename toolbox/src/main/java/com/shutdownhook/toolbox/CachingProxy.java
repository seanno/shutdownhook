/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.Closeable;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

abstract public class CachingProxy<K,T> implements Closeable
{
	// +--------------+
	// | Implement Me |
	// +--------------+

	// fetch methods should return null when id is not found, else throw
	abstract public T liveFetch(K id) throws Exception;
	abstract public T cacheFetch(K id) throws Exception;
	abstract public void cacheStore(K id, T obj) throws Exception;
	abstract public Instant cacheTime(K id, T obj) throws Exception;

	// +----------------+
	// | Config & Setup |
	// +----------------+
	
	public static class Config
	{
		// entries this old will be refreshed SYNC
		public Integer HardCacheExpirationSeconds = 60 * 60 * 24 * 2; // 7 days

		// entries this old will be refreshed ASYNC
		public Integer SoftCacheExpirationSeconds = 60 * 60 * 24; // 1 day

		// "not found" ids will be requeried after this long
		public Integer PoisonExpirationSeconds = 60 * 5; // 10 minutes

		public Integer ShutdownWaitSeconds = 10;
		public Boolean LogStackTraces = true;
	}

	public CachingProxy(Config cfg) throws Exception {
		this.cfg = cfg;
		this.pool = Executors.newCachedThreadPool();
		this.poisonIds = new ConcurrentHashMap<K,Instant>();
	}

	public void close() {
		try {
			pool.shutdown();
			pool.awaitTermination(cfg.ShutdownWaitSeconds / 2, TimeUnit.SECONDS);
			pool.shutdownNow();
			pool.awaitTermination(cfg.ShutdownWaitSeconds / 2, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			// oh well
		}
	}

	// +---------+
	// | getItem |
	// +---------+

	public T getItem(K id) throws Exception {

		// 1. poison ?

		if (isPoison(id)) {
			log.fine(String.format("Found poison id: %s", id.toString()));
			return(null);
		}
		
		// 2. in the cache ?
		
		T obj = cacheFetch(id);
		
		if (obj != null) {

			Instant cacheTime = cacheTime(id, obj);
			Instant now = Instant.now();
			Instant hardLimit = now.minusSeconds(cfg.HardCacheExpirationSeconds);
			Instant softLimit = now.minusSeconds(cfg.SoftCacheExpirationSeconds);

			if (cacheTime.isBefore(hardLimit)) {
				// too old to return
				log.fine(String.format("hard cache limit for %s", id.toString()));
				obj = null;
			}
			else if (cacheTime.isBefore(softLimit)) {
				// ok to return but start an async refresh
				log.fine(String.format("soft cache limit for %s", id.toString()));
				queueFetch(id);
			}
		}

		// 3. fetch live
		
		if (obj == null) {
			log.fine(String.format("live fetching id %s", id.toString()));
			obj = liveFetch(id);
			queueCache(id, obj);
		}

		return(obj);
	}

	// +-------------+
	// | Async Stuff |
	// +-------------+

	private void queueFetch(K id) {
		pool.submit(() -> {
			try {
				T obj = liveFetch(id);
				cache(id, obj);
			}
			catch (Exception ex) {
				log.warning(Easy.exMsg(ex, "queueFetch", cfg.LogStackTraces));
			}
		});
	}

	private void queueCache(K id, T obj) {
		pool.submit(() -> {
			try {
				cache(id, obj);
			}
			catch (Exception ex) {
				log.warning(Easy.exMsg(ex, "queueCache", cfg.LogStackTraces));
			}
		});
	}

	private void cache(K id, T obj) {

		if (obj == null) {
			// don't keep looking for something not there
			rememberPoison(id);
		}
		else {
			// cache it
			try {
				cacheStore(id, obj);
			}
			catch (Exception ex) {
				log.warning(Easy.exMsg(ex, "cache", cfg.LogStackTraces));
			}
		}
	}

	// +--------------+
	// | Poisoned IDs |
	// +--------------+

	private boolean isPoison(K id) {
		
		if (!poisonIds.containsKey(id))
			return(false);

		Instant cacheTime = poisonIds.get(id);
		Instant limit = Instant.now().minusSeconds(cfg.PoisonExpirationSeconds);
		
		if (cacheTime.isBefore(limit)) {
			poisonIds.remove(id);
			return(false);
		}

		return(true);
	}

	private void rememberPoison(K id) {
		poisonIds.put(id, Instant.now());
	}

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	private Config cfg;
	private ExecutorService pool;
	private ConcurrentHashMap<K,Instant> poisonIds;

	private final static Logger log = Logger.getLogger(CachingProxy.class.getName());
}

