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
package com.joliciel.talismane.parser.features;

import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.machineLearning.features.BooleanFeature;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.parser.ParseConfiguration;
import com.joliciel.talismane.posTagger.PosTaggedToken;
import com.joliciel.talismane.posTagger.features.PosTaggedTokenAddressFunction;
import com.joliciel.talismane.posTagger.features.PosTaggedTokenWrapper;

/**
 * Looks at all pos-tagged tokens following the reference token in sequence, and
 * returns the first one matching certain criteria.
 * 
 * @author Assaf Urieli
 *
 */
public final class ForwardSearchFeature extends AbstractAddressFunction {
  private PosTaggedTokenAddressFunction<ParseConfigurationWrapper> referenceTokenFeature;
  private BooleanFeature<PosTaggedTokenWrapper> criterionFeature;

  public ForwardSearchFeature(PosTaggedTokenAddressFunction<ParseConfigurationWrapper> referenceTokenFeature,
      BooleanFeature<PosTaggedTokenWrapper> criterionFeature) {
    super();
    this.referenceTokenFeature = referenceTokenFeature;
    this.criterionFeature = criterionFeature;

    this.setName(super.getName() + "(" + referenceTokenFeature.getName() + "," + criterionFeature.getName() + ")");
  }

  @Override
  public FeatureResult<PosTaggedTokenWrapper> check(ParseConfigurationWrapper wrapper, RuntimeEnvironment env) throws TalismaneException {
    ParseConfiguration configuration = wrapper.getParseConfiguration();
    PosTaggedToken resultToken = null;
    FeatureResult<PosTaggedTokenWrapper> referenceTokenResult = referenceTokenFeature.check(wrapper, env);

    if (referenceTokenResult != null) {
      PosTaggedToken referenceToken = referenceTokenResult.getOutcome().getPosTaggedToken();

      ParseConfigurationAddress parseConfigurationAddress = new ParseConfigurationAddress(env);
      parseConfigurationAddress.setParseConfiguration(configuration);
      for (int i = referenceToken.getToken().getIndex() + 1; i < configuration.getPosTagSequence().size(); i++) {
        PosTaggedToken nextToken = configuration.getPosTagSequence().get(i);
        parseConfigurationAddress.setPosTaggedToken(nextToken);
        FeatureResult<Boolean> criterionResult = criterionFeature.check(parseConfigurationAddress, env);
        if (criterionResult != null) {
          boolean criterion = criterionResult.getOutcome();
          if (criterion) {
            resultToken = nextToken;
            break;
          }
        }
      }
    }
    FeatureResult<PosTaggedTokenWrapper> featureResult = null;
    if (resultToken != null)
      featureResult = this.generateResult(resultToken);
    return featureResult;
  }
}
