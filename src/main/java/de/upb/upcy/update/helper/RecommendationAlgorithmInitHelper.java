package de.upb.upcy.update.helper;

import java.io.IOException;
import java.nio.file.Path;
import de.upb.upcy.base.mvn.MavenInvokerProject;
import de.upb.upcy.update.recommendation.algorithms.*;

public class RecommendationAlgorithmInitHelper {

    public static IRecommendationAlgorithm InitializeRecommendationAlgorithm(MavenInvokerProject mavenInvokerProject,
            Path depGraphJsonFile, String graphAlgorithm) throws IllegalArgumentException, IOException {
        if (graphAlgorithm == null) {
            return new EdmondsKarpRecommendationAlgorithm(mavenInvokerProject, depGraphJsonFile);
        }

        switch (graphAlgorithm.toLowerCase()) {
            case "dijkstra":
                throw new UnsupportedOperationException("Recommendation Algorithm with dijkstra is not implemented yet."); 
                //return new DijkstraRecommendationAlgortihm(mavenInvokerProject, depGraphJsonFile);
            case "boykov":
            case "kolmogorov":
            case "boykovkolmogorov":
                return new BoykovKolmogorovRecommendationAlgorithm(mavenInvokerProject, depGraphJsonFile);
            case "gusfield":
            case "gomory":
            case "gusfieldgomory":
                return new GusfieldGomoryHuCutTreeRecommendationAlgorithm(mavenInvokerProject, depGraphJsonFile);
            case "pushrelabel":
                return new PushRelabelRecommendationAlgorithm(mavenInvokerProject, depGraphJsonFile);
            case "edmonds":
            case "karp":
            case "edmondskarp":
                return new EdmondsKarpRecommendationAlgorithm(mavenInvokerProject, depGraphJsonFile);
            default: // Default to original implementation
                return new EdmondsKarpRecommendationAlgorithm(mavenInvokerProject, depGraphJsonFile);
        }
    }
}