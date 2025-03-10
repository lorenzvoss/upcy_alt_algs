package de.upb.upcy.update;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainRunBenchmarkOnAllAlgorithms {

  private static final int COUNT_WARMUP_RUNS = 10;
  private static final int COUNT_RUNS_PER_ALGORITHM = 20;
  private static final boolean DO_PREFLIGHT_CHECK = false;

  private static final String[] ALGORITHMS = {
    "boykovkolmogorov", "gusfieldgomory", "edmondskarp", "pushrelabel"
  };
  private static final BenchmarkUpdate[] UPDATES_FOR_BENCHMARK = {
    new BenchmarkUpdate(
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/docker-maven-plugin/dependency-graph.json",
        "javax.ws.rs:javax.ws.rs-api:2.0",
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/docker-maven-plugin/",
        "javax.ws.rs:javax.ws.rs-api:2.1.1"),
    new BenchmarkUpdate(
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/logback-awslogs-appender/dependency-graph.json",
        "ch.qos.logback:logback-classic:1.1.7",
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/logback-awslogs-appender/",
        "ch.qos.logback:logback-classic:1.2.11"),
    new BenchmarkUpdate(
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/excelReader/dependency-graph.json",
        "org.apache.poi:poi-ooxml-schemas:3.10.1",
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/excelReader/",
        "org.apache.poi:poi-ooxml-schemas:4.1.2"),
    new BenchmarkUpdate(
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/extentreports-java/dependency-graph.json",
        "org.mongodb:mongodb-driver:3.3.0",
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/extentreports-java/",
        "org.mongodb:mongodb-driver:3.12.11"),
    new BenchmarkUpdate(
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/Copiers/dependency-graph.json",
        "org.ow2.asm:asm:6.2",
        "/home/lvoss/Bachelorarbeit/UpCy/projects_for_benchmark/Copiers/",
        "org.ow2.asm:asm:9.2"),
  };

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MainRunBenchmarkOnAllAlgorithms.class);

  public static void main(String[] args) {
    int countWarmupRuns = COUNT_WARMUP_RUNS;
    int countRunsPerAlgorithm = COUNT_RUNS_PER_ALGORITHM;

    ArrayList<BenchmarkResult> benchmarkResults = new ArrayList<>();

    if (args.length > 1) {

      try {
        countWarmupRuns = Integer.parseInt(args[0]);
      } catch (NumberFormatException ex) {
        LOGGER.warn("Error setting number of warmup runs: {}", ex);
        countWarmupRuns = COUNT_WARMUP_RUNS;
      }

      try {
        countRunsPerAlgorithm = Integer.parseInt(args[1]);
      } catch (NumberFormatException ex) {
        LOGGER.warn("Error setting number of test runs: {}", ex);
        countRunsPerAlgorithm = COUNT_RUNS_PER_ALGORITHM;
      }

    } else if (args.length > 0) {
      try {
        countWarmupRuns = Integer.parseInt(args[0]);
      } catch (NumberFormatException ex) {
        LOGGER.warn("Error setting number of warmup runs: {}", ex);
        countWarmupRuns = COUNT_WARMUP_RUNS;
      }
    }

    LOGGER.info("Number of warmup-runs: {}", countWarmupRuns);
    LOGGER.info("Number of test-runs: {}", countRunsPerAlgorithm);

    for (String algorithm : ALGORITHMS) {
      for (BenchmarkUpdate update : UPDATES_FOR_BENCHMARK) {
        ArrayList<Long> results;

        // run warmup
        run(countWarmupRuns, algorithm, update, false);

        // run main tests
        results = run(countRunsPerAlgorithm, algorithm, update, true);

        if (results != null) {
          benchmarkResults.add(new BenchmarkResult(algorithm, update.getModulePath(), results));
        } else {
          LOGGER.error("Executing benchmark went wrong. Benchmark results might be not valid.");
        }
      }
    }

    writeResultsToCSV(
        benchmarkResults, "benchmark_" + java.time.LocalDateTime.now().toString() + ".csv");
  }

  public static ArrayList<Long> run(
      int countRuns, String algorithm, BenchmarkUpdate update, boolean mainTests) {
    String runName;
    if (mainTests) {
      runName = "benchmark";
    } else {
      runName = "warmup";
    }

    ArrayList<Long> results = new ArrayList<Long>();

    LOGGER.info(
        "Start {} runs for algorithm {} with project {}",
        runName,
        algorithm,
        update.getModulePath());
    for (int i = 1; i <= countRuns; i++) {
      long start, end;
      try {
        start = System.nanoTime();

        MainMavenComputeUpdateSuggestion.main(
            new String[] {
              "-dg", update.getDependencyGraphPath(),
              "-gav", update.getGav(),
              "-module", update.getModulePath(),
              "-targetGav", update.getTargetGAV(),
              "-graphAlgorithm", algorithm
            });

        // LOGGER.info("run number {} for update {} with algorithm {}", i,
        // update.getModulePath(),
        // algorithm);

        end = System.nanoTime();
        results.add(end - start);
      } catch (Exception ex) {
        LOGGER.warn(ex.toString());
        return null;
      }

      // LOGGER.info("warmup nr {}", i);
    }

    for (Long value : results) {
      LOGGER.info("Gebrauchte Zeit: {}", value);
    }

    LOGGER.info(
        "Finished {} runs for algorithm {} with project {}",
        runName,
        algorithm,
        update.getModulePath());
    return results;
  }

  public static void writeResultsToCSV(
      ArrayList<BenchmarkResult> benchmarkResults, String filename) {
    if (benchmarkResults.isEmpty()) {
      System.out.println("Liste ist leer. Keine CSV-Datei erstellt.");
      return;
    }

    try (FileWriter writer = new FileWriter(filename)) {
      writer.append("Algorithm;Module;Mean");

      int maxResults = benchmarkResults.stream().mapToInt(r -> r.results.size()).max().orElse(0);

      for (int i = 1; i <= maxResults; i++) {
        writer.append(";Result").append(String.valueOf(i));
      }
      writer.append("\n");

      for (BenchmarkResult result : benchmarkResults) {
        writer
            .append(result.algorithm)
            .append(";")
            .append(result.module)
            .append(";")
            .append(String.valueOf(result.mean));

        for (Long time : result.results) {
          writer.append(";").append(time.toString());
        }

        writer.append("\n");
      }
      LOGGER.info("CSV-Datei erfolgreich erstellt: " + filename);
    } catch (IOException e) {
      LOGGER.error("Error writing csv file: {}", e);
    }
  }
}
