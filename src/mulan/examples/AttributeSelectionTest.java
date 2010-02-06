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
 *    AttributeSelectionTest.java
 *    Copyright (C) 2009-2010 Aristotle University of Thessaloniki, Thessaloniki, Greece
 */
package mulan.examples;

import java.util.Arrays;
import mulan.attributeSelection.BinaryRelevanceAttributeEvaluator;
import mulan.attributeSelection.LabelPowersetAttributeEvaluator;
import mulan.attributeSelection.Ranker;
import mulan.attributeSelection.MultiClassAttributeEvaluator;
import mulan.data.MultiLabelInstances;
import mulan.transformations.multiclass.Copy;
import mulan.transformations.multiclass.MultiClassTransformation;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ChiSquaredAttributeEval;
import weka.core.Instances;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

/**
 * Demonstrates the attribute selection capabilities of Mulan
 *
 * @author Grigorios Tsoumakas
 */
public class AttributeSelectionTest {

    public static void main(String[] args) throws Exception {
        String path = Utils.getOption("path", args);
        String filestem = Utils.getOption("filestem", args);
        MultiLabelInstances mlData = new MultiLabelInstances(path + filestem + ".arff", path + filestem + ".xml");

        ASEvaluation ase = new ChiSquaredAttributeEval();
        //LabelPowersetAttributeEvaluator ae = new LabelPowersetAttributeEvaluator(ase, mlData);
        //BinaryRelevanceAttributeEvaluator ae = new BinaryRelevanceAttributeEvaluator(ase, mlData, "max", "dl", "eval");
        MultiClassTransformation mt = new Copy();
        MultiClassAttributeEvaluator ae = new MultiClassAttributeEvaluator(ase, mt, mlData);

        Ranker r = new Ranker();
        int[] result = r.search(ae, mlData);
        System.out.println(Arrays.toString(result));

        final int NUM_TO_KEEP = 10;
        int[] toKeep = new int[NUM_TO_KEEP + mlData.getNumLabels()];
        System.arraycopy(result, 0, toKeep, 0, NUM_TO_KEEP);
        int[] labelIndices = mlData.getLabelIndices();
        for (int i = 0; i < mlData.getNumLabels(); i++) {
            toKeep[NUM_TO_KEEP + i] = labelIndices[i];
        }

        Remove filterRemove = new Remove();
        filterRemove.setAttributeIndicesArray(toKeep);
        filterRemove.setInvertSelection(true);
        filterRemove.setInputFormat(mlData.getDataSet());
        Instances filtered = Filter.useFilter(mlData.getDataSet(), filterRemove);
        MultiLabelInstances mlFiltered = new MultiLabelInstances(filtered, mlData.getLabelsMetaData());

        // You can now work on the reduced multi-label dataset mlFiltered
    }
}
