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
package com.joliciel.talismane.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.talismane.filters.Sentence;
import com.joliciel.talismane.machineLearning.Decision;
import com.joliciel.talismane.machineLearning.GeometricMeanScoringStrategy;
import com.joliciel.talismane.machineLearning.RankingSolution;
import com.joliciel.talismane.machineLearning.ScoringStrategy;
import com.joliciel.talismane.machineLearning.Solution;
import com.joliciel.talismane.machineLearning.features.Feature;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.posTagger.PosTagSequence;
import com.joliciel.talismane.posTagger.PosTaggedToken;
import com.joliciel.talismane.posTagger.PosTaggedTokenLeftToRightComparator;

final class ParseConfigurationImpl implements ParseConfigurationInternal {
    @SuppressWarnings("unused")
	private static final Log LOG = LogFactory.getLog(ParseConfigurationImpl.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private PosTagSequence posTagSequence;
	private double score;
	private boolean scoreCalculated = false;
	private Deque<PosTaggedToken> buffer;
	private Deque<PosTaggedToken> stack;
	private List<Transition> transitions;
	private Set<DependencyArc> dependencies;
	private Map<PosTaggedToken,DependencyArc> governingDependencyMap = null;
	private Map<PosTaggedToken, Set<PosTaggedToken>> leftDependentMap = null;
	private Map<PosTaggedToken, Set<PosTaggedToken>> rightDependentMap = null;
	private Map<PosTaggedToken,Transition> dependentTransitionMap = new HashMap<PosTaggedToken, Transition>();
	
	private ParserServiceInternal parserServiceInternal;
	
	private DependencyNode parseTree = null;
	
	private List<Decision<Transition>> decisions = new ArrayList<Decision<Transition>>();
	private int nextDecisionToAdd = 0;
	private List<Solution> underlyingSolutions = new ArrayList<Solution>();
	private ScoringStrategy scoringStrategy = new GeometricMeanScoringStrategy();
	private List<List<FeatureResult<?>>> incrementalFeatureResults = new ArrayList<List<FeatureResult<?>>>();

	private Map<String,FeatureResult<?>> featureCache = new HashMap<String, FeatureResult<?>>();
	
	private long createDate = System.currentTimeMillis();

	public ParseConfigurationImpl(PosTagSequence posTagSequence) {
		super();
		this.posTagSequence = posTagSequence;
		PosTaggedToken rootToken = posTagSequence.prependRoot();
		this.underlyingSolutions.add(this.posTagSequence);

		this.buffer = new ArrayDeque<PosTaggedToken>(posTagSequence.size());
		for (PosTaggedToken posTaggedToken : posTagSequence)
			this.buffer.add(posTaggedToken);
		this.buffer.remove(rootToken);
		
		this.stack = new ArrayDeque<PosTaggedToken>();
		this.stack.push(rootToken);
		
		this.dependencies = new TreeSet<DependencyArc>();
		this.transitions = new ArrayList<Transition>();
	}
	
	public ParseConfigurationImpl(ParseConfiguration history) {
		super();
		this.transitions = new ArrayList<Transition>(history.getTransitions());
		this.dependencies = new TreeSet<DependencyArc>(history.getDependencies());
		this.posTagSequence = history.getPosTagSequence();
		posTagSequence.prependRoot();
		this.underlyingSolutions.add(this.posTagSequence);
		this.buffer = new ArrayDeque<PosTaggedToken>(history.getBuffer());
		this.stack = new ArrayDeque<PosTaggedToken>(history.getStack());
		this.dependentTransitionMap = new HashMap<PosTaggedToken, Transition>(((ParseConfigurationInternal)history).getDependentTransitionMap());
		
		this.decisions = new ArrayList<Decision<Transition>>(history.getDecisions());
		this.nextDecisionToAdd = ((ParseConfigurationInternal)history).getNextDecisionToAdd();
	}
		
	@Override
	public PosTagSequence getPosTagSequence() {
		return this.posTagSequence;
	}

	@Override
	public double getScore() {
		if (!scoreCalculated) {
			score = this.scoringStrategy.calculateScore(this);
			scoreCalculated = true;
		}
		return score;
	}

	public void setScore(double score) {
		this.score = score;
		this.scoreCalculated = true;
	}

	@Override
	public Deque<PosTaggedToken> getBuffer() {
		return this.buffer;
	}

	@Override
	public Deque<PosTaggedToken> getStack() {
		return this.stack;
	}

	
	@Override
	public int compareTo(ParseConfiguration o) {
		// order by descending score if possible, otherwise by create date, otherwise by hash code
		if (this==o)
			return 0;
		else if (this.getScore()<o.getScore())
			return 1;
		else if (this.getScore()>o.getScore())
			return -1;
		else if (o instanceof ParseConfigurationInternal)
			return new Long(((ParseConfigurationInternal) o).getCreateDate() - this.getCreateDate()).intValue();
		else
			return o.hashCode() - this.hashCode();
	}

	@Override
	public boolean isTerminal() {
		return this.buffer.isEmpty();
	}

	public List<Transition> getTransitions() {
		return transitions;
	}

	public Set<DependencyArc> getDependencies() {
		return dependencies;
	}
	
	public Set<DependencyArc> getRealDependencies() {
		Set<DependencyArc> realDependencies = new TreeSet<DependencyArc>();
		for (DependencyArc arc : dependencies) {
			if (arc.getHead().getTag().equals(PosTag.ROOT_POS_TAG)
					&& (arc.getLabel()==null || arc.getLabel().length()==0)) {
				// do nothing
			} else {
				realDependencies.add(arc);
			}
		}
		return realDependencies;
	}

	@Override
	public PosTaggedToken getHead(PosTaggedToken dependent) {
		this.updateDependencyMaps();
		DependencyArc arc = this.governingDependencyMap.get(dependent);
		PosTaggedToken head = null;
		if (arc!=null)
			head = arc.getHead();
		return head;
	}


	@Override
	public DependencyArc getGoverningDependency(PosTaggedToken dependent) {
		this.updateDependencyMaps();
		DependencyArc arc = this.governingDependencyMap.get(dependent);
		return arc;
	}
	
	public Transition getTransition(DependencyArc arc) {
		PosTaggedToken dependent = arc.getDependent();
		Transition transition = this.dependentTransitionMap.get(dependent);
		return transition;
	}
	
	@Override
	public List<PosTaggedToken> getLeftDependents(PosTaggedToken head) {
		this.updateDependencyMaps();
		Set<PosTaggedToken> dependents = this.leftDependentMap.get(head);
		List<PosTaggedToken> dependentList = new ArrayList<PosTaggedToken>();
		if (dependents!=null)
			dependentList.addAll(dependents);
		return dependentList;
	}
	
	@Override
	public List<PosTaggedToken> getRightDependents(PosTaggedToken head) {
		this.updateDependencyMaps();
		Set<PosTaggedToken> dependents = this.rightDependentMap.get(head);
		List<PosTaggedToken> dependentList = new ArrayList<PosTaggedToken>();
		if (dependents!=null)
			dependentList.addAll(dependents);
		return dependentList;
	}

	
	@Override
	public List<PosTaggedToken> getDependents(PosTaggedToken head) {
		this.updateDependencyMaps();
		Set<PosTaggedToken> dependents = new TreeSet<PosTaggedToken>();
		dependents.addAll(this.getLeftDependents(head));
		dependents.addAll(this.getRightDependents(head));
		List<PosTaggedToken> dependentList = new ArrayList<PosTaggedToken>(dependents);
		return dependentList;
	}

	void updateDependencyMaps() {
		if (this.governingDependencyMap==null) {
			this.governingDependencyMap = new HashMap<PosTaggedToken, DependencyArc>();
			this.rightDependentMap = new HashMap<PosTaggedToken, Set<PosTaggedToken>>();
			this.leftDependentMap = new HashMap<PosTaggedToken, Set<PosTaggedToken>>();
			
			for (DependencyArc arc : this.dependencies) {
				this.governingDependencyMap.put(arc.getDependent(), arc);
				Map<PosTaggedToken, Set<PosTaggedToken>> dependentMap = null;
				if (arc.getDependent().getToken().getIndex()<arc.getHead().getToken().getIndex())
					dependentMap = this.leftDependentMap;
				else
					dependentMap = this.rightDependentMap;
				
				Set<PosTaggedToken> dependents = dependentMap.get(arc.getHead());
				if (dependents==null) {
					dependents = new TreeSet<PosTaggedToken>(new PosTaggedTokenLeftToRightComparator());
					dependentMap.put(arc.getHead(), dependents);
				}
				dependents.add(arc.getDependent());
			}
		}
	}
	
	@Override
	public DependencyArc addDependency(PosTaggedToken head,
			PosTaggedToken dependent, String label, Transition transition) {
		DependencyArc arc = this.parserServiceInternal.getDependencyArc(head, dependent, label);
		this.addDependency(arc);
		this.dependentTransitionMap.put(dependent, transition);
		
		// we assign this arc a probability
		// since several transitions (e.g. shift, reduce) could have occurred prior to adding a dependency
		// the probability will be the product of these transitions
		double probLog = 0.0;
		int decisionCount = 0;
		for (int i=nextDecisionToAdd; i<this.decisions.size(); i++) {
			probLog += decisions.get(i).getProbabilityLog();
			decisionCount++;
		}
		if (decisionCount>0) {
			nextDecisionToAdd = this.decisions.size();
			arc.setProbability(Math.exp(probLog));
		}
		return arc;
	}
	
	void addDependency(DependencyArc arc) {
		PosTaggedToken ancestor = arc.getHead();
		while (ancestor!=null) {
			if (ancestor.equals(arc.getDependent())) {
				throw new CircularDependencyException(this, arc.getHead(), arc.getDependent());
			}
			ancestor = this.getHead(ancestor);
		}
		
		this.dependencies.add(arc);
		
		if (this.governingDependencyMap!=null) {
			this.governingDependencyMap.put(arc.getDependent(), arc);
			Map<PosTaggedToken, Set<PosTaggedToken>> dependentMap = null;
			if (arc.getDependent().getToken().getIndex()<arc.getHead().getToken().getIndex())
				dependentMap = this.leftDependentMap;
			else
				dependentMap = this.rightDependentMap;
			
			Set<PosTaggedToken> dependents = dependentMap.get(arc.getHead());
			if (dependents==null) {
				dependents = new TreeSet<PosTaggedToken>(new PosTaggedTokenLeftToRightComparator());
				dependentMap.put(arc.getHead(), dependents);
			}
			dependents.add(arc.getDependent());
		}
	}

	public ParserServiceInternal getParserServiceInternal() {
		return parserServiceInternal;
	}

	public void setParserServiceInternal(ParserServiceInternal parserServiceInternal) {
		this.parserServiceInternal = parserServiceInternal;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		Iterator<PosTaggedToken> stackIterator = this.stack.iterator();
		if (stackIterator.hasNext())
			sb.insert(0, stackIterator.next().toString());
		if (stackIterator.hasNext())
			sb.insert(0, stackIterator.next().toString()+ ",");
		if (stackIterator.hasNext())
			sb.insert(0, stackIterator.next().toString()+ ",");
		if (stackIterator.hasNext())
			sb.insert(0, "...,");
		sb.insert(0, "Stack[");
		sb.append("]. Buffer[");
		Iterator<PosTaggedToken> bufferIterator = this.buffer.iterator();
		if (bufferIterator.hasNext())
			sb.append(bufferIterator.next().toString());
		if (bufferIterator.hasNext())
			sb.append("," + bufferIterator.next().toString());
		if (bufferIterator.hasNext())
			sb.append("," + bufferIterator.next().toString());
		if (bufferIterator.hasNext())
			sb.append(",...");
		sb.append("]");
		sb.append(" Deps[");
		for (DependencyArc arc : this.dependencies) {
			sb.append(arc.toString() + ",");
		}
		sb.append("]");
		return sb.toString();
	}

	public Map<PosTaggedToken, Transition> getDependentTransitionMap() {
		return dependentTransitionMap;
	}

	@Override
	public DependencyNode getParseTree() {
		if (parseTree==null) {
			PosTaggedToken root = null;
			for (PosTaggedToken token : this.posTagSequence) {
				if (token.getTag().equals(PosTag.ROOT_POS_TAG)) {
					root = token;
					break;
				}
			}
			parseTree = this.parserServiceInternal.getDependencyNode(root, "", this);
			parseTree.autoPopulate();
		}
		return parseTree;
	}

	@Override
	public DependencyNode getDetachedDependencyNode(
			PosTaggedToken posTaggedToken) {
		DependencyNode node = this.parserServiceInternal.getDependencyNode(posTaggedToken, "", this);
		return node;
	}

	@Override
	public List<Decision<Transition>> getDecisions() {
		return decisions;
	}

	@Override
	public List<Solution> getUnderlyingSolutions() {
		return underlyingSolutions;
	}

	@Override
	public void addDecision(Decision<Transition> decision) {
		this.decisions.add(decision);
	}

	public ScoringStrategy getScoringStrategy() {
		return scoringStrategy;
	}

	public void setScoringStrategy(ScoringStrategy scoringStrategy) {
		this.scoringStrategy = scoringStrategy;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T, Y> FeatureResult<Y> getResultFromCache(Feature<T, Y> feature, RuntimeEnvironment env) {
		FeatureResult<Y> result = null;
		
		String key = feature.getName() + env.getKey();
		if (this.featureCache.containsKey(key)) {
			result = (FeatureResult<Y>) this.featureCache.get(key);
		}
		return result;
	}

	@Override
	public <T, Y> void putResultInCache(Feature<T, Y> feature,
			FeatureResult<Y> featureResult, RuntimeEnvironment env) {
		String key = feature.getName() + env.getKey();
		this.featureCache.put(key, featureResult);	
	}

	@Override
	public ParseConfiguration getParseConfiguration() {
		return this;
	}

	public void clearMemory() {
		this.governingDependencyMap = null;
		this.rightDependentMap = null;
		this.leftDependentMap = null;
	}

	@Override
	public Sentence getSentence() {
		return this.getPosTagSequence().getTokenSequence().getSentence();
	}

	public int getNextDecisionToAdd() {
		return nextDecisionToAdd;
	}

	@Override
	public List<List<FeatureResult<?>>> getIncrementalFeatureResults() {
		return incrementalFeatureResults;
	}

	@Override
	public boolean canReach(RankingSolution correctSolution) {
		if (correctSolution instanceof ParseConfiguration) {
			ParseConfiguration configuration = (ParseConfiguration) correctSolution;
			if (configuration.getTransitions().size()<this.getTransitions().size()) {
				return false;
			}
			for (int i=0; i<this.getTransitions().size(); i++) {
				Transition myTransition = this.getTransitions().get(i);
				Transition hisTransition = configuration.getTransitions().get(i);
				if (!myTransition.getCode().equals(hisTransition.getCode())) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}

	public long getCreateDate() {
		return createDate;
	}

	@Override
	public List<String> getIncrementalOutcomes() {
		List<String> outcomes = new ArrayList<String>();
		for (Transition transition : this.transitions) {
			outcomes.add(transition.getCode());
		}
		return outcomes;
	}
	
	
}
