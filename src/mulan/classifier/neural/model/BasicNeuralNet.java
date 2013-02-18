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
 *    BasicNeuralNet.java
 *    Copyright (C) 2009-2012 Aristotle University of Thessaloniki, Greece
 */
package mulan.classifier.neural.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import mulan.core.ArgumentNullException;

/**
 * Implementation of basic neural network. The network consists of one input
 * layer, zero or more hidden layers and one output layer. Each layer contains 1
 * or more {@link Neuron} units. The input layer is used just to store and
 * forward input pattern of the network to first hidden layer for processing.
 * Input layer do not process input pattern. Neurons of input layer have one
 * input weight equal to 1, bias weight equal to 0 and use linear activation
 * function.
 * 
 * @author Jozef Vilcek
 * @version 2012.02.27
 */
public class BasicNeuralNet implements NeuralNet, Serializable {

	private static final long serialVersionUID = -8944873770650464701L;
	private final List<List<Neuron>> layers;
	private double[] currentNetOutput;
	private final int netInputDim;
	private final int netOutputDim;

	private ExecutorService threadPool;
	private ArrayBlockingQueue<Integer> inputQueue;
	private ArrayBlockingQueue<Integer> outputQueue;
	private int cpus;

	/**
	 * Creates a new {@link BasicNeuralNet} instance.
	 * 
	 * @param netTopology
	 *            defines a topology of the network. The array length
	 *            corresponds to number of network layers. The values of the
	 *            array corresponds to number of neurons in each particular
	 *            layer.
	 * @param biasInput
	 *            the bias input value for neurons of the neural network.
	 * @param activationFunction
	 *            the type of activation function to be used by network elements
	 * @param random
	 *            the pseudo-random generator instance to be used for
	 *            computations involving randomness. This parameter can be null.
	 *            In this case, new random instance with default seed will be
	 *            constructed where needed.
	 * @throws IllegalArgumentException
	 *             if network topology is incorrect of activation function class
	 *             is null.
	 */
	public BasicNeuralNet(int[] netTopology, double biasInput,
			Class<? extends ActivationFunction> activationFunction,
			Random random) {

		if (netTopology == null || netTopology.length < 2) {
			throw new IllegalArgumentException(
					"The topology for neural network is not specified "
							+ "or is invalid. Please provide correct topology for the network.");
		}
		if (activationFunction == null) {
			throw new ArgumentNullException("activationFunction");
		}

		netInputDim = netTopology[0];
		netOutputDim = netTopology[netTopology.length - 1];
		layers = new ArrayList<List<Neuron>>(netTopology.length);
		// set up input layer
		List<Neuron> inputLayer = new ArrayList<Neuron>(netTopology[0]);
		for (int n = 0; n < netTopology[0]; n++) {
			Neuron neuron = new Neuron(new ActivationLinear(), 1, biasInput,
					random);
			double[] weights = neuron.getWeights();
			weights[0] = 1;
			weights[1] = 0;
			inputLayer.add(neuron);
		}
		layers.add(inputLayer);
		int maxSize = inputLayer.size();

		// set up other layers
		try {
			for (int index = 1; index < netTopology.length; index++) {
				// create neurons of a layer
				List<Neuron> layer = new ArrayList<Neuron>(netTopology[index]);
				maxSize = Math.max(maxSize, netTopology[index]);

				for (int n = 0; n < netTopology[index]; n++) {
					Neuron neuron = new Neuron(
							activationFunction.newInstance(),
							netTopology[index - 1], biasInput, random);
					layer.add(neuron);
				}
				layers.add(layer);
				// add forward connections between layers
				List<Neuron> prevLayer = layers.get(index - 1);
				for (int n = 0; n < prevLayer.size(); n++) {
					prevLayer.get(n).addAllNeurons(layer);
				}
			}
		} catch (InstantiationException e) {
			throw new IllegalArgumentException(
					"Failed to create activation function instance.", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(
					"Failed to create activation function instance.", e);
		}

		this.cpus = 3; //Runtime.getRuntime().availableProcessors();
		this.threadPool = Executors.newFixedThreadPool(cpus);
		this.inputQueue = new ArrayBlockingQueue<Integer>(maxSize);
		this.outputQueue = new ArrayBlockingQueue<Integer>(maxSize);
	}

	public List<Neuron> getLayerUnits(int layerIndex) {

		return Collections.unmodifiableList(layers.get(layerIndex));
	}

	public int getLayersCount() {
		return layers.size();
	}

	private double[][] layersTmp = null;
	private int layerIndexTmp;
	private List<Neuron> layerTmp;
	private int layerSizeTmp;
	private AtomicInteger counter = new AtomicInteger();

	public double[] feedForward(final float[] inputPattern) {

		if (inputPattern == null || inputPattern.length != netInputDim) {
			throw new IllegalArgumentException(
					"Specified input pattern vector is null "
							+ "or does not match network input dimension.");
		}

		if (layersTmp == null) {
			layersTmp = new double[layers.size()][];

			for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++)
				layersTmp[layerIndex] = new double[layers.get(layerIndex)
						.size()];

			for (int cpu = 0; cpu < cpus; cpu++)
				threadPool.submit(new Runnable() {
					public void run() {
						while (true) {
							try {
								inputQueue.take(); // start
								while (true) {
									int n = counter.getAndIncrement();
									if (n >= layerSizeTmp)
										break;

									if (layerIndexTmp == 0)
										layersTmp[layerIndexTmp][n] = layerTmp
												.get(n)
												.processInput(
														new double[] { inputPattern[n] });
									else
										layersTmp[layerIndexTmp][n] = layerTmp
												.get(n)
												.processInput(
														layersTmp[layerIndexTmp - 1]);
								}
								outputQueue.offer(1);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}
				});
		}

		for (layerIndexTmp = 0; layerIndexTmp < layers.size(); layerIndexTmp++) {
			layerTmp = layers.get(layerIndexTmp);
			layerSizeTmp = layerTmp.size();
			try {
				counter.set(0);
				for (int n = 0; n < cpus; n++)
					inputQueue.offer(1);
				for (int n = 0; n < cpus; n++)
					outputQueue.take(); // ignore value
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		currentNetOutput = Arrays.copyOf(layersTmp[layers.size() - 1],
				layersTmp[layers.size() - 1].length);
		return currentNetOutput;
	}

	public double[] getOutput() {
		if (currentNetOutput == null) {
			return new double[netOutputDim];
		}

		return currentNetOutput;
	}

	public void reset() {
		currentNetOutput = null;
		for (List<Neuron> layer : layers) {
			for (Neuron neuron : layer) {
				neuron.reset();
			}
		}
	}

	public int getNetInputSize() {
		return netInputDim;
	}

	public int getNetOutputSize() {
		return netOutputDim;
	}
}