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
package com.joliciel.talismane.utils.features;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.talismane.utils.JolicielException;
import com.joliciel.talismane.utils.util.PerformanceMonitor;

/**
 * An abstract base class for feature parsers,
 * which simplifies the parsing by performing reflection on the feature classes corresponding to function names.
 * @author Assaf Urieli
 *
 * @param <T>
 */
public abstract class AbstractFeatureParser<T> implements FeatureParser<T>, FeatureClassContainer {
	private static final Log LOG = LogFactory.getLog(AbstractFeatureParser.class);
	private FeatureService featureService;
	private Map<String,List<Feature<T, ?>>> namedFeatures = new TreeMap<String, List<Feature<T,?>>>();
	@SuppressWarnings("rawtypes")
	private Map<String,List<Class<? extends Feature>>> featureClasses = null;
	
	public AbstractFeatureParser(FeatureService featureService) {
		super();
		this.featureService = featureService;
	}

	@SuppressWarnings("rawtypes")
	final void addFeatureClassesInternal() {
		if (featureClasses==null) {
			// note: for a given classname with both IntegerFeature and DoubleFeature arguments,
			// the version with the IntegerFeature arguments should always be added first.
			featureClasses = new TreeMap<String, List<Class<? extends Feature>>>();
			this.addFeatureClass("RootWrapper", RootWrapper.class);
			this.addFeatureClass("-", MinusIntegerOperator.class);
			this.addFeatureClass("-", MinusOperator.class);
			this.addFeatureClass("+", PlusIntegerOperator.class);
			this.addFeatureClass("+", PlusOperator.class);
			this.addFeatureClass("*", MultiplyIntegerOperator.class);
			this.addFeatureClass("*", MultiplyOperator.class);
			this.addFeatureClass("/", DivideOperator.class);
			this.addFeatureClass("%", ModuloOperator.class);
			this.addFeatureClass("==", EqualsOperator.class);
			this.addFeatureClass("!=", NotEqualsOperator.class);
			this.addFeatureClass(">", GreaterThanOperator.class);
			this.addFeatureClass(">=", GreaterThanOrEqualsOperator.class);
			this.addFeatureClass("<", LessThanOperator.class);
			this.addFeatureClass("<=", LessThanOrEqualsOperator.class);
			this.addFeatureClass("&", AndFeature.class);
			this.addFeatureClass("|", OrFeature.class);
			this.addFeatureClass("Concat", ConcatenateFeature.class);
			this.addFeatureClass("ConcatNoNulls", ConcatenateNoNullsFeature.class);
			this.addFeatureClass("And", AndFeature.class);
			this.addFeatureClass("Or", OrFeature.class);
			this.addFeatureClass("Not", NotFeature.class);
			this.addFeatureClass("IsNull", IsNullFeature.class);
			this.addFeatureClass("IfThenElse", IfThenElseIntegerFeature.class);
			this.addFeatureClass("IfThenElse", IfThenElseDoubleFeature.class);
			this.addFeatureClass("IfThenElse", IfThenElseStringFeature.class);
			this.addFeatureClass("IfThenElse", IfThenElseBooleanFeature.class);
			this.addFeatureClass("NullIf", NullIfIntegerFeature.class);
			this.addFeatureClass("NullIf", NullIfDoubleFeature.class);
			this.addFeatureClass("NullIf", NullIfStringFeature.class);
			this.addFeatureClass("NullIf", NullIfBooleanFeature.class);
			this.addFeatureClass("OnlyTrue", OnlyTrueFeature.class);
			this.addFeatureClass("NullToFalse", NullToFalseFeature.class);
			this.addFeatureClass("Normalise", NormaliseFeature.class);
			this.addFeatureClass("ToString", ToStringFeature.class);
			this.addFeatureClass("Truncate", TruncateFeature.class);
			this.addFeatureClass("Round", RoundFeature.class);
			this.addFeatureClass("Graduate", GraduateFeature.class);
			this.addFeatureClass("Inverse", InverseFeature.class);
			this.addFeatureClass("Integer", IntegerLiteralFeatureWrapper.class);
			this.addFeatureClasses(this);
		}
	}

	/**
	 * Get the features corresponding to a particular descriptor by performing
	 * reflection on the corresponding feature class to be instantiated.
	 * @param descriptor
	 * @param featureClass
	 * @return
	 */
	final List<Feature<T, ?>> getFeatures(FunctionDescriptor descriptor, @SuppressWarnings("rawtypes") Class<? extends Feature> featureClass) {
		if (featureClass==null)
			throw new FeatureSyntaxException("No class provided for", descriptor);
		
		List<Feature<T,?>> features = new ArrayList<Feature<T,?>>();
		int i = 0;
		List<List<Object>> argumentLists = new ArrayList<List<Object>>();
		List<Object> initialArguments = new ArrayList<Object>();
		argumentLists.add(initialArguments);
		
		for (FunctionDescriptor argumentDescriptor : descriptor.getArguments()) {
			List<List<Object>> newArgumentLists = new ArrayList<List<Object>>();
			for (List<Object> arguments : argumentLists) {
				if (!argumentDescriptor.isFunction()) {
					Object literal = argumentDescriptor.getObject();
					Object convertedObject = literal;
					if (literal instanceof String) {
						StringLiteralFeature<T> stringLiteralFeature = new StringLiteralFeature<T>((String) literal);
						convertedObject = stringLiteralFeature;
					} else if (literal instanceof Boolean) {
						BooleanLiteralFeature<T> booleanLiteralFeature = new BooleanLiteralFeature<T>((Boolean) literal);
						convertedObject = booleanLiteralFeature;
					} else if (literal instanceof Double) {
						DoubleLiteralFeature<T> doubleLiteralFeature = new DoubleLiteralFeature<T>((Double) literal);
						convertedObject = doubleLiteralFeature;
					} else if (literal instanceof Integer) {
						IntegerLiteralFeature<T> integerLiteralFeature = new IntegerLiteralFeature<T>((Integer) literal);
						convertedObject = integerLiteralFeature;
					} else {
						// do nothing - this was some sort of other object added by getModifiedDescriptors that should
						// be handled as is.
					}
					arguments.add(convertedObject);
					newArgumentLists.add(arguments);
					
				} else {
					List<Feature<T,?>> featureArguments = this.parseInternal(argumentDescriptor);
					
					if (featureArguments.size()>0) {
						// a single argument descriptor could produce multiple arguments
						// e.g. when a function with an array argument is mapped onto multiple function calls
						for (Feature<T,?> featureArgument : featureArguments) {
							List<Object> newArguments = new ArrayList<Object>(arguments);
							newArguments.add(featureArgument);
							newArgumentLists.add(newArguments);
						}
					} else {
						Object argument = this.parseArgument(argumentDescriptor);
						if (argument==null) {
							throw new FeatureSyntaxException("Unknown function", argumentDescriptor);
						}
						arguments.add(argument);
						newArgumentLists.add(arguments);
					}
				} // function or object?

			} // next argument list (under construction from original arguments)
			argumentLists = newArgumentLists;
		} // next argument
		
		for (List<Object> originalArgumentList : argumentLists) {
			// add the argument types (i.e. classes)
			// and convert arrays to multiple constructor calls
			List<Object[]> argumentsList = new ArrayList<Object[]>();
			argumentsList.add(new Object[originalArgumentList.size()]);
			
			Class<?>[] argumentTypes = new Class<?>[originalArgumentList.size()];
			List<Object[]> newArgumentsList = new ArrayList<Object[]>();
			for (i=0;i<originalArgumentList.size();i++) {
				Object arg = originalArgumentList.get(i);
				
				if (arg.getClass().isArray()) {
					// arrays represent multiple constructor calls
					Object[] argArray = (Object[]) arg;
					for (Object oneArg : argArray) {
						for (Object[] arguments : argumentsList) {
							Object[] newArguments = arguments.clone();
							newArguments[i] = oneArg;
							newArgumentsList.add(newArguments);
						}
					}
					argumentTypes[i] = arg.getClass().getComponentType();
				} else {
					for (Object[] myArguments : argumentsList) {
						newArgumentsList.add(myArguments);
						myArguments[i] = arg;
					}
					argumentTypes[i] = arg.getClass();
				}
				argumentsList = newArgumentsList;
				newArgumentsList = new ArrayList<Object[]>();
			} // next argument
			
			@SuppressWarnings("rawtypes")
			Constructor<? extends Feature> constructor = null;
			PerformanceMonitor.startTask("AbstractFeatureParser.findContructor");
			try {
				constructor = ConstructorUtils.getMatchingAccessibleConstructor(featureClass, argumentTypes);
								
				if (constructor==null) {
					Constructor<?>[] constructors = featureClass.getConstructors();
					
					// check if there's a variable argument constructor
					for (Constructor<?> oneConstructor : constructors) {
						Class<?>[] parameterTypes = oneConstructor.getParameterTypes();
						
						if (parameterTypes.length==1 && argumentsList.size()==1 && argumentsList.get(0).length>=1) {
							Object[] arguments = argumentsList.get(0);
							Class<?> parameterType = parameterTypes[0];
							if (parameterType.isArray()) {
								// assume it's a variable-argument constructor
								// build the argument for this constructor
								// find a common type for all of the arguments.
								Object argument = arguments[0];
								Class<?> clazz = null;
								if (argument instanceof StringFeature)
									clazz = StringFeature.class;
								else if (argument instanceof BooleanFeature)
									clazz = BooleanFeature.class;
								else if (argument instanceof DoubleFeature)
									clazz = DoubleFeature.class;
								else if (argument instanceof IntegerFeature)
									clazz = IntegerFeature.class;
								else
									throw new FeatureSyntaxException("Unknown argument type: " + argument.getClass().getSimpleName(), descriptor);
								
								Object[] argumentArray = (Object[]) Array.newInstance(clazz, arguments.length);
								int j = 0;
								for (Object oneArgument : arguments) {
									argumentArray[j++] = oneArgument;
								} // next argument
								
								constructor = ConstructorUtils.getMatchingAccessibleConstructor(featureClass, argumentArray.getClass());
								
								if (constructor!=null) {
									argumentsList = new ArrayList<Object[]>();
									argumentsList.add(new Object[] {argumentArray});
									break;
								}
							} // constructor takes an array
						} // exactly one parameter for constructor
					} // next constructor
					
					if (constructor==null) {
						// see if converting ints to doubles finds a constructor
						for (Constructor<?> oneConstructor : constructors) {
							Class<?>[] parameterTypes = oneConstructor.getParameterTypes();
							boolean isMatchingConstructor = false;
							List<Integer> intParametersToConvert = new ArrayList<Integer>();
							if (parameterTypes.length==argumentTypes.length) {
								int j=0;
								isMatchingConstructor = true;
								for (Class<?> parameterType : parameterTypes) {
									if (parameterType.isAssignableFrom(argumentTypes[j])) {
										// nothing to do here
									} else if (parameterType.equals(DoubleFeature.class) && IntegerFeature.class.isAssignableFrom(argumentTypes[j])) {
										intParametersToConvert.add(j);
									} else {
										isMatchingConstructor = false;
										break;
									}
									j++;
								}
							}
							if (isMatchingConstructor) {
								@SuppressWarnings({ "rawtypes", "unchecked" })
								Constructor<? extends Feature> matchingConstructor = (Constructor<? extends Feature>) oneConstructor;
								constructor = matchingConstructor;
								
								for (Object[] myArguments : argumentsList) {
									for (int indexToConvert : intParametersToConvert) {
										@SuppressWarnings("unchecked")
										IntegerFeature<T> integerFeature = (IntegerFeature<T>) myArguments[indexToConvert];
										IntegerToDoubleFeature<T> intToDoubleFeature = new IntegerToDoubleFeature<T>(integerFeature);
										myArguments[indexToConvert] = intToDoubleFeature;
									}
								}
								break;
							} // found a matching constructor
						} // next possible constructor
					} // still haven't found a constructor, what next?
				} // didn't find a constructor yet
			} finally {
				PerformanceMonitor.endTask("AbstractFeatureParser.findContructor");
			}
			
			if (constructor==null)
				throw new NoConstructorFoundException("No constructor found for " + descriptor.getFunctionName() + " (" + featureClass.getName() + ") matching the arguments provided", descriptor);			
			
			for (Object[] myArguments : argumentsList) {
				@SuppressWarnings("rawtypes")
				Feature feature;
				try {
					feature = constructor.newInstance(myArguments);
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(e);
				} catch (InstantiationException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				} catch (InvocationTargetException e) {
					throw new RuntimeException(e);
				}
				
				@SuppressWarnings("unchecked")
				Feature<T,?> convertedFeature = this.convertFeature(feature);
				
				features.add(convertedFeature);
			} // next internal argument list
		} // next argument list
		return features;
	}
	
	/**
	 * Add the feature return-type interface if required,
	 * so that the feature can be used as an argument for features requiring this return type.
	 * @param feature
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	Feature<T, ?> convertFeature(Feature<T,?> feature) {
		Feature<T, ?> convertedFeature = feature;
		if (feature.getFeatureType().equals(StringFeature.class) && !(feature instanceof StringFeature)) {
			convertedFeature = new StringFeatureWrapper(feature);
		} else if (feature.getFeatureType().equals(BooleanFeature.class) && !(feature instanceof BooleanFeature)) {
			convertedFeature = new BooleanFeatureWrapper(feature);
		} else if (feature.getFeatureType().equals(DoubleFeature.class) && !(feature instanceof DoubleFeature)) {
			convertedFeature = new DoubleFeatureWrapper(feature);
		} else if (feature.getFeatureType().equals(IntegerFeature.class) && !(feature instanceof IntegerFeature)) {
			convertedFeature = new IntegerFeatureWrapper(feature);
		}
		return convertedFeature;
	}

	/**
	 * Parse an argument that is specific to a particular parser type
	 * (e.g. an address function within a dependency parser parse configuration),
	 * or null if the argumentDescriptor is unparseable.
	 * @param argumentDescriptor
	 * @return
	 */
	protected abstract Object parseArgument(FunctionDescriptor argumentDescriptor);
	
	@SuppressWarnings("unchecked")
	@Override
	public final List<Feature<T, ?>> parse(FunctionDescriptor descriptor) {
		this.addFeatureClassesInternal();
		FunctionDescriptor rootDescriptor = this.featureService.getFunctionDescriptor("RootWrapper");
		rootDescriptor.addArgument(descriptor);
		List<Feature<T, ?>> rootFeatures = this.parseInternal(rootDescriptor);
		List<Feature<T, ?>> features = new ArrayList<Feature<T,?>>();
		for (Feature<T, ?> rootFeature : rootFeatures) {
			Feature<T, ?> oneRootFeature = rootFeature;
			while (oneRootFeature instanceof FeatureWrapper) {
				oneRootFeature = ((FeatureWrapper<T,?>) oneRootFeature).getWrappedFeature();
			}
			RootWrapper<T, ?> rootWrapper = (RootWrapper<T, ?>) oneRootFeature;
			features.add(rootWrapper.feature);
		}
		if (descriptor.getDescriptorName()!=null && descriptor.getDescriptorName().length()>0) {
			if (featureClasses.containsKey(descriptor.getDescriptorName())||namedFeatures.containsKey(descriptor.getDescriptorName())) {
				throw new JolicielException("Feature name already used: " + descriptor.getDescriptorName());
			}
			this.namedFeatures.put(descriptor.getDescriptorName(), features);
			for (Feature<T,?> feature : features) {
				feature.setGroupName(descriptor.getDescriptorName());
			}
			if (features.size()==1)
				features.get(0).setName(descriptor.getDescriptorName());

		}
		return features;
	}
	
	final List<Feature<T, ?>> parseInternal(FunctionDescriptor descriptor) {
		if (LOG.isTraceEnabled())
			LOG.trace(descriptor.toString());
		List<Feature<T, ?>> features = new ArrayList<Feature<T,?>>();
		
		List<FunctionDescriptor> modifiedDescriptors = new ArrayList<FunctionDescriptor>();
		if (descriptor.getFunctionName().equals("IndexRange")) {
			if (descriptor.getArguments().size()<2 || descriptor.getArguments().size()> 3)
				throw new FeatureSyntaxException(descriptor.getFunctionName() + " needs 2 or 3 arguments", descriptor);
			if (!(descriptor.getArguments().get(0).getObject() instanceof Integer))
				throw new FeatureSyntaxException(descriptor.getFunctionName() + " argument 1 must be a whole number", descriptor);
			if (!(descriptor.getArguments().get(1).getObject() instanceof Integer))
				throw new FeatureSyntaxException(descriptor.getFunctionName() + " argument 2 must be a whole number", descriptor);
			if (descriptor.getArguments().size()==3 && !(descriptor.getArguments().get(2).getObject() instanceof Integer))
				throw new FeatureSyntaxException(descriptor.getFunctionName() + " argument 3 must be a whole number", descriptor);
				
			int start = ((Integer)descriptor.getArguments().get(0).getObject()).intValue();
			int end = ((Integer)descriptor.getArguments().get(1).getObject()).intValue();
			int step = 1;
			if (descriptor.getArguments().size()==3)
				step = ((Integer)descriptor.getArguments().get(2).getObject()).intValue();
			
			for (int i=start;i<=end;i+=step) {
				FunctionDescriptor indexDescriptor = this.getFeatureService().getFunctionDescriptor("Integer");
				indexDescriptor.addArgument(i);
				modifiedDescriptors.add(indexDescriptor);
			}
		}
		
		if (modifiedDescriptors.size()==0)
			modifiedDescriptors = this.getModifiedDescriptors(descriptor);
		if (modifiedDescriptors==null)
			modifiedDescriptors = new ArrayList<FunctionDescriptor>();
		if (modifiedDescriptors.size()==0)
			modifiedDescriptors.add(descriptor);
		
		for (FunctionDescriptor modifiedDescriptor : modifiedDescriptors) {
			String functionName = modifiedDescriptor.getFunctionName();
			@SuppressWarnings("rawtypes")
			List<Class<? extends Feature>> featureClasses = this.featureClasses.get(functionName);
			if (featureClasses!=null) {
				// add the features corresponding to the first class for which a constructor is found
				int i = 0;
				for (@SuppressWarnings("rawtypes") Class<? extends Feature> featureClass : featureClasses) {
					boolean lastClass = (i==featureClasses.size()-1);
					boolean foundConstructor = false;
					try {
						features.addAll(this.getFeatures(modifiedDescriptor, featureClass));
						foundConstructor = true;
					} catch (NoConstructorFoundException ncfe) {
						if (lastClass)
							throw ncfe;
					}
					if (foundConstructor)
						break;
					i++;
				}
			} else if (namedFeatures.containsKey(functionName)) {
				features.addAll(namedFeatures.get(functionName));
			}
		}
		
		return features;
	}

	
	/**
	 * Add all feature classes supported by this parser via calls to addFeatureClass.
	 * Note: for a given classname which is mapped to two different classes,
	 * one with IntegerFeature and one with DoubleFeature arguments,
	 * the version with the IntegerFeature arguments should always be added first.
	 * This is only required if the class returns a different type of feature result
	 * (e.g. int or double) depending on the arguments provided.
	 */
	public abstract void addFeatureClasses(FeatureClassContainer container);
	
	@SuppressWarnings("rawtypes")
	final public void addFeatureClass(String name, Class<? extends Feature> featureClass) {
		List<Class<? extends Feature>> featureClasses = this.featureClasses.get(name);
		if (featureClasses==null) {
			featureClasses = new ArrayList<Class<? extends Feature>>();
			this.featureClasses.put(name, featureClasses);
		}
		featureClasses.add(featureClass);
	}
	
	/**
	 * Return the feature classes currently mapped to the name provided.
	 */
	@SuppressWarnings("rawtypes")
	final public List<Class<? extends Feature>> getFeatureClasses(String name) {
		return this.featureClasses.get(name);
	}

	/**
	 * Given a feature descriptor, converts it into multiple feature descriptors if required,
	 * for example when generating a separate feature for each pos-tag, or for an whole range of indexes.
	 * Should return a List containing the initial function descriptor if no modification is required.
	 * @param functionDescriptor
	 * @return
	 */
	public abstract List<FunctionDescriptor> getModifiedDescriptors(FunctionDescriptor functionDescriptor);
	
	public static class RootWrapper<T,Y> extends AbstractFeature<T,Y> implements Feature<T,Y> {
		private Feature<T,Y> feature;
		public RootWrapper(Feature<T,Y> feature) {
			this.feature = feature;
			this.setName(super.getName() + "|" + this.feature.getName());
		}

		@Override
		public FeatureResult<Y> check(T context) {
			return null;
		}
		
		@SuppressWarnings("rawtypes")
		@Override
		public Class<? extends Feature> getFeatureType() {
			return feature.getFeatureType();
		}
	}

	private static class StringFeatureWrapper<T> extends AbstractFeature<T,String> implements StringFeature<T>, FeatureWrapper<T,String> {
		private Feature<T,String> feature;

		public StringFeatureWrapper(Feature<T,String> feature) {
			this.feature = feature;
			this.setName(this.feature.getName());
		}

		@Override
		public FeatureResult<String> check(T context) {
			return this.feature.check(context);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Class<? extends Feature> getFeatureType() {
			return feature.getFeatureType();
		}

		@Override
		public Feature<T, String> getWrappedFeature() {
			return feature;
		}

	}
	
	private static class BooleanFeatureWrapper<T> extends AbstractFeature<T,Boolean> implements BooleanFeature<T>, FeatureWrapper<T,Boolean> {
		private Feature<T,Boolean> feature;
		public BooleanFeatureWrapper(Feature<T,Boolean> feature) {
			this.feature = feature;
			this.setName(this.feature.getName());
		}
		
		@Override
		public FeatureResult<Boolean> check(T context) {
			return this.feature.check(context);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Class<? extends Feature> getFeatureType() {
			return feature.getFeatureType();
		}

		@Override
		public Feature<T, Boolean> getWrappedFeature() {
			return feature;
		}
	}
	
	private static class DoubleFeatureWrapper<T> extends AbstractFeature<T,Double> implements DoubleFeature<T>, FeatureWrapper<T,Double> {
		private Feature<T,Double> feature;
		public DoubleFeatureWrapper(Feature<T,Double> feature) {
			this.feature = feature;
			this.setName(this.feature.getName());
		}
		
		@Override
		public FeatureResult<Double> check(T context) {
			return this.feature.check(context);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Class<? extends Feature> getFeatureType() {
			return feature.getFeatureType();
		}

		@Override
		public Feature<T, Double> getWrappedFeature() {
			return feature;
		}
	}
	
	private static class IntegerFeatureWrapper<T> extends AbstractFeature<T,Integer> implements IntegerFeature<T>, FeatureWrapper<T,Integer> {
		private Feature<T,Integer> feature;
		public IntegerFeatureWrapper(Feature<T,Integer> feature) {
			this.feature = feature;
			this.setName(this.feature.getName());
		}
		
		@Override
		public FeatureResult<Integer> check(T context) {
			return this.feature.check(context);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Class<? extends Feature> getFeatureType() {
			return feature.getFeatureType();
		}

		@Override
		public Feature<T, Integer> getWrappedFeature() {
			return feature;
		}
	}

	public final FeatureService getFeatureService() {
		return featureService;
	}

	public final void setFeatureService(FeatureService featureService) {
		this.featureService = featureService;
	}
	
}
