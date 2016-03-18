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
package com.joliciel.talismane;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import com.joliciel.talismane.Talismane;
import com.joliciel.talismane.TalismaneConfig;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneService;
import com.joliciel.talismane.TalismaneServiceLocator;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.lexicon.Diacriticizer;
import com.joliciel.talismane.lexicon.LexicalEntry;
import com.joliciel.talismane.lexicon.LexiconChain;
import com.joliciel.talismane.lexicon.LexiconDeserializer;
import com.joliciel.talismane.lexicon.LexiconSerializer;
import com.joliciel.talismane.lexicon.LexiconService;
import com.joliciel.talismane.lexicon.LexiconServiceLocator;
import com.joliciel.talismane.lexicon.PosTaggerLexicon;
import com.joliciel.talismane.utils.PerformanceMonitor;
import com.joliciel.talismane.utils.StringUtils;

/**
 * Direct entry point for Talismane from the command line.
 * 
 * @author Assaf Urieli
 *
 */
public class TalismaneMain {
	private static final Log LOG = LogFactory.getLog(TalismaneMain.class);

	private enum OtherCommand {
		serializeLexicon, testLexicon, serializeDiacriticizer, testDiacriticizer,
	}

	public static void main(String[] args) throws Exception {
		Map<String, String> argsMap = StringUtils.convertArgs(args);

		if (argsMap.size() == 0) {
			System.out.println("Talismane usage instructions: ");
			System.out.println("* indicates optional, + indicates default value");
			System.out.println("");
			System.out
					.println("Usage: command=analyse *startModule=[sentence+|tokenise|postag|parse] *endModule=[sentence|tokenise|postag|parse+] *inFile=[inFilePath, stdin if missing] *outFile=[outFilePath, stdout if missing] *template=[outputTemplatePath]");
			System.out.println("");
			System.out.println("Additional optional parameters:");
			System.out
					.println(" *encoding=[UTF-8, ...] *includeDetails=[true|false+] posTaggerRules*=[posTaggerRuleFilePath] textFilters*=[regexFilterFilePath] *sentenceModel=[path] *tokeniserModel=[path] *posTaggerModel=[path] *parserModel=[path] *inputPatternFile=[inputPatternFilePath] *posTagSet=[posTagSetPath]");
			return;
		}

		OtherCommand otherCommand = null;
		if (argsMap.containsKey("command")) {
			try {
				otherCommand = OtherCommand.valueOf(argsMap.get("command"));
				argsMap.remove("command");
			} catch (IllegalArgumentException e) {
				// not an otherCommand
			}
		}

		// Configure log4j
		String logConfigPath = argsMap.get("logConfigFile");
		if (logConfigPath != null) {
			argsMap.remove("logConfigFile");
			Properties props = new Properties();
			props.load(new FileInputStream(logConfigPath));
			PropertyConfigurator.configure(props);
		} else {
			Properties props = new Properties();
			InputStream stream = TalismaneMain.class.getResourceAsStream("resources/default-log4j.properties");
			props.load(stream);
			PropertyConfigurator.configure(props);
		}

		String sessionId = "";
		TalismaneServiceLocator locator = TalismaneServiceLocator.getInstance(sessionId);
		TalismaneService talismaneService = locator.getTalismaneService();
		TalismaneSession talismaneSession = talismaneService.getTalismaneSession();

		if (otherCommand == null) {
			// regular command
			TalismaneConfig config = talismaneService.getTalismaneConfig(argsMap, sessionId);
			if (config.getCommand() == null)
				return;

			PerformanceMonitor.start(config.getPerformanceConfigFile());
			if (config.getPerformanceConfigFile() != null && PerformanceMonitor.getFilePath() == null)
				PerformanceMonitor.setFilePath(config.getBaseName() + ".performance.csv");
			try {
				Talismane talismane = config.getTalismane();
				talismane.process();
			} finally {
				PerformanceMonitor.end();
			}
		} else {
			// other command
			switch (otherCommand) {
			case serializeLexicon: {
				LexiconSerializer serializer = new LexiconSerializer();
				serializer.serializeLexicons(argsMap);
				break;
			}
			case testLexicon: {
				String lexiconFilePath = null;
				String[] wordList = null;
				for (String argName : argsMap.keySet()) {
					String argValue = argsMap.get(argName);
					if (argName.equals("lexicon")) {
						lexiconFilePath = argValue;
					} else if (argName.equals("words")) {
						wordList = argValue.split(",");
					} else {
						throw new TalismaneException("Unknown argument: " + argName);
					}
				}

				if (lexiconFilePath == null)
					throw new TalismaneException("Missing argument: lexicon");
				if (wordList == null)
					throw new TalismaneException("Missing argument: words");

				File lexiconFile = new File(lexiconFilePath);
				LexiconDeserializer lexiconDeserializer = new LexiconDeserializer(talismaneSession);
				List<PosTaggerLexicon> lexicons = lexiconDeserializer.deserializeLexicons(lexiconFile);
				for (PosTaggerLexicon lexicon : lexicons)
					talismaneSession.addLexicon(lexicon);
				PosTaggerLexicon mergedLexicon = talismaneSession.getMergedLexicon();
				for (String word : wordList) {
					LOG.info("################");
					LOG.info("Word: " + word);
					List<LexicalEntry> entries = mergedLexicon.getEntries(word);
					for (LexicalEntry entry : entries) {
						LOG.info(entry + ", Full morph: " + entry.getMorphologyForCoNLL());
					}
				}
				break;
			}
			case serializeDiacriticizer: {
				String lexiconFilePath = null;
				String outFilePath = null;
				for (String argName : argsMap.keySet()) {
					String argValue = argsMap.get(argName);
					if (argName.equals("lexicon")) {
						lexiconFilePath = argValue;
					} else if (argName.equals("outFile")) {
						outFilePath = argValue;
					} else {
						throw new TalismaneException("Unknown argument: " + argName);
					}
				}

				if (lexiconFilePath == null)
					throw new TalismaneException("Missing argument: lexicon");
				if (outFilePath == null)
					throw new TalismaneException("Missing argument: outFile");

				File lexiconFile = new File(lexiconFilePath);
				LexiconDeserializer lexiconDeserializer = new LexiconDeserializer(talismaneSession);
				List<PosTaggerLexicon> lexicons = lexiconDeserializer.deserializeLexicons(lexiconFile);
				PosTaggerLexicon lexicon = lexicons.get(0);
				if (lexicons.size() > 1) {
					LexiconChain lexiconChain = new LexiconChain();
					for (PosTaggerLexicon oneLexicon : lexicons)
						lexiconChain.addLexicon(oneLexicon);
					lexicon = lexiconChain;
				}
				LexiconServiceLocator lexiconServiceLocator = new LexiconServiceLocator(locator);
				LexiconService lexiconService = lexiconServiceLocator.getLexiconService();
				Diacriticizer diacriticizer = lexiconService.getDiacriticizer(talismaneSession, lexicon);

				File outFile = new File(outFilePath);
				File outDir = outFile.getParentFile();
				outDir.mkdirs();

				FileOutputStream fos = new FileOutputStream(outFile);
				ZipOutputStream zos = new ZipOutputStream(fos);
				zos.putNextEntry(new ZipEntry("diacriticizer.obj"));
				ObjectOutputStream out = new ObjectOutputStream(zos);
				try {
					out.writeObject(diacriticizer);
				} finally {
					out.flush();
				}
				zos.flush();
				zos.close();

				break;
			}
			case testDiacriticizer: {
				String inFilePath = null;
				String[] wordList = null;
				for (String argName : argsMap.keySet()) {
					String argValue = argsMap.get(argName);
					if (argName.equals("inFile")) {
						inFilePath = argValue;
					} else if (argName.equals("words")) {
						wordList = argValue.split(",");
					} else {
						throw new TalismaneException("Unknown argument: " + argName);
					}
				}

				if (inFilePath == null)
					throw new TalismaneException("Missing argument: inFile");
				if (wordList == null)
					throw new TalismaneException("Missing argument: words");

				File inFile = new File(inFilePath);
				LexiconServiceLocator lexiconServiceLocator = new LexiconServiceLocator(locator);
				LexiconService lexiconService = lexiconServiceLocator.getLexiconService();
				Diacriticizer diacriticizer = lexiconService.deserializeDiacriticizer(inFile, talismaneSession);

				for (String word : wordList) {
					LOG.info("################");
					LOG.info("Word: " + word);
					Set<String> entries = diacriticizer.diacriticize(word);
					for (String entry : entries) {
						LOG.info(entry);
					}
				}

				break;
			}
			}
		}
	}
}
