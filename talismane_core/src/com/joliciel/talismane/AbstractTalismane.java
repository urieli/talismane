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
package com.joliciel.talismane;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.talismane.filters.FilterService;
import com.joliciel.talismane.filters.MarkerFilterType;
import com.joliciel.talismane.filters.RollingSentenceProcessor;
import com.joliciel.talismane.filters.Sentence;
import com.joliciel.talismane.filters.SentenceHolder;
import com.joliciel.talismane.filters.TextMarker;
import com.joliciel.talismane.filters.TextMarkerFilter;
import com.joliciel.talismane.machineLearning.AnalysisObserver;
import com.joliciel.talismane.machineLearning.MachineLearningModel;
import com.joliciel.talismane.machineLearning.MachineLearningService;
import com.joliciel.talismane.output.FreemarkerTemplateWriter;
import com.joliciel.talismane.parser.NonDeterministicParser;
import com.joliciel.talismane.parser.ParseConfiguration;
import com.joliciel.talismane.parser.ParseConfigurationProcessor;
import com.joliciel.talismane.parser.Parser;
import com.joliciel.talismane.parser.ParserEvaluator;
import com.joliciel.talismane.parser.ParserRegexBasedCorpusReader;
import com.joliciel.talismane.parser.ParserService;
import com.joliciel.talismane.parser.Transition;
import com.joliciel.talismane.parser.TransitionSystem;
import com.joliciel.talismane.parser.features.ParseConfigurationFeature;
import com.joliciel.talismane.parser.features.ParserFeatureService;
import com.joliciel.talismane.posTagger.NonDeterministicPosTagger;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.posTagger.PosTagRegexBasedCorpusReader;
import com.joliciel.talismane.posTagger.PosTagSequence;
import com.joliciel.talismane.posTagger.PosTagSequenceProcessor;
import com.joliciel.talismane.posTagger.PosTagSet;
import com.joliciel.talismane.posTagger.PosTagger;
import com.joliciel.talismane.posTagger.PosTaggerEvaluator;
import com.joliciel.talismane.posTagger.PosTaggerLexicon;
import com.joliciel.talismane.posTagger.PosTaggerService;
import com.joliciel.talismane.posTagger.features.PosTaggerFeature;
import com.joliciel.talismane.posTagger.features.PosTaggerFeatureService;
import com.joliciel.talismane.posTagger.features.PosTaggerRule;
import com.joliciel.talismane.sentenceDetector.SentenceDetector;
import com.joliciel.talismane.sentenceDetector.SentenceDetectorOutcome;
import com.joliciel.talismane.sentenceDetector.SentenceDetectorService;
import com.joliciel.talismane.sentenceDetector.SentenceProcessor;
import com.joliciel.talismane.sentenceDetector.features.SentenceDetectorFeature;
import com.joliciel.talismane.sentenceDetector.features.SentenceDetectorFeatureService;
import com.joliciel.talismane.stats.FScoreCalculator;
import com.joliciel.talismane.tokeniser.PretokenisedSequence;
import com.joliciel.talismane.tokeniser.TokenRegexBasedCorpusReader;
import com.joliciel.talismane.tokeniser.TokenSequence;
import com.joliciel.talismane.tokeniser.TokenSequenceProcessor;
import com.joliciel.talismane.tokeniser.Tokeniser;
import com.joliciel.talismane.tokeniser.TokeniserOutcome;
import com.joliciel.talismane.tokeniser.TokeniserService;
import com.joliciel.talismane.tokeniser.features.TokenFeatureService;
import com.joliciel.talismane.tokeniser.features.TokeniserContextFeature;
import com.joliciel.talismane.tokeniser.filters.TokenFilter;
import com.joliciel.talismane.tokeniser.filters.TokenSequenceFilter;
import com.joliciel.talismane.tokeniser.filters.TokenFilterService;
import com.joliciel.talismane.tokeniser.patterns.TokeniserPatternManager;
import com.joliciel.talismane.tokeniser.patterns.TokeniserPatternService;
import com.joliciel.talismane.utils.LogUtils;
import com.joliciel.talismane.utils.PerformanceMonitor;

/**
 * A language-neutral abstract implementation of Talismane.
 * Sub-classes need to add language-specific method implementations.
 * @author Assaf Urieli
 */
public abstract class AbstractTalismane implements Talismane {
	private static final Log LOG = LogFactory.getLog(AbstractTalismane.class);
	
	private SentenceDetector sentenceDetector;
	private Tokeniser tokeniser;
	private PosTagger posTagger;
	private Parser parser;
	private boolean propagateBeam = true;
	private static final int MIN_BLOCK_SIZE = 1000;
	private List<TextMarkerFilter> textMarkerFilters = new ArrayList<TextMarkerFilter>();
	private SentenceProcessor sentenceProcessor;
	private TokenSequenceProcessor tokenSequenceProcessor;
	private PosTagSequenceProcessor posTagSequenceProcessor;
	private ParseConfigurationProcessor parseConfigurationProcessor;
	private String inputRegex;
	private List<PosTaggerRule> posTaggerRules = null;
	
	private TokenRegexBasedCorpusReader tokenCorpusReader = null;
	private PosTagRegexBasedCorpusReader posTagCorpusReader = null;
	private ParserRegexBasedCorpusReader parserCorpusReader = null;
	
	private Module startModule = Module.SentenceDetector;
	private Module endModule = Module.Parser;
	private Module module = Module.Parser;
	
	private TokeniserService tokeniserService;
	private PosTaggerService posTaggerService;
	private PosTaggerLexicon lexiconService;
	private FilterService filterService;
	private TokenFilterService tokenFilterService;
	private ParserService parserService;
	
	private boolean stopOnError = false;
	private String fileName = "";
	
	private Command command = Command.analyse;
	private Reader reader = null;
	Writer writer = null;
	
	
	private ParserEvaluator parserEvaluator;
	private Writer csvFileWriter;
	private PosTaggerEvaluator posTaggerEvaluator;
	private File outDir;
	private String baseName;
	private long startTime;
	private boolean processByDefault = true;
	private int maxSentenceCount = 0;
	
	private Charset inputCharset = null;
	private Charset outputCharset = null;
	
	private char endBlockCharacter = '\f';

	protected AbstractTalismane() {
		
	}
	
	public void runCommand(String[] args) throws Exception {
		Map<String,String> argMap = this.convertArgs(args);
		this.runCommand(argMap);
	}
	
	public void runCommand(Map<String,String> args) throws Exception {
		boolean logPerformance = false;
		File outDir = null;
		if (args.containsKey("logPerformance")) {
			logPerformance = args.get("logPerformance").equalsIgnoreCase("true");
		}
		if (args.containsKey("outDir")) {
			outDir = new File(args.get("outDir"));
			outDir.mkdirs();
		}
		
		startTime = new Date().getTime();
		PerformanceMonitor.setActive(logPerformance);
		PerformanceMonitor.start();
		try {
			this.loadParameters(args);
			this.runCommand();
		} catch (Exception e) {
			LogUtils.logError(LOG, e);
			throw e;
		} finally {
			PerformanceMonitor.end();
			
			if (logPerformance) {
				try {
					Writer csvFileWriter = null;
					File csvFile = null;
					if (outDir!=null) {
						csvFile = new File(outDir, "performance.csv");
					} else {
						csvFile = new File("performance.csv");
					}
					csvFile.delete();
					csvFile.createNewFile();
					csvFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile, false),"UTF8"));
					PerformanceMonitor.writePerformanceCSV(csvFileWriter);
					csvFileWriter.flush();
					csvFileWriter.close();
				} catch (Exception e) {
					LogUtils.logError(LOG, e);
				}
			}
			long endTime = new Date().getTime();
			long totalTime = endTime - startTime;
			LOG.info("Total time: " + totalTime);
		}
	}
	
	Map<String, String> convertArgs(String[] args) {
		Map<String,String> argMap = new HashMap<String, String>();
		for (String arg : args) {
			int equalsPos = arg.indexOf('=');
			String argName = arg.substring(0, equalsPos);
			String argValue = arg.substring(equalsPos+1);
			argMap.put(argName, argValue);
		}
		return argMap;
	}
	
	public void loadParameters(String[] args) throws Exception {
		Map<String,String> argMap = this.convertArgs(args);
		this.loadParameters(argMap);
	}
	
	public void loadParameters(Map<String,String> args) throws Exception {
		if (args.size()==0) {
			System.out.println("Talismane usage instructions: ");
			System.out.println("* indicates optional, + indicates default value");
			System.out.println("");
			System.out.println("Usage: command=analyse *startModule=[sentence+|tokenise|postag|parse] *endModule=[sentence|tokenise|postag|parse+] *inFile=[inFilePath, stdin if missing] *outFile=[outFilePath, stdout if missing] *template=[outputTemplatePath]");
			System.out.println("");
			System.out.println("Usage: command=evaluate module=[sentence|tokenise|postag|parse] *inFile=[inFilePath] *outDir=[outDirPath]");
			System.out.println("");
			System.out.println("Additional optional parameters shared by both command types:");
			System.out.println(" *encoding=[UTF-8+ or other] *includeDetails=[true|false+] posTaggerRules*=[posTaggerRuleFilePath] regexFilters*=[regexFilterFilePath] *sentenceModel=[path] *tokeniserModel=[path] *posTaggerModel=[path] *parserModel=[path] *inputPatternFile=[inputPatternFilePath] *posTagSet=[posTagSetPath]");
			return;
		}
		
		String inFilePath = null;
		String outFilePath = null;
		String outDirPath = null;
		String encoding = null;
		String inputEncoding = null;
		String outputEncoding = null;
		String templatePath = null;
		String builtInTemplate = null;
		int beamWidth = 1;
		boolean propagateBeam = true;
		boolean includeDetails = false;
		String parserModelFilePath = null;
		String posTaggerModelFilePath = null;
		String tokeniserModelFilePath = null;
		String sentenceModelFilePath = null;
		Talismane.Module startModule = Talismane.Module.SentenceDetector;
		Talismane.Module endModule = Talismane.Module.Parser;
		
		String inputPatternFilePath = null;
		String inputRegex = null;
		String posTaggerRuleFilePath = null;
		String posTagSetPath = null;
		String textFiltersPath = null;
		String tokenFiltersPath = null;
		String fileName = null;
		int maxParseAnalysisTime = 60;
		String transitionSystemStr = null;
		
		MarkerFilterType newlineMarker = MarkerFilterType.SENTENCE_BREAK;
		
		for (Entry<String,String> arg : args.entrySet()) {
			String argName = arg.getKey();
			String argValue = arg.getValue();
			if (argName.equals("command")) {
				String commandString = argValue;
				if (commandString.equals("analyze"))
					commandString = "analyse";
				
				command = Command.valueOf(commandString);
			} else if (argName.equals("module")) {
				if (argValue.equalsIgnoreCase("sentence"))
					module = Talismane.Module.SentenceDetector;
				else if (argValue.equalsIgnoreCase("tokenise"))
					module = Talismane.Module.Tokeniser;
				else if (argValue.equalsIgnoreCase("postag"))
					module = Talismane.Module.PosTagger;
				else if (argValue.equalsIgnoreCase("parse"))
					module = Talismane.Module.Parser;
				else
					throw new TalismaneException("Unknown module: " + argValue);
			} else if (argName.equals("startModule")) {
				if (argValue.equalsIgnoreCase("sentence"))
					startModule = Talismane.Module.SentenceDetector;
				else if (argValue.equalsIgnoreCase("tokenise"))
					startModule = Talismane.Module.Tokeniser;
				else if (argValue.equalsIgnoreCase("postag"))
					startModule = Talismane.Module.PosTagger;
				else if (argValue.equalsIgnoreCase("parse"))
					startModule = Talismane.Module.Parser;
				else
					throw new TalismaneException("Unknown startModule: " + argValue);
			} else if (argName.equals("endModule")) {
				if (argValue.equalsIgnoreCase("sentence"))
					endModule = Talismane.Module.SentenceDetector;
				else if (argValue.equalsIgnoreCase("tokenise"))
					endModule = Talismane.Module.Tokeniser;
				else if (argValue.equalsIgnoreCase("postag"))
					endModule = Talismane.Module.PosTagger;
				else if (argValue.equalsIgnoreCase("parse"))
					endModule = Talismane.Module.Parser;
				else
					throw new TalismaneException("Unknown endModule: " + argValue);
			} else if (argName.equals("inFile"))
				inFilePath = argValue;
			else if (argName.equals("outFile")) 
				outFilePath = argValue;
			else if (argName.equals("outDir")) 
				outDirPath = argValue;
			else if (argName.equals("template")) 
				templatePath = argValue;
			else if (argName.equals("builtInTemplate")) {
				builtInTemplate = argValue;
			}
			else if (argName.equals("encoding")) {
				if (inputEncoding!=null || outputEncoding !=null)
					throw new TalismaneException("The parameter 'encoding' cannot be used with 'inputEncoding' or 'outputEncoding'");
				encoding = argValue;
			} else if (argName.equals("inputEncoding")) {
				if (encoding !=null)
					throw new TalismaneException("The parameter 'encoding' cannot be used with 'inputEncoding' or 'outputEncoding'");
				inputEncoding = argValue;
			} else if (argName.equals("outputEncoding")) {
				if (encoding !=null)
					throw new TalismaneException("The parameter 'encoding' cannot be used with 'inputEncoding' or 'outputEncoding'");
				outputEncoding = argValue;
			} else if (argName.equals("includeDetails"))
				includeDetails = argValue.equalsIgnoreCase("true");
			else if (argName.equals("propagateBeam"))
				propagateBeam = argValue.equalsIgnoreCase("true");
			else if (argName.equals("beamWidth"))
				beamWidth = Integer.parseInt(argValue);
			else if (argName.equals("sentenceModel"))
				sentenceModelFilePath = argValue;
			else if (argName.equals("tokeniserModel"))
				tokeniserModelFilePath = argValue;
			else if (argName.equals("posTaggerModel"))
				posTaggerModelFilePath = argValue;
			else if (argName.equals("parserModel"))
				parserModelFilePath = argValue;
			else if (argName.equals("inputPatternFile"))
				inputPatternFilePath = argValue;
			else if (argName.equals("inputPattern"))
				inputRegex = argValue;
			else if (argName.equals("posTaggerRules"))
				posTaggerRuleFilePath = argValue;
			else if (argName.equals("posTagSet"))
				posTagSetPath = argValue;
			else if (argName.equals("textFilters"))
				textFiltersPath = argValue;
			else if (argName.equals("tokenFilters"))
				tokenFiltersPath = argValue;
			else if (argName.equals("logPerformance")) {
				// do nothing - already read
			} else if (argName.equals("newline"))
				newlineMarker = MarkerFilterType.valueOf(argValue);
			else if (argName.equals("fileName"))
				fileName = argValue;
			else if (argName.equals("processByDefault"))
				processByDefault = argValue.equalsIgnoreCase("true");
			else if (argName.equals("maxParseAnalysisTime"))
				maxParseAnalysisTime = Integer.parseInt(argValue);
			else if (argName.equals("transitionSystem"))
				transitionSystemStr = argValue;
			else if (argName.equals("sentenceCount"))
				maxSentenceCount = Integer.parseInt(argValue);
			else if (argName.equals("endBlockCharCode")) {
				endBlockCharacter = (char) Integer.parseInt(argValue);
			}
			else {
				System.out.println("Unknown argument: " + argName);
				throw new RuntimeException("Unknown argument: " + argName);
			}
		}
		
		if (command==null)
			throw new TalismaneException("No command provided.");

		String sentenceTemplateName = "sentence_template.ftl";
		String tokeniserTemplateName = "tokeniser_template.ftl";
		String posTaggerTemplateName = "posTagger_template.ftl";
		String parserTemplateName = "parser_conll_template.ftl";
		if (builtInTemplate!=null && builtInTemplate.equalsIgnoreCase("conll_with_location"))
			parserTemplateName = "parser_conll_template_with_location.ftl";

    	TalismaneServiceLocator talismaneServiceLocator = TalismaneServiceLocator.getInstance();
		this.setTokeniserService(talismaneServiceLocator.getTokeniserServiceLocator().getTokeniserService());
		this.setPosTaggerService(talismaneServiceLocator.getPosTaggerServiceLocator().getPosTaggerService());
		this.setFilterService(talismaneServiceLocator.getFilterServiceLocator().getFilterService());
		this.setParserService(talismaneServiceLocator.getParserServiceLocator().getParserService());
		
		TalismaneSession.setLexicon(this.getLexiconService());
		
		this.setTokeniserFilterService(talismaneServiceLocator.getTokenFilterServiceLocator().getTokenFilterService());

        SentenceDetectorService sentenceDetectorService = talismaneServiceLocator.getSentenceDetectorServiceLocator().getSentenceDetectorService();
        SentenceDetectorFeatureService sentenceDetectorFeatureService = talismaneServiceLocator.getSentenceDetectorFeatureServiceLocator().getSentenceDetectorFeatureService();
 
        TokenFeatureService tokenFeatureService = talismaneServiceLocator.getTokenFeatureServiceLocator().getTokenFeatureService();
        TokeniserPatternService tokeniserPatternService = talismaneServiceLocator.getTokenPatternServiceLocator().getTokeniserPatternService();
        
        PosTaggerService posTaggerService = talismaneServiceLocator.getPosTaggerServiceLocator().getPosTaggerService();
		PosTaggerFeatureService posTaggerFeatureService = talismaneServiceLocator.getPosTaggerFeatureServiceLocator().getPosTaggerFeatureService();
		
		ParserService parserService = talismaneServiceLocator.getParserServiceLocator().getParserService();
		ParserFeatureService parserFeatureService = talismaneServiceLocator.getParserFeatureServiceLocator().getParserFeatureService();

		MachineLearningService machineLearningService = talismaneServiceLocator.getMachineLearningServiceLocator().getMachineLearningService();
		
		Scanner posTagSetScanner = null;
		if (posTagSetPath==null||posTagSetPath.length()==0) {
			posTagSetScanner = new Scanner(this.getDefaultPosTagSetFromStream());
		} else {
			File posTagSetFile = new File(posTagSetPath);
			posTagSetScanner = new Scanner(posTagSetFile);
		}
		
		PosTagSet posTagSet = posTaggerService.getPosTagSet(posTagSetScanner);
		TalismaneSession.setPosTagSet(posTagSet);
		
		TransitionSystem transitionSystem = null;
		if (transitionSystemStr!=null) {
			if (transitionSystemStr.equalsIgnoreCase("ShiftReduce")) {
				transitionSystem = parserService.getShiftReduceTransitionSystem();
			} else if (transitionSystemStr.equalsIgnoreCase("ArcEager")) {
				transitionSystem = parserService.getArcEagerTransitionSystem();
			} else {
				throw new TalismaneException("Unknown transition system: " + transitionSystemStr);
			}
		} else {
			transitionSystem = this.getDefaultTransitionSystem();
		}
		TalismaneSession.setTransitionSystem(transitionSystem);
		
		inputCharset = Charset.defaultCharset();
		outputCharset = Charset.defaultCharset();
		if (encoding!=null) {
			inputCharset = Charset.forName(encoding);
			outputCharset = Charset.forName(encoding);
		} else {
			if (inputEncoding!=null)
				inputCharset = Charset.forName(inputEncoding);
			if (outputEncoding!=null)
				outputCharset = Charset.forName(outputEncoding);
		}

		
		if (inFilePath!=null) {
			try {
				File inFile = new File(inFilePath);
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), inputCharset));
			} catch (FileNotFoundException fnfe) {
				LogUtils.logError(LOG, fnfe);
				throw new RuntimeException(fnfe);
			}
		} else {
			reader = new BufferedReader(new InputStreamReader(System.in, inputCharset));
		}
		if (fileName!=null) {
			this.setFileName(fileName);
		} else if (inFilePath!=null) {
			this.setFileName(inFilePath);
		}
		
		if (inputRegex==null && inputPatternFilePath!=null && inputPatternFilePath.length()>0) {
			Scanner inputPatternScanner = null;
			File inputPatternFile = new File(inputPatternFilePath);
			inputPatternScanner = new Scanner(inputPatternFile);
			if (inputPatternScanner.hasNextLine()) {
				inputRegex = inputPatternScanner.nextLine();
			}
			inputPatternScanner.close();
			if (inputRegex==null)
				throw new TalismaneException("No input pattern found in " + inputPatternFilePath);
		}
		this.setInputRegex(inputRegex);
		
		List<PosTaggerRule> posTaggerRules = new ArrayList<PosTaggerRule>();
		for (int i=0; i<=1; i++) {
			Scanner rulesScanner = null;
			if (i==0) {
				InputStream defaultRulesStream = this.getDefaultPosTaggerRulesFromStream();
				if (defaultRulesStream!=null)
					rulesScanner = new Scanner(defaultRulesStream);
			} else {
				if (posTaggerRuleFilePath!=null && posTaggerRuleFilePath.length()>0) {
					File posTaggerRuleFile = new File(posTaggerRuleFilePath);
					rulesScanner = new Scanner(posTaggerRuleFile);
				}
			}
			
			if (rulesScanner!=null) {
				List<String> ruleDescriptors = new ArrayList<String>();
				while (rulesScanner.hasNextLine()) {
					String ruleDescriptor = rulesScanner.nextLine();
					if (ruleDescriptor.length()>0) {
						ruleDescriptors.add(ruleDescriptor);
						LOG.debug(ruleDescriptor);
					}
				}
				List<PosTaggerRule> rules = posTaggerFeatureService.getRules(ruleDescriptors);
				posTaggerRules.addAll(rules);
				
			}
		}
		this.setPosTaggerRules(posTaggerRules);
		
		if (command.equals(Command.analyse)) {
			this.setStartModule(startModule);
			this.setEndModule(endModule);
			
			if (outFilePath!=null) {
				if (outFilePath.lastIndexOf("/")>=0) {
					String outFileDirPath = outFilePath.substring(0, outFilePath.lastIndexOf("/"));
					File outFileDir = new File(outFileDirPath);
					outFileDir.mkdirs();
				}
				File outFile = new File(outFilePath);
				outFile.delete();
				outFile.createNewFile();
			
				writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), outputCharset));
			} else {
				writer = new BufferedWriter(new OutputStreamWriter(System.out, outputCharset));
			}

			if (newlineMarker.equals(MarkerFilterType.SENTENCE_BREAK))
				this.addTextMarkerFilter(filterService.getNewlineEndOfSentenceMarker());
			else if (newlineMarker.equals(MarkerFilterType.SPACE))
				this.addTextMarkerFilter(filterService.getNewlineSpaceMarker());
			
			this.addTextMarkerFilter(filterService.getDuplicateWhiteSpaceFilter());

			for (int i=0; i<=1; i++) {
				LOG.debug("Text marker filters");
				Scanner textFilterScanner = null;
				if (i==0) {
					if (textFiltersPath!=null && textFiltersPath.length()>0) {
						LOG.debug("From: " + textFiltersPath);
						File textFilterFile = new File(textFiltersPath);
						textFilterScanner = new Scanner(textFilterFile);
					}
				} else {
					InputStream stream = this.getDefaultRegexTextMarkerFiltersFromStream();
					if (stream!=null) {
						LOG.debug("From default");
						textFilterScanner = new Scanner(stream);
					}
				}
				if (textFilterScanner!=null) {
					while (textFilterScanner.hasNextLine()) {
						String descriptor = textFilterScanner.nextLine();
						LOG.debug(descriptor);
						if (descriptor.length()>0 && !descriptor.startsWith("#")) {
							TextMarkerFilter textMarkerFilter = filterService.getTextMarkerFilter(descriptor);
							this.addTextMarkerFilter(textMarkerFilter);
						}
					}
				}
			}
			
			List<TokenFilter> tokenFilters = new ArrayList<TokenFilter>();
			for (int i=0; i<=1; i++) {
				LOG.debug("Token filters");
				Scanner tokenFilterScanner = null;
				if (i==0) {
					if (tokenFiltersPath!=null && tokenFiltersPath.length()>0) {
						LOG.debug("From: " + tokenFiltersPath);
						File tokenFilterFile = new File(tokenFiltersPath);
						tokenFilterScanner = new Scanner(tokenFilterFile);
					}
				} else {
					InputStream stream = this.getDefaultTokenRegexFiltersFromStream();
					if (stream!=null) {
						LOG.debug("From default");
						tokenFilterScanner = new Scanner(stream);
					}
				}
				if (tokenFilterScanner!=null) {
					while (tokenFilterScanner.hasNextLine()) {
						String descriptor = tokenFilterScanner.nextLine();
						LOG.debug(descriptor);
						if (descriptor.length()>0 && !descriptor.startsWith("#")) {
							TokenFilter tokenFilter = tokenFilterService.getTokenFilter(descriptor);
							tokenFilters.add(tokenFilter);
						}
					}
				}
			}
			
			if (this.needsSentenceDetector()) {
				LOG.debug("Getting sentence detector model");
				MachineLearningModel<SentenceDetectorOutcome> sentenceModel = null;
				if (sentenceModelFilePath!=null) {
					sentenceModel = machineLearningService.getModel(new ZipInputStream(new FileInputStream(sentenceModelFilePath)));
				} else {
					sentenceModel = machineLearningService.getModel(this.getDefaultSentenceModelStream());
				}
				Set<SentenceDetectorFeature<?>> sentenceDetectorFeatures =
					sentenceDetectorFeatureService.getFeatureSet(sentenceModel.getFeatureDescriptors());
				SentenceDetector sentenceDetector = sentenceDetectorService.getSentenceDetector(sentenceModel.getDecisionMaker(), sentenceDetectorFeatures);
		
				this.setSentenceDetector(sentenceDetector);
			}
			
			if (this.needsTokeniser()) {
				LOG.debug("Getting tokeniser model");
				MachineLearningModel<TokeniserOutcome> tokeniserModel = null;
				if (tokeniserModelFilePath!=null) {
					tokeniserModel = machineLearningService.getModel(new ZipInputStream(new FileInputStream(tokeniserModelFilePath)));
				} else {
					tokeniserModel = machineLearningService.getModel(this.getDefaultTokeniserModelStream());
				}
				
				TokeniserPatternManager tokeniserPatternManager = tokeniserPatternService.getPatternManager(tokeniserModel.getDescriptors().get(TokeniserPatternService.PATTERN_DESCRIPTOR_KEY));
				Set<TokeniserContextFeature<?>> tokeniserContextFeatures = tokenFeatureService.getTokeniserContextFeatureSet(tokeniserModel.getFeatureDescriptors(), tokeniserPatternManager.getParsedTestPatterns());
				Tokeniser tokeniser = tokeniserPatternService.getPatternTokeniser(tokeniserPatternManager, tokeniserContextFeatures, tokeniserModel.getDecisionMaker(), beamWidth);

				if (includeDetails) {
					String detailsFilePath = outFilePath.substring(0, outFilePath.lastIndexOf(".")) + "_tokeniser_details.txt";
					File detailsFile = new File(detailsFilePath);
					detailsFile.delete();
					AnalysisObserver observer = tokeniserModel.getDetailedAnalysisObserver(detailsFile);
					tokeniser.addObserver(observer);
				}
				
				for (TokenFilter tokenFilter : tokenFilters) {
					tokeniser.addTokenFilter(tokenFilter);
					if (this.needsSentenceDetector()) {
						this.getSentenceDetector().addTokenFilter(tokenFilter);
					}
				}

				List<String> tokenFilterDescriptors = tokeniserModel.getDescriptors().get(TokenFilterService.TOKEN_FILTER_DESCRIPTOR_KEY);
				if (tokenFilterDescriptors!=null) {
					for (String descriptor : tokenFilterDescriptors) {
						if (descriptor.length()>0 && !descriptor.startsWith("#")) {
							TokenFilter tokenFilter = this.tokenFilterService.getTokenFilter(descriptor);
							tokeniser.addTokenFilter(tokenFilter);
							if (this.needsSentenceDetector()) {
								this.getSentenceDetector().addTokenFilter(tokenFilter);
							}
						}
					}
				}
				
				for (TokenSequenceFilter tokenFilter : this.getTokenSequenceFilters()) {
					tokeniser.addTokenSequenceFilter(tokenFilter);
				}

				List<String> tokenSequenceFilterDescriptors = tokeniserModel.getDescriptors().get(TokenFilterService.TOKEN_SEQUENCE_FILTER_DESCRIPTOR_KEY);
				if (tokenSequenceFilterDescriptors!=null) {
					for (String descriptor : tokenSequenceFilterDescriptors) {
						if (descriptor.length()>0 && !descriptor.startsWith("#")) {
							TokenSequenceFilter tokenSequenceFilter = this.tokenFilterService.getTokenSequenceFilter(descriptor);
							tokeniser.addTokenSequenceFilter(tokenSequenceFilter);
						}
					}
				}

				this.setTokeniser(tokeniser);
			}
			
			if (this.needsPosTagger()) {				
				LOG.debug("Getting pos-tagger model");
				MachineLearningModel<PosTag> posTaggerModel = null;
				if (posTaggerModelFilePath!=null) {
					posTaggerModel = machineLearningService.getModel(new ZipInputStream(new FileInputStream(posTaggerModelFilePath)));
				} else {
					posTaggerModel = machineLearningService.getModel(this.getDefaultPosTaggerModelStream());
				}
				Set<PosTaggerFeature<?>> posTaggerFeatures = posTaggerFeatureService.getFeatureSet(posTaggerModel.getFeatureDescriptors());
				PosTagger posTagger = posTaggerService.getPosTagger(posTaggerFeatures, posTagSet, posTaggerModel.getDecisionMaker(), beamWidth);
				
				if (startModule.equals(Module.PosTagger)) {
					tokenCorpusReader = tokeniserService.getRegexBasedCorpusReader(reader);
					if (inputRegex!=null)
						tokenCorpusReader.setRegex(inputRegex);

					
					List<String> tokenFilterDescriptors = posTaggerModel.getDescriptors().get(TokenFilterService.TOKEN_FILTER_DESCRIPTOR_KEY);
					if (tokenFilterDescriptors!=null) {
						for (String descriptor : tokenFilterDescriptors) {
							if (descriptor.length()>0 && !descriptor.startsWith("#")) {
								TokenFilter tokenFilter = this.tokenFilterService.getTokenFilter(descriptor);
								tokenCorpusReader.addTokenFilter(tokenFilter);
							}
						}
					}
					
					List<String> tokenSequenceFilterDescriptors = posTaggerModel.getDescriptors().get(TokenFilterService.TOKEN_SEQUENCE_FILTER_DESCRIPTOR_KEY);
					if (tokenSequenceFilterDescriptors!=null) {
						for (String descriptor : tokenSequenceFilterDescriptors) {
							if (descriptor.length()>0 && !descriptor.startsWith("#")) {
								TokenSequenceFilter tokenSequenceFilter = this.tokenFilterService.getTokenSequenceFilter(descriptor);
								tokenCorpusReader.addTokenSequenceFilter(tokenSequenceFilter);
							}
						}
					}
					
					for (TokenSequenceFilter tokenSequenceFilter : this.getTokenSequenceFilters()) {
						tokenCorpusReader.addTokenSequenceFilter(tokenSequenceFilter);
					}
				}
				
				List<String> posTaggerPreprocessingFilters = posTaggerModel.getDescriptors().get(PosTagger.POSTAG_PREPROCESSING_FILTER_DESCRIPTOR_KEY);
				if (posTaggerPreprocessingFilters!=null) {
					for (String descriptor : posTaggerPreprocessingFilters) {
						if (descriptor.length()>0 && !descriptor.startsWith("#")) {
							TokenSequenceFilter tokenSequenceFilter = this.tokenFilterService.getTokenSequenceFilter(descriptor);
							posTagger.addPreprocessingFilter(tokenSequenceFilter);
						}
					}
				}
				
				for (TokenSequenceFilter tokenFilter : this.getPosTaggerPreprocessingFilters()) {
					posTagger.addPreprocessingFilter(tokenFilter);
				}
				posTagger.setPosTaggerRules(this.getPosTaggerRules());
		
				if (includeDetails) {
					String detailsFilePath = outFilePath.substring(0, outFilePath.lastIndexOf(".")) + "_posTagger_details.txt";
					File detailsFile = new File(detailsFilePath);
					detailsFile.delete();
					AnalysisObserver observer = posTaggerModel.getDetailedAnalysisObserver(detailsFile);
					posTagger.addObserver(observer);
				}
				
				this.setPosTagger(posTagger);
			}
			
			if (this.needsParser()) {
				LOG.debug("Getting parser model");
				MachineLearningModel<Transition> parserModel = null;
				if (parserModelFilePath!=null) {
					parserModel = machineLearningService.getModel(new ZipInputStream(new FileInputStream(parserModelFilePath)));
				} else {
					parserModel = machineLearningService.getModel(this.getDefaultParserModelStream());
				}
				NonDeterministicParser parser = parserService.getTransitionBasedParser(parserModel, beamWidth);
				parser.setMaxAnalysisTimePerSentence(maxParseAnalysisTime);
				
				if (includeDetails) {
					String detailsFilePath = outFilePath.substring(0, outFilePath.lastIndexOf(".")) + "_parser_details.txt";
					File detailsFile = new File(detailsFilePath);
					detailsFile.delete();
					AnalysisObserver observer = parserModel.getDetailedAnalysisObserver(detailsFile);
					parser.addObserver(observer);
				}
				TalismaneSession.setTransitionSystem(parser.getTransitionSystem());

				if (startModule.equals(Module.Parser)) {
					posTagCorpusReader = posTaggerService.getRegexBasedCorpusReader(reader);
					if (inputRegex!=null)
						posTagCorpusReader.setRegex(inputRegex);
					
					List<String> tokenFilterDescriptors = parserModel.getDescriptors().get(TokenFilterService.TOKEN_FILTER_DESCRIPTOR_KEY);
					if (tokenFilterDescriptors!=null) {
						List<TokenFilter> parserTokenFilters = new ArrayList<TokenFilter>();
						for (String descriptor : tokenFilterDescriptors) {
							if (descriptor.length()>0 && !descriptor.startsWith("#")) {
								TokenFilter tokenFilter = tokenFilterService.getTokenFilter(descriptor);
								parserTokenFilters.add(tokenFilter);
							}
						}
						TokenSequenceFilter tokenFilterWrapper = tokenFilterService.getTokenSequenceFilter(parserTokenFilters);
						posTagCorpusReader.addTokenSequenceFilter(tokenFilterWrapper);
					}
					
					List<String> tokenSequenceFilterDescriptors = parserModel.getDescriptors().get(TokenFilterService.TOKEN_SEQUENCE_FILTER_DESCRIPTOR_KEY);
					if (tokenSequenceFilterDescriptors!=null) {
						for (String descriptor : tokenSequenceFilterDescriptors) {
							if (descriptor.length()>0 && !descriptor.startsWith("#")) {
								TokenSequenceFilter tokenSequenceFilter = tokenFilterService.getTokenSequenceFilter(descriptor);
								posTagCorpusReader.addTokenSequenceFilter(tokenSequenceFilter);
							}
						}
					}
					
					List<String> posTaggerPreprocessingFilters = parserModel.getDescriptors().get(PosTagger.POSTAG_PREPROCESSING_FILTER_DESCRIPTOR_KEY);
					if (posTaggerPreprocessingFilters!=null) {
						for (String descriptor : posTaggerPreprocessingFilters) {
							if (descriptor.length()>0 && !descriptor.startsWith("#")) {
								TokenSequenceFilter tokenSequenceFilter = tokenFilterService.getTokenSequenceFilter(descriptor);
								posTagCorpusReader.addTokenSequenceFilter(tokenSequenceFilter);
							}
						}
					}
				}

				this.setParser(parser);				
			}

			this.setPropagateBeam(propagateBeam);

			if (endModule.equals(Talismane.Module.Parser)) {
				if (this.getParseConfigurationProcessor()==null) {
					Reader templateReader = null;
					if (templatePath==null) {
						templateReader = new BufferedReader(new InputStreamReader(getInputStreamFromResource(parserTemplateName)));
					} else {
						templateReader = new BufferedReader(new FileReader(new File(templatePath)));
					}
					FreemarkerTemplateWriter templateWriter = new FreemarkerTemplateWriter(writer, templateReader);
					this.setParseConfigurationProcessor(templateWriter);
				}
				this.setParseConfigurationProcessor(this.getParseConfigurationProcessor());
			} else if (endModule.equals(Talismane.Module.PosTagger)) {
				Reader templateReader = null;
				if (templatePath==null) {
					templateReader = new BufferedReader(new InputStreamReader(getInputStreamFromResource(posTaggerTemplateName)));
				} else {
					templateReader = new BufferedReader(new FileReader(new File(templatePath)));
				}
				FreemarkerTemplateWriter templateWriter = new FreemarkerTemplateWriter(writer, templateReader);
				this.setPosTagSequenceProcessor(templateWriter);
			} else if (endModule.equals(Talismane.Module.Tokeniser)) {
				Reader templateReader = null;
				if (templatePath==null) {
					templateReader = new BufferedReader(new InputStreamReader(getInputStreamFromResource(tokeniserTemplateName)));
				} else {
					templateReader = new BufferedReader(new FileReader(new File(templatePath)));
				}
				FreemarkerTemplateWriter templateWriter = new FreemarkerTemplateWriter(writer, templateReader);
				this.setTokenSequenceProcessor(templateWriter);
			} else if (endModule.equals(Talismane.Module.SentenceDetector)) {
				Reader templateReader = null;
				if (templatePath==null) {
					templateReader = new BufferedReader(new InputStreamReader(getInputStreamFromResource(sentenceTemplateName)));
				} else {
					templateReader = new BufferedReader(new FileReader(new File(templatePath)));
				}
				FreemarkerTemplateWriter templateWriter = new FreemarkerTemplateWriter(writer, templateReader);
				this.setSentenceProcessor(templateWriter);
			}
		} else if (command.equals(Command.evaluate)) {
			if (outDirPath.length()==0)
				throw new RuntimeException("Missing argument: outdir");
			
			outDir = new File(outDirPath);
			outDir.mkdirs();

			if (module.equals(Talismane.Module.PosTagger)) {
				posTagCorpusReader = posTaggerService.getRegexBasedCorpusReader(reader);
				if (inputRegex!=null)
					posTagCorpusReader.setRegex(inputRegex);
								
				for (TokenSequenceFilter tokenFilter : this.getTokenSequenceFilters()) {
					posTagCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
				for (TokenSequenceFilter tokenFilter : this.getPosTaggerPreprocessingFilters()) {
					posTagCorpusReader.addTokenSequenceFilter(tokenFilter);
				}

				baseName = "PosTaggerEval";
				if (inFilePath!=null) {
					if (inFilePath.indexOf('.')>0)
						baseName = inFilePath.substring(inFilePath.lastIndexOf('/')+1, inFilePath.indexOf('.'));
					else
						baseName = inFilePath.substring(inFilePath.lastIndexOf('/')+1);
				} else if (posTaggerModelFilePath!=null) {
					if (posTaggerModelFilePath.indexOf('.')>0)
						baseName = posTaggerModelFilePath.substring(posTaggerModelFilePath.lastIndexOf('/')+1, posTaggerModelFilePath.indexOf('.'));
					else
						baseName = posTaggerModelFilePath.substring(posTaggerModelFilePath.lastIndexOf('/')+1);
				} 

				csvFileWriter = null;
				boolean includeSentences = true;
				if (includeSentences) {
					File csvFile = new File(outDir, baseName + "_sentences.csv");
					csvFile.delete();
					csvFile.createNewFile();
					csvFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile, false),"UTF8"));
				}

				MachineLearningModel<PosTag> posTaggerModel = null;

				if (posTaggerModelFilePath!=null) {
					posTaggerModel = machineLearningService.getModel(new ZipInputStream(new FileInputStream(posTaggerModelFilePath)));
				} else {
					posTaggerModel = machineLearningService.getModel(this.getDefaultPosTaggerModelStream());
				}
				Set<PosTaggerFeature<?>> posTaggerFeatures = posTaggerFeatureService.getFeatureSet(posTaggerModel.getFeatureDescriptors());
				PosTagger posTagger = posTaggerService.getPosTagger(posTaggerFeatures, posTagSet, posTaggerModel.getDecisionMaker(), beamWidth);

				posTagger.setPosTaggerRules(this.getPosTaggerRules());

				if (includeDetails) {
					String detailsFilePath = baseName + "_posTagger_details.txt";
					File detailsFile = new File(outDir, detailsFilePath);
					detailsFile.delete();
					AnalysisObserver observer = posTaggerModel.getDetailedAnalysisObserver(detailsFile);
					posTagger.addObserver(observer);
				}
				
				posTaggerEvaluator = posTaggerService.getPosTaggerEvaluator(posTagger, csvFileWriter);
				posTaggerEvaluator.setPropagateBeam(propagateBeam);

			} else if (module.equals(Module.Parser)) {
				parserCorpusReader = parserService.getRegexBasedCorpusReader(reader);
				if (inputRegex!=null)
					parserCorpusReader.setRegex(inputRegex);
				
				for (TokenSequenceFilter tokenFilter : this.getTokenSequenceFilters()) {
					parserCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
				for (TokenSequenceFilter tokenFilter : this.getPosTaggerPreprocessingFilters()) {
					parserCorpusReader.addTokenSequenceFilter(tokenFilter);
				}

				baseName = "ParserEval";
				if (inFilePath!=null) {
					if (inFilePath.indexOf('.')>0)
						baseName = inFilePath.substring(inFilePath.lastIndexOf('/')+1, inFilePath.indexOf('.'));
					else
						baseName = inFilePath.substring(inFilePath.lastIndexOf('/')+1);
				} else if (parserModelFilePath!=null) {
					if (parserModelFilePath.indexOf('.')>0)
						baseName = parserModelFilePath.substring(parserModelFilePath.lastIndexOf('/')+1, parserModelFilePath.indexOf('.'));
					else
						baseName = parserModelFilePath.substring(parserModelFilePath.lastIndexOf('/')+1);
				} 

				csvFileWriter = null;
				boolean includeSentences = true;
				if (includeSentences) {
					File csvFile = new File(outDir, baseName + "_sentences.csv");
					csvFile.delete();
					csvFile.createNewFile();
					csvFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile, false),"UTF8"));
				}
					MachineLearningModel<Transition> parserModel = null;
					if (posTaggerModelFilePath!=null) {
						parserModel = machineLearningService.getModel(new ZipInputStream(new FileInputStream(parserModelFilePath)));
					} else {
						parserModel = machineLearningService.getModel(this.getDefaultParserModelStream());
					}
					TalismaneSession.setTransitionSystem((TransitionSystem) parserModel.getDecisionMaker().getDecisionFactory());
					Set<ParseConfigurationFeature<?>> parserFeatures = parserFeatureService.getFeatures(parserModel.getFeatureDescriptors());
					Parser parser = parserService.getTransitionBasedParser(parserModel.getDecisionMaker(), TalismaneSession.getTransitionSystem(), parserFeatures, beamWidth);
					
					if (includeDetails) {
						String detailsFilePath = baseName + "_posTagger_details.txt";
						File detailsFile = new File(outDir, detailsFilePath);
						detailsFile.delete();
						AnalysisObserver observer = parserModel.getDetailedAnalysisObserver(detailsFile);
						parser.addObserver(observer);
					}
					
					parserEvaluator = parserService.getParserEvaluator();
					parserEvaluator.setParser(parser);
					parserEvaluator.setCsvFileWriter(csvFileWriter);
			} else {
				throw new TalismaneException("The module " + module + " is not yet supported for evaluation.");
			} // which module?
		} else if (command.equals(Command.process)) {
			if (module.equals(Talismane.Module.Tokeniser)) {
				tokenCorpusReader = tokeniserService.getRegexBasedCorpusReader(reader);
				if (inputRegex!=null)
					tokenCorpusReader.setRegex(inputRegex);
								
				for (TokenSequenceFilter tokenFilter : this.getTokenSequenceFilters()) {
					tokenCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
				for (TokenSequenceFilter tokenFilter : this.getPosTaggerPreprocessingFilters()) {
					tokenCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
			} else if (module.equals(Talismane.Module.PosTagger)) {
				posTagCorpusReader = posTaggerService.getRegexBasedCorpusReader(reader);
				if (inputRegex!=null)
					posTagCorpusReader.setRegex(inputRegex);
								
				for (TokenSequenceFilter tokenFilter : this.getTokenSequenceFilters()) {
					posTagCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
				for (TokenSequenceFilter tokenFilter : this.getPosTaggerPreprocessingFilters()) {
					posTagCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
			} else if (module.equals(Module.Parser)) {
				parserCorpusReader = parserService.getRegexBasedCorpusReader(reader);
				if (inputRegex!=null)
					parserCorpusReader.setRegex(inputRegex);
				
				for (TokenSequenceFilter tokenFilter : this.getTokenSequenceFilters()) {
					parserCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
				for (TokenSequenceFilter tokenFilter : this.getPosTaggerPreprocessingFilters()) {
					parserCorpusReader.addTokenSequenceFilter(tokenFilter);
				}
			} else {
				throw new TalismaneException("The module " + module + " is not yet supported for processing.");
			} // which module?
		} // which command?
	}

	protected abstract TransitionSystem getDefaultTransitionSystem();

	public void runCommand() throws Exception {
		try {
			if (this.command.equals(Command.analyse)) {
				try {
					this.process(reader);
				} finally {
					writer.flush();
					writer.close();
					
					if (parseConfigurationProcessor!=null) {
						parseConfigurationProcessor.onCompleteParse();
					}
				}
			} else if (command.equals(Command.process)) {
				int sentenceCount = 0;
				if (module.equals(Talismane.Module.Tokeniser)) {
					if (tokenSequenceProcessor==null)
						throw new TalismaneException("Cannot process tokeniser output without a token sequence processor!");
					while (tokenCorpusReader.hasNextTokenSequence()) {
						TokenSequence tokenSequence = tokenCorpusReader.nextTokenSequence();
						tokenSequenceProcessor.onNextTokenSequence(tokenSequence);
						sentenceCount++;
						if (maxSentenceCount>0 && sentenceCount>=maxSentenceCount)
							break;
					}
				} else if (module.equals(Talismane.Module.PosTagger)) {
					if (posTagSequenceProcessor==null)
						throw new TalismaneException("Cannot process pos-tagger output without a pos-tag sequence processor!");
					while (posTagCorpusReader.hasNextPosTagSequence()) {
						PosTagSequence posTagSequence = posTagCorpusReader.nextPosTagSequence();
						posTagSequenceProcessor.onNextPosTagSequence(posTagSequence);
						sentenceCount++;
						if (maxSentenceCount>0 && sentenceCount>=maxSentenceCount)
							break;
					}
				} else if (module.equals(Talismane.Module.Parser)) {
					if (parseConfigurationProcessor==null)
						throw new TalismaneException("Cannot process parser output without a parse configuration processor!");
					try {
						while (parserCorpusReader.hasNextConfiguration()) {
							ParseConfiguration parseConfiguration = parserCorpusReader.nextConfiguration();
							parseConfigurationProcessor.onNextParseConfiguration(parseConfiguration);
							sentenceCount++;
							if (maxSentenceCount>0 && sentenceCount>=maxSentenceCount)
								break;
						}
					} finally {
						parseConfigurationProcessor.onCompleteParse();
					}
				}
			} else if (command.equals(Command.evaluate)) {
				try {
					FScoreCalculator<String> fScoreCalculator = null;
					if (module.equals(Talismane.Module.PosTagger)) {
						fScoreCalculator = posTaggerEvaluator.evaluate(posTagCorpusReader);
						
						double unknownLexiconFScore = posTaggerEvaluator.getFscoreUnknownInLexicon().getTotalFScore();
						LOG.debug("F-score for words unknown in lexicon: " + unknownLexiconFScore);
						if (posTaggerEvaluator.getFscoreUnknownInCorpus()!=null) {
							double unknownCorpusFScore = posTaggerEvaluator.getFscoreUnknownInCorpus().getTotalFScore();
							LOG.debug("F-score for words unknown in corpus: " + unknownCorpusFScore);
						}
						
						double fscore = fScoreCalculator.getTotalFScore();
						LOG.debug("F-score: " + fscore);

					} else if (module.equals(Talismane.Module.Parser)) {
						fScoreCalculator = parserEvaluator.evaluate(parserCorpusReader);
						
						double fscore = fScoreCalculator.getTotalFScore();
						LOG.debug("F-score: " + fscore);
					}
					
					File fscoreFile = new File(outDir, baseName + ".fscores.csv");
					fScoreCalculator.writeScoresToCSVFile(fscoreFile);
				} finally {
					if (csvFileWriter!=null) {
						csvFileWriter.flush();
						csvFileWriter.close();
					}
				}
			} // which command?
		} catch (IOException e) {
			LogUtils.logError(LOG, e);
		}

	}
	
	protected abstract InputStream getDefaultRegexTextMarkerFiltersFromStream();

	protected abstract InputStream getDefaultTokenRegexFiltersFromStream();

	/**
	 * A list of filters to be applied to the atomic token sequences
	 * prior to tokenisation.
	 * @return
	 */
	protected abstract List<TokenSequenceFilter> getTokenSequenceFilters();
	
	/**
	 * A list of filters to be applied to token sequences generated by the tokeniser
	 * prior to pos-tagging.
	 * @return
	 */
	protected abstract List<TokenSequenceFilter> getPosTaggerPreprocessingFilters();



	@Override
	public boolean needsSentenceDetector() {
		return startModule.compareTo(Module.SentenceDetector)<=0 && endModule.compareTo(Module.SentenceDetector)>=0;
	}
	@Override
	public boolean needsTokeniser() {
		return startModule.compareTo(Module.Tokeniser)<=0 && endModule.compareTo(Module.Tokeniser)>=0;
	}
	@Override
	public boolean needsPosTagger() {
		return startModule.compareTo(Module.PosTagger)<=0 && endModule.compareTo(Module.PosTagger)>=0;
	}
	@Override
	public boolean needsParser() {
		return startModule.compareTo(Module.Parser)<=0 && endModule.compareTo(Module.Parser)>=0;
	}
	
	/**
	 * Process the reader from a given start module to a given end module, where the modules available are:
	 * Sentence detector, Tokeniser, Pos tagger, Parser.<br/>
	 * A fixed input format is expected depending on the start module:<br/>
	 * <li>Sentence detector: newlines indicate sentence breaks, but can have multiple sentences per paragraph.</li>
	 * <li>Tokeniser: expect exactly one sentence per newline.</li>
	 * <li>Pos tagger: expect one token per line and empty line to indicate sentence breaks. Empty tokens are indicated by an underscore.</li>
	 * <li>Parser: each line should start with token-tab-postag and end with a newline.</li>
	 * @param reader
	 */
	@Override
	public void process(Reader reader) {
		try {
			if (this.needsSentenceDetector()) {
				if (sentenceDetector==null) {
					throw new TalismaneException("Sentence detector not provided.");
				}
			}
			if (this.needsTokeniser()) {
				if (tokeniser==null) {
					throw new TalismaneException("Tokeniser not provided.");
				}
			}
			if (this.needsPosTagger()) {
				if (posTagger==null) {
					throw new TalismaneException("Pos-tagger not provided.");
				}
			}
			if (this.needsParser()) {
				if (parser==null) {
					throw new TalismaneException("Parser not provided.");
				}
			}
			
			if (endModule.equals(Module.SentenceDetector)) {
				if (sentenceProcessor==null) {
					throw new TalismaneException("No sentence processor provided with sentence detector end module, cannot generate output.");
				}
			}
			if (endModule.equals(Module.Tokeniser)) {
				if (tokenSequenceProcessor==null) {
					throw new TalismaneException("No token sequence processor provided with tokeniser end module, cannot generate output.");
				}
			}
			if (endModule.equals(Module.PosTagger)) {
				if (posTagSequenceProcessor==null) {
					throw new TalismaneException("No postag sequence processor provided with pos-tagger end module, cannot generate output.");
				}
			}
			if (endModule.equals(Module.Parser)) {
				if (parseConfigurationProcessor==null) {
					throw new TalismaneException("No parse configuration processor provided with parser end module, cannot generate output.");
				}
			}
			
			LinkedList<String> textSegments = new LinkedList<String>();
			LinkedList<Sentence> sentences = new LinkedList<Sentence>();
			TokenSequence tokenSequence = null;
			PosTagSequence posTagSequence = null;
			
			RollingSentenceProcessor rollingSentenceProcessor = this.filterService.getRollingSentenceProcessor(fileName, processByDefault);
			Sentence leftover = null;
			if (this.needsSentenceDetector()) {
				// prime the sentence detector with two text segments, to ensure everything gets processed
				textSegments.addLast("");
				textSegments.addLast("");
			}
			
		    StringBuilder stringBuilder = new StringBuilder();
		    boolean finished = false;
		    
			String prevProcessedText = "";
			String processedText = "";
			String nextProcessedText = "";
			SentenceHolder prevSentenceHolder = null;

		    while (!finished) {
		    	if (startModule.equals(Module.SentenceDetector)||startModule.equals(Module.Tokeniser)) {
				    // read characters from the reader, one at a time
			    	char c;
			    	int r = reader.read();
			    	if (r==-1) {
			    		finished = true;
			    		c = '\n';
			    	} else {
		    			c = (char) r;
			    	}
	    			
	    			// have sentence detector
		    		if (finished || (Character.isWhitespace(c) && stringBuilder.length()>MIN_BLOCK_SIZE) || c==endBlockCharacter) {
		    			if (stringBuilder.length()>0) {
			    			String textSegment = stringBuilder.toString();
			    			stringBuilder = new StringBuilder();
			    			
		    				textSegments.add(textSegment);
		    			} // is the current block > 0 characters?
		    		} // is there a next block available?
		    		
		    		if (finished) {
		    			if (stringBuilder.length()>0) {
		    				textSegments.addLast(stringBuilder.toString());
		    				stringBuilder = new StringBuilder();
		    			}
						textSegments.addLast("");
						textSegments.addLast("");
						textSegments.addLast("");
		    		}
		    		
		    		stringBuilder.append(c);
		    		
					while (textSegments.size()>=3) {
						String prevText = textSegments.removeFirst();
						String text = textSegments.removeFirst();
						String nextText = textSegments.removeFirst();
						if (LOG.isTraceEnabled()) {
							LOG.trace("prevText: " + prevText);
							LOG.trace("text: " + text);
							LOG.trace("nextText: " + nextText);							
						}
						
						Set<TextMarker> textMarkers = new TreeSet<TextMarker>();
						for (TextMarkerFilter textMarkerFilter : textMarkerFilters) {
							Set<TextMarker> result = textMarkerFilter.apply(prevText, text, nextText);
							textMarkers.addAll(result);
						}
						
						// push the text segments back onto the beginning of Deque
						textSegments.addFirst(nextText);
						textSegments.addFirst(text);
						
						SentenceHolder sentenceHolder = rollingSentenceProcessor.addNextSegment(text, textMarkers);
						prevProcessedText = processedText;
						processedText = nextProcessedText;
						nextProcessedText = sentenceHolder.getText();
						
						if (LOG.isTraceEnabled()) {
							LOG.trace("prevProcessedText: " + prevProcessedText);
							LOG.trace("processedText: " + processedText);
							LOG.trace("nextProcessedText: " + nextProcessedText);							
						}

					    boolean reallyFinished = finished && textSegments.size()==3;

						if (prevSentenceHolder!=null) {
							if (startModule.equals(Module.SentenceDetector)) {
								List<Integer> sentenceBreaks = sentenceDetector.detectSentences(prevProcessedText, processedText, nextProcessedText);
								for (int sentenceBreak : sentenceBreaks) {
									prevSentenceHolder.addSentenceBoundary(sentenceBreak);
								}
							}
							
							List<Sentence> theSentences = prevSentenceHolder.getDetectedSentences(leftover);
							leftover = null;
							for (Sentence sentence : theSentences) {
								if (sentence.isComplete()||reallyFinished) {
									sentences.add(sentence);
								} else {
									LOG.debug("Setting leftover to: " + sentence.getText());
									leftover = sentence;
								}
							}
						}
						prevSentenceHolder = sentenceHolder;
					} // we have at least 3 text segments (should always be the case once we get started)
				} else if (startModule.equals(Module.PosTagger)) {
	    			if (tokenCorpusReader.hasNextTokenSequence()) {
	    				tokenSequence = tokenCorpusReader.nextTokenSequence();
	    			} else {
	    				tokenSequence = null;
	    				finished = true;
	    			}
	    		} else if (startModule.equals(Module.Parser)) {
	    			if (posTagCorpusReader.hasNextPosTagSequence()) {
	    				posTagSequence = posTagCorpusReader.nextPosTagSequence();
	    			} else {
	    				posTagSequence = null;
	    				finished = true;
	    			}
	    		} // which start module?
	    		
	    		boolean needToProcess = false;
	    		if (startModule.equals(Module.SentenceDetector)||startModule.equals(Module.Tokeniser))
	    			needToProcess = !sentences.isEmpty();
	    		else if (startModule.equals(Module.PosTagger))
	    			needToProcess = tokenSequence!=null;
	    		else if (startModule.equals(Module.Parser))
	    			needToProcess = posTagSequence!=null;
	    		
	    		while (needToProcess) {
	    			Sentence sentence = null;
	    			if (startModule.compareTo(Module.Tokeniser)<=0 && endModule.compareTo(Module.SentenceDetector)>=0) {
		    			sentence = sentences.poll();
		    			LOG.debug("Sentence: " + sentence);
		    			if (sentenceProcessor!=null)
		    				sentenceProcessor.process(sentence.getText());
	    			} // need to read next sentence
	    			
	    			List<TokenSequence> tokenSequences = null;
	    			if (this.needsTokeniser()) {
	    				tokenSequences = tokeniser.tokenise(sentence);
    					tokenSequence = tokenSequences.get(0);
	    				
    					if (tokenSequenceProcessor!=null) {
    						tokenSequenceProcessor.onNextTokenSequence(tokenSequence);
    					}
	    			} // need to tokenise ?
	    			
	    			List<PosTagSequence> posTagSequences = null;
 	    			if (this.needsPosTagger()) {
    					posTagSequence = null;
    					if (tokenSequences==null||!propagateBeam) {
    						tokenSequences = new ArrayList<TokenSequence>();
    						tokenSequences.add(tokenSequence);
    					}

	    				if (posTagger instanceof NonDeterministicPosTagger) {
	    					NonDeterministicPosTagger nonDeterministicPosTagger = (NonDeterministicPosTagger) posTagger;
	    					posTagSequences = nonDeterministicPosTagger.tagSentence(tokenSequences);
	    					posTagSequence = posTagSequences.get(0);
	    				} else {
	    					posTagSequence = posTagger.tagSentence(tokenSequence);
	    				}
	    				
	    				if (posTagSequenceProcessor!=null) {
	    					posTagSequenceProcessor.onNextPosTagSequence(posTagSequence);
	    				}

	    				tokenSequence = null;
 	    			} // need to postag
 	    			
	    			if (this.needsParser()) {
    					if (posTagSequences==null||!propagateBeam) {
    						posTagSequences = new ArrayList<PosTagSequence>();
    						posTagSequences.add(posTagSequence);
    					}
    					
    					ParseConfiguration parseConfiguration = null;
    					List<ParseConfiguration> parseConfigurations = null;
    					try {
	    					if (parser instanceof NonDeterministicParser) {
	    						NonDeterministicParser nonDeterministicParser = (NonDeterministicParser) parser;
		    					parseConfigurations = nonDeterministicParser.parseSentence(posTagSequences);
		    					parseConfiguration = parseConfigurations.get(0);
	    					} else {
	    						parseConfiguration = parser.parseSentence(posTagSequence);
	    					}
	    					
	    					if (parseConfigurationProcessor!=null) {
	    						parseConfigurationProcessor.onNextParseConfiguration(parseConfiguration);
	    					}
    					} catch (Exception e) {
    						LOG.error(e);
    						if (stopOnError)
    							throw new RuntimeException(e);
    					}
    					posTagSequence = null;
    				} // need to parse
	    			
		    		if (startModule.equals(Module.SentenceDetector)||startModule.equals(Module.Tokeniser))
		    			needToProcess = !sentences.isEmpty();
		    		else if (startModule.equals(Module.PosTagger))
		    			needToProcess = tokenSequence!=null;
		    		else if (startModule.equals(Module.Parser))
		    			needToProcess = posTagSequence!=null;
	    		} // next sentence
			} // next character
		} catch (IOException ioe) {
			LOG.error(ioe);
			throw new RuntimeException(ioe);
		} finally {
			try {
				reader.close();
			} catch (IOException ioe2) {
				LOG.error(ioe2);
				throw new RuntimeException(ioe2);
			}
		}
	}

	void addToken(PretokenisedSequence pretokenisedSequence, String token) {
		if (token.equals("_")) {
			pretokenisedSequence.addToken("");
		} else {
			if (pretokenisedSequence.size()==0) {
				// do nothing
			} else if (pretokenisedSequence.get(pretokenisedSequence.size()-1).getText().endsWith("'")) {
				// do nothing
			} else if (token.equals(".")||token.equals(",")||token.equals(")")||token.equals("]")) {
				// do nothing
			} else {
				// add a space
				pretokenisedSequence.addToken(" ");
			}
			pretokenisedSequence.addToken(token.replace("_", " "));
		}
	}
	
	@Override
	public SentenceDetector getSentenceDetector() {
		return sentenceDetector;
	}

	public void setSentenceDetector(SentenceDetector sentenceDetector) {
		this.sentenceDetector = sentenceDetector;
	}

	@Override
	public Tokeniser getTokeniser() {
		return tokeniser;
	}

	public void setTokeniser(Tokeniser tokeniser) {
		this.tokeniser = tokeniser;
	}

	@Override
	public PosTagger getPosTagger() {
		return posTagger;
	}

	public void setPosTagger(PosTagger posTagger) {
		this.posTagger = posTagger;
	}

	@Override
	public Parser getParser() {
		return parser;
	}

	public void setParser(Parser parser) {
		this.parser = parser;
	}

	@Override
	public List<TextMarkerFilter> getTextMarkerFilters() {
		return textMarkerFilters;
	}

	@Override
	public void setTextMarkerFilters(List<TextMarkerFilter> textMarkerFilters) {
		this.textMarkerFilters = textMarkerFilters;
	}

	@Override
	public void addTextMarkerFilter(TextMarkerFilter textMarkerFilter) {
		this.textMarkerFilters.add(textMarkerFilter);
	}

	@Override
	public boolean isPropagateBeam() {
		return propagateBeam;
	}

	@Override
	public void setPropagateBeam(boolean propagateBeam) {
		this.propagateBeam = propagateBeam;
	}

	@Override
	public SentenceProcessor getSentenceProcessor() {
		return sentenceProcessor;
	}

	@Override
	public void setSentenceProcessor(SentenceProcessor sentenceProcessor) {
		this.sentenceProcessor = sentenceProcessor;
	}

	@Override
	public TokenSequenceProcessor getTokenSequenceProcessor() {
		return tokenSequenceProcessor;
	}

	@Override
	public void setTokenSequenceProcessor(
			TokenSequenceProcessor tokenSequenceProcessor) {
		this.tokenSequenceProcessor = tokenSequenceProcessor;
	}

	@Override
	public PosTagSequenceProcessor getPosTagSequenceProcessor() {
		return posTagSequenceProcessor;
	}

	@Override
	public void setPosTagSequenceProcessor(
			PosTagSequenceProcessor posTagSequenceProcessor) {
		this.posTagSequenceProcessor = posTagSequenceProcessor;
	}

	@Override
	public ParseConfigurationProcessor getParseConfigurationProcessor() {
		return parseConfigurationProcessor;
	}

	@Override
	public void setParseConfigurationProcessor(
			ParseConfigurationProcessor parseConfigurationProcessor) {
		this.parseConfigurationProcessor = parseConfigurationProcessor;
	}

	public TokeniserService getTokeniserService() {
		return tokeniserService;
	}

	public void setTokeniserService(TokeniserService tokeniserService) {
		this.tokeniserService = tokeniserService;
	}

	public PosTaggerService getPosTaggerService() {
		return posTaggerService;
	}

	public void setPosTaggerService(PosTaggerService posTaggerService) {
		this.posTaggerService = posTaggerService;
	}

	public Module getStartModule() {
		return startModule;
	}

	public Module getEndModule() {
		return endModule;
	}

	public void setStartModule(Module startModule) {
		this.startModule = startModule;
	}

	public void setEndModule(Module endModule) {
		this.endModule = endModule;
	}

	public PosTaggerLexicon getLexiconService() {
		if (this.lexiconService==null)
			this.lexiconService = this.getDefaultLexiconService();
		return lexiconService;
	}

	public void setLexiconService(PosTaggerLexicon lexiconService) {
		this.lexiconService = lexiconService;
	}
	protected abstract PosTaggerLexicon getDefaultLexiconService();
	protected abstract InputStream getDefaultPosTagSetFromStream();
	protected abstract InputStream getDefaultPosTaggerRulesFromStream();
	protected abstract ZipInputStream getDefaultSentenceModelStream();
	protected abstract ZipInputStream getDefaultTokeniserModelStream();
	protected abstract ZipInputStream getDefaultPosTaggerModelStream();
	protected abstract ZipInputStream getDefaultParserModelStream();
	
	private static InputStream getInputStreamFromResource(String resource) {
		String path = "/com/joliciel/talismane/output/" + resource;
		InputStream inputStream = Talismane.class.getResourceAsStream(path); 
		
		return inputStream;
	}

	public String getInputRegex() {
		return inputRegex;
	}

	public void setInputRegex(String inputRegex) {
		this.inputRegex = inputRegex;
	}

	public List<PosTaggerRule> getPosTaggerRules() {
		return posTaggerRules;
	}

	public void setPosTaggerRules(List<PosTaggerRule> posTaggerRules) {
		this.posTaggerRules = posTaggerRules;
	}

	public FilterService getFilterService() {
		return filterService;
	}

	public void setFilterService(FilterService filterService) {
		this.filterService = filterService;
	}

	public TokenFilterService getTokeniserFilterService() {
		return tokenFilterService;
	}

	public void setTokeniserFilterService(
			TokenFilterService tokeniserFilterService) {
		this.tokenFilterService = tokeniserFilterService;
	}

	public ParserService getParserService() {
		return parserService;
	}

	public void setParserService(ParserService parserService) {
		this.parserService = parserService;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public Charset getInputCharset() {
		return inputCharset;
	}

	public Charset getOutputCharset() {
		return outputCharset;
	}
	
}
