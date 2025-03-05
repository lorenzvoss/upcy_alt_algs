package de.upb.upcy.update.recommendation.algorithms;

import com.fasterxml.jackson.databind.JsonNode;
import de.upb.maven.ecosystem.persistence.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import de.upb.upcy.base.graph.GraphModel;
import de.upb.upcy.base.graph.GraphModel.Artifact;
import de.upb.upcy.base.graph.GraphModel.Dependency;
import de.upb.upcy.base.mvn.MavenInvokerProject;
import de.upb.upcy.base.mvn.MavenInvokerProject.BuildToolException;
import de.upb.upcy.base.mvn.MavenSearchAPIClient;
import de.upb.upcy.update.recommendation.BlossomGraphCreator;
import de.upb.upcy.update.recommendation.CustomEdge;
import de.upb.upcy.update.recommendation.NodeMatchUtil;
import de.upb.upcy.update.recommendation.UpdateSuggestion;
import de.upb.upcy.update.recommendation.check.UpdateCheck;
import de.upb.upcy.update.recommendation.check.Violation;
import de.upb.upcy.update.recommendation.cypher.CypherQueryCreator;
import de.upb.upcy.update.recommendation.exception.CompatabilityComputeException;
import de.upb.upcy.update.recommendation.exception.EmptyCallGraphException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.AsWeightedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DijkstraRecommendationAlgortihm implements IRecommendationAlgorithm {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EdmondsKarpRecommendationAlgorithm.class);
  private final DaoMvnArtifactNode doaMvnArtifactNode;

  private final MavenInvokerProject mavenInvokerProject;
  private NodeMatchUtil nodeMatchUtil;
  private Pair<DefaultDirectedGraph<GraphModel.Artifact, GraphModel.Dependency>, GraphModel>
      pairGraph;
  private Graph<String, CustomEdge> shrinkedCG;
  private boolean isInitialized;
  private DefaultDirectedGraph<GraphModel.Artifact, GraphModel.Dependency> depGraph;
  private GraphModel.Artifact rootNode;

  private Graph<GraphModel.Artifact, GraphModel.Dependency> blossemedDepGraph;
  private BlossomGraphCreator blossomGraphCreator;
  private CypherQueryCreator cypherQueryCreator;
  private String targetGav;

  public DijkstraRecommendationAlgortihm(
      MavenInvokerProject mavenInvokerProject, Path depGraphJsonFile) throws IOException {
    LOGGER.debug("Init connection to Neo4j");
    Driver driver = Neo4JConnector.getDriver();
    LOGGER.info("Connected successfully to Neo4j");

    doaMvnArtifactNode = new DoaMvnArtifactNodeImpl(driver);
    this.mavenInvokerProject = mavenInvokerProject;
    this.pairGraph = getDepGraph(depGraphJsonFile);
    this.isInitialized = false;
  }

  @Override
  public Pair<DefaultDirectedGraph<Artifact, Dependency>, GraphModel> getDepGraph(Path jsonDepGraph)
      throws IOException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getDepGraph'");
  }

  @Override
  public void initProject() throws BuildToolException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'initProject'");
  }

  // kick out non-compile dependencies and junit
  public static boolean isRelevantCompileDependency(GraphModel.Artifact artifact) {
    final boolean compile = artifact.getScopes().contains("compile");
    if (!compile) {
      return false;
    }
    return !StringUtils.contains(artifact.getArtifactId(), "junit");
  }

  @Override
  public List<UpdateSuggestion> run(String gavOfLibraryToUpdate, String targetGav)
      throws BuildToolException {
    this.targetGav = targetGav;
    final String[] targetGavSplit = targetGav.split(":");
    if (targetGavSplit.length < 2) {
      LOGGER.error("TargetGAV does not contain a valid version information");
      return Collections.emptyList();
    }
    String lowerVersionBound = targetGavSplit[2];

    this.initProject();

    final GraphModel.Artifact libToUpdateInDepGraph =
        nodeMatchUtil
            .findInDepGraphByGav(gavOfLibraryToUpdate, depGraph, true)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot find library to update with gav: " + gavOfLibraryToUpdate));

    if (isRelevantCompileDependency(libToUpdateInDepGraph)) {
      LOGGER.error("Only compile dependencies are currently supported");
      return Collections.emptyList();
    }

    if (shrinkedCG == null || shrinkedCG.vertexSet().isEmpty() || shrinkedCG.edgeSet().isEmpty()) {
      LOGGER.error("Empty shrinked CG");
    }

    List<UpdateSuggestion> updateSuggestions = new ArrayList<>();
    // run the algorithm

    // check which newer version are available on maven central
    List<String> newerVersions =
        getArtifactsWithNewerVersion(
            libToUpdateInDepGraph.getGroupId(),
            libToUpdateInDepGraph.getArtifactId(),
            lowerVersionBound);

    UpdateSuggestion simpleUpdateSuggestion =
        getSimpleUpdateSuggestion(libToUpdateInDepGraph, newerVersions);
    // get the weight -- if weight 0-- we are done
    if (simpleUpdateSuggestion.getStatus() == UpdateSuggestion.SuggestionStatus.SUCCESS
        && (simpleUpdateSuggestion.getViolations() == null
            || simpleUpdateSuggestion.getViolations().isEmpty())) {
      LOGGER.info("Simple Update does not produce any violations, Done");
      return Collections.singletonList(simpleUpdateSuggestion);
    }
    updateSuggestions.add(simpleUpdateSuggestion);

    // else we have violations continue with the dijkstra approach
    // call dijkstra and get update suggestions
    final List<UpdateSuggestion> minCutUpdateSuggestions =
        this.computeUpdateUsingDijkstra(libToUpdateInDepGraph, newerVersions);
    updateSuggestions.addAll(minCutUpdateSuggestions);

    LOGGER.info("Done with dijkstra");
    return updateSuggestions;
  }

  private List<UpdateSuggestion> computeUpdateUsingDijkstra(
      GraphModel.Artifact libToUpdateInDepGraph, List<String> newerVersions) {
    LOGGER.info("Compute Dijkstra solution");
    List<UpdateSuggestion> updateSuggestions = new ArrayList<>();
    // export graph for debugging
    final DOTExporter<GraphModel.Artifact, GraphModel.Dependency> objectObjectDOTExporter =
        new DOTExporter<>();
    objectObjectDOTExporter.setVertexAttributeProvider(
        v -> {
          Map<String, Attribute> map = new LinkedHashMap<>();
          map.put("label", DefaultAttribute.createAttribute(v.toGav()));
          return map;
        });

    objectObjectDOTExporter.exportGraph(blossemedDepGraph, new File("out.dot"));

    final AsSubgraph<GraphModel.Artifact, GraphModel.Dependency> blossomGraphCompileOnly =
        new AsSubgraph<>(
            blossemedDepGraph,
            blossemedDepGraph.vertexSet().stream()
                .filter(EdmondsKarpRecommendationAlgorithm::isRelevantCompileDependency)
                .collect(Collectors.toSet()),
            blossemedDepGraph.edgeSet());

    // use the blossom-graph
    // init all edge weights
    Map<GraphModel.Dependency, Double> initWeights = new HashMap<>();
    blossomGraphCompileOnly.edgeSet().forEach(x -> initWeights.put(x, 1.0));

    final AsWeightedGraph<GraphModel.Artifact, GraphModel.Dependency> unDirectedDepGraph =
        new AsWeightedGraph<>(new AsUndirectedGraph<>(blossomGraphCompileOnly), initWeights);

    DijkstraShortestPath<Artifact, Dependency> dijkstraShortestPath =
        new DijkstraShortestPath<Artifact, Dependency>(unDirectedDepGraph);

    GraphPath<Artifact, Dependency> computedPath =
        dijkstraShortestPath.getPath(this.rootNode, libToUpdateInDepGraph);

    List<Artifact> vertices = computedPath.getVertexList();
    if (vertices.isEmpty()) {
      // Ergebnis von Dijkstra ist leer; Fehler werfen? Log & return?
    }
    Collections.reverse(vertices); // reverse to start iteration at end vertex

    // Dont start at end vertex (now index 0 because of reversal)
    for (int i = 1; i < vertices.size(); i++) {
      Artifact currentVertex = vertices.get(i);
    }

    return updateSuggestions;
  }

  private UpdateSuggestion getSimpleUpdateSuggestion(
      GraphModel.Artifact libToUpdateInDepGraph, Collection<String> newerVersions) {

    // 1. check simple update
    // get the library to update and check which nodes are not updated
    // all nodes, which are in the dep-tree NOT after library (so, the ones in the
    // depgraph before)
    Collection<GraphModel.Artifact> updatedNodes = new ArrayList<>();
    // only check on compile and included edges, since we want to find out which
    // libraries are
    // included by the libToUpdate

    final AsSubgraph<GraphModel.Artifact, GraphModel.Dependency> depSubGraphOnlyCompileAndIncluded =
        new AsSubgraph<>(
            depGraph,
            depGraph.vertexSet().stream()
                .filter(EdmondsKarpRecommendationAlgorithm::isRelevantCompileDependency)
                .collect(Collectors.toSet()),
            depGraph.edgeSet().stream()
                .filter(x -> x.getResolution() == GraphModel.ResolutionType.INCLUDED)
                .collect(Collectors.toSet()));
    BreadthFirstIterator<GraphModel.Artifact, GraphModel.Dependency> breadthFirstIterator =
        new BreadthFirstIterator<>(depSubGraphOnlyCompileAndIncluded, libToUpdateInDepGraph);
    while (breadthFirstIterator.hasNext()) {
      final GraphModel.Artifact next = breadthFirstIterator.next();
      updatedNodes.add(next);
    }
    // the nodes that are not updated
    Collection<GraphModel.Artifact> unUpdatedNodes =
        depSubGraphOnlyCompileAndIncluded.vertexSet().stream()
            .filter(x -> !updatedNodes.contains(x))
            .collect(Collectors.toList());

    // get updateSubGraph - as received from neo4j
    DefaultDirectedGraph<MvnArtifactNode, DependencyRelation> updateSubGraph = null;
    String pickedVersion = "";
    for (String newVersion : newerVersions) {
      String neo4jQuery =
          cypherQueryCreator.createNeo4JQuery(
              depSubGraphOnlyCompileAndIncluded,
              Collections.singleton(libToUpdateInDepGraph),
              Collections.singleton(libToUpdateInDepGraph),
              libToUpdateInDepGraph,
              newVersion);
      LOGGER.trace(neo4jQuery);
      // query neo4j and get the update subgraph
      updateSubGraph = doaMvnArtifactNode.getGraph(neo4jQuery);

      if (updateSubGraph != null && !updateSubGraph.vertexSet().isEmpty()) {
        // we found a solution
        pickedVersion = newVersion;
        break;
      }
    }
    if (updateSubGraph == null || updateSubGraph.vertexSet().isEmpty()) {
      LOGGER.error("No solution found in Neo4j");
      UpdateSuggestion simpleUpdateSuggestion = new UpdateSuggestion();
      simpleUpdateSuggestion.setOrgGav(libToUpdateInDepGraph.toGav());
      simpleUpdateSuggestion.setTargetGav(targetGav);
      simpleUpdateSuggestion.setSimpleUpdate(true);
      simpleUpdateSuggestion.setStatus(UpdateSuggestion.SuggestionStatus.NO_NEO4J_ENTRY);
      simpleUpdateSuggestion.setNrOfViolations(-1);
      simpleUpdateSuggestion.setNrOfViolatedCalls(-1);
      return simpleUpdateSuggestion;
    }

    UpdateCheck updateCheck =
        new UpdateCheck(
            shrinkedCG,
            depGraph,
            unUpdatedNodes,
            updateSubGraph,
            nodeMatchUtil,
            blossomGraphCreator,
            false);

    final Collection<Violation> simpleUpdateViolations;

    // store it as a suggestions
    UpdateSuggestion simpleUpdateSuggestion = new UpdateSuggestion();
    simpleUpdateSuggestion.setOrgGav(libToUpdateInDepGraph.toGav());

    String updateGav =
        libToUpdateInDepGraph.getGroupId()
            + ":"
            + libToUpdateInDepGraph.getArtifactId()
            + ":"
            + pickedVersion;
    simpleUpdateSuggestion.setTargetGav(targetGav);
    simpleUpdateSuggestion.setUpdateGav(updateGav);
    simpleUpdateSuggestion.setSimpleUpdate(true);
    // if target targetGav and updateGav differ we found a better solution than the
    // naive update
    simpleUpdateSuggestion.setNaiveUpdate(StringUtils.equals(targetGav, updateGav));
    ArrayList<Pair<String, String>> updateSteps = new ArrayList<>();
    updateSteps.add(Pair.of(libToUpdateInDepGraph.toGav(), updateGav));
    simpleUpdateSuggestion.setUpdateSteps(updateSteps);
    try {
      simpleUpdateViolations =
          updateCheck.computeViolation(Collections.singletonList(libToUpdateInDepGraph));
      simpleUpdateSuggestion.setViolations(simpleUpdateViolations);
      simpleUpdateSuggestion.setNrOfViolations(
          Math.toIntExact(
              simpleUpdateViolations.stream()
                  .filter(x -> x.getViolatedCalls().size() > 0)
                  .count()));
      simpleUpdateSuggestion.setNrOfViolatedCalls(
          simpleUpdateViolations.stream()
              .mapToInt(x -> x == null ? 0 : x.getViolatedCalls().size())
              .sum());

      simpleUpdateSuggestion.setStatus(UpdateSuggestion.SuggestionStatus.SUCCESS);
      LOGGER.info("Simple Update has {} violations", simpleUpdateViolations.size());
    } catch (CompatabilityComputeException e) {
      simpleUpdateSuggestion.setNrOfViolations(-1);
      simpleUpdateSuggestion.setNrOfViolatedCalls(-1);
      simpleUpdateSuggestion.setStatus(UpdateSuggestion.SuggestionStatus.FAILED_SIGTEST);
    } catch (EmptyCallGraphException e) {
      simpleUpdateSuggestion.setStatus(UpdateSuggestion.SuggestionStatus.EMPTY_CG);
      simpleUpdateSuggestion.setNrOfViolations(-1);
      simpleUpdateSuggestion.setNrOfViolatedCalls(-1);
    }

    return simpleUpdateSuggestion;
  }

  private List<String> getArtifactsWithNewerVersion(
      String group, String artifact, String lowerBoundVersion) {
    DefaultArtifactVersion defaultArtifactVersion = new DefaultArtifactVersion(lowerBoundVersion);

    // number of artifacts that directly depends on this dependency
    // number of updates
    // check maven central for update version
    JsonNode listOfArtifacts = null;
    try {
      JsonNode response = MavenSearchAPIClient.getListOfArtifacts(group, artifact);
      listOfArtifacts = response.at("/response/docs");
    } catch (IOException e) {
      LOGGER.error("Failed to retrieve version from maven central", e);
    }
    if (listOfArtifacts == null || listOfArtifacts.isNull() || listOfArtifacts.isEmpty()) {
      LOGGER.error("Found no artifacts to update");
      return Collections.emptyList();
    }
    List<String> newerVersions = new ArrayList<>();
    // get the ones with a newer version
    for (Iterator<JsonNode> iterator = listOfArtifacts.iterator(); iterator.hasNext(); ) {
      final JsonNode next = iterator.next();
      final DefaultArtifactVersion nextVersion = new DefaultArtifactVersion(next.get("v").asText());

      if (nextVersion.compareTo(defaultArtifactVersion) >= 0) {
        newerVersions.add(next.get("v").asText());
      }
    }
    return newerVersions;
  }
}
