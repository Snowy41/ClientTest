package com.hades.client.ai;

/**
 * A lightweight, highly optimized mathematical representation of a native FeedForward Neural Network.
 * Uses strict matrix dot-products to process heuristic parameters into decision nodes.
 */
public class NeuralNet {

    private final float[][] weights;
    private final float[] biases;

    private final int inputSize;
    private final int outputSize;

    /**
     * Initializes a standard Perceptron.
     * For simplistic decision matrices like item-sorting, hidden layers over-complicate prediction 
     * without a massive labeled dataset, so we use direct input->output mappings configured by heuristic tuning.
     */
    public NeuralNet(int inputSize, int outputSize) {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        
        this.weights = new float[outputSize][inputSize];
        this.biases = new float[outputSize];
    }

    /**
     * Defines exactly what the network "knows" by injecting a structured heuristic matrix.
     */
    public void setWeightsAndBiases(float[][] weights, float[] biases) {
        if (weights.length != outputSize || weights[0].length != inputSize) {
            throw new IllegalArgumentException("NeuralNet dimension mismatch");
        }
        for (int i = 0; i < outputSize; i++) {
            System.arraycopy(weights[i], 0, this.weights[i], 0, inputSize);
            this.biases[i] = biases[i];
        }
    }

    /**
     * Propagates an array of features forward through the network.
     * @param inputs Array of normalized features [0.0 - 1.0]
     * @return Array of predictive outcomes [0.0 - 1.0] (e.g [DropProbability, HotbarProbability])
     */
    public float[] predict(float[] inputs) {
        if (inputs.length != inputSize) return new float[outputSize];

        float[] outputs = new float[outputSize];
        
        for (int i = 0; i < outputSize; i++) {
            float sum = biases[i];
            for (int j = 0; j < inputSize; j++) {
                sum += inputs[j] * weights[i][j];
            }
            outputs[i] = sigmoid(sum);
        }
        
        return outputs;
    }

    private float sigmoid(float x) {
        return 1.0f / (1.0f + (float) Math.exp(-x));
    }
}
