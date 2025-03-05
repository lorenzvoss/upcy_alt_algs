package de.upb.upcy.update;

public class BenchmarkUpdate {
  private String dependencyGraphPath;
  private String gav;
  private String modulePath;
  private String targetGAV;

  public BenchmarkUpdate(
      String pDependencyGraphPath, String pGAV, String pModulePath, String pTargetGAV) {
    this.dependencyGraphPath = pDependencyGraphPath;
    this.gav = pGAV;
    this.modulePath = pModulePath;
    this.targetGAV = pTargetGAV;
  }

  public String getDependencyGraphPath() {
    return dependencyGraphPath;
  }

  public void setDependencyGraphPath(String dependencyGraphPath) {
    this.dependencyGraphPath = dependencyGraphPath;
  }

  public String getGav() {
    return gav;
  }

  public void setGav(String gav) {
    this.gav = gav;
  }

  public String getModulePath() {
    return modulePath;
  }

  public void setModulePath(String modulePath) {
    this.modulePath = modulePath;
  }

  public String getTargetGAV() {
    return targetGAV;
  }

  public void setTargetGAV(String targetGAV) {
    this.targetGAV = targetGAV;
  }
}
