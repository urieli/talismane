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
package com.joliciel.talismane.parser;

import com.joliciel.talismane.posTagger.PosTaggedToken;
import com.joliciel.talismane.tokeniser.Token;

/**
 * Comparison based on number of elements remaining on the buffer.
 * 
 * @author Assaf Urieli
 *
 */
class BufferSizeComparisonStrategy implements ParseComparisonStrategy {

  @Override
  public int getComparisonIndex(ParseConfiguration configuration) {
    int compIndex = (configuration.getPosTagSequence().getTokenSequence().getAtomicTokenCount() + 1);
    // remove the atomic tokens of each element still to be processed in the
    // buffer
    for (PosTaggedToken posTaggedToken : configuration.getBuffer()) {
      Token token = posTaggedToken.getToken();
      if (token.getAtomicParts().size() == 0)
        compIndex -= 1;
      else
        compIndex -= token.getAtomicParts().size();
    }
    return compIndex;
  }
}
