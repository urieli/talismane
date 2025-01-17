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
package com.joliciel.talismane.posTagger.evaluate;

import java.io.File;
import java.util.List;

import com.joliciel.talismane.Talismane;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.posTagger.PosTagSequence;
import com.joliciel.talismane.stats.FScoreCalculator;
import com.joliciel.talismane.tokeniser.TaggedToken;
import com.typesafe.config.Config;

/**
 * Calculates the f-score of a pos-tag evaluation.
 * 
 * @author Assaf Urieli
 *
 */
public class PosTagFScoreCalculator implements PosTagEvaluationObserver {
  private static final Logger LOG = LoggerFactory.getLogger(PosTagFScoreCalculator.class);
  private FScoreCalculator<String> fScoreCalculator = new FScoreCalculator<String>();
  private FScoreCalculator<String> fscoreUnknownInLexicon = new FScoreCalculator<String>();
  private FScoreCalculator<String> fscoreKnownInLexicon = new FScoreCalculator<String>();

  private final File fScoreFile;
  private final File fScoreUnknownInLexiconFile;
  private final File fScoreKnownInLexiconFile;

  public PosTagFScoreCalculator(File outDir, String sessionId) {
    Config config = ConfigFactory.load();
    Config posTaggerConfig = config.getConfig("talismane.core." + sessionId + ".pos-tagger");
    Config evalConfig = posTaggerConfig.getConfig("evaluate");

    this.fScoreFile = new File(outDir, TalismaneSession.get(sessionId).getBaseName() + "_fscores.csv");

    if (evalConfig.getBoolean("include-unknown-word-results")) {
      this.fScoreUnknownInLexiconFile = new File(outDir, TalismaneSession.get(sessionId).getBaseName() + "_unknown.csv");
      this.fScoreKnownInLexiconFile = new File(outDir, TalismaneSession.get(sessionId).getBaseName() + "_known.csv");
    } else {
      this.fScoreUnknownInLexiconFile = null;
      this.fScoreKnownInLexiconFile = null;
    }
  }

  @Override
  public void onNextPosTagSequence(PosTagSequence realSequence, List<PosTagSequence> guessedSequences) throws TalismaneException {

    PosTagSequence guessedSequence = guessedSequences.get(0);

    int j = 0;
    for (int i = 0; i < realSequence.size(); i++) {
      TaggedToken<PosTag> realToken = realSequence.get(i);
      TaggedToken<PosTag> testToken = guessedSequence.get(j);

      // special handling for null tags & empty tokens
      if (realToken.getTag().equals(PosTag.NULL_POS_TAG)) {
        // If the real token is null (and presumably empty)
        // we don't include it in our stats
        // We assume the previous non-empty token took care of any
        // required comparisons.
        if (testToken.getToken().isEmpty()) {
          j++;
        }
        continue;
      } else if (testToken.getToken().isEmpty() && !realToken.getToken().isEmpty()) {
        // If the test token is empty, but the real token isn't, we skip
        // this as well
        // Again, we assume the previous non-empty token took care of
        // any required comparisons.
        j++;
        testToken = guessedSequence.get(j);
      }

      boolean tokenError = false;
      if (realToken.getToken().getStartIndex() == testToken.getToken().getStartIndex()
          && realToken.getToken().getEndIndex() == testToken.getToken().getEndIndex()) {
        // no token error
        j++;
        if (j == guessedSequence.size()) {
          j--;
        }
      } else {
        tokenError = true;
        while (realToken.getToken().getEndIndex() >= testToken.getToken().getEndIndex()) {
          j++;
          if (j == guessedSequence.size()) {
            j--;
            break;
          }
          testToken = guessedSequence.get(j);
        }
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace("Token " + testToken.getToken().getAnalyisText() + ", guessed: " + testToken.getTag().getCode() + " ("
            + testToken.getDecision().getProbability() + "), actual: " + realToken.getTag().getCode());
      }

      String result = testToken.getTag().getCode();
      if (tokenError)
        result = "TOKEN_ERROR";

      fScoreCalculator.increment(realToken.getTag().getCode(), result);
      if (testToken.getToken().getPossiblePosTags() == null || testToken.getToken().getPossiblePosTags().size() == 0)
        fscoreUnknownInLexicon.increment(realToken.getTag().getCode(), result);
      else
        fscoreKnownInLexicon.increment(realToken.getTag().getCode(), result);
    }
  }

  /**
   * An overall f-score calculator for all words.
   */
  public FScoreCalculator<String> getFScoreCalculator() {
    return fScoreCalculator;
  }

  /**
   * An f-score calculator for unknown words in the lexicon.
   */
  public FScoreCalculator<String> getFscoreUnknownInLexicon() {
    return fscoreUnknownInLexicon;
  }

  @Override
  public void onEvaluationComplete() {
    fScoreCalculator.getTotalFScore();
    if (fScoreFile != null) {
      fScoreCalculator.writeScoresToCSVFile(fScoreFile);
    }
    if (fScoreUnknownInLexiconFile != null) {
      fscoreUnknownInLexicon.writeScoresToCSVFile(fScoreUnknownInLexiconFile);
    }
    if (fScoreKnownInLexiconFile != null) {
      fscoreKnownInLexicon.writeScoresToCSVFile(fScoreKnownInLexiconFile);
    }
  }
}
