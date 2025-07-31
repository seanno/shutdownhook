/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.lorawan2;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.SqlStore;

public class SingleMetricStore extends MetricStore
{
	public SingleMetricStore(String metricName, SqlStore.Config cfg) throws Exception {
		super(cfg);
		this.metricName = metricName;
	}

	public boolean saveMetric(double value) throws Exception {
		return(super.saveMetric(metricName, value));
	}

	public boolean saveMetric(double value, long epochSecond) throws Exception {

		return(super.saveMetric(metricName, value, epochSecond));
	}

	public Metric getLatestMetric(ZoneId tz) throws Exception {
		return(super.getLatestMetric(metricName, tz));
	}

	public List<Metric> getMetrics(Instant start, Instant end,
								   Integer maxCount, ZoneId tz) throws Exception {
		
		return(super.getMetrics(metricName, start, end, maxCount, tz));
	}

	private String metricName;

	private final static Logger log = Logger.getLogger(SingleMetricStore.class.getName());
}
