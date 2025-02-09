package de.upb.upcy.update.recommendation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.DefaultDirectedGraph;

import de.upb.upcy.base.graph.GraphModel;
import de.upb.upcy.base.mvn.MavenInvokerProject;

public interface IRecommendationAlgorithm {
    public Pair<DefaultDirectedGraph<GraphModel.Artifact, GraphModel.Dependency>, GraphModel>
        getDepGraph(Path jsonDepGraph) throws IOException;

    public void initProject() throws MavenInvokerProject.BuildToolException;

    public List<UpdateSuggestion> run(String gavOfLibraryToUpdate, String targetGav)
      throws MavenInvokerProject.BuildToolException;
}
