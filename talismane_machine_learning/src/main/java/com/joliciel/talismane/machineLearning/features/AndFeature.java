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
package com.joliciel.talismane.machineLearning.features;

import com.joliciel.talismane.TalismaneException;

/**
 * Combines any number boolean features using a boolean AND. If any of the
 * features is null, will return null.
 * 
 * @author Assaf Urieli
 *
 */
public class AndFeature<T> extends AbstractCachableFeature<T, Boolean>implements BooleanFeature<T> {
  BooleanFeature<T>[] booleanFeatures;

  @SafeVarargs
  public AndFeature(BooleanFeature<T>... booleanFeatures) {
    super();
    this.booleanFeatures = booleanFeatures;
    String name = "And(";
    boolean firstFeature = true;
    for (BooleanFeature<T> booleanFeature : booleanFeatures) {
      if (!firstFeature)
        name += ",";
      name += booleanFeature.getName();
      firstFeature = false;
    }
    name += ")";
    this.setName(name);
  }

  @Override
  public FeatureResult<Boolean> checkInternal(T context, RuntimeEnvironment env) throws TalismaneException {
    FeatureResult<Boolean> featureResult = null;

    boolean hasNull = false;
    boolean booleanResult = true;
    for (BooleanFeature<T> booleanFeature : booleanFeatures) {
      FeatureResult<Boolean> result = booleanFeature.check(context, env);
      if (result == null) {
        hasNull = true;
        break;
      }
      booleanResult = booleanResult && result.getOutcome();
      // not breaking out as soon as we hit a false,
      // since if any single feature returns a null, we want to return a
      // null
    }

    if (!hasNull) {
      featureResult = this.generateResult(booleanResult);
    }
    return featureResult;
  }

  public BooleanFeature<T>[] getBooleanFeatures() {
    return booleanFeatures;
  }
}
