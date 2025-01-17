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

import java.util.List;

/**
 * A concrete class useful for testing the AbstractFeatureParser functionality.
 * 
 * @author Assaf Urieli
 *
 */
final class TestParser extends AbstractFeatureParser<String> {

  public TestParser() {
  }

  @Override
  public void addFeatureClasses(FeatureClassContainer container) {
    container.addFeatureClass("Length", StringLengthTestFeature.class);
    container.addFeatureClass("Substring", SubstringTestFeature.class);
    container.addFeatureClass("TestStringCollectionFeature", TestStringCollectionFeature.class);
  }

  @Override
  public List<FunctionDescriptor> getModifiedDescriptors(FunctionDescriptor functionDescriptor) {
    return null;
  }

  @Override
  public void injectDependencies(@SuppressWarnings("rawtypes") Feature feature) {

  }

  @Override
  protected boolean canConvert(Class<?> parameterType, Class<?> originalArgumentType) {
    return false;
  }

  @Override
  protected Feature<String, ?> convertArgument(Class<?> parameterType, Feature<String, ?> originalArgument) {
    return null;
  }

  @Override
  public Feature<String, ?> convertFeatureCustomType(Feature<String, ?> feature) {
    return null;
  }

}
