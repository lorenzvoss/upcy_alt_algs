package de.upb.upcy.update;

import java.util.ArrayList;

public class BenchmarkResult {
  public String algorithm;
  public String module;
  public ArrayList<Long> results;
  public double mean;

  public BenchmarkResult(String pAlgorithm, String pModule, ArrayList<Long> pResults) {
    algorithm = pAlgorithm;
    module = pModule;
    results = pResults;
    mean = calculateMean();
  }

  private double calculateMean() {
    if (results.isEmpty()) return 0;

    long sum = 0;
    for (Long value : results) {
      sum += value;
    }
    return (double) sum / results.size();
  }
}
