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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.joliciel.talismane.NeedsTalismaneSession;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.tokeniser.Token;
import com.joliciel.talismane.tokeniser.TokenSequence;
import com.joliciel.talismane.tokeniser.filters.TokenSequenceFilter;

/**
 * Transforms a word into lower-case if it is a known word in the lexicon,
 * and if it is at the start of a sentence or just following a quote.
 * @author Assaf Urieli
 *
 */
public class LowercaseKnownFirstWordFilter implements TokenSequenceFilter, NeedsTalismaneSession {
	private static List<String> openingPunctuationList = Arrays.asList("\"", "-", "--", "—", "*", "(", "•", "[", "{" );
	private Set<String> openingPunctuation;
	private TalismaneSession talismaneSession;
	
	public LowercaseKnownFirstWordFilter() {
		super();
		openingPunctuation = new HashSet<String>(openingPunctuationList);
	}

	@Override
	public void apply(TokenSequence tokenSequence) {
		int startIndex = 0;
		if (tokenSequence.isWithRoot())
			startIndex += 1;
		String word0 = "";
		String word1 = "";
		String word2 = "";
		
		if (tokenSequence.size()>startIndex) word0 = tokenSequence.get(startIndex).getText();
		if (tokenSequence.size()>startIndex+1) word1 = tokenSequence.get(startIndex+1).getText();
		if (tokenSequence.size()>startIndex+2) word2 = tokenSequence.get(startIndex+2).getText();

		boolean word0IsInteger = false;
		try {
			Integer.parseInt(word0);
			word0IsInteger = true;
		} catch (NumberFormatException nfe) {
			word0IsInteger = false;
		}
		
		boolean word1IsInteger = false;
		try {
			Integer.parseInt(word1);
			word1IsInteger = true;
		} catch (NumberFormatException nfe) {
			word1IsInteger = false;
		}
		
		if (openingPunctuation.contains(word0)) {
			startIndex += 1;
		} else if ((word0IsInteger||word0.length()==1)
				&& (word1.equals(")")||word1.equals(".")||word1.equals("]"))) {
			startIndex += 2;
		} else if (word0.equals("(")
				&& (word1IsInteger||word1.length()==1)
				&& word2.equals(")")) {
			startIndex += 3;
		}
		
		boolean lowerCaseNextWord = true;
		int index = -1;
		for (Token token : tokenSequence) {
			index++;
			if (index < startIndex)
				continue;
			
			if (token.getText().length()==0)
				continue;
			
			if (lowerCaseNextWord) {
				char firstChar = token.getText().charAt(0);
				if (Character.isUpperCase(firstChar)) {
					char[] firstChars = null;
					switch (firstChar) {
						case 'E':
							firstChars = new char[] {'e', 'é', 'ê', 'è'};
							break;
						case 'A':
							firstChars = new char[] {'à', 'a', 'â', 'á'};
							break;
						case 'O':
							firstChars  = new char[] {'o', 'ô', 'ò', 'ó'};
							break;
						case 'I':
							firstChars  = new char[] {'i', 'î', 'ï', 'í'};
							break;
						case 'U':
							firstChars = new char[] {'u', 'ú', 'ü'};
							break;
						case 'C':
							firstChars = new char[] {'c', 'ç'};
							break;
						default:
							firstChars = new char[] {Character.toLowerCase(firstChar)};
							break;
					}
					boolean foundWord = false;
					for (char c : firstChars) {
						String newWord = c + token.getText().substring(1);
						Set<PosTag> posTags = talismaneSession.getMergedLexicon().findPossiblePosTags(newWord);
						if (posTags.size()>0) {
							token.setText(newWord);
							foundWord = true;
							break;
						}
					}
					if (!foundWord) {
						// if it's an unknown word, don't lower-case it (could be a proper noun)
						// hence do nothing
					}
				} // next word starts with an upper-case
				lowerCaseNextWord = false;
			} // should we lower-case the next word?
			if (token.getText().equals("\"")||token.getText().equals("...")||token.getText().equals(":")||token.getText().equals(";")||token.getText().equals("(")) {
				lowerCaseNextWord = true;
			}
		} // next token
	}

	public TalismaneSession getTalismaneSession() {
		return talismaneSession;
	}

	public void setTalismaneSession(TalismaneSession talismaneSession) {
		this.talismaneSession = talismaneSession;
	}
	
	
}