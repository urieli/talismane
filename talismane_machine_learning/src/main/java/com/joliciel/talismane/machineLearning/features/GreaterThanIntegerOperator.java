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
 * Returns operand1 &gt; operand2.
 * 
 * @author Assaf Urieli
 *
 */
public class GreaterThanIntegerOperator<T> extends AbstractCachableFeature<T, Boolean>implements BooleanFeature<T> {
  private IntegerFeature<T> operand1;
  private IntegerFeature<T> operand2;

  public GreaterThanIntegerOperator(IntegerFeature<T> operand1, IntegerFeature<T> operand2) {
    super();
    this.operand1 = operand1;
    this.operand2 = operand2;
    this.setName("(" + operand1.getName() + ">" + operand2.getName() + ")");
  }

  @Override
  protected FeatureResult<Boolean> checkInternal(T context, RuntimeEnvironment env) throws TalismaneException {
    FeatureResult<Boolean> featureResult = null;

    FeatureResult<Integer> operand1Result = operand1.check(context, env);
    FeatureResult<Integer> operand2Result = operand2.check(context, env);

    if (operand1Result != null && operand2Result != null) {
      boolean result = operand1Result.getOutcome() > operand2Result.getOutcome();
      featureResult = this.generateResult(result);
    }

    return featureResult;
  }

  public IntegerFeature<T> getOperand1() {
    return operand1;
  }

  public void setOperand1(IntegerFeature<T> operand1) {
    this.operand1 = operand1;
  }

  public IntegerFeature<T> getOperand2() {
    return operand2;
  }

  public void setOperand2(IntegerFeature<T> operand2) {
    this.operand2 = operand2;
  }

}
