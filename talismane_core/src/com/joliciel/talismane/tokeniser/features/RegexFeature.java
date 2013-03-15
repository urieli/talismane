///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2012 Assaf Urieli
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

import java.util.regex.Pattern;

import com.joliciel.talismane.machineLearning.features.BooleanFeature;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.machineLearning.features.StringFeature;
import com.joliciel.talismane.tokeniser.Token;

/**
 * Returns true if the token matches a given regular expression.
 * @author Assaf Urieli
 *
 */
public class RegexFeature extends AbstractTokenFeature<Boolean> implements BooleanFeature<TokenWrapper> {
	StringFeature<TokenWrapper> regexFeature = null;
	Pattern pattern = null;
	
	public RegexFeature(StringFeature<TokenWrapper> regexFeature) {
		this.regexFeature = regexFeature;
		this.setName(super.getName() + "(" + regexFeature.getName() + ")");
	}
	
	@Override
	public FeatureResult<Boolean> checkInternal(TokenWrapper tokenWrapper, RuntimeEnvironment env) {
		Token token = tokenWrapper.getToken();
		FeatureResult<Boolean> result = null;
		
		FeatureResult<String> regexResult = regexFeature.check(tokenWrapper, env);
		if (regexResult!=null) {
			String regex = regexResult.getOutcome();
			this.pattern = Pattern.compile(regex);
	
			boolean matches = this.pattern.matcher(token.getText()).matches();
			result = this.generateResult(matches);
		}
		
		return result;
	}
}
