///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2016 Joliciel Informatique
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
package com.joliciel.talismane.rawText;

import com.joliciel.talismane.TalismaneSession;

/**
 * An annotated text which can be filtered and sentence-detected, after which we
 * can retrieve the resulting sentences. <br>
 * <br>
 * Typical usage:
 * 
 * <pre>
 * String text = ...;
 * RawText rawText = new RawText(text, processByDefault, session);
 * 
 * // annotate  with raw text filters
 * for (RawTextAnnotator annotator : session.getTextAnnotators()) {
 *   annotator.annotate(rawText);
 * }
 * 
 * // detect sentences on processed text using the sentence detector
 * AnnotatedText processedText = rawText.getProcessedText();
 * sentenceDetector.detectSentences(processedText);
 * 
 * // get the sentences detected
 * List&lt;Sentence&gt; sentences = rawText.getDetectedSentences();
 * </pre>
 * 
 * @author Assaf Urieli
 *
 */
public class RawText extends RawTextProcessor {
  private final CharSequence text;
  private final String sessionId;

  /**
   * Constructor
   * 
   * @param text
   *          the text to analyse
   * @param processByDefault
   *          whether the text should be analysed from the start, or should wait
   *          for a filter to indicate that analysis starts (e.g. when
   *          processing XML)
   */
  public RawText(CharSequence text, boolean processByDefault, String sessionId) {
    super(text, processByDefault, sessionId);
    this.text = text;
    this.sessionId = sessionId;
  }

  @Override
  protected int getTextProcessingStart() {
    return 0;
  }

  @Override
  protected int getTextProcessingEnd() {
    return text.length();
  }

  @Override
  protected SentenceHolder getPreviousSentenceHolder() {
    return new SentenceHolder(0, false, sessionId);
  }

  @Override
  protected SentenceHolder getCurrentSentenceHolder() {
    return this.processText(0, text.length(), text, true);
  }

  @Override
  protected SentenceHolder getNextSentenceHolder() {
    return new SentenceHolder(text.length(), true, sessionId);
  }
}
