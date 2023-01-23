package Model;

/**
 * Enum of possible linkage types for dependency edges in the graph database. They may either be Package-to-Package,
 * Artifact-to-Package or Artifact-to-Artifact.
 */
public enum Linkage {
    PackagePackage,
    ArtifactPackage,
    ArtifactArtifact
}
