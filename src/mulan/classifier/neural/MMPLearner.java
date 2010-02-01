/*
*    This program is free software; you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation; either version 2 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program; if not, write to the Free Software
*    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

/*
*    MMPLearner.java
*    Copyright (C) 2009 Aristotle University of Thessaloniki, Thessaloniki, Greece
*
*/

package mulan.classifier.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mulan.classifier.InvalidDataException;
import mulan.classifier.MultiLabelLearnerBase;
import mulan.classifier.MultiLabelOutput;
import mulan.classifier.neural.model.ActivationLinear;
import mulan.classifier.neural.model.Neuron;
import mulan.core.ArgumentNullException;
import mulan.core.WekaException;
import mulan.data.MultiLabelInstances;
import mulan.evaluation.measure.AveragePrecision;
import mulan.evaluation.measure.ErrorSetSize;
import mulan.evaluation.measure.IsError;
import mulan.evaluation.measure.Measure;
import mulan.evaluation.measure.OneError;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NominalToBinary;

/**
 * Implementation of Multiclass Multilabel Percpetrons learner. For more information refer
 * to technical paper describing the learner.
 * 
 * <!-- technical-bibtex-start -->
 * 
 * <!-- technical-bibtex-end -->
 * 
 * @author Jozef Vilcek
 */
public class MMPLearner extends MultiLabelLearnerBase {

	/** Version UID for serialization */
	private static final long serialVersionUID = 2221778416856852684L;
	/** The bias value for perceptrons */
	private static final double PERCEP_BIAS = 1;
	
	/** 
	 * List of perceptrons representing model of the learner. One for each label. 
	 * They are ordered in same sequence as labels observed from training data.
	 **/
	private List<Neuron> perceptrons;
	/** Determines if feature attributes has to be normalized prior to learning */
	private boolean normalizeAttributes = true;
	/** Indicates whether any nominal attributes from input data set has to be converted to binary */
	private boolean convertNomToBin = true;
	/** Filter used for conversion of nominal attributes to binary (if enabled) */
	private NominalToBinary nomToBinFilter;
	/** The loss measure type to be used to judge the performance of ranking when learning the model */
	private final LossMeasure lossMeasure;
	/** The name of a model update rule used to update the model when learning from training data */
	private final MMPUpdateRuleType mmpUpdateRule;
	/** 
	 * The flag indicating if initialization with of learner first learning data samples already 
	 * took place. This is because the {@link MMPLearner} is online and updatable. 
	 */
	private boolean isInitialized = false;
	
	
	/**
	 * Creates a new instance of {@link MMPLearner}. 
	 * 
	 * @param lossMeasure the loss measure to be used when judging 
	 * 	ranking performance in learning process 
	 */
	public MMPLearner(LossMeasure lossMeasure, MMPUpdateRuleType modelUpdateRule){
		if(lossMeasure == null){
			throw new ArgumentNullException("lossMeasure");
		}
		if(modelUpdateRule == null){
			throw new ArgumentNullException("modelUpdateRule");
		}
		
		mmpUpdateRule = modelUpdateRule;
		this.lossMeasure = lossMeasure;
	}
	
	/**
	 * Sets whether nominal attributes from input data set has to be converted to binary
	 * prior to learning (and respectively making a prediction).
	 *  
	 * @param convert flag indicating whether conversion should take place
	 */
	public void setConvertNominalToBinary(boolean convert){
		convertNomToBin = convert;
	}
	
	/**
	 * Gets a value indication whether conversion of nominal attributes from input data 
	 * set to binary takes place prior to learning (and respectively making a prediction).
	 * 
	 * @return value indication whether conversion takes place
	 */
	public boolean getConvertNominalToBinary(){
		return convertNomToBin;
	}
	
	/**
	 * Sets whether feature attributes should be normalized prior to learning. 
	 * Normalization is performed on numeric attributes to the range <-1,1>.<br/> 
	 * When making prediction, attributes of passed input instance are also 
	 * normalized prior to making prediction.<br/>
	 * Default value is <code>true</code> (normalization of attributes takes place).
	 * 
	 * @param normalize flag if normalization of feature attributes should be performed
	 */
	public void setNormalizeAttributes(boolean normalize){
		normalizeAttributes = normalize;
	}
	
	/**
	 * Gets whether normalization of feature attributes takes place prior to learning.
	 * @return
	 */
	public boolean getNormalizeAttributes(){
		return normalizeAttributes;
	}
	
	@Override
	public boolean isUpdatable(){
		return true;
	}
	
	@Override
	protected void buildInternal(MultiLabelInstances trainingSet)
			throws Exception {
		
		trainingSet = trainingSet.clone();
		List<DataPair> trainData = prepareData(trainingSet);
		
		int numFeatures = trainData.get(0).getInput().length;
		if(!isInitialized){
			perceptrons = initializeModel(numFeatures, numLabels);
			isInitialized = true;
		}
		
		Measure lossMeasure = getLossMeasure();
		ModelUpdateRule modelUpdateRule = getModelUpdateRule(lossMeasure);
		
		for(DataPair dataItem : trainData){
			modelUpdateRule.process(dataItem, null);
		}
	}

	@Override
	public MultiLabelOutput makePredictionInternal(Instance instance) throws InvalidDataException {
		
		double[] input = getFeatureVector(instance);
		
		// update model prediction on raking for given example
		double[] labelConfidences = new double[numLabels];
		for(int index = 0; index < numLabels; index++){
			Neuron perceptron = perceptrons.get(index);
			labelConfidences[index] = perceptron.processInput(input);
		}
		
		MultiLabelOutput mlOut = new MultiLabelOutput(
				MultiLabelOutput.ranksFromValues(labelConfidences));
		
		return mlOut;
	}
	
	@Override
	public TechnicalInformation getTechnicalInformation() {
		TechnicalInformation technicalInfo = new TechnicalInformation(Type.ARTICLE);
	    technicalInfo.setValue(Field.AUTHOR, "Koby Crammer, Yoram Singer");
	    technicalInfo.setValue(Field.YEAR, "2003");
	    technicalInfo.setValue(Field.TITLE, "A Family of Additive Online Algorithms for Category Ranking.");
	    technicalInfo.setValue(Field.JOURNAL, "Journal of Machine Learning Research");
	    technicalInfo.setValue(Field.VOLUME, "3(6)");
	    technicalInfo.setValue(Field.PAGES, "1025�1058");
	    return technicalInfo;
	}
	
	private List<Neuron> initializeModel(int numFeatures, int numLabels){
		
		List<Neuron> perceptrons = new ArrayList<Neuron>(numLabels);
		for(int i=0; i<numLabels; i++){
			perceptrons.add(new Neuron(new ActivationLinear(), numFeatures, PERCEP_BIAS));
		}
		
		return perceptrons;
	}
	
	private Measure getLossMeasure() {
		switch(lossMeasure){
			case OneError:
				return new OneError();
			case IsError:
				return new IsError();
			case ErrorSetSize:
				return new ErrorSetSize();
			case AveragePrecision:
				return new AveragePrecision();
			default: 
				throw new IllegalArgumentException(String.format("The specified loss measure '%s' " +
						"is not supported.", lossMeasure));
		}
	}
	
	private ModelUpdateRule getModelUpdateRule(Measure lossMeasure) {
		switch (mmpUpdateRule) {
			case UniformUpdate:
				return new MMPUniformUpdateRule(perceptrons, lossMeasure);
	
			case MaxUpdate:
				return new MMPMaxUpdateRule(perceptrons, lossMeasure);
				
			case RandomizedUpdate:
				return new MMPRandomizedUpdateRule(perceptrons, lossMeasure);
				
			default:
				throw new IllegalArgumentException(String.format(
						"The specified model update rule '%s' is not supported.",
						mmpUpdateRule));
		}
	}
	
	/**
	 * Prepares {@link MultiLabelInstances} data set for a learning:<br/>
	 * - feature attributes are checked for correct format (nominal of numeric)
	 * - nominal feature attributes are converted to binary
	 * - feature attributes are normalized if normalization is enabled
	 * - instances are converted to {@link DataPair} instances (convenience for manipulation) 
	 */
	private List<DataPair> prepareData(MultiLabelInstances mlData){
		
		Set<Attribute> featureAttr = mlData.getFeatureAttributes();
		String nominalAttrRange = ensureAttributesFormat(featureAttr);
		Instances dataSet = mlData.getDataSet();
		
		// if configured, perform conversion of nominal attributes to binary
		if(convertNomToBin && nominalAttrRange.length() > 0){
			// create a filter definition for the first time  
			if(!isInitialized){
				nomToBinFilter = new NominalToBinary();
				try {
					nomToBinFilter = new NominalToBinary();
					nomToBinFilter.setAttributeIndices(nominalAttrRange.toString());
					nomToBinFilter.setInputFormat(dataSet);
				} catch (Exception exception) {
					nomToBinFilter = null;
					if(getDebug()){
						debug("Failed to create NominalToBinary filter for the input instances data. " +
								"Error message: " + exception.getMessage());
					}
					throw new WekaException("Failed to create NominalToBinary filter for the input instances data.", exception);
				}
			}
			
			// apply nominal -> binary filter to the data
			try {
				dataSet = Filter.useFilter(dataSet, nomToBinFilter);
				mlData = mlData.reintegrateModifiedDataSet(dataSet);
			} catch (Exception exception) {
				if(getDebug()){
					debug("Failed to apply NominalToBinary filter to the input instances data. " +
							"Error message: " + exception.getMessage());
				}
				throw new WekaException("Failed to apply NominalToBinary filter to the input instances data.", exception);
			}
		}
		
		return DataPair.createDataPairs(mlData, false);
	}
	
	/**
	 * Ensures that all attributes are nominal or numeric. In case they are not, 
	 * exception is thrown. 
	 * 
	 * @param attributes attributes to be checked
	 * @return the string with indices of nominal attributes, which can by used for 
	 * 	nominal to binary transformation of attributes
	 */
	private String ensureAttributesFormat(Set<Attribute> attributes){
		
		// TODO: where should the check takes place ... should be general and 
		//       and use declaratively "capabilities" similar to weka
		
		StringBuilder nominalAttrRange = new StringBuilder();
		String rangeDelimiter = ",";
		for(Attribute attribute : attributes){
			if(!attribute.isNumeric()){
				if(attribute.isNominal()){
					nominalAttrRange.append((attribute.index() + 1) + rangeDelimiter);
				}
				else{
					// fail check if any other attribute type than nominal or numeric is used
					//return false;
				}
			}
		}
		
		if(nominalAttrRange.length() > 0)
			nominalAttrRange.deleteCharAt(nominalAttrRange.lastIndexOf(rangeDelimiter));
		
		return nominalAttrRange.toString();
	}
	
	private double[] getFeatureVector(Instance inputInstance){

		// check if number in attributes is at least equal to model input
		int numAttributes = inputInstance.numAttributes();
		int modelInputDim = perceptrons.get(0).getWeights().length - 1;
		if(numAttributes < modelInputDim){
			throw new InvalidDataException("Input instance do not have enough attributes " +
					"to be processed by the model. Instance is not consistent with the data the model was built for.");
		}

		// copy the instance before modify (we are not touching mutable input data)
		inputInstance = (inputInstance instanceof SparseInstance) ?
				new SparseInstance(inputInstance) : new Instance(inputInstance);

		// perform normalization if necessary
		if(convertNomToBin && nomToBinFilter != null){
			nomToBinFilter.input(inputInstance);
			inputInstance = nomToBinFilter.output();
			inputInstance.setDataset(null);
		}

		// if instance has more attributes than model input, we assume that true outputs 
		// are there, so we remove them
		List<Integer> labelIndices = new ArrayList<Integer>();
		boolean labelsAreThere = false;
		if(numAttributes > modelInputDim){
			for(int index : this.labelIndices)
				labelIndices.add(index);
			
			labelsAreThere = true;
		}
		
		double[] inputPattern = new double[modelInputDim];
		int indexCounter = 0;
		for(int attrIndex = 0; attrIndex < numAttributes; attrIndex++){
			if(labelsAreThere && labelIndices.contains(attrIndex)){
				continue;
			}
			inputPattern[indexCounter] = inputInstance.value(attrIndex);
			indexCounter++;
		}
		
		return inputPattern;
	}
}
