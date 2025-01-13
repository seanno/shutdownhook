//
// ORGANISM.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Exec;

import com.shutdownhook.life.lifelib.Fitness.FitnessType;
import com.shutdownhook.life.lifelib.Rules.RulesProcessor;
import com.shutdownhook.life.lifelib.Rules.RulesType;

public class Organism
{
	// +-------+
	// | Setup |
	// +-------+

	public Organism(RulesType rulesType) {
		this(Rules.get(rulesType));
	}

	public Organism(RulesProcessor rules) {
		
		this.id = UUID.randomUUID().toString();
		this.rules = rules;
		
		this.age = 0;
		
		this.lastCycle = null;
	}

	// +------------+
	// | Properties |
	// +------------+

	public String getId() { return(id); }

	public int getAge() { return(age); }
	public Cycle getLastCycle() { return(lastCycle); }

	public RulesProcessor getRulesProcessor() { return(rules); }
	
	// +-----------------+
	// | runCycle(Async) |
	// +-----------------+

	public static class Cycle
	{
		public double Fitness;
		public Bitmap EndState;
		public Bitmap[] AllStates;
	}
	
	public CompletableFuture<Cycle> runCycleAsync(Exec exec,
												  Bitmap initialEnv,
												  int iterations,
												  FitnessType fitnessType,
												  double lastFitnessWeight,
												  boolean saveStates) {
		
		return(exec.runAsync("runCycle", new Exec.AsyncOperation() {
			public Cycle execute() throws Exception {
				return(runCycle(initialEnv, iterations, fitnessType,
								lastFitnessWeight, saveStates));
			}
		}));
	}
	
	public Cycle runCycle(Bitmap initialEnv, int iterations,
						  FitnessType fitnessType, double lastFitnessWeight,
						  boolean saveStates) throws Exception {
		++age;

		Cycle cycle = new Cycle();
		
		if (saveStates) {
			cycle.AllStates = new Bitmap[iterations + 1];
			cycle.AllStates[0] = initialEnv;
		}

		Bitmap env = initialEnv;
		for (int i = 0; i < iterations; ++i) {
			env = Rules.apply(env, rules);
			if (saveStates) cycle.AllStates[i+1] = env;
		}

		cycle.EndState = env;
		cycle.Fitness = Fitness.compute(cycle.EndState, fitnessType);

		if (lastCycle != null && lastFitnessWeight > 0.0) {
			cycle.Fitness = (lastCycle.Fitness * lastFitnessWeight) +
				            (cycle.Fitness * (1.0 - lastFitnessWeight));
		}

		lastCycle = cycle;
		return(cycle);
	}

	// +------------------+
	// | reproduce(Async) |
	// +------------------+

	public CompletableFuture<Organism> reproduceAsync(Exec exec,
													  Organism other,
													  Reproduction.Params params) {

		return(exec.runAsync("reproduce", new Exec.AsyncOperation() {
			public Organism execute() throws Exception {
				return(reproduce(other, params));
			}
		}));
	}
	
	public Organism reproduce(Organism other, Reproduction.Params params) throws Exception {
		RulesProcessor newRules = rules.reproduce(other.rules, params);
		return(new Organism(newRules));
	}

	// +-------+
	// | sorts |
	// +-------+

	public static class OrgAgeSorter implements Comparator<Organism>
	{
		public int compare(Organism org1, Organism org2) {
			int cmp = Integer.compare(org2.age, org1.age); // descending!
			if (cmp == 0) cmp = org1.id.compareTo(org2.id);
			return(cmp);
		}
	}
	
	public static class OrgFitnessSorter implements Comparator<Organism>
	{
		public int compare(Organism org1, Organism org2) {
			int cmp = Double.compare(org2.lastCycle.Fitness, org1.lastCycle.Fitness);
			if (cmp == 0) cmp = Integer.compare(org2.age, org1.age);
			if (cmp == 0) cmp = org1.id.compareTo(org2.id);
			return(cmp);
		}
	}
												
	public static void sortByAge(Organism[] organisms) {
		Arrays.sort(organisms, new OrgAgeSorter());
	}

	public static void sortByFitness(Organism[] organisms) {
		Arrays.sort(organisms, new OrgFitnessSorter());
	}

	// +---------+
	// | Members |
	// +---------+

	private String id;
	private int age;
	private Cycle lastCycle;
	private RulesProcessor rules;

	private final static Logger log = Logger.getLogger(Organism.class.getName());
}

