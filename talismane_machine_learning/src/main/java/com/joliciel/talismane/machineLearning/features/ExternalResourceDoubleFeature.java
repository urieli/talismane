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

import java.util.ArrayList;
import java.util.List;

import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.machineLearning.ExternalResource;
import com.joliciel.talismane.machineLearning.ExternalResourceFinder;
import com.joliciel.talismane.utils.JolicielException;

/**
 * An external resource feature for an external resource with a double value per
 * key.
 * 
 * @author Assaf Urieli
 *
 */
public class ExternalResourceDoubleFeature<T> extends AbstractCachableFeature<T, Double>implements DoubleFeature<T> {
  ExternalResourceFinder externalResourceFinder;

  StringFeature<T> resourceNameFeature;
  StringFeature<T>[] keyElementFeatures;

  @SafeVarargs
  public ExternalResourceDoubleFeature(StringFeature<T> resourceNameFeature, StringFeature<T>... keyElementFeatures) {
    this.resourceNameFeature = resourceNameFeature;
    this.keyElementFeatures = keyElementFeatures;

    String name = super.getName() + "(" + resourceNameFeature.getName() + ",";

    for (StringFeature<T> stringFeature : keyElementFeatures) {
      name += stringFeature.getName();
    }
    name += ")";
    this.setName(name);
  }

  @Override
  public FeatureResult<Double> checkInternal(T context, RuntimeEnvironment env) throws TalismaneException {
    FeatureResult<Double> result = null;
    FeatureResult<String> resourceNameResult = resourceNameFeature.check(context, env);
    if (resourceNameResult != null) {
      String resourceName = resourceNameResult.getOutcome();

      @SuppressWarnings("unchecked")
      ExternalResource<Double> externalResource = (ExternalResource<Double>) externalResourceFinder.getExternalResource(resourceName);
      if (externalResource == null) {
        throw new JolicielException("External resource not found: " + resourceName);
      }

      List<String> keyElements = new ArrayList<String>();
      for (StringFeature<T> stringFeature : keyElementFeatures) {
        FeatureResult<String> keyElementResult = stringFeature.check(context, env);
        if (keyElementResult == null) {
          return null;
        }
        String keyElement = keyElementResult.getOutcome();
        keyElements.add(keyElement);
      }
      Double outcome = externalResource.getResult(keyElements);
      if (outcome != null)
        result = this.generateResult(outcome);
    }

    return result;
  }

  public ExternalResourceFinder getExternalResourceFinder() {
    return externalResourceFinder;
  }

  public void setExternalResourceFinder(ExternalResourceFinder externalResourceFinder) {
    this.externalResourceFinder = externalResourceFinder;
  }

}
