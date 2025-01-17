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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.joliciel.talismane.NeedsSessionId;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.machineLearning.features.AbstractStringCollectionFeature;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.posTagger.PosTagSet;
import com.joliciel.talismane.utils.WeightedOutcome;

/**
 * A StringCollectionFeature returning all of the postags in the current
 * postagset.
 * 
 * @author Assaf Urieli
 *
 */
public final class PosTagSetFeature extends AbstractStringCollectionFeature<TokenWrapper> implements NeedsSessionId {

  String sessionId;

  @Override
  public FeatureResult<List<WeightedOutcome<String>>> checkInternal(TokenWrapper context, RuntimeEnvironment env) {
    PosTagSet posTagSet = TalismaneSession.get(sessionId).getPosTagSet();
    Set<PosTag> posTags = posTagSet.getTags();
    List<WeightedOutcome<String>> resultList = new ArrayList<WeightedOutcome<String>>();
    for (PosTag posTag : posTags) {
      resultList.add(new WeightedOutcome<String>(posTag.getCode(), 1.0));
    }
    return this.generateResult(resultList);
  }

  @Override
  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
