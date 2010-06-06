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
 *    PrunedSets.java
 *    Copyright (C) 2009-2010 Aristotle University of Thessaloniki, Thessaloniki, Greece
 */
package mulan.classifier.transformation;

import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;
import mulan.data.LabelSet;
import mulan.data.DataUtils;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;

/**
 * Class that implements the PrunedSets algorithm <p>
 *
 * @author Grigorios Tsoumakas 
 * @version June 4, 2010
 */
public class PrunedSets extends LabelsetPruning {

    /** strategies for processing infrequent labelsets*/
    public enum Strategy {

        /**
         * Strategy A: rank subsets firstly by the number of labels they
         * contain and secondly by the times they occur, then keep top b ranked
         */
        A,
        /**
         * Strategy B: keep all subsets of size greater than b
         */
        B;
    };
    /** strategy for processing infrequent labelsets */
    private Strategy strategy;
    /** parameter of strategy for processing infrequent labelsets*/
    private int b;

    /**
     * Constructor that initializes learner with base algorithm, parameter p
     * and strategy for processing infrequent labelsets
     *
     * @param classifier base single-label classification algorithm
     * @param aP number of instances required for a labelset to be included.
     * @param aStrategy strategy for processing infrequent labelsets
     * @param aB parameter of the strategy for processing infrequent labelsets
     */
    public PrunedSets(Classifier classifier, int aP, Strategy aStrategy, int aB) {
        super(classifier, aP);
        b = aB;
        strategy = aStrategy;
        setConfidenceCalculationMethod(2);
        setMakePredictionsBasedOnConfidences(true);
        threshold = 0.21;
    }

    /**
     * Returns an instance of a TechnicalInformation object, containing
     * detailed information about the technical background of this class,
     * e.g., paper reference or book this class is based on.
     *
     * @return the technical information about this class
     */
    @Override
    public TechnicalInformation getTechnicalInformation() {
        TechnicalInformation result = new TechnicalInformation(Type.INPROCEEDINGS);
        result.setValue(Field.AUTHOR, "Read, Jesse and Pfahringer, Bernhard and Holmes, Geoff");
        result.setValue(Field.TITLE, "Multi-Label Classification using Ensembles of Pruned Sets");
        result.setValue(Field.PAGES, "995-1000");
        result.setValue(Field.BOOKTITLE, "Proc. 8th IEEE International Conference on Data Mining (ICDM 2008)");
        result.setValue(Field.YEAR, "2008");
        return result;
    }

    @Override
    ArrayList<Instance> processRejected(LabelSet ls) {
        ArrayList<LabelSet> subsets = null;
        ArrayList<Instance> instances = null;
        ArrayList<Instance> newInstances = null; 
        switch (strategy) {
            case A:
                // split LabelSet into smaller ones
                //debug System.out.println("original:" + ls.toString());
                subsets = null;
                try {
                    subsets = ls.getSubsets();
                } catch (Exception ex) {
                    Logger.getLogger(PrunedSets.class.getName()).log(Level.SEVERE, null, ex);
                }
                //System.out.println("subsets: " + subsets.toString());
                ArrayList<LabelSet> sortedSubsets = new ArrayList<LabelSet>();
                for (LabelSet l : subsets) {
                    //System.out.println(l.toString());
                    // check if it exists in the training set
                    if (!ListInstancePerLabel.containsKey(l)) {
                        continue;
                    }
                    // check if it occurs more than p times 
                    if (ListInstancePerLabel.get(l).size() <= p) {
                        continue;
                    }
                    //
                    boolean added = false;
                    for (int i = 0; i < sortedSubsets.size(); i++) {
                        LabelSet l2 = sortedSubsets.get(i);
                        if (l.size() > l2.size()) {
                            sortedSubsets.add(i, l);
                            //System.out.println("adding " + l.toString());
                            added = true;
                            break;
                        }
                        if (l.size() == l2.size() && ListInstancePerLabel.get(l).size() > ListInstancePerLabel.get(l2).size()) {
                            sortedSubsets.add(i, l);
                            //System.out.println("adding " + l.toString());
                            added = true;
                            break;
                        }
                    }
                    if (added == false) {
                        //System.out.println("adding " + l.toString());
                        sortedSubsets.add(l);
                    }
                    //System.out.println("sorted: " + sortedSubsets.toString());
                }
                // take the top b
                newInstances = new ArrayList<Instance>();
                instances = ListInstancePerLabel.get(ls);
                for (Instance tempInstance : instances) {
                    int counter = 0;
                    for (LabelSet l : sortedSubsets) {
                        double[] temp = tempInstance.toDoubleArray();
                        double[] tempLabels = l.toDoubleArray();
                        for (int i = 0; i < numLabels; i++) {
                            if (format.attribute(labelIndices[i]).value(0).equals("0"))
                                temp[labelIndices[i]] = tempLabels[i];
                            else
                                temp[labelIndices[i]] = 1 - tempLabels[i];
                        }
                        Instance newInstance = DataUtils.createInstance(tempInstance, 1, temp);
                        newInstances.add(newInstance);
                        counter++;
                        if (counter == b) {
                            break;
                        }
                    }
                }
                return newInstances;
            case B:
                // split LabelSet into smaller ones
                //debug System.out.println("original:" + ls.toString());
                subsets = null;
                try {
                    subsets = ls.getSubsets();
                } catch (Exception ex) {
                    Logger.getLogger(PrunedSets.class.getName()).log(Level.SEVERE, null, ex);
                }
                ArrayList<LabelSet> subsetsForInsertion = new ArrayList<LabelSet>();
                for (LabelSet l : subsets) {
                    // check if it exists in the training set
                    if (!ListInstancePerLabel.containsKey(l)) {
                        continue;
                    }
                    // check if it occurs more than p times
                    if (ListInstancePerLabel.get(l).size() <= p) {
                        continue;
                    }
                    // check if it has more than b elements
                    if (l.size() <= b) {
                        continue;
                    }
                    subsetsForInsertion.add(l);
                }

                // insert subsetsForInsertion with corresponding instances
                // from the original labelset
                instances = ListInstancePerLabel.get(ls);
                newInstances = new ArrayList<Instance>();
                for (Instance tempInstance : instances) {
                    for (LabelSet l : subsetsForInsertion) {
                        double[] temp = tempInstance.toDoubleArray();
                        double[] tempLabels = l.toDoubleArray();
                        for (int i = 0; i < numLabels; i++) {
                            if (format.attribute(labelIndices[i]).value(0).equals("0"))
                                temp[labelIndices[i]] = tempLabels[i];
                            else
                                temp[labelIndices[i]] = 1 - tempLabels[i];
                        }
                        Instance newInstance = DataUtils.createInstance(tempInstance, 1, temp);
                        newInstances.add(newInstance);
                    }
                }
                return newInstances;
            default:
                return null;
        }
    }
}
