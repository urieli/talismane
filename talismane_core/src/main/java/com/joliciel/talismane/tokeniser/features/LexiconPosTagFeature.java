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

import com.joliciel.talismane.NeedsSessionId;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.machineLearning.features.BooleanFeature;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.machineLearning.features.StringFeature;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.tokeniser.Token;

/**
 * Returns true if the token has a lexical entry for any one of the PosTags
 * provided.
 * 
 * @author Assaf Urieli
 *
 */
public final class LexiconPosTagFeature extends AbstractTokenFeature<Boolean>implements BooleanFeature<TokenWrapper>, NeedsSessionId {
  StringFeature<TokenWrapper>[] posTagFeatures;

  String sessionId;

  @SafeVarargs
  public LexiconPosTagFeature(StringFeature<TokenWrapper>... posTagFeatures) {
    this.posTagFeatures = posTagFeatures;
    String name = super.getName() + "(";
    boolean first = true;
    for (StringFeature<TokenWrapper> posTagFeature : posTagFeatures) {
      if (!first)
        name += ",";
      name += posTagFeature.getName();
      first = false;
    }
    name += ")";
    this.setName(name);
  }

  @SafeVarargs
  public LexiconPosTagFeature(TokenAddressFunction<TokenWrapper> addressFunction, StringFeature<TokenWrapper>... posTagFeatures) {
    this(posTagFeatures);
    this.setAddressFunction(addressFunction);
  }

  @Override
  public FeatureResult<Boolean> checkInternal(TokenWrapper tokenWrapper, RuntimeEnvironment env) throws TalismaneException {
    TokenWrapper innerWrapper = this.getToken(tokenWrapper, env);
    if (innerWrapper == null)
      return null;
    Token token = innerWrapper.getToken();
    FeatureResult<Boolean> result = null;

    boolean matches = false;
    for (StringFeature<TokenWrapper> posTagFeature : posTagFeatures) {
      FeatureResult<String> posTagResult = posTagFeature.check(innerWrapper, env);
      if (posTagResult != null) {
        PosTag posTag = TalismaneSession.get(sessionId).getPosTagSet().getPosTag(posTagResult.getOutcome());
        boolean hasPosTag = (token.getPossiblePosTags().contains(posTag));
        if (hasPosTag) {
          matches = true;
          break;
        }
      }
    }

    result = this.generateResult(matches);

    return result;
  }
  
  @Override
  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
