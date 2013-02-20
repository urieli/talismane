package com.joliciel.talismane.trainer.fr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.frenchTreebank.TreebankReader;
import com.joliciel.frenchTreebank.TreebankService;
import com.joliciel.frenchTreebank.TreebankServiceLocator;
import com.joliciel.frenchTreebank.TreebankSubSet;
import com.joliciel.frenchTreebank.export.TreebankExportService;
import com.joliciel.frenchTreebank.upload.TreebankUploadService;
import com.joliciel.talismane.TalismaneServiceLocator;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.lexicon.LexiconChain;
import com.joliciel.talismane.lexicon.LexiconDeserializer;
import com.joliciel.talismane.lexicon.PosTaggerLexicon;
import com.joliciel.talismane.machineLearning.CorpusEventStream;
import com.joliciel.talismane.machineLearning.DecisionFactory;
import com.joliciel.talismane.machineLearning.MachineLearningModel;
import com.joliciel.talismane.machineLearning.MachineLearningService;
import com.joliciel.talismane.machineLearning.ModelTrainer;
import com.joliciel.talismane.machineLearning.MachineLearningModel.MachineLearningAlgorithm;
import com.joliciel.talismane.machineLearning.linearsvm.LinearSVMModelTrainer;
import com.joliciel.talismane.machineLearning.linearsvm.LinearSVMModelTrainer.LinearSVMSolverType;
import com.joliciel.talismane.machineLearning.maxent.MaxentModelTrainer;
import com.joliciel.talismane.machineLearning.maxent.PerceptronModelTrainer;
import com.joliciel.talismane.posTagger.PosTagSet;
import com.joliciel.talismane.posTagger.PosTaggerService;
import com.joliciel.talismane.posTagger.PosTaggerServiceLocator;
import com.joliciel.talismane.stats.FScoreCalculator;
import com.joliciel.talismane.tokeniser.TokenEvaluationFScoreCalculator;
import com.joliciel.talismane.tokeniser.Tokeniser;
import com.joliciel.talismane.tokeniser.TokeniserOutcome;
import com.joliciel.talismane.tokeniser.TokeniserEvaluator;
import com.joliciel.talismane.tokeniser.TokeniserService;
import com.joliciel.talismane.tokeniser.TokeniserAnnotatedCorpusReader;
import com.joliciel.talismane.tokeniser.features.TokenFeatureService;
import com.joliciel.talismane.tokeniser.features.TokeniserContextFeature;
import com.joliciel.talismane.tokeniser.filters.TokenFilter;
import com.joliciel.talismane.tokeniser.filters.TokenFilterService;
import com.joliciel.talismane.tokeniser.filters.TokenSequenceFilter;
import com.joliciel.talismane.tokeniser.patterns.TokeniserPatternManager;
import com.joliciel.talismane.tokeniser.patterns.TokeniserPatternService;
import com.joliciel.talismane.utils.PerformanceMonitor;

public class TokeniserMaxentRunner {
    private static final Log LOG = LogFactory.getLog(TokeniserMaxentRunner.class);

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		Map<String, String> argMap = new HashMap<String, String>();
		
		for (String arg : args) {
			int equalsPos = arg.indexOf('=');
			String argName = arg.substring(0, equalsPos);
			String argValue = arg.substring(equalsPos+1);
			argMap.put(argName, argValue);
		}
		
		@SuppressWarnings("unused")
		TokeniserMaxentRunner maxentRunner = new TokeniserMaxentRunner(argMap);
	}

    public TokeniserMaxentRunner(Map<String,String> argMap) throws Exception {
    	String command = null;
		String tokeniserModelFilePath = "";
		String tokeniserFeatureFilePath = "";
		String tokeniserPatternFilePath = "";
		String tokenFilterPath = "";
		String tokenSequenceFilterPath = "";
		String lexiconDirPath = "";
		String treebankPath = "";
		String outDirPath = "";
		String tokeniserType = "maxent";
		int sentenceCount = 0;
		int beamWidth = 10;
		int startSentence = 0;
		String posTagSetPath = "";
		String sentenceNumber = "";
		MachineLearningAlgorithm algorithm = MachineLearningAlgorithm.MaxEnt;
		
		
		int iterations = 0;
		int cutoff = 0;
		double smoothing = 0;
		double constraintViolationCost = -1;
		double epsilon = -1;
		LinearSVMSolverType solverType = null;
		boolean perceptronAveraging = false;
		boolean perceptronSkippedAveraging = false;
		double perceptronTolerance = -1;

		for (Entry<String, String> argEntry : argMap.entrySet()) {
			String argName = argEntry.getKey();
			String argValue = argEntry.getValue();
			if (argName.equals("command"))
				command = argValue;
			else if (argName.equals("tokeniserModel"))
				tokeniserModelFilePath = argValue;
			else if (argName.equals("tokeniserFeatures"))
				tokeniserFeatureFilePath = argValue;
			else if (argName.equals("tokeniserPatterns"))
				tokeniserPatternFilePath = argValue;
			else if (argName.equals("tokenFilters"))
				tokenFilterPath = argValue;
			else if (argName.equals("tokenSequenceFilters"))
				tokenSequenceFilterPath = argValue;
			else if (argName.equals("posTagSet"))
				posTagSetPath = argValue;
			else if (argName.equals("iterations"))
				iterations = Integer.parseInt(argValue);
			else if (argName.equals("cutoff"))
				cutoff = Integer.parseInt(argValue);
			else if (argName.equals("smoothing"))
				smoothing = Double.parseDouble(argValue);
			else if (argName.equals("outDir")) 
				outDirPath = argValue;
			else if (argName.equals("tokeniser")) 
				tokeniserType = argValue;
			else if (argName.equals("treebank"))
				treebankPath = argValue;
			else if (argName.equals("lexiconDir"))
				lexiconDirPath = argValue;
			else if (argName.equals("sentenceCount"))
				sentenceCount = Integer.parseInt(argValue);
			else if (argName.equals("startSentence"))
				startSentence = Integer.parseInt(argValue);
			else if (argName.equals("sentence"))
				sentenceNumber = argValue;
			else if (argName.equals("beamWidth"))
				beamWidth = Integer.parseInt(argValue);
			else if (argName.equals("algorithm"))
				algorithm = MachineLearningAlgorithm.valueOf(argValue);
			else if (argName.equals("linearSVMSolver"))
				solverType = LinearSVMSolverType.valueOf(argValue);
			else if (argName.equals("linearSVMCost"))
				constraintViolationCost = Double.parseDouble(argValue);
			else if (argName.equals("linearSVMEpsilon"))
				epsilon = Double.parseDouble(argValue);
			else if (argName.equals("perceptronAveraging"))
				perceptronAveraging = argValue.equalsIgnoreCase("true");
			else if (argName.equals("perceptronSkippedAveraging"))
				perceptronSkippedAveraging = argValue.equalsIgnoreCase("true");
			else if (argName.equals("perceptronTolerance"))
				perceptronTolerance = Double.parseDouble(argValue);
			else
				throw new RuntimeException("Unknown argument: " + argName);
		}
		
		if (lexiconDirPath.length()==0)
			throw new RuntimeException("Missing argument: lexiconDir");
		if (posTagSetPath.length()==0)
			throw new RuntimeException("Missing argument: posTagSet");
		
		PerformanceMonitor.start();
		try {
			TalismaneServiceLocator talismaneServiceLocator = TalismaneServiceLocator.getInstance();

	        PosTaggerServiceLocator posTaggerServiceLocator = talismaneServiceLocator.getPosTaggerServiceLocator();
	        PosTaggerService posTaggerService = posTaggerServiceLocator.getPosTaggerService();
	        File posTagSetFile = new File(posTagSetPath);
			PosTagSet posTagSet = posTaggerService.getPosTagSet(posTagSetFile);
			
	       	TalismaneSession.setPosTagSet(posTagSet);
	        
			
			File lexiconDir = new File(lexiconDirPath);
			LexiconDeserializer lexiconDeserializer = new LexiconDeserializer();
			List<PosTaggerLexicon> lexicons = lexiconDeserializer.deserializeLexicons(lexiconDir);
			LexiconChain lexiconChain = new LexiconChain();
			for (PosTaggerLexicon lexicon : lexicons) {
				lexiconChain.addLexicon(lexicon);
			}
	        
        	TalismaneSession.setLexicon(lexiconChain);
	 
	        TokeniserService tokeniserService = talismaneServiceLocator.getTokeniserServiceLocator().getTokeniserService();
	        TokenFeatureService tokenFeatureService = talismaneServiceLocator.getTokenFeatureServiceLocator().getTokenFeatureService();
	        TokeniserPatternService tokeniserPatternService = talismaneServiceLocator.getTokenPatternServiceLocator().getTokeniserPatternService();
	        TokenFilterService tokenFilterService = talismaneServiceLocator.getTokenFilterServiceLocator().getTokenFilterService();
			TreebankServiceLocator treebankServiceLocator = TreebankServiceLocator.getInstance(talismaneServiceLocator);

			if (treebankPath.length()==0)
				treebankServiceLocator.setDataSourcePropertiesFile("jdbc-ftb.properties");
	
			TreebankService treebankService = treebankServiceLocator.getTreebankService();
	        TreebankUploadService treebankUploadService = treebankServiceLocator.getTreebankUploadServiceLocator().getTreebankUploadService();
	        TreebankExportService treebankExportService = treebankServiceLocator.getTreebankExportServiceLocator().getTreebankExportService();

			MachineLearningService machineLearningService = talismaneServiceLocator.getMachineLearningServiceLocator().getMachineLearningService();

			if (command.equals("train")) {
				if (tokeniserModelFilePath.length()==0)
					throw new RuntimeException("Missing argument: tokeniserModel");
				if (tokeniserFeatureFilePath.length()==0)
					throw new RuntimeException("Missing argument: tokeniserFeatures");
				if (tokeniserPatternFilePath.length()==0)
					throw new RuntimeException("Missing argument: tokeniserPatterns");
				String modelDirPath = tokeniserModelFilePath.substring(0, tokeniserModelFilePath.lastIndexOf("/"));
				File modelDir = new File(modelDirPath);
				modelDir.mkdirs();
				
				File tokeniserModelFile = new File(tokeniserModelFilePath);
				
				TreebankReader treebankReader = null;
				
				if (treebankPath.length()>0) {
					File treebankFile = new File(treebankPath);
					treebankReader = treebankUploadService.getXmlReader(treebankFile, sentenceNumber);
				} else {
					TreebankSubSet testSection = TreebankSubSet.TRAINING;
					treebankReader = treebankService.getDatabaseReader(testSection, startSentence);
				}
				treebankReader.setSentenceCount(sentenceCount);

				TokeniserAnnotatedCorpusReader reader = treebankExportService.getTokeniserAnnotatedCorpusReader(treebankReader);
				
				List<String> tokenFilterDescriptors = new ArrayList<String>();
				if (tokenFilterPath!=null && tokenFilterPath.length()>0) {
					LOG.debug("From: " + tokenFilterPath);
					File tokenFilterFile = new File(tokenFilterPath);
					Scanner tokenFilterScanner = new Scanner(tokenFilterFile);
					while (tokenFilterScanner.hasNextLine()) {
						String descriptor = tokenFilterScanner.nextLine();
						tokenFilterDescriptors.add(descriptor);
					}
				}
				
				for (String descriptor : tokenFilterDescriptors) {
					LOG.debug(descriptor);
					if (descriptor.length()>0 && !descriptor.startsWith("#")) {
						TokenFilter tokenFilter = tokenFilterService.getTokenFilter(descriptor);
						reader.addTokenFilter(tokenFilter);
					}
				}
				
				List<String> tokenSequenceFilterDescriptors = new ArrayList<String>();
				if (tokenSequenceFilterPath!=null && tokenSequenceFilterPath.length()>0) {
					LOG.debug("From: " + tokenSequenceFilterPath);
					File tokenSequenceFilterFile = new File(tokenSequenceFilterPath);
					Scanner tokenSequenceFilterScanner = new Scanner(tokenSequenceFilterFile);
					while (tokenSequenceFilterScanner.hasNextLine()) {
						String descriptor = tokenSequenceFilterScanner.nextLine();
						tokenSequenceFilterDescriptors.add(descriptor);
					}
				}
				
				for (String descriptor : tokenSequenceFilterDescriptors) {
					LOG.debug(descriptor);
					if (descriptor.length()>0 && !descriptor.startsWith("#")) {
						TokenSequenceFilter tokenSequenceFilter = tokenFilterService.getTokenSequenceFilter(descriptor);
						reader.addTokenSequenceFilter(tokenSequenceFilter);
					}
				}
	
				File tokeniserPatternFile = new File(tokeniserPatternFilePath);
				Scanner scanner = new Scanner(tokeniserPatternFile);
				List<String> patternDescriptors = new ArrayList<String>();
				while (scanner.hasNextLine()) {
					String descriptor = scanner.nextLine();
					patternDescriptors.add(descriptor);
					LOG.debug(descriptor);
				}
				scanner.close();
				
				TokeniserPatternManager tokeniserPatternManager =
					tokeniserPatternService.getPatternManager(patternDescriptors);
	
				File tokeniserFeatureFile = new File(tokeniserFeatureFilePath);
				scanner = new Scanner(tokeniserFeatureFile);
				List<String> featureDescriptors = new ArrayList<String>();
				while (scanner.hasNextLine()) {
					String descriptor = scanner.nextLine();
					featureDescriptors.add(descriptor);
					LOG.debug(descriptor);
				}
				scanner.close();
				Set<TokeniserContextFeature<?>> tokeniserContextFeatures = tokenFeatureService.getTokeniserContextFeatureSet(featureDescriptors, tokeniserPatternManager.getParsedTestPatterns());
	
				CorpusEventStream tokeniserEventStream = tokeniserService.getTokeniserEventStream(reader, tokeniserContextFeatures, tokeniserPatternManager);
				
				Map<String,Object> trainParameters = new HashMap<String, Object>();
				if (algorithm.equals(MachineLearningAlgorithm.MaxEnt)) {
					trainParameters.put(MaxentModelTrainer.MaxentModelParameter.Iterations.name(), iterations);
					trainParameters.put(MaxentModelTrainer.MaxentModelParameter.Cutoff.name(), cutoff);
					if (smoothing > 0)
						trainParameters.put(MaxentModelTrainer.MaxentModelParameter.Smoothing.name(), smoothing);
				} else if (algorithm.equals(MachineLearningAlgorithm.Perceptron)) {
					trainParameters.put(PerceptronModelTrainer.PerceptronModelParameter.Iterations.name(), iterations);
					trainParameters.put(PerceptronModelTrainer.PerceptronModelParameter.Cutoff.name(), cutoff);
					trainParameters.put(PerceptronModelTrainer.PerceptronModelParameter.UseAverage.name(), perceptronAveraging);
					trainParameters.put(PerceptronModelTrainer.PerceptronModelParameter.UseSkippedAverage.name(), perceptronSkippedAveraging);					
					if (perceptronTolerance>=0)
						trainParameters.put(PerceptronModelTrainer.PerceptronModelParameter.Tolerance.name(), perceptronTolerance);					
				} else if (algorithm.equals(MachineLearningAlgorithm.LinearSVM)) {
					trainParameters.put(LinearSVMModelTrainer.LinearSVMModelParameter.Cutoff.name(), cutoff);
					if (solverType!=null)
						trainParameters.put(LinearSVMModelTrainer.LinearSVMModelParameter.SolverType.name(), solverType);
					if (constraintViolationCost>=0)
						trainParameters.put(LinearSVMModelTrainer.LinearSVMModelParameter.ConstraintViolationCost.name(), constraintViolationCost);
					if (epsilon>=0)
						trainParameters.put(LinearSVMModelTrainer.LinearSVMModelParameter.Epsilon.name(), epsilon);
				}

				ModelTrainer<TokeniserOutcome> trainer = machineLearningService.getModelTrainer(algorithm, trainParameters);
				
				DecisionFactory<TokeniserOutcome> decisionFactory = tokeniserService.getDecisionFactory();
				Map<String,List<String>> descriptors = new HashMap<String, List<String>>();
				descriptors.put(MachineLearningModel.FEATURE_DESCRIPTOR_KEY, featureDescriptors);
				descriptors.put(TokeniserPatternService.PATTERN_DESCRIPTOR_KEY, patternDescriptors);
				descriptors.put(TokenFilterService.TOKEN_FILTER_DESCRIPTOR_KEY, tokenFilterDescriptors);
				descriptors.put(TokenFilterService.TOKEN_SEQUENCE_FILTER_DESCRIPTOR_KEY, tokenSequenceFilterDescriptors);
				
				MachineLearningModel<TokeniserOutcome> tokeniserModel = trainer.trainModel(tokeniserEventStream, decisionFactory, descriptors);
	
				tokeniserModel.persist(tokeniserModelFile);
	
			} else if (command.equals("evaluate")) {
				TreebankReader treebankReader = null;
				
				if (treebankPath.length()>0) {
					File treebankFile = new File(treebankPath);
					treebankReader = treebankUploadService.getXmlReader(treebankFile, sentenceNumber);
				} else {
					TreebankSubSet testSection = TreebankSubSet.DEV;
					treebankReader = treebankService.getDatabaseReader(testSection, startSentence);
				}
				treebankReader.setSentenceCount(sentenceCount);				
				TokeniserAnnotatedCorpusReader reader = treebankExportService.getTokeniserAnnotatedCorpusReader(treebankReader);
					
				File outDir = new File(outDirPath);
				outDir.mkdirs();
				
				Writer errorFileWriter = null;
				String filebase = "results";
				if (tokeniserModelFilePath.length()>0)
					filebase = tokeniserModelFilePath.substring(tokeniserModelFilePath.lastIndexOf('/'));
				File errorFile = new File(outDir, filebase + ".errors.txt");
				errorFile.delete();
				errorFile.createNewFile();
				errorFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(errorFile, false),"UTF8"));
				
				FScoreCalculator<TokeniserOutcome> fScoreCalculator = null;

				Tokeniser tokeniser = null;
				if (tokeniserType.equalsIgnoreCase("simple")) {
					tokeniser = tokeniserService.getSimpleTokeniser();
				} else {
					if (tokeniserModelFilePath.length()==0)
						throw new RuntimeException("Missing argument: tokeniserModel");
					ZipInputStream zis = new ZipInputStream(new FileInputStream(tokeniserModelFilePath));
					MachineLearningModel<TokeniserOutcome> tokeniserModel = machineLearningService.getModel(zis);

					TokeniserPatternManager tokeniserPatternManager =
						tokeniserPatternService.getPatternManager(tokeniserModel.getDescriptors().get(TokeniserPatternService.PATTERN_DESCRIPTOR_KEY));
					Set<TokeniserContextFeature<?>> tokeniserContextFeatures = tokenFeatureService.getTokeniserContextFeatureSet(tokeniserModel.getFeatureDescriptors(), tokeniserPatternManager.getParsedTestPatterns());

					if (tokeniserType.equalsIgnoreCase("pattern")) {
						tokeniser = tokeniserPatternService.getPatternTokeniser(tokeniserPatternManager, tokeniserContextFeatures, null, beamWidth);
					} else if (tokeniserType.equalsIgnoreCase("maxent")) {
						tokeniser = tokeniserPatternService.getPatternTokeniser(tokeniserPatternManager, tokeniserContextFeatures, tokeniserModel.getDecisionMaker(), beamWidth);
						
					} else {
						throw new RuntimeException("Unknown tokeniser type: " + tokeniserType);
					}
					
					List<String> tokenFilterDescriptors = tokeniserModel.getDescriptors().get(TokenFilterService.TOKEN_FILTER_DESCRIPTOR_KEY);
					if (tokenFilterDescriptors!=null) {
						for (String descriptor : tokenFilterDescriptors) {
							if (descriptor.length()>0 && !descriptor.startsWith("#")) {
								TokenFilter tokenFilter = tokenFilterService.getTokenFilter(descriptor);
								tokeniser.addTokenFilter(tokenFilter);
								reader.addTokenFilter(tokenFilter);
							}
						}
					}

					List<String> tokenSequenceFilterDescriptors = tokeniserModel.getDescriptors().get(TokenFilterService.TOKEN_SEQUENCE_FILTER_DESCRIPTOR_KEY);
					if (tokenSequenceFilterDescriptors!=null) {
						for (String descriptor : tokenSequenceFilterDescriptors) {
							if (descriptor.length()>0 && !descriptor.startsWith("#")) {
								TokenSequenceFilter tokenSequenceFilter = tokenFilterService.getTokenSequenceFilter(descriptor);
								tokeniser.addTokenSequenceFilter(tokenSequenceFilter);
								reader.addTokenSequenceFilter(tokenSequenceFilter);
							}
						}
					}
				}
				
				TokeniserEvaluator evaluator = tokeniserService.getTokeniserEvaluator(tokeniser);
				
				TokenEvaluationFScoreCalculator tokenFScoreCalculator = new TokenEvaluationFScoreCalculator();
				tokenFScoreCalculator.setErrorWriter(errorFileWriter);
				evaluator.addObserver(tokenFScoreCalculator);
				evaluator.evaluate(reader);
				
				fScoreCalculator = tokenFScoreCalculator.getFScoreCalculator();
				double fscore = fScoreCalculator.getTotalFScore();
				LOG.debug("F-score for " + tokeniserModelFilePath + ": " + fscore);

				
				File fscoreFile = new File(outDir, filebase + ".fscores.csv");
				fScoreCalculator.writeScoresToCSVFile(fscoreFile);	
			}
		} finally {
			PerformanceMonitor.end();
		}
	}
}
