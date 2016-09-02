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
package com.joliciel.talismane.parser;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.TalismaneService;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.machineLearning.ClassificationObserver;
import com.joliciel.talismane.machineLearning.Decision;
import com.joliciel.talismane.machineLearning.DecisionMaker;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.parser.features.ParseConfigurationFeature;
import com.joliciel.talismane.parser.features.ParserRule;
import com.joliciel.talismane.posTagger.PosTagSequence;
import com.joliciel.talismane.posTagger.PosTaggedToken;
import com.joliciel.talismane.tokeniser.TokenSequence;
import com.joliciel.talismane.utils.PerformanceMonitor;

/**
 * A non-deterministic parser implementing transition based parsing, using a
 * Shift-Reduce algorithm.<br/>
 * See Nivre 2008 for details on the algorithm for the deterministic case.</br>
 * 
 * @author Assaf Urieli
 *
 */
class TransitionBasedParserImpl implements TransitionBasedParser {
	private static final Logger LOG = LoggerFactory.getLogger(TransitionBasedParserImpl.class);
	private static final Logger LOG_FEATURES = LoggerFactory.getLogger(TransitionBasedParserImpl.class.getName() + ".features");
	private static final PerformanceMonitor MONITOR = PerformanceMonitor.getMonitor(TransitionBasedParserImpl.class);
	private static final double MIN_PROB_TO_STORE = 0.0001;
	private static final DecimalFormat df = new DecimalFormat("0.0000");
	private int beamWidth = 1;
	private boolean earlyStop = false;

	private Set<ParseConfigurationFeature<?>> parseFeatures;

	private ParserServiceInternal parserServiceInternal;
	private TalismaneService talismaneService;

	private DecisionMaker decisionMaker;
	private TransitionSystem transitionSystem;
	private ParseComparisonStrategy parseComparisonStrategy = new BufferSizeComparisonStrategy();

	private TalismaneSession talismaneSession;

	private List<ClassificationObserver> observers = new ArrayList<ClassificationObserver>();
	private int maxAnalysisTimePerSentence = 60;
	private int minFreeMemory = 64;
	private static final int KILOBYTE = 1024;

	private List<ParserRule> parserRules;
	private List<ParserRule> parserPositiveRules;
	private List<ParserRule> parserNegativeRules;

	public TransitionBasedParserImpl(DecisionMaker decisionMaker, TransitionSystem transitionSystem, Set<ParseConfigurationFeature<?>> parseFeatures,
			int beamWidth) {
		super();
		this.decisionMaker = decisionMaker;
		this.transitionSystem = transitionSystem;
		this.parseFeatures = parseFeatures;
		this.beamWidth = beamWidth;
	}

	@Override
	public ParseConfiguration parseSentence(PosTagSequence posTagSequence) {
		List<PosTagSequence> posTagSequences = new ArrayList<PosTagSequence>();
		posTagSequences.add(posTagSequence);
		List<ParseConfiguration> parseConfigurations = this.parseSentence(posTagSequences);
		ParseConfiguration parseConfiguration = parseConfigurations.get(0);
		return parseConfiguration;
	}

	@Override
	public List<ParseConfiguration> parseSentence(List<PosTagSequence> posTagSequences) {
		MONITOR.startTask("parseSentence");
		long startTime = System.currentTimeMillis();
		try {
			int maxAnalysisTimeMilliseconds = maxAnalysisTimePerSentence * 1000;
			int minFreeMemoryBytes = minFreeMemory * KILOBYTE;

			TokenSequence tokenSequence = posTagSequences.get(0).getTokenSequence();

			TreeMap<Integer, PriorityQueue<ParseConfiguration>> heaps = new TreeMap<Integer, PriorityQueue<ParseConfiguration>>();

			PriorityQueue<ParseConfiguration> heap0 = new PriorityQueue<ParseConfiguration>();
			for (PosTagSequence posTagSequence : posTagSequences) {
				// add an initial ParseConfiguration for each postag sequence
				ParseConfiguration initialConfiguration = this.getParserServiceInternal().getInitialConfiguration(posTagSequence);
				initialConfiguration.setScoringStrategy(decisionMaker.getDefaultScoringStrategy());
				heap0.add(initialConfiguration);
				if (LOG.isDebugEnabled()) {
					LOG.debug("Adding initial posTagSequence: " + posTagSequence);
				}
			}
			heaps.put(0, heap0);
			PriorityQueue<ParseConfiguration> backupHeap = null;

			PriorityQueue<ParseConfiguration> finalHeap = null;
			PriorityQueue<ParseConfiguration> terminalHeap = new PriorityQueue<ParseConfiguration>();
			while (heaps.size() > 0) {
				Entry<Integer, PriorityQueue<ParseConfiguration>> heapEntry = heaps.pollFirstEntry();
				PriorityQueue<ParseConfiguration> currentHeap = heapEntry.getValue();
				int currentHeapIndex = heapEntry.getKey();
				if (LOG.isTraceEnabled()) {
					LOG.trace("##### Polling next heap: " + heapEntry.getKey() + ", size: " + heapEntry.getValue().size());
				}

				boolean finished = false;
				// systematically set the final heap here, just in case we exit
				// "naturally" with no more heaps
				finalHeap = heapEntry.getValue();
				backupHeap = new PriorityQueue<ParseConfiguration>();

				// we jump out when either (a) all tokens have been attached or
				// (b) we go over the max alloted time
				ParseConfiguration topConf = currentHeap.peek();
				if (topConf.isTerminal()) {
					LOG.trace("Exiting with terminal heap: " + heapEntry.getKey() + ", size: " + heapEntry.getValue().size());
					finished = true;
				}

				if (earlyStop && terminalHeap.size() >= beamWidth) {
					LOG.debug("Early stop activated and terminal heap contains " + beamWidth + " entries. Exiting.");
					finalHeap = terminalHeap;
					finished = true;
				}

				long analysisTime = System.currentTimeMillis() - startTime;
				if (maxAnalysisTimePerSentence > 0 && analysisTime > maxAnalysisTimeMilliseconds) {
					LOG.info("Parse tree analysis took too long for sentence: " + tokenSequence.getText());
					LOG.info("Breaking out after " + maxAnalysisTimePerSentence + " seconds.");
					finished = true;
				}

				if (minFreeMemory > 0) {
					long freeMemory = Runtime.getRuntime().freeMemory();
					if (freeMemory < minFreeMemoryBytes) {
						LOG.info("Not enough memory left to parse sentence: " + tokenSequence.getText());
						LOG.info("Min free memory (bytes):" + minFreeMemoryBytes);
						LOG.info("Current free memory (bytes): " + freeMemory);
						finished = true;
					}
				}

				if (finished) {
					break;
				}

				// limit the breadth to K
				int maxSequences = currentHeap.size() > this.beamWidth ? this.beamWidth : currentHeap.size();

				int j = 0;
				while (currentHeap.size() > 0) {
					ParseConfiguration history = currentHeap.poll();
					if (LOG.isTraceEnabled()) {
						LOG.trace("### Next configuration on heap " + heapEntry.getKey() + ":");
						LOG.trace(history.toString());
						LOG.trace("Score: " + df.format(history.getScore()));
						LOG.trace(history.getPosTagSequence().toString());
					}

					List<Decision> decisions = new ArrayList<Decision>();

					// test the positive rules on the current configuration
					boolean ruleApplied = false;
					if (parserPositiveRules != null) {
						MONITOR.startTask("check rules");
						try {
							for (ParserRule rule : parserPositiveRules) {
								if (LOG.isTraceEnabled()) {
									LOG.trace("Checking rule: " + rule.toString());
								}
								RuntimeEnvironment env = new RuntimeEnvironment();
								FeatureResult<Boolean> ruleResult = rule.getCondition().check(history, env);
								if (ruleResult != null && ruleResult.getOutcome()) {
									Decision positiveRuleDecision = new Decision(rule.getTransition().getCode());
									decisions.add(positiveRuleDecision);
									positiveRuleDecision.addAuthority(rule.getCondition().getName());
									ruleApplied = true;
									if (LOG.isTraceEnabled()) {
										LOG.trace("Rule applies. Setting transition to: " + rule.getTransition().getCode());
									}
									break;
								}
							}
						} finally {
							MONITOR.endTask();
						}
					}

					if (!ruleApplied) {
						// test the features on the current configuration
						List<FeatureResult<?>> parseFeatureResults = new ArrayList<FeatureResult<?>>();
						MONITOR.startTask("feature analyse");
						try {
							for (ParseConfigurationFeature<?> feature : this.parseFeatures) {
								MONITOR.startTask(feature.getName());
								try {
									RuntimeEnvironment env = new RuntimeEnvironment();
									FeatureResult<?> featureResult = feature.check(history, env);
									if (featureResult != null)
										parseFeatureResults.add(featureResult);
								} finally {
									MONITOR.endTask();
								}
							}
							if (LOG_FEATURES.isTraceEnabled()) {
								for (FeatureResult<?> featureResult : parseFeatureResults) {
									LOG_FEATURES.trace(featureResult.toString());
								}
							}
						} finally {
							MONITOR.endTask();
						}

						// evaluate the feature results using the decision maker
						MONITOR.startTask("make decision");
						try {
							decisions = this.decisionMaker.decide(parseFeatureResults);

							for (ClassificationObserver observer : this.observers) {
								observer.onAnalyse(history, parseFeatureResults, decisions);
							}

							List<Decision> decisionShortList = new ArrayList<Decision>(decisions.size());
							for (Decision decision : decisions) {
								if (decision.getProbability() > MIN_PROB_TO_STORE)
									decisionShortList.add(decision);
							}
							decisions = decisionShortList;
						} finally {
							MONITOR.endTask();
						}

						// apply the negative rules
						Set<Transition> eliminatedTransitions = new HashSet<Transition>();
						if (parserNegativeRules != null) {
							MONITOR.startTask("check negative rules");
							try {
								for (ParserRule rule : parserNegativeRules) {
									if (LOG.isTraceEnabled()) {
										LOG.trace("Checking negative rule: " + rule.toString());
									}
									RuntimeEnvironment env = new RuntimeEnvironment();
									FeatureResult<Boolean> ruleResult = rule.getCondition().check(history, env);
									if (ruleResult != null && ruleResult.getOutcome()) {
										eliminatedTransitions.addAll(rule.getTransitions());
										if (LOG.isTraceEnabled()) {
											for (Transition eliminatedTransition : rule.getTransitions())
												LOG.trace("Rule applies. Eliminating transition: " + eliminatedTransition.getCode());
										}
									}
								}

								if (eliminatedTransitions.size() > 0) {
									List<Decision> decisionShortList = new ArrayList<Decision>();
									for (Decision decision : decisions) {
										if (!eliminatedTransitions.contains(decision.getOutcome())) {
											decisionShortList.add(decision);
										} else {
											LOG.trace("Eliminating decision: " + decision.toString());
										}
									}
									if (decisionShortList.size() > 0) {
										decisions = decisionShortList;
									} else {
										LOG.debug("All decisions eliminated! Restoring original decisions.");
									}
								}
							} finally {
								MONITOR.endTask();
							}
						}
					} // has a positive rule been applied?

					boolean transitionApplied = false;
					TransitionSystem transitionSystem = this.talismaneSession.getTransitionSystem();

					// add new configuration to the heap, one for each valid
					// transition
					MONITOR.startTask("heap sort");
					try {
						// Why apply all decisions here? Why not just the top N
						// (where N = beamwidth)?
						// Answer: because we're not always adding solutions to
						// the same heap
						// And yet: a decision here can only do one of two
						// things: process a token (heap+1000), or add a
						// non-processing transition (heap+1)
						// So, if we've already applied N decisions of each
						// type, we should be able to stop
						for (Decision decision : decisions) {
							Transition transition = transitionSystem.getTransitionForCode(decision.getOutcome());
							if (LOG.isTraceEnabled())
								LOG.trace("Outcome: " + transition.getCode() + ", " + decision.getProbability());

							if (transition.checkPreconditions(history)) {
								transitionApplied = true;
								ParseConfiguration configuration = this.parserServiceInternal.getConfiguration(history);
								if (decision.isStatistical())
									configuration.addDecision(decision);
								transition.apply(configuration);

								int nextHeapIndex = parseComparisonStrategy.getComparisonIndex(configuration) * 1000;
								if (configuration.isTerminal()) {
									nextHeapIndex = Integer.MAX_VALUE;
								} else {
									while (nextHeapIndex <= currentHeapIndex)
										nextHeapIndex++;
								}

								PriorityQueue<ParseConfiguration> nextHeap = heaps.get(nextHeapIndex);
								if (nextHeap == null) {
									if (configuration.isTerminal())
										nextHeap = terminalHeap;
									else
										nextHeap = new PriorityQueue<ParseConfiguration>();
									heaps.put(nextHeapIndex, nextHeap);
									if (LOG.isTraceEnabled())
										LOG.trace("Created heap with index: " + nextHeapIndex);
								}
								nextHeap.add(configuration);
								if (LOG.isTraceEnabled()) {
									LOG.trace("Added configuration with score " + configuration.getScore() + " to heap: " + nextHeapIndex + ", total size: "
											+ nextHeap.size());
								}

								configuration.clearMemory();
							} else {
								if (LOG.isTraceEnabled())
									LOG.trace("Cannot apply transition: doesn't meet pre-conditions");
								// just in case the we run out of both heaps and
								// analyses, we build this backup heap
								backupHeap.add(history);
							} // does transition meet pre-conditions?
						} // next transition
					} finally {
						MONITOR.endTask();
					}

					if (transitionApplied) {
						j++;
					} else {
						LOG.trace("No transitions could be applied: not counting this history as part of the beam");
					}

					// beam width test
					if (j == maxSequences)
						break;
				} // next history
			} // next atomic index

			// return the best sequences on the heap
			List<ParseConfiguration> bestConfigurations = new ArrayList<ParseConfiguration>();
			int i = 0;

			if (finalHeap.isEmpty())
				finalHeap = backupHeap;

			while (!finalHeap.isEmpty()) {
				bestConfigurations.add(finalHeap.poll());
				i++;
				if (i >= this.getBeamWidth())
					break;
			}
			if (LOG.isDebugEnabled()) {
				for (ParseConfiguration finalConfiguration : bestConfigurations) {
					LOG.debug(df.format(finalConfiguration.getScore()) + ": " + finalConfiguration.toString());
					LOG.debug("Pos tag sequence: " + finalConfiguration.getPosTagSequence());
					LOG.debug("Transitions: " + finalConfiguration.getTransitions());
					LOG.debug("Decisions: " + finalConfiguration.getDecisions());
					if (LOG.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						for (Decision decision : finalConfiguration.getDecisions()) {
							sb.append(" * ");
							sb.append(df.format(decision.getProbability()));
						}
						sb.append(" root ");
						sb.append(finalConfiguration.getTransitions().size());
						LOG.trace(sb.toString());

						sb = new StringBuilder();
						sb.append(" * PosTag sequence score ");
						sb.append(df.format(finalConfiguration.getPosTagSequence().getScore()));
						sb.append(" = ");
						for (PosTaggedToken posTaggedToken : finalConfiguration.getPosTagSequence()) {
							sb.append(" * ");
							sb.append(df.format(posTaggedToken.getDecision().getProbability()));
						}
						sb.append(" root ");
						sb.append(finalConfiguration.getPosTagSequence().size());
						LOG.trace(sb.toString());

						sb = new StringBuilder();
						sb.append(" * Token sequence score = ");
						sb.append(df.format(finalConfiguration.getPosTagSequence().getTokenSequence().getScore()));
						LOG.trace(sb.toString());

					}
				}
			}
			return bestConfigurations;
		} finally {
			MONITOR.endTask();
		}
	}

	@Override
	public int getBeamWidth() {
		return beamWidth;
	}

	public ParserServiceInternal getParserServiceInternal() {
		return parserServiceInternal;
	}

	public void setParserServiceInternal(ParserServiceInternal parserServiceInternal) {
		this.parserServiceInternal = parserServiceInternal;
	}

	@Override
	public void addObserver(ClassificationObserver observer) {
		this.observers.add(observer);
	}

	@Override
	public TransitionSystem getTransitionSystem() {
		return transitionSystem;
	}

	public void setTransitionSystem(TransitionSystem transitionSystem) {
		this.transitionSystem = transitionSystem;
	}

	@Override
	public int getMaxAnalysisTimePerSentence() {
		return maxAnalysisTimePerSentence;
	}

	@Override
	public void setMaxAnalysisTimePerSentence(int maxAnalysisTimePerSentence) {
		this.maxAnalysisTimePerSentence = maxAnalysisTimePerSentence;
	}

	@Override
	public int getMinFreeMemory() {
		return minFreeMemory;
	}

	@Override
	public void setMinFreeMemory(int minFreeMemory) {
		this.minFreeMemory = minFreeMemory;
	}

	@Override
	public void setParserRules(List<ParserRule> parserRules) {
		this.parserRules = parserRules;
		this.parserPositiveRules = new ArrayList<ParserRule>();
		this.parserNegativeRules = new ArrayList<ParserRule>();
		for (ParserRule rule : parserRules) {
			if (rule.isNegative())
				parserNegativeRules.add(rule);
			else
				parserPositiveRules.add(rule);
		}
	}

	@Override
	public List<ParserRule> getParserRules() {
		return parserRules;
	}

	@Override
	public ParseComparisonStrategy getParseComparisonStrategy() {
		return parseComparisonStrategy;
	}

	@Override
	public void setParseComparisonStrategy(ParseComparisonStrategy parseComparisonStrategy) {
		this.parseComparisonStrategy = parseComparisonStrategy;
	}

	@Override
	public boolean isEarlyStop() {
		return earlyStop;
	}

	@Override
	public void setEarlyStop(boolean earlyStop) {
		this.earlyStop = earlyStop;
	}

	@Override
	public Set<ParseConfigurationFeature<?>> getParseFeatures() {
		return parseFeatures;
	}

	@Override
	public void setParseFeatures(Set<ParseConfigurationFeature<?>> parseFeatures) {
		this.parseFeatures = parseFeatures;
	}

	public TalismaneService getTalismaneService() {
		return talismaneService;
	}

	public void setTalismaneService(TalismaneService talismaneService) {
		this.talismaneService = talismaneService;
		this.talismaneSession = talismaneService.getTalismaneSession();
	}

}
