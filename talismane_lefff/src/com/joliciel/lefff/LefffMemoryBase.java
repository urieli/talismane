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
package com.joliciel.lefff;

import java.io.Serializable;
import java.util.Map;

import com.joliciel.talismane.lexicon.PosTaggerLexicon;
import com.joliciel.talismane.posTagger.PosTagSet;

/**
 * An in-memory representation of the lefff lexicon.
 * @author Assaf Urieli
 *
 */
public interface LefffMemoryBase extends Serializable, PosTaggerLexicon {
	public Map<PosTagSet, LefffPosTagMapper> getPosTagMappers();
}
