///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2012 Assaf Urieli
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
package com.joliciel.talismane.tokeniser;

import java.util.List;


/**
 * A sequence of tokens.
 * Note: by default, List iteration and associated methods will only return non-whitespace tokens.
 * For a list that includes whitespace tokens, use the listWithWhiteSpace() method.
 * @author Assaf Urieli
 *
 */
public interface TokenSequence extends Iterable<Token> {
	/**
	 * Get the nth token in this sequence.
	 */
	public Token get(int index);
	
	/**
	 * Size of this sequence.
	 * @return
	 */
	public int size();
	
	/**
	 * The sentence on which this token sequence was built.
	 * @return
	 */
	public String getSentence();
	
	/**
	 * Adds a token to the current sequence, using substring coordinates
	 * from the associated sentence.
	 * If a token already exists with this exact start and end, will not perform any action.
	 * Any other existing token whose start < end and end > start will be removed.
	 * @param start
	 * @param end
	 * @return
	 */
	public Token addToken(int start, int end);
	
	/**
	 * Add an empty token at a certain position in the sentence.
	 * @param position
	 * @return
	 */
	public Token addEmptyToken(int position);
	
	/**
	 * Returns the token splits represented by this token sequence,
	 * where each integer represents the symbol immediately following a token split.
	 * Only available if this TokenSequence is associated with a sentence,
	 * e.g. TokenSequence.getSentence()!=null.
	 * @return
	 */
	public List<Integer> getTokenSplits();
	
	/**
	 * A list of tokens that includes white space tokens.
	 * @return
	 */
	public List<Token> listWithWhiteSpace();
	
	/**
	 * Finalise a reconstructed token sequence so that all of the indexes
	 * are correct on the component tokens.
	 */
	public void finalise();
	
	/**
	 * The geometric mean of the tokeniser decisions.
	 * Note that only actual decisions made by a decision maker will be taken into account - any default decisions will not be included.
	 * @return
	 */
	public double getScore();
	
	/**
	 * The number of unit tokens making up this token sequence (+1 for each empty token).
	 * @return
	 */
	public int getUnitTokenCount();
	
	/**
	 * Cleans out any collections of modifications, so that any modifications after this
	 * clean slate can be viewed (see getTokensAdded()).
	 */
	public void cleanSlate();
	
	/**
	 * Returns the tokens added since the last clean slate (see cleanSlate()).
	 * @return
	 */
	public List<Token> getTokensAdded();
}
