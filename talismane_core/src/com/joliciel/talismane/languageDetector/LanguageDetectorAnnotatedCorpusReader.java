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
package com.joliciel.talismane.languageDetector;

import com.joliciel.talismane.AnnotatedCorpusReader;

/**
 * An interface for reading language tagged text from a training corpus.
 * @author Assaf Urieli
 *
 */
public interface LanguageDetectorAnnotatedCorpusReader extends AnnotatedCorpusReader {
	/**
	 * Is there another text to be read?
	 * @return
	 */
	public boolean hasNextText();
	
	/**
	 * Reads the next sentence from the corpus.
	 * @return
	 */
	public LanguageTaggedText nextText();
}