///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2014 Joliciel Informatique
//
//This file is part of Talismane.
//
//Talismane is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//Talismane is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Talismane.  If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.talismane.machineLearning.perceptron;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.machineLearning.ClassificationEvent;
import com.joliciel.talismane.machineLearning.ClassificationEventStream;
import com.joliciel.talismane.machineLearning.ClassificationModel;
import com.joliciel.talismane.machineLearning.ClassificationModelTrainer;
import com.joliciel.talismane.machineLearning.MachineLearningModel;
import com.joliciel.talismane.utils.LogUtils;
import com.typesafe.config.Config;

public class PerceptronClassificationModelTrainer implements ClassificationModelTrainer {
  /**
   * A parameter accepted by the perceptron model trainer.
   * 
   * @author Assaf Urieli
   *
   */
  public enum PerceptronModelParameter {
    Iterations(Integer.class),
    Cutoff(Integer.class),
    Tolerance(Double.class),
    AverageAtIntervals(Boolean.class);

    private Class<?> parameterType;

    private PerceptronModelParameter(Class<?> parameterType) {
      this.parameterType = parameterType;
    }

    public Class<?> getParameterType() {
      return parameterType;
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(PerceptronClassificationModelTrainer.class);
  private int iterations;
  private int cutoff;
  private double tolerance;
  private PerceptronScoring scoring;

  private double[][] totalFeatureWeights;
  private PerceptronModelParameters params;
  private File eventFile;
  private PerceptronDecisionMaker decisionMaker;
  private Map<String, List<String>> descriptors;
  private ClassificationEventStream corpusEventStream;
  private PerceptronModelTrainerObserver observer;
  private List<Integer> observationPoints;
  private boolean averageAtIntervals = false;

  private Config config;

  public PerceptronClassificationModelTrainer() {
  }

  void prepareData(ClassificationEventStream eventStream) throws TalismaneException {
    try {
      eventFile = File.createTempFile("events", "txt");
      eventFile.deleteOnExit();
      Writer eventWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(eventFile), "UTF-8"));
      while (eventStream.hasNext()) {
        ClassificationEvent corpusEvent = eventStream.next();
        PerceptronEvent event = new PerceptronEvent(corpusEvent, params);
        event.write(eventWriter);
      }
      eventWriter.flush();
      eventWriter.close();

      if (cutoff > 1) {
        params.initialiseCounts();
        File originalEventFile = eventFile;
        try (Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(eventFile), "UTF-8")))) {

          while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            PerceptronEvent event = new PerceptronEvent(line);
            for (int featureIndex : event.getFeatureIndexes()) {
              params.getFeatureCounts()[featureIndex]++;
            }
          }
        }

        if (LOG.isDebugEnabled()) {
          int[] cutoffCounts = new int[21];
          for (int count : params.getFeatureCounts()) {
            for (int i = 1; i < 21; i++) {
              if (count >= i) {
                cutoffCounts[i]++;
              }
            }
          }
          LOG.debug("Feature counts:");
          for (int i = 1; i < 21; i++) {
            LOG.debug("Cutoff " + i + ": " + cutoffCounts[i]);
          }
        }
        PerceptronModelParameters cutoffParams = new PerceptronModelParameters();
        int[] newIndexes = cutoffParams.initialise(params, cutoff);
        decisionMaker = new PerceptronDecisionMaker(cutoffParams, this.scoring);
        try (Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(eventFile), "UTF-8")))) {
          eventFile = File.createTempFile("eventsCutoff", "txt");
          eventFile.deleteOnExit();
          try (Writer eventCutoffWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(eventFile), "UTF-8"))) {
            while (scanner.hasNextLine()) {
              String line = scanner.nextLine();
              PerceptronEvent oldEvent = new PerceptronEvent(line);
              PerceptronEvent newEvent = new PerceptronEvent(oldEvent, newIndexes);
              newEvent.write(eventCutoffWriter);
            }
            eventCutoffWriter.flush();
          }
          params = cutoffParams;
          originalEventFile.delete();
        }
      }

      params.initialiseWeights();
      totalFeatureWeights = new double[params.getFeatureCount()][params.getOutcomeCount()];
    } catch (IOException e) {
      LogUtils.logError(LOG, e);
      throw new RuntimeException(e);
    }
  }

  void train() {
    try {
      double prevAccuracy1 = 0.0;
      double prevAccuracy2 = 0.0;
      double prevAccuracy3 = 0.0;
      int i = 0;
      int averagingCount = 0;
      for (i = 1; i <= iterations; i++) {
        LOG.debug("Iteration " + i);
        int totalErrors = 0;
        int totalEvents = 0;

        try (Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(eventFile), "UTF-8")))) {

          while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            PerceptronEvent event = new PerceptronEvent(line);
            totalEvents++;

            // don't normalise unless we calculate the
            // log-likelihood,
            // to avoid mathematical cost of normalising
            double[] results = decisionMaker.predict(event.getFeatureIndexes(), event.getFeatureValues());
            double maxValue = results[0];
            int predicted = 0;
            for (int j = 1; j < results.length; j++) {
              if (results[j] > maxValue) {
                maxValue = results[j];
                predicted = j;
              }
            }

            int actual = event.getOutcomeIndex();

            if (actual != predicted) {
              for (int j = 0; j < event.getFeatureIndexes().size(); j++) {
                double[] classWeights = params.getFeatureWeights()[event.getFeatureIndexes().get(j)];
                classWeights[actual] += event.getFeatureValues().get(j);
                classWeights[predicted] -= event.getFeatureValues().get(j);
              }
              totalErrors++;
            } // correct outcome?
          } // next event
        }

        // Add feature weights for this iteration
        boolean addAverage = true;
        if (this.isAverageAtIntervals()) {
          if (i <= 20 || i == 25 || i == 36 || i == 49 || i == 64 || i == 81 || i == 100 || i == 121 || i == 144 || i == 169 || i == 196) {
            addAverage = true;
            LOG.debug("Averaging at iteration: " + i);
          } else
            addAverage = false;
        }

        if (addAverage) {
          for (int j = 0; j < params.getFeatureWeights().length; j++) {
            double[] totalClassWeights = totalFeatureWeights[j];
            double[] classWeights = params.getFeatureWeights()[j];
            for (int k = 0; k < params.getOutcomeCount(); k++) {
              totalClassWeights[k] += classWeights[k];
            }
          }
          averagingCount++;
        }

        if (observer != null && observationPoints.contains(i)) {
          PerceptronModelParameters cloneParams = params.clone();
          // average the weights for this model
          for (int j = 0; j < cloneParams.getFeatureWeights().length; j++) {
            double[] totalClassWeights = totalFeatureWeights[j];
            double[] classWeights = cloneParams.getFeatureWeights()[j];
            for (int k = 0; k < cloneParams.getOutcomeCount(); k++) {
              classWeights[k] = totalClassWeights[k] / averagingCount;
            }
          }
          ClassificationModel model = this.getModel(cloneParams, i);
          observer.onNextModel(model, i);
          cloneParams = null;
        }

        double accuracy = (double) (totalEvents - totalErrors) / (double) totalEvents;
        LOG.debug("Accuracy: " + accuracy);

        // exit if accuracy hasn't significantly changed in 3 iterations
        if (Math.abs(accuracy - prevAccuracy1) < tolerance && Math.abs(accuracy - prevAccuracy2) < tolerance
            && Math.abs(accuracy - prevAccuracy3) < tolerance) {
          LOG.info("Accuracy change < " + tolerance + " for 3 iterations: exiting after " + i + " iterations");
          break;
        }

        prevAccuracy3 = prevAccuracy2;
        prevAccuracy2 = prevAccuracy1;
        prevAccuracy1 = accuracy;
      } // next iteration

      // average the final weights
      for (int j = 0; j < params.getFeatureWeights().length; j++) {
        double[] totalClassWeights = totalFeatureWeights[j];
        double[] classWeights = params.getFeatureWeights()[j];
        for (int k = 0; k < params.getOutcomeCount(); k++) {
          classWeights[k] = totalClassWeights[k] / averagingCount;
        }
      }

    } catch (IOException e) {
      LogUtils.logError(LOG, e);
      throw new RuntimeException(e);
    }
  }

  private static final class PerceptronEvent {
    List<Integer> featureIndexes;
    List<Double> featureValues;
    int outcomeIndex;

    public PerceptronEvent(ClassificationEvent corpusEvent, PerceptronModelParameters params) {
      featureIndexes = new ArrayList<Integer>();
      featureValues = new ArrayList<Double>();
      params.prepareData(corpusEvent.getFeatureResults(), featureIndexes, featureValues, true);
      outcomeIndex = params.getOrCreateOutcomeIndex(corpusEvent.getClassification());
    }

    public PerceptronEvent(String line) {
      String[] parts = line.split(" ");
      this.outcomeIndex = Integer.parseInt(parts[0]);
      int featureCount = (parts.length - 1) / 2;
      featureIndexes = new ArrayList<Integer>(featureCount);
      featureValues = new ArrayList<Double>(featureCount);
      int j = 1;
      for (int i = 0; i < featureCount; i++) {
        featureIndexes.add(Integer.parseInt(parts[j++]));
        featureValues.add(Double.parseDouble(parts[j++]));
      }
    }

    public PerceptronEvent(PerceptronEvent oldEvent, int[] newIndexes) {
      featureIndexes = new ArrayList<Integer>();
      featureValues = new ArrayList<Double>();
      int i = 0;
      for (int oldIndex : oldEvent.featureIndexes) {
        if (newIndexes[oldIndex] >= 0) {
          featureIndexes.add(newIndexes[oldIndex]);
          featureValues.add(oldEvent.featureValues.get(i));
        }
        i++;
      }
      outcomeIndex = oldEvent.outcomeIndex;
    }

    public List<Integer> getFeatureIndexes() {
      return featureIndexes;
    }

    public List<Double> getFeatureValues() {
      return featureValues;
    }

    public int getOutcomeIndex() {
      return outcomeIndex;
    }

    public void write(Writer writer) throws IOException {
      writer.write("" + outcomeIndex);
      for (int i = 0; i < featureIndexes.size(); i++) {
        writer.write(" ");
        writer.write("" + featureIndexes.get(i));
        writer.write(" ");
        writer.write("" + featureValues.get(i));
      }
      writer.write("\n");
      writer.flush();
    }

  }

  /**
   * The maximum number of training iterations to run.
   */
  public int getIterations() {
    return iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  @Override
  public int getCutoff() {
    return cutoff;
  }

  @Override
  public void setCutoff(int cutoff) {
    this.cutoff = cutoff;
  }

  public double getTolerance() {
    return tolerance;
  }

  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * If true, will only average for iterations &lt;= 20 and then for all perfect
   * squares (25, 36, 49, 64, 81, 100, etc.).
   */
  public boolean isAverageAtIntervals() {
    return averageAtIntervals;
  }

  public void setAverageAtIntervals(boolean averageAtIntervals) {
    this.averageAtIntervals = averageAtIntervals;
  }

  public void trainModelsWithObserver(ClassificationEventStream corpusEventStream, List<String> featureDescriptors, PerceptronModelTrainerObserver observer,
      List<Integer> observationPoints) throws TalismaneException {
    Map<String, List<String>> descriptors = new HashMap<String, List<String>>();
    descriptors.put(MachineLearningModel.FEATURE_DESCRIPTOR_KEY, featureDescriptors);
    this.trainModelsWithObserver(corpusEventStream, descriptors, observer, observationPoints);
  }

  public void trainModelsWithObserver(ClassificationEventStream corpusEventStream, Map<String, List<String>> descriptors,
      PerceptronModelTrainerObserver observer, List<Integer> observationPoints) throws TalismaneException {
    params = new PerceptronModelParameters();
    decisionMaker = new PerceptronDecisionMaker(params, this.getScoring());
    this.descriptors = descriptors;
    this.observer = observer;
    this.observationPoints = observationPoints;
    this.corpusEventStream = corpusEventStream;
    this.prepareData(corpusEventStream);
    this.train();

    if (this.eventFile != null) {
      this.eventFile.delete();
    }

  }

  @Override
  public ClassificationModel trainModel(ClassificationEventStream corpusEventStream, List<String> featureDescriptors) throws TalismaneException {
    Map<String, List<String>> descriptors = new HashMap<String, List<String>>();
    descriptors.put(MachineLearningModel.FEATURE_DESCRIPTOR_KEY, featureDescriptors);
    return this.trainModel(corpusEventStream, descriptors);
  }

  @Override
  public ClassificationModel trainModel(ClassificationEventStream corpusEventStream, Map<String, List<String>> descriptors) throws TalismaneException {
    params = new PerceptronModelParameters();
    decisionMaker = new PerceptronDecisionMaker(params, this.getScoring());
    this.descriptors = descriptors;
    this.corpusEventStream = corpusEventStream;
    this.prepareData(corpusEventStream);
    this.train();
    ClassificationModel model = this.getModel(params, this.getIterations());

    if (this.eventFile != null)
      this.eventFile.delete();

    return model;
  }

  ClassificationModel getModel(PerceptronModelParameters params, int iterations) {
    PerceptronClassificationModel model = new PerceptronClassificationModel(params, config, descriptors);
    model.addModelAttribute("cutoff", this.getCutoff());
    model.addModelAttribute("iterations", this.getIterations());
    model.addModelAttribute("tolerance", this.getTolerance());
    model.addModelAttribute("averageAtIntervals", this.isAverageAtIntervals());
    model.addModelAttribute("scoring", this.getScoring());

    model.getModelAttributes().putAll(corpusEventStream.getAttributes());

    return model;
  }

  @Override
  public void setParameters(Config config) {
    this.config = config;

    Config perceptronConfig = config.getConfig("Perceptron");

    this.setCutoff(config.getInt("cutoff"));
    this.setIterations(config.getInt("iterations"));
    this.setTolerance(perceptronConfig.getDouble("tolerance"));
    this.setAverageAtIntervals(perceptronConfig.getBoolean("average-at-intervals"));
    this.setScoring(PerceptronScoring.valueOf(perceptronConfig.getString("scoring")));
  }

  public PerceptronScoring getScoring() {
    return scoring;
  }

  public void setScoring(PerceptronScoring scoring) {
    this.scoring = scoring;
  }

}
