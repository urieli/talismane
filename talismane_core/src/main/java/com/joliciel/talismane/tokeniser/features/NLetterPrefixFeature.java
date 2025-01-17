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
package com.joliciel.talismane.tokeniser.features;

import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.IntegerFeature;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.machineLearning.features.StringFeature;
import com.joliciel.talismane.tokeniser.Token;

/**
 * Retrieves the first N letters of the first entire word in the present token,
 * as long as N &lt; the length of the first entire word.
 * 
 * @author Assaf Urieli
 *
 */
public final class NLetterPrefixFeature extends AbstractTokenFeature<String> implements StringFeature<TokenWrapper> {
  private IntegerFeature<TokenWrapper> nFeature;

  public NLetterPrefixFeature(IntegerFeature<TokenWrapper> nFeature) {
    this.nFeature = nFeature;
    this.setName(super.getName() + "(" + this.nFeature.getName() + ")");
  }

  public NLetterPrefixFeature(TokenAddressFunction<TokenWrapper> addressFunction, IntegerFeature<TokenWrapper> nFeature) {
    this(nFeature);
    this.setAddressFunction(addressFunction);
  }

  @Override
  public FeatureResult<String> checkInternal(TokenWrapper tokenWrapper, RuntimeEnvironment env) throws TalismaneException {
    TokenWrapper innerWrapper = this.getToken(tokenWrapper, env);
    if (innerWrapper == null)
      return null;
    Token token = innerWrapper.getToken();
    FeatureResult<String> result = null;

    FeatureResult<Integer> nResult = nFeature.check(innerWrapper, env);
    if (nResult != null) {
      int n = nResult.getOutcome();

      String firstWord = token.getAnalyisText().trim();
      if (firstWord.indexOf(' ') >= 0) {
        firstWord = firstWord.substring(0, firstWord.indexOf(' '));
      }

      if (firstWord.length() > n) {
        String prefix = firstWord.substring(0, n);
        result = this.generateResult(prefix);
      }
    }
    return result;
  }
}
