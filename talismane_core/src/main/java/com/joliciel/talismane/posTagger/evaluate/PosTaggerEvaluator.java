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
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.Talismane.Module;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.lexicon.LexicalEntry;
import com.joliciel.talismane.posTagger.NonDeterministicPosTagger;
import com.joliciel.talismane.posTagger.PosTagAnnotatedCorpusReader;
import com.joliciel.talismane.posTagger.PosTagSequence;
import com.joliciel.talismane.posTagger.PosTaggedToken;
import com.joliciel.talismane.posTagger.PosTagger;
import com.joliciel.talismane.posTagger.PosTaggers;
import com.joliciel.talismane.rawText.Sentence;
import com.joliciel.talismane.tokeniser.TokenSequence;
import com.joliciel.talismane.tokeniser.Tokeniser;
import com.typesafe.config.Config;

/**
 * An interface for evaluating a given pos tagger.
 * 
 * @author Assaf Urieli
 *
 */
public class PosTaggerEvaluator {
  private static final Logger LOG = LoggerFactory.getLogger(PosTaggerEvaluator.class);

  private final PosTagger posTagger;
  private final PosTagAnnotatedCorpusReader corpusReader;
  private final Tokeniser tokeniser;

  private List<PosTagEvaluationObserver> observers = new ArrayList<PosTagEvaluationObserver>();

  public PosTaggerEvaluator(Reader evalReader, File outDir, String sessionId)
      throws IOException, ClassNotFoundException, ReflectiveOperationException, TalismaneException {
    Config config = ConfigFactory.load();
    this.observers = PosTagEvaluationObserver.getObservers(outDir, sessionId);

    Config posTaggerConfig = config.getConfig("talismane.core." + sessionId + ".pos-tagger");

    this.corpusReader = PosTagAnnotatedCorpusReader.getCorpusReader(evalReader, posTaggerConfig.getConfig("input"), sessionId);

    this.posTagger = PosTaggers.getPosTagger(sessionId);

    Module startModule = Module.valueOf(posTaggerConfig.getString("evaluate.start-module"));
    if (startModule == Module.tokeniser)
      this.tokeniser = Tokeniser.getInstance(sessionId);
    else
      this.tokeniser = null;
  }

  /**
   * 
   * @param posTagger
   * @param corpusReader
   *          for reading manually tagged tokens from a corpus
   * 
   * @param tokeniser
   *          if not null, evaluate tokenisation as well.
   */
  public PosTaggerEvaluator(PosTagger posTagger, PosTagAnnotatedCorpusReader corpusReader, Tokeniser tokeniser, String sessionId) {
    this.posTagger = posTagger;
    this.corpusReader = corpusReader;
    this.tokeniser = tokeniser;
  }

  /**
   * Evaluate a given pos tagger.
   * 
   * @throws TalismaneException
   * @throws IOException
   * 
   */
  public void evaluate() throws TalismaneException, IOException {
    while (corpusReader.hasNextSentence()) {
      PosTagSequence realPosTagSequence = corpusReader.nextPosTagSequence();

      List<TokenSequence> tokenSequences = null;
      List<PosTagSequence> guessedSequences = null;

      TokenSequence tokenSequence = realPosTagSequence.getTokenSequence();
      PosTagSequence guessedSequence = null;

      if (this.tokeniser != null) {
        Sentence sentence = tokenSequence.getSentence();

        tokenSequences = tokeniser.tokenise(sentence);
        tokenSequence = tokenSequences.get(0);
      } else {
        tokenSequences = new ArrayList<TokenSequence>();
        tokenSequences.add(tokenSequence);
      }

      if (posTagger instanceof NonDeterministicPosTagger) {
        NonDeterministicPosTagger nonDeterministicPosTagger = (NonDeterministicPosTagger) posTagger;
        guessedSequences = nonDeterministicPosTagger.tagSentence(tokenSequences);
        guessedSequence = guessedSequences.get(0);
      } else {
        guessedSequence = posTagger.tagSentence(tokenSequence);
      }

      if (LOG.isDebugEnabled()) {
        StringBuilder stringBuilder = new StringBuilder();
        for (PosTaggedToken posTaggedToken : guessedSequence) {
          Set<String> lemmas = new TreeSet<String>();
          stringBuilder.append(posTaggedToken.getToken().getOriginalText());
          stringBuilder.append("[" + posTaggedToken.getTag());

          List<LexicalEntry> entries = posTaggedToken.getLexicalEntries();
          boolean dropCurrentWord = false;
          if (entries.size() > 1)
            dropCurrentWord = true;
          for (LexicalEntry entry : posTaggedToken.getLexicalEntries()) {
            if (!lemmas.contains(entry.getLemma())) {
              if (dropCurrentWord && posTaggedToken.getToken().getText().equals(entry.getLemma())) {
                dropCurrentWord = false;
                continue;
              }
              stringBuilder.append("|" + entry.getLemma());
              // stringBuilder.append("/" + entry.getCategory());
              stringBuilder.append("/" + entry.getMorphology());
              lemmas.add(entry.getLemma());
            }
          }
          stringBuilder.append("] ");
        }
        LOG.debug(stringBuilder.toString());
      }

      for (PosTagEvaluationObserver observer : this.observers) {
        observer.onNextPosTagSequence(realPosTagSequence, guessedSequences);
      }
    } // next sentence

    for (PosTagEvaluationObserver observer : this.observers) {
      observer.onEvaluationComplete();
    }
  }

  public PosTagger getPosTagger() {
    return posTagger;
  }

  /**
   * A tokeniser to tokenise the sentences brought back by the corpus reader,
   * rather than automatically using their existing tokenisation.
   */
  public Tokeniser getTokeniser() {
    return tokeniser;
  }

  public List<PosTagEvaluationObserver> getObservers() {
    return observers;
  }

  public void setObservers(List<PosTagEvaluationObserver> observers) {
    this.observers = observers;
  }

  public void addObserver(PosTagEvaluationObserver observer) {
    this.observers.add(observer);
  }

}
