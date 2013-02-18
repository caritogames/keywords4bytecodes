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
 *    MultiLabelStackingTest.java
 *    Copyright (C) 2009-2012 Aristotle University of Thessaloniki, Greece
 */
package mulan.classifier.transformation;

import weka.classifiers.Classifier;
import weka.classifiers.functions.Logistic;
import weka.classifiers.lazy.IBk;

/**
 * Unit test routines for {@link MultiLabelStacking}.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class MultiLabelStackingTest extends TransformationBasedMultiLabelLearnerTest {

    @Override
    public void setUp() throws Exception {
        Classifier baseClassifier = new IBk();
        Classifier metaClassifier = new Logistic();
        learner = new MultiLabelStacking(baseClassifier, metaClassifier);
    }
}
