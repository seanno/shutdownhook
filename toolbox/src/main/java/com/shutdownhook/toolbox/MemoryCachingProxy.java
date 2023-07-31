/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

abstract public class MemoryCachingProxy<K,T> extends CachingProxy<K,T>
{
	// +--------------+
	// | Implement Me |
	// +--------------+

	// return null when id is not found, else throw
	// abstract public T liveFetch(K id) throws Exception;

	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config extends CachingProxy.Config
	{
		public Integer MaxCacheItems = 1000;
	}

	// +----------------+
	// | Implementation |
	// +----------------+

	public MemoryCachingProxy(Config cfg) throws Exception {
		super(cfg);
		this.cacheMap = new MemoryCacheMap<K,T>(cfg.MaxCacheItems);
	}

	@Override
	public T cacheFetch(K id) throws Exception {
		MemoryCacheItem<T> item = null;
		synchronized (cacheMap) { item = cacheMap.get(id); }
		return(item == null ? null : item.Obj);
	}

	@Override
	public void cacheStore(K id, T obj) throws Exception {

		MemoryCacheItem<T> item = new MemoryCacheItem<T>();
		item.Obj = obj;
		item.CacheTime = Instant.now();

		synchronized (cacheMap) {
			// the remove is important because insertion order
			// only updates if the put is net-new. Note also that
			// if we get larger than allowed the removeEldestEntry
			// implementation below will auto-prune things
			cacheMap.remove(id); 
			cacheMap.put(id, item);
		}
	}

	@Override
	public Instant cacheTime(K id, T obj) throws Exception {

		MemoryCacheItem<T> item = null;
		synchronized (cacheMap) { item = cacheMap.get(id); }
		return(item == null ? null : item.CacheTime);
	}

	// +-----------------+
	// | MemoryCacheMap  |
	// | MemoryCacheItem |
	// +-----------------+

	public static class MemoryCacheItem<T>
	{
		public T Obj;
		public Instant CacheTime;
	}

	public static class MemoryCacheMap<K,T>	extends LinkedHashMap<K,MemoryCacheItem<T>>
	{
		public MemoryCacheMap(int maxItems) {
			this.maxItems = maxItems;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<K,MemoryCacheItem<T>> entry) {
			return(size() > maxItems);
		}

		private int maxItems;
	}

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	private MemoryCacheMap<K,T> cacheMap;

	private final static Logger log = Logger.getLogger(MemoryCachingProxy.class.getName());
}

