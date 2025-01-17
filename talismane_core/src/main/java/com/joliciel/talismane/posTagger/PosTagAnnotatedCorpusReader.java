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
package com.joliciel.talismane.posTagger;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;

import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.parser.ParserAnnotatedCorpusReader;
import com.joliciel.talismane.tokeniser.TokeniserAnnotatedCorpusReader;
import com.joliciel.talismane.utils.io.CurrentFileObserver;
import com.joliciel.talismane.utils.io.CurrentFileProvider;
import com.typesafe.config.Config;

/**
 * An interface for reading tokenized and tagged sentences from a corpus.
 * 
 * @author Assaf Urieli
 *
 */
public interface PosTagAnnotatedCorpusReader extends TokeniserAnnotatedCorpusReader {
  /**
   * Read the list of tagged tokens from next sentence from the training corpus.
   * 
   * @throws TalismaneException
   *           if it's logically impossible to read the next pos-tag sequence
   * @throws IOException
   */
  public abstract PosTagSequence nextPosTagSequence() throws TalismaneException, IOException;

  /**
   * Builds the reader configured at "talismane.core.[sessionId].parser.input"
   */
  static PosTagAnnotatedCorpusReader getConfiguredReader(Reader reader, Config config, String sessionId) throws ReflectiveOperationException, IOException {
    return getCorpusReader(reader, config.getConfig("talismane.core." + sessionId + ".parser.input"), sessionId);
  }

  /**
   * Builds an annotated corpus reader for a particular Reader and Config, where
   * the config is the local namespace. For configuration example, see
   * talismane.core.generic.tokeniser.input in reference.conf.
   * 
   * @param config
   *          the local configuration section from which we're building a reader
   * @throws IOException
   *           problem reading the files referred in the configuration
   * @throws ReflectiveOperationException
   *           if the corpus-reader class could not be instantiated
   */
  public static PosTagAnnotatedCorpusReader getCorpusReader(Reader reader, Config config, String sessionId)
      throws IOException, ReflectiveOperationException {
    String className = config.getString("corpus-reader");

    @SuppressWarnings("unchecked")
    Class<? extends PosTagAnnotatedCorpusReader> clazz = (Class<? extends PosTagAnnotatedCorpusReader>) Class.forName(className);
    Constructor<? extends PosTagAnnotatedCorpusReader> cons = clazz.getConstructor(Reader.class, Config.class, String.class);

    PosTagAnnotatedCorpusReader corpusReader = cons.newInstance(reader, config, sessionId);
    if (reader instanceof CurrentFileProvider && corpusReader instanceof CurrentFileObserver) {
      ((CurrentFileProvider) reader).addCurrentFileObserver((CurrentFileObserver) corpusReader);
    }
    return corpusReader;
  }
}
