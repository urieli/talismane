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
package com.joliciel.talismane.sentenceDetector.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.joliciel.talismane.NeedsSessionId;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.machineLearning.features.AbstractFeature;
import com.joliciel.talismane.machineLearning.features.AbstractFeatureParser;
import com.joliciel.talismane.machineLearning.features.Feature;
import com.joliciel.talismane.machineLearning.features.FeatureClassContainer;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.FeatureWrapper;
import com.joliciel.talismane.machineLearning.features.FunctionDescriptor;
import com.joliciel.talismane.machineLearning.features.FunctionDescriptorParser;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.sentenceDetector.PossibleSentenceBoundary;

/**
 * A parser for sentence detector descriptors.<br>
 * The list of available features is given in
 * {@link #addFeatureClasses(FeatureClassContainer)}.
 * 
 * @author Assaf Urieli
 *
 */
public class SentenceDetectorFeatureParser extends AbstractFeatureParser<PossibleSentenceBoundary> {
  private final String sessionId;

  public SentenceDetectorFeatureParser(String sessionId) {
    this.sessionId = sessionId;
    this.setExternalResourceFinder(TalismaneSession.get(sessionId).getExternalResourceFinder());
  }

  public Set<SentenceDetectorFeature<?>> getFeatureSet(List<String> featureDescriptors) {
    Set<SentenceDetectorFeature<?>> features = new TreeSet<SentenceDetectorFeature<?>>();

    FunctionDescriptorParser descriptorParser = new FunctionDescriptorParser();

    for (String featureDescriptor : featureDescriptors) {
      if (featureDescriptor.length() > 0 && !featureDescriptor.startsWith("#")) {
        FunctionDescriptor functionDescriptor = descriptorParser.parseDescriptor(featureDescriptor);
        List<SentenceDetectorFeature<?>> myFeatures = this.parseDescriptor(functionDescriptor);
        features.addAll(myFeatures);
      }
    }
    return features;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public List<SentenceDetectorFeature<?>> parseDescriptor(FunctionDescriptor functionDescriptor) {
    List<Feature<PossibleSentenceBoundary, ?>> tokenFeatures = this.parse(functionDescriptor);
    List<SentenceDetectorFeature<?>> wrappedFeatures = new ArrayList<SentenceDetectorFeature<?>>();
    for (Feature<PossibleSentenceBoundary, ?> tokenFeature : tokenFeatures) {
      SentenceDetectorFeature<?> wrappedFeature = null;
      if (tokenFeature instanceof SentenceDetectorFeature) {
        wrappedFeature = (SentenceDetectorFeature<?>) tokenFeature;
      } else {
        wrappedFeature = new SentenceDetectorFeatureWrapper(tokenFeature);
      }
      wrappedFeatures.add(wrappedFeature);
    }
    return wrappedFeatures;
  }

  /**
   * Adds the following feature class mappings:<br>
   * - BoundaryString: {@link BoundaryStringFeature}<br>
   * - InParentheses: {@link InParenthesesFeature}<br>
   * - Initials: {@link InitialsFeature}<br>
   * - IsStrongPunctuation: {@link IsStrongPunctuationFeature}<br>
   * - NextLetterCapital: {@link NextLetterCapitalFeature}<br>
   * - NextLetters: {@link NextLettersFeature}<br>
   * - NextTokens: {@link NextTokensFeature}<br>
   * - PreviousLetters: {@link PreviousLettersFeature}<br>
   * - PreviousTokens: {@link PreviousTokensFeature}<br>
   * - Surroundings: {@link SurroundingsFeature}<br>
   */
  @Override
  public void addFeatureClasses(FeatureClassContainer container) {
    container.addFeatureClass("BoundaryString", BoundaryStringFeature.class);
    container.addFeatureClass("InParentheses", InParenthesesFeature.class);
    container.addFeatureClass("Initials", InitialsFeature.class);
    container.addFeatureClass("IsStrongPunctuation", IsStrongPunctuationFeature.class);
    container.addFeatureClass("NextLetterCapital", NextLetterCapitalFeature.class);
    container.addFeatureClass("NextLetters", NextLettersFeature.class);
    container.addFeatureClass("NextTokens", NextTokensFeature.class);
    container.addFeatureClass("PreviousLetters", PreviousLettersFeature.class);
    container.addFeatureClass("PreviousTokens", PreviousTokensFeature.class);
    container.addFeatureClass("Surroundings", SurroundingsFeature.class);
  }

  @Override
  public List<FunctionDescriptor> getModifiedDescriptors(FunctionDescriptor functionDescriptor) {
    List<FunctionDescriptor> descriptors = new ArrayList<FunctionDescriptor>();

    descriptors.add(functionDescriptor);

    return descriptors;
  }

  @Override
  public void injectDependencies(@SuppressWarnings("rawtypes") Feature feature) {
    if (feature instanceof NeedsSessionId) {
      ((NeedsSessionId) feature).setSessionId(sessionId);
    }
  }

  private static class SentenceDetectorFeatureWrapper<T> extends AbstractFeature<PossibleSentenceBoundary, T>
      implements SentenceDetectorFeature<T>, FeatureWrapper<PossibleSentenceBoundary, T> {
    private Feature<PossibleSentenceBoundary, T> wrappedFeature = null;

    public SentenceDetectorFeatureWrapper(Feature<PossibleSentenceBoundary, T> wrappedFeature) {
      super();
      this.wrappedFeature = wrappedFeature;
      this.setName(wrappedFeature.getName());
      this.setCollectionName(wrappedFeature.getCollectionName());
    }

    @Override
    public FeatureResult<T> check(PossibleSentenceBoundary context, RuntimeEnvironment env) throws TalismaneException {
      return wrappedFeature.check(context, env);
    }

    @Override
    public Feature<PossibleSentenceBoundary, T> getWrappedFeature() {
      return this.wrappedFeature;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class<? extends Feature> getFeatureType() {
      return wrappedFeature.getFeatureType();
    }
  }

  @Override
  protected boolean canConvert(Class<?> parameterType, Class<?> originalArgumentType) {
    return false;
  }

  @Override
  protected Feature<PossibleSentenceBoundary, ?> convertArgument(Class<?> parameterType, Feature<PossibleSentenceBoundary, ?> originalArgument) {
    return null;
  }

  @Override
  public Feature<PossibleSentenceBoundary, ?> convertFeatureCustomType(Feature<PossibleSentenceBoundary, ?> feature) {
    return null;
  }
}
