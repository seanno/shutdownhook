//
// BACKSTOPHELPERS.JAVA
//

package com.shutdownhook.backstop;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;
import com.shutdownhook.toolbox.WebRequests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BackstopHelpers implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String ZoneId = null;
		public SqlStore.Config Sql = null;
		public WebRequests.Config Requests = new WebRequests.Config();
	}

	public BackstopHelpers(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		initState();
		initDatesAndTimes();
	}

	public void close() {
		requests.close();
	}

	// +-----------+
	// | Accessors |
	// +-----------+

	public Gson getGson() { return(gson); }
	public WebRequests getRequests() { return(requests); }

	// +-------+
	// | State |
	// +-------+

	public Map<String,String> getAllState(String stateId) throws Exception {
		if (Easy.nullOrEmpty(stateId)) throw new Exception("Can't use state without StateId");
		return(state.getAll(stateId));
	}
	
	public String getState(String stateId, String name) throws Exception {
		if (Easy.nullOrEmpty(stateId)) throw new Exception("Can't use state without StateId");
		return(state.get(stateId, name));
	}

	public void setState(String stateId, String name, String value) throws Exception {
		if (Easy.nullOrEmpty(stateId)) throw new Exception("Can't use state without StateId");
		state.set(stateId, name, value);
	}
	
	private void initState() throws Exception {
		if (cfg.Sql != null) this.state = new State(cfg.Sql);
	}

	// +--------------+
	// | Date & Times |
	// +--------------+

	public ZoneId getZoneId() { return(zoneId); }
	public LocalDate getDateToday() { return(dateToday); }

	public LocalDate minusDays(LocalDate dateFrom, int days) {
		return(dateFrom.minus(days, ChronoUnit.DAYS));
	}
	
	public long minutesAgo(long sinceEpochSecond) {
		return(minutesAgo(Instant.ofEpochSecond(sinceEpochSecond)));
	}
	
	public long minutesAgo(Instant sinceInstant) {
		return(Duration.between(sinceInstant, Instant.now()).toMinutes());
	}

	public LocalDate parseUSDate(String str) {
		return(LocalDate.parse(str, dtfUSDate));
	}

	private void initDatesAndTimes() {
		this.zoneId = (Easy.nullOrEmpty(cfg.ZoneId) ? ZoneId.systemDefault() : ZoneId.of(cfg.ZoneId));
		this.dateToday = LocalDate.now(zoneId);
		this.dtfUSDate = DateTimeFormatter.ofPattern("M/d/[uuuu][uu]");
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebRequests requests;
	private Gson gson;
	private State state;
	
	private ZoneId zoneId;
	private LocalDate dateToday;
	private DateTimeFormatter dtfUSDate;
}
