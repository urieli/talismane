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
package com.joliciel.talismane.posTagger.features;

import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.machineLearning.features.StringFeature;
import com.joliciel.talismane.posTagger.PosTaggedToken;

/**
 * The actual text of a given token.
 * 
 * @author Assaf Urieli
 *
 */
public final class WordFormFeature<T> extends AbstractPosTaggedTokenFeature<T, String>implements StringFeature<T> {
  public WordFormFeature(PosTaggedTokenAddressFunction<T> addressFunction) {
    super(addressFunction);
    this.setAddressFunction(addressFunction);
  }

  @Override
  public FeatureResult<String> checkInternal(T context, RuntimeEnvironment env) throws TalismaneException {
    PosTaggedTokenWrapper innerWrapper = this.getToken(context, env);
    if (innerWrapper == null)
      return null;
    PosTaggedToken posTaggedToken = innerWrapper.getPosTaggedToken();
    if (posTaggedToken == null)
      return null;

    FeatureResult<String> featureResult = null;
    featureResult = this.generateResult(posTaggedToken.getToken().getAnalyisText());
    return featureResult;
  }

}
