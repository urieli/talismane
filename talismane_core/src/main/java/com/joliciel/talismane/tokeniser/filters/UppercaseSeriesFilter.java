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
package com.joliciel.talismane.tokeniser.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.lexicon.Diacriticizer;
import com.joliciel.talismane.tokeniser.Token;
import com.joliciel.talismane.tokeniser.TokenSequence;

/**
 * Looks for a series of 2+ words in all upper-case, and transforms them into
 * lower-case. Unknown words will be given an initial upper case.
 * 
 * @author Assaf Urieli
 *
 */
public class UppercaseSeriesFilter implements TokenFilter {
  private final String sessionId;

  public UppercaseSeriesFilter(String sessionId) {
    this.sessionId = sessionId;
  }

  @Override
  public void apply(TokenSequence tokenSequence) {
    List<Token> upperCaseSequence = new ArrayList<Token>();
    for (Token token : tokenSequence) {
      String word = token.getText();

      if (word.length() == 0)
        continue;

      boolean hasLowerCase = false;
      boolean hasUpperCase = false;
      for (int i = 0; i < word.length(); i++) {
        char c = word.charAt(i);
        if (Character.isUpperCase(c)) {
          hasUpperCase = true;
        }
        if (Character.isLowerCase(c)) {
          hasLowerCase = true;
          break;
        }
      }

      if (hasUpperCase && !hasLowerCase) {
        upperCaseSequence.add(token);
      } else if (!hasLowerCase) {
        // do nothing, might be punctuation or number in middle of upper case
        // sequence
      } else {
        if (upperCaseSequence.size() > 1) {
          this.checkSequence(upperCaseSequence);
        }
        upperCaseSequence.clear();
      }
    } // next token
    if (upperCaseSequence.size() > 1) {
      this.checkSequence(upperCaseSequence);
    }
  }

  void checkSequence(List<Token> upperCaseSequence) {
    for (Token token : upperCaseSequence) {
      token.setText(getKnownWord(this.sessionId, token.getText()));
    }
  }

  public static String getKnownWord(String sessionId, String word) {
    String knownWord = word;
    boolean foundWord = false;
    Diacriticizer diacriticizer = TalismaneSession.get(sessionId).getDiacriticizer();
    Set<String> lowercaseForms = diacriticizer.diacriticize(word);
    if (lowercaseForms.size() > 0) {
      knownWord = lowercaseForms.iterator().next();
      foundWord = true;
    }
    if (!foundWord) {
      if (word.length() > 0) {
        knownWord = word.substring(0, 1) + word.substring(1).toLowerCase(TalismaneSession.get(sessionId).getLocale());
      }
    }
    return knownWord;
  }
}
