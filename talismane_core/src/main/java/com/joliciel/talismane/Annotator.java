///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2016 Joliciel Informatique
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

package com.joliciel.talismane;

import java.io.IOException;

/**
 * Implemented by classes which take an annotated text and add zero or more
 * annotations.
 * 
 * @author Assaf Urieli
 *
 */
public interface Annotator<T extends AnnotatedText> {
	/**
	 * Annotates the text provided, and adds the labels to all annotations.
	 * 
	 * @throws TalismaneException
	 * @throws IOException
	 */
	public void annotate(T annotatedText, String... labels) throws TalismaneException, IOException;
}
