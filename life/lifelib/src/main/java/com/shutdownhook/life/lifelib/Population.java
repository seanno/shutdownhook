/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.life.lifelib;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.Template;

import com.shutdownhook.life.lifelib.Organism.Cycle;
import com.shutdownhook.life.lifelib.Reproduction.FitnessIndex;
import com.shutdownhook.life.lifelib.Reproduction.Triple;

public class Population
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String Name = UUID.randomUUID().toString();
		
		public Integer WorldDx;
		public Integer WorldDy;
		public Bitmap.EdgeStrategy WorldEdgeStrategy = Bitmap.EdgeStrategy.Wrap;
		public Integer CycleLength;
		
		public Integer OrganismCount;

		public Rules.RulesType RulesType = Rules.RulesType.Neighborhood_Moore;
		
		public Fitness.FitnessType FitnessType = Fitness.FitnessType.FiftyFifty;
		public Reproduction.PairingType PairingType = Reproduction.PairingType.Prom;

		// 0-1; when computing fitness this fraction of the last fitness
		// will be included (meaning 1-LFW is apportioned to the current fitness.
		// This helps blunt the effect of "one bad showing"
		public double LastFitnessWeight = .25;

		public Reproduction.Params ReproductionParams = new Reproduction.Params();

		public PopWriter.Config PopWriter;
	}

	public Population(Config cfg, int cycleCount) throws Exception {
		
		this.cfg = cfg;
		this.age = 0;
		this.cycleCount = cycleCount;
		this.metrics = new ArrayList<FitnessMetrics>();
		
		this.organisms = new Organism[cfg.OrganismCount];
		for (int i = 0; i < organisms.length; ++i) {
			organisms[i] = new Organism(cfg.RulesType);
		}

		if (cfg.PopWriter != null) {
			this.popWriter = new PopWriter(this, cfg.PopWriter);
			this.popWriter.writeConfig();
		}
	}
	
	// +------------+
	// | Properties |
	// +------------+

	public Config getConfig() { return(cfg); }
	public String getName() { return(cfg.Name); }
	public int getAge() { return(age); }
	public int getCycleCount() { return(cycleCount); }
	public Organism[] getOrganisms() { return(organisms); }
	public PopWriter getPopWriter() { return(popWriter); }

	// dangerous!
	public void updateCycleCount(int cycleCount) {
		this.cycleCount = cycleCount;
	}

	// +-----------------+
	// | runCycle(Async) |
	// +-----------------+

	public CompletableFuture<Boolean> runCycleAsync(Exec exec, boolean saveStates) {
		return(exec.runAsync("runCycle", new Exec.AsyncOperation() {
			public Boolean execute() throws Exception { return(runCycle(exec, saveStates)); }
			public Boolean exceptionResult() { return(false); }
		}));
	}

	public Boolean runCycle(Exec exec, boolean saveStates) throws Exception {

		++age;

		if (age == 1 && popWriter != null) {
			for (int i = 0; i < organisms.length; ++i) {
				popWriter.writeOrganismPrefix(organisms[i], null, null);
			}
		}

		// start organism cycles
		
		log.info(String.format("Starting cycle %d for population %s", age, cfg.Name));

		Bitmap initialEnv = new Bitmap(cfg.WorldDx, cfg.WorldDy, cfg.WorldEdgeStrategy);
		initialEnv.randomize();
		
		List<CompletableFuture<Cycle>> futures = new ArrayList<CompletableFuture<Cycle>>();
		for (int i = 0; i < organisms.length; ++i) {
			futures.add(organisms[i].runCycleAsync(exec,
												   initialEnv,
												   cfg.CycleLength,
												   cfg.FitnessType,
												   cfg.LastFitnessWeight,
												   saveStates));
		}

		// and wait for them to be done
		
		log.info("Waiting for cycle to complete");

		newMetrics();

		for (int i = 0; i < futures.size(); ++i) {

			Cycle cycle = futures.get(i).get();

			if (cycle == null) {
				String msg = String.format("runCycle exception for organism idx %d (%s)",
										   i, organisms[i].getId());
				
				throw new Exception(msg);
			}

			accumulateMetrics(cycle.Fitness);

			if (popWriter != null) popWriter.writeOrganismCycle(organisms[i]);
		}

		finalizeMetrics();

		Organism.sortByFitness(organisms);
		
		log.info(String.format("cycle %s complete: ", getLastMetrics().toString()));

		if (popWriter != null) {
			
			popWriter.writePopulation();
			
			if (age == cycleCount) {

				for (int i = 0; i < organisms.length; ++i) {
					popWriter.writeOrganismSuffix(organisms[i]);
				}

				popWriter.writeIndex();
				popWriter.writeCsv();
				popWriter.writeTopRules();
			}
		}

		return(true);
	}

	// +------------------+
	// | reproduce(Async) |
	// +------------------+

	public CompletableFuture<Boolean> reproduceAsync(Exec exec) {
		return(exec.runAsync("reproduce", new Exec.AsyncOperation() {
			public Boolean execute() throws Exception { return(reproduce(exec)); }
			public Boolean exceptionResult() { return(false); }
		}));
	}

	public Boolean reproduce(Exec exec) throws Exception {

		// get the reproduction triples

		log.info(String.format("assessing reproduction for population %s at age %d",
							   cfg.Name, age));

		FitnessIndex[] fitnesses = new FitnessIndex[organisms.length];
		for (int i = 0; i < fitnesses.length; ++i) {
			fitnesses[i] = new FitnessIndex(i, organisms[i].getLastCycle().Fitness);
		}
		
		Triple[] triples = Reproduction.assess(fitnesses, cfg.PairingType);

		// start them reproducing
		
		log.info("starting reproduction phase");
		
		List<CompletableFuture<Organism>> futures = new ArrayList<CompletableFuture<Organism>>();
		
		for (Triple triple : triples) {
			
			Organism org1 = organisms[triple.Partner1];
			Organism org2 = organisms[triple.Partner2];
			futures.add(org1.reproduceAsync(exec, org2, cfg.ReproductionParams));
		}

		// and wait for them to be done
		
		log.info("Waiting for reproduction to complete");

		for (int i = 0; i < futures.size(); ++i) {

			Organism newOrganism = futures.get(i).get();
			if (newOrganism == null) throw new Exception("exception reproducing organisms");

			if (popWriter != null) {
				popWriter.writeOrganismSuffix(organisms[triples[i].Kill]);
				
				popWriter.writeOrganismPrefix(newOrganism,
											  organisms[triples[i].Partner1],
											  organisms[triples[i].Partner2]);
			}
															 
												   
			organisms[triples[i].Kill] = newOrganism;
		}

		return(true);
	}

	// +----------------+
	// | FitnessMetrics |
	// +----------------+

	public static class FitnessMetrics
	{
		public double Min = Double.MAX_VALUE;
		public double Max = Double.MIN_VALUE;
		public double Avg = 0.0;

		public String toString() {
			return(String.format("Max=%s; Avg=%s; Min=%s",
								 doubleString(Max),
								 doubleString(Avg),
								 doubleString(Min)));
		}
		
		private static String doubleString(double d) {
			if (d == Double.MAX_VALUE) return("inf");
			if (d == Double.MIN_VALUE) return("inf");
			return(String.format("%.3f", d));
		}
	}


	public FitnessMetrics getLastMetrics() {
		return(metrics.get(metrics.size() - 1));
	}

	public List<FitnessMetrics> getMetrics() {
		return(metrics);
	}

	private void newMetrics() {
		metrics.add(new FitnessMetrics());
	}

	private void accumulateMetrics(double fitness) {
		FitnessMetrics metrics = getLastMetrics();
		if (fitness > metrics.Max) metrics.Max = fitness;
		if (fitness < metrics.Min) metrics.Min = fitness;
		metrics.Avg += fitness;
	}

	private void finalizeMetrics() {
		FitnessMetrics metrics = getLastMetrics();
		metrics.Avg /= ((double)organisms.length);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private Organism[] organisms;
	private int age;
	private int cycleCount;

	private List<FitnessMetrics> metrics;
	
	private PopWriter popWriter;

	private final static Logger log = Logger.getLogger(Population.class.getName());
}
