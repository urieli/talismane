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
//////////////////////////////////////////////////////////////////////////////package com.joliciel.talismane.parser;
package com.joliciel.talismane.parser.evaluate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.Talismane.Module;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.parser.DependencyArc;
import com.joliciel.talismane.parser.ParseConfiguration;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.posTagger.PosTagSequence;
import com.joliciel.talismane.posTagger.PosTaggedToken;
import com.joliciel.talismane.stats.FScoreCalculator;
import com.joliciel.talismane.utils.CSVFormatter;
import com.typesafe.config.Config;

/**
 * Calculates the f-score for each separate distance during a parse evaluation.
 * 
 * @author Assaf Urieli
 *
 */
public class ParserFScoreCalculatorByDistance implements ParseEvaluationObserver {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ParserFScoreCalculatorByDistance.class);
  private static final CSVFormatter CSV = new CSVFormatter();
  private final Map<Integer, FScoreCalculator<String>> fscoreByDistanceMap = new TreeMap<Integer, FScoreCalculator<String>>();
  private final boolean labeledEvaluation;
  private final boolean hasTokeniser;
  private final boolean hasPosTagger;
  private final Writer writer;
  private final String skipLabel;

  public ParserFScoreCalculatorByDistance(File outDir, String sessionId) throws FileNotFoundException {
    Config config = ConfigFactory.load();
    Config parserConfig = config.getConfig("talismane.core." + sessionId + ".parser");
    Config evalConfig = parserConfig.getConfig("evaluate");

    File csvFile = new File(outDir, TalismaneSession.get(sessionId).getBaseName() + "_distances.csv");
    this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile, false), TalismaneSession.get(sessionId).getCsvCharset()));

    this.labeledEvaluation = evalConfig.getBoolean("labeled-evaluation");

    Module startModule = Module.valueOf(evalConfig.getString("start-module"));
    this.hasTokeniser = (startModule == Module.tokeniser);
    this.hasPosTagger = (startModule == Module.tokeniser || startModule == Module.posTagger);
    if (evalConfig.hasPath("skip-label"))
      this.skipLabel = evalConfig.getString("skip-label");
    else
      this.skipLabel = "";
  }

  @Override
  public void onParseEnd(ParseConfiguration realConfiguration, List<ParseConfiguration> guessedConfigurations) {
    PosTagSequence posTagSequence = realConfiguration.getPosTagSequence();
    ParseConfiguration bestGuess = guessedConfigurations.get(0);
    for (PosTaggedToken posTaggedToken : posTagSequence) {
      if (posTaggedToken.getTag().equals(PosTag.ROOT_POS_TAG))
        continue;

      DependencyArc realArc = realConfiguration.getGoverningDependency(posTaggedToken);

      int depDistance = realArc.getHead().getToken().getIndex() - realArc.getDependent().getToken().getIndex();
      if (depDistance < 0)
        depDistance = 0 - depDistance;
      FScoreCalculator<String> fscoreCalculator = fscoreByDistanceMap.get(depDistance);
      if (fscoreCalculator == null) {
        fscoreCalculator = new FScoreCalculator<String>(depDistance);
        fscoreByDistanceMap.put(depDistance, fscoreCalculator);
      }
      DependencyArc guessedArc = null;
      if (!hasTokeniser && !hasPosTagger) {
        guessedArc = bestGuess.getGoverningDependency(posTaggedToken);
      } else {
        for (PosTaggedToken guessedToken : bestGuess.getPosTagSequence()) {
          if (guessedToken.getToken().getStartIndex() == posTaggedToken.getToken().getStartIndex()) {
            guessedArc = bestGuess.getGoverningDependency(guessedToken);
            break;
          }
        }
      }

      String realLabel = realArc == null ? "noHead" : labeledEvaluation ? realArc.getLabel() : "head";
      String guessedLabel = guessedArc == null ? "noHead" : labeledEvaluation ? guessedArc.getLabel() : "head";

      if (realLabel == null || realLabel.length() == 0)
        realLabel = "noLabel";
      if (guessedLabel == null || guessedLabel.length() == 0)
        guessedLabel = "noLabel";

      // anything attached "by default" to the root, without a label,
      // should be considered a "no head" rather than "no label"
      if (realArc != null && realArc.getHead().getTag().equals(PosTag.ROOT_POS_TAG) && realLabel.equals("noLabel"))
        realLabel = "noHead";
      if (guessedArc != null && guessedArc.getHead().getTag().equals(PosTag.ROOT_POS_TAG) && guessedLabel.equals("noLabel"))
        guessedLabel = "noHead";

      if (realLabel.equals(skipLabel))
        return;

      if (realArc == null || guessedArc == null) {
        fscoreCalculator.increment(realLabel, guessedLabel);
      } else {
        boolean sameHead = false;
        if (hasTokeniser || hasPosTagger)
          sameHead = realArc.getHead().getToken().getStartIndex() == guessedArc.getHead().getToken().getStartIndex();
        else
          sameHead = realArc.getHead().equals(guessedArc.getHead());

        if (sameHead) {
          fscoreCalculator.increment(realLabel, guessedLabel);
        } else if (guessedLabel.equals("noHead")) {
          fscoreCalculator.increment(realLabel, "noHead");
        } else if (realArc.getLabel().equals(guessedArc.getLabel())) {
          fscoreCalculator.increment(realLabel, "wrongHead");
        } else {
          fscoreCalculator.increment(realLabel, "wrongHeadWrongLabel");
        }

      }

    }
  }

  public Map<Integer, FScoreCalculator<String>> getFscoreByDistanceMap() {
    return fscoreByDistanceMap;
  }

  @Override
  public void onEvaluationComplete() throws IOException {
    if (writer != null) {
      Map<Integer, Integer[]> aboveBelowMap = new TreeMap<Integer, Integer[]>();
      for (int distance : this.fscoreByDistanceMap.keySet()) {
        aboveBelowMap.put(distance, new Integer[] { 0, 0, 0, 0 });
      }
      for (int key : aboveBelowMap.keySet()) {
        Integer[] measures = aboveBelowMap.get(key);
        for (int distance : this.fscoreByDistanceMap.keySet()) {
          FScoreCalculator<String> fScoreCalculator = this.fscoreByDistanceMap.get(distance);
          if (distance <= key) {
            measures[0] += fScoreCalculator.getTotalTruePositiveCount();
            measures[1] += fScoreCalculator.getTotalFalseNegativeCount();
          } else {
            measures[2] += fScoreCalculator.getTotalTruePositiveCount();
            measures[3] += fScoreCalculator.getTotalFalseNegativeCount();
          }
        }
      }
      try {
        writer.write(
            CSV.format("distance") + CSV.format("true+") + CSV.format("false-") + CSV.format("accuracy") + CSV.format("above") + CSV.format("below") + "\n");
        for (int distance : this.fscoreByDistanceMap.keySet()) {
          writer.write(distance + ",");
          FScoreCalculator<String> fScoreCalculator = this.fscoreByDistanceMap.get(distance);
          writer.write(CSV.format(fScoreCalculator.getTotalTruePositiveCount()));
          writer.write(CSV.format(fScoreCalculator.getTotalFalseNegativeCount()));
          writer.write(CSV.format(fScoreCalculator.getTotalFScore() * 100.0));

          Integer[] aboveBelowMeasures = aboveBelowMap.get(distance);
          double belowAccuracy = 0;
          double aboveAccuracy = 0;
          if (aboveBelowMeasures[0] > 0)
            belowAccuracy = (double) aboveBelowMeasures[0] / ((double) aboveBelowMeasures[0] + (double) aboveBelowMeasures[1]);
          if (aboveBelowMeasures[2] > 0)
            aboveAccuracy = (double) aboveBelowMeasures[2] / ((double) aboveBelowMeasures[2] + (double) aboveBelowMeasures[3]);

          writer.write(CSV.format(aboveAccuracy * 100.0));
          writer.write(CSV.format(belowAccuracy * 100.0));
          writer.write("\n");
          writer.flush();
        }
      } finally {
        writer.flush();
        writer.close();
      }
    }
  }

  @Override
  public void onParseStart(ParseConfiguration realConfiguration, List<PosTagSequence> posTagSequences) {
  }
}
