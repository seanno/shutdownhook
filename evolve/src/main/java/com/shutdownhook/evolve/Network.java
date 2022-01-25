/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.evolve;

import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.lang.Math;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;

public class Network
{
	// +-------+
	// | Setup |
	// +-------+

	public static class Config
	{
		public int[] Layers;
		public double LearningRate = 0.01;
		public String ActivationFunction = null;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Network(Config cfg) throws IllegalArgumentException {
		
		if (cfg.Layers == null || cfg.Layers.length < 2) {
			throw new IllegalArgumentException();
		}

		this.rand = new Random();
		
		this.cfg = cfg;
		this.activation = createActivationFunction(cfg.ActivationFunction);

		weights = new ArrayList<Matrix>();
		biases = new ArrayList<Matrix>();
		
		for (int i = 1; i < cfg.Layers.length; ++i) {

			Matrix m = new Matrix(cfg.Layers[i], cfg.Layers[i-1]);
			m.randomize();
			weights.add(m);

			Matrix b = new Matrix(cfg.Layers[i], 1);
			b.randomize();
			biases.add(b);

			log.fine(String.format("Added (%dx%d) weights & bias",
								   cfg.Layers[i], cfg.Layers[i-1]));
		}
	}

	private ActivationFunction createActivationFunction(String className) {
		// nyi create the right one
		return(new SigmoidActivationFunction());
	}

	public int numInputs() { return(cfg.Layers[0]); }
	public int numOutputs() { return(cfg.Layers[cfg.Layers.length - 1]); }

	// +--------------------+
	// | ActivationFunction |
	// +--------------------+

	public interface ActivationFunction
	{
		public double function(double input);
		public double derivative(double input);
	}

	public static class SigmoidActivationFunction implements ActivationFunction
	{
		public double function(double input) {
			return(1d / (1d + Math.exp(-1d * input)));
		}
		
		public double derivative(double input) {
			return(input * (1d - input));
		}
	}

	// +-------------+
	// | forwardPass |
	// +-------------+

	public double[] forwardPass(double[] input) {
		
		List<Matrix> results = forwardPassInternal(input);
		return(results.get(results.size() - 1).toArray());
	}

	private List<Matrix> forwardPassInternal(double[] input) {

		List<Matrix> results = new ArrayList<Matrix>();
		results.add(new Matrix(input, 0, cfg.Layers[0]));

		for (int i = 0; i < weights.size(); ++i) {

			Matrix layer = weights.get(i).multiply(results.get(i));
			layer.add(biases.get(i));
			layer.transform(v -> activation.function(v));

			results.add(layer);
		}

		return(results);
	}

	// +-------+
	// | train |
	// +-------+

	// vals should have inputs first and then expected
	public void trainOne(double[] vals) {

		// forwardprop

		List<Matrix> results = forwardPassInternal(vals);

		// backprop
		
		Matrix errors = new Matrix(vals, cfg.Layers[0], vals.length);
		errors.subtract(results.get(results.size() - 1));

		for (int i = weights.size() - 1; i >= 0; --i) {

			// figure out the gradient for each weight in the layer
			Matrix gradient = new Matrix(results.get(i+1));
			gradient.transform(v -> activation.derivative(v));
			gradient.scale(errors);
			gradient.scale(cfg.LearningRate);

			// do this before updating weights
			errors = weights.get(i).transpose().multiply(errors);

			// the actual learning part!
			Matrix weightDeltas = gradient.multiply(results.get(i).transpose());
			weights.get(i).add(weightDeltas);
			biases.get(i).add(gradient);
		}
	}

	public void trainMany(double[][] rgvals, int iterations) {
		trainMany(rgvals, iterations, null);
	}
	
	public void trainMany(double[][] rgvals, int iterations, TrainManyCallback callback) {

		// all this work is to be sure we cover the training
		// set in random order, but as evenly as possible
		
		int iterationsLeft = iterations;

		int[] runOrder = new int[rgvals.length];
		for (int i = 0; i < runOrder.length; ++i) runOrder[i] = i;

		int totalRuns = 0;
		while (iterationsLeft > 0) {

			shuffle(runOrder);
			
			int iterationsNow = runOrder.length;
			if (iterationsNow > iterationsLeft) iterationsNow = iterationsLeft;

			for (int i = 0; i < iterationsNow; ++i) {

				if (callback != null && !callback.call(totalRuns)) return;
			    ++totalRuns;

				trainOne(rgvals[runOrder[i]]);
			}
			
			iterationsLeft -= iterationsNow;
		}

		if (callback != null) callback.call(totalRuns);
	}

	public interface TrainManyCallback {
		public boolean call(int iterations);
	}

	// +-------------+
	// | testVerbose |
	// +-------------+

	// run tests and return a table with inputs / outputs / expected / errors for each

	public double[][] testVerbose(double[][] rgvals) {
			
		int numInputs = numInputs();
		int numOutputs = numOutputs();

		double[][] r = new double[rgvals.length][numInputs + (numOutputs * 3)];
		
		for (int i = 0; i < rgvals.length; ++i) {
			
			for (int j = 0; j < numInputs; ++j) {
				r[i][j] = rgvals[i][j];
			}

			double[] outputs = forwardPass(rgvals[i]);

			for (int j = 0; j < numOutputs; ++j) {
				r[i][numInputs + (j * 3)] = outputs[j];
				r[i][numInputs + (j * 3) + 1] = rgvals[i][j + numInputs];
				r[i][numInputs + (j * 3) + 2] =	Math.abs(outputs[j] - rgvals[i][j + numInputs]);
			}
		}

		return(r);
	}

	public String[] testVerboseHeaders() {

		int numInputs = numInputs();
		int numOutputs = numOutputs();

		String[] hdrs = new String[numInputs + (numOutputs * 3)];

		for (int i = 0; i < numInputs; ++i) {
			hdrs[i] = "in" + Integer.toString(i);
		}

		for (int i = 0; i < numOutputs; ++i) {
			String t = Integer.toString(i);
			hdrs[numInputs + (i * 3)] = "out" + t;
			hdrs[numInputs + (i * 3) + 1] = "exp" + t;
			hdrs[numInputs + (i * 3) + 2] = "err" + t;
		}

		return(hdrs);
	}

	// +-------------+
	// | testSummary |
	// +-------------+

	// run tests and return some stats about errors
	
	public TestResultErrorSummary testSummary(double[][] rgvals) {

		int numInputs = numInputs();
		int numOutputs = numOutputs();

		TestResultErrorSummary r = new TestResultErrorSummary();

		for (int i = 0; i < rgvals.length; ++i) {
			
			double[] outputs = forwardPass(rgvals[i]);

			for (int j = 0; j < numOutputs; ++j) {

				double err = Math.abs(outputs[j] - rgvals[i][j + numInputs]);

				if (err < r.Min) r.Min = err;
				if (err > r.Max) r.Max = err;
				r.Sum += err;
			}
		}

		r.Avg = (r.Sum / (rgvals.length * numOutputs));

		return(r);
	}

	public static class TestResultErrorSummary
	{
		public double Max = Double.MIN_VALUE;
		public double Min = Double.MAX_VALUE;
		public double Sum = 0.0;
		public double Avg;
	}

	// +--------------+
	// | trainAndTest |
	// +--------------+

	public static class TrainAndTestConfig
	{
		public Config Network;

		// each row should have inputs followed by expected outputs;
		// number of each must match Network config (duh)
		public String DataSetTsv;

		public int TrainingIterations;
		public int TrainingReportInterval = 0; // 0 means never

		// 0 means test with the full training set
		public int HoldBackPercentage = 0;
		
		public static TrainAndTestConfig fromJson(String json) {
			return(new Gson().fromJson(json, TrainAndTestConfig.class));
		}
	}

	public static class TrainAndTestResults
	{
		public Network Network;
		public double[][] TestOutput;
	}
	
	public static TrainAndTestResults trainAndTest(TrainAndTestConfig cfg)
		throws IllegalArgumentException, IOException {

		// load up the dataset tsv and skip header if present

		List<String> dataSetLines =
			Files.readAllLines(Paths.get(cfg.DataSetTsv), StandardCharsets.UTF_8);

		int firstLine = 0;
		int sampleCount = dataSetLines.size();
		if (sampleCount > 0 && Character.isAlphabetic(dataSetLines.get(0).charAt(0))) {
			++firstLine; --sampleCount;
		}

		int holdback = (sampleCount * cfg.HoldBackPercentage / 100);

		// set up train and test sets

		if (holdback > 0) shuffle(dataSetLines, firstLine);

		int trainSetSize = sampleCount - holdback;
		double[][] trainSet = new double[trainSetSize][];
		
		for (int i = 0; i < trainSetSize; ++i) {
			trainSet[i] = splitToDoubles(dataSetLines.get(i + firstLine));
		}

		final double[][] testSet = (holdback > 0 ? new double[holdback][] : trainSet);

		if (holdback > 0) {
			for (int i = 0; i < holdback; ++i) {
				testSet[i] = splitToDoubles(dataSetLines.get(i + firstLine + trainSetSize));
			}
		}

		// actually train ...
		
		TrainAndTestResults results = new TrainAndTestResults();
		results.Network = new Network(cfg.Network);
		results.Network.trainMany(trainSet, cfg.TrainingIterations, (iterations) -> {
				
			if (cfg.TrainingReportInterval > 0 &&
				iterations % cfg.TrainingReportInterval == 0) {

				TestResultErrorSummary r = results.Network.testSummary(testSet);
				log.info(String.format("ITERATION\t%7d\t%04.3f\t%04.3f\t%04.3f",
									   iterations, r.Min, r.Max, r.Avg));
			}

			return(true);
		});

		// ... and actually test
		results.TestOutput = results.Network.testVerbose(testSet);
		
		return(results);
	}

	private static double[] splitToDoubles(String input) {
		
		String[] split = input.split("\t");
		double[] rgd = new double[split.length];

		for (int i = 0; i < split.length; ++i) {
			rgd[i] = Double.parseDouble(split[i].trim());
		}

		return(rgd);
	}
	
	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat();

		String json = Easy.stringFromSmartyPath(args[0]);
		TrainAndTestConfig cfg = TrainAndTestConfig.fromJson(json);

		TrainAndTestResults results = trainAndTest(cfg);

		String[] hdrs = results.Network.testVerboseHeaders();
		for (int i = 0; i < hdrs.length; ++i) {
			if (i > 0) System.out.print("\t");
			System.out.print(hdrs[i]);
		}

		System.out.println("");

		for (int i = 0; i < results.TestOutput.length; ++i) {
			double[] vals = results.TestOutput[i];
			for (int j = 0; j < vals.length; ++j) {
				if (j > 0) System.out.print("\t");
				System.out.print(String.format("%04.3f", vals[j]));
			}

			System.out.println("");
		}
	}

	// +--------------+
	// | Misc Helpers |
	// +--------------+

	private static <T> void shuffle(List<T> list, int startLine) {

		Random rand = new Random();
		
		for (int i = startLine; i < list.size(); ++i) {
			int j = rand.nextInt((list.size() - startLine) + startLine);
			T temp = list.get(i);
			list.set(i, list.get(j));
			list.set(j, temp);
		}
	}
	
	private void shuffle(int[] rgi) {

		for (int i = 0; i < rgi.length; ++i) {
			int j = rand.nextInt(rgi.length);
			int temp = rgi[i];
			rgi[i] = rgi[j];
			rgi[j] = temp;
		}
	}
	
	// +---------+
	// | Members |
	// +---------+

	private List<Matrix> weights;
	private List<Matrix> biases;

	private Config cfg;
	private ActivationFunction activation;

	private Random rand;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}

