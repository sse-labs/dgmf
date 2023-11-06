# Dependency Graph Mining Framwork (DGMF)
This repository holds DGMF, an extensible framwork for generating whole-repository dependency graphs. You can use DGMF out-of-the-box to build configurable dependency graphs for [Maven Central](https://mvnrepository.com/repos/central), [NPM](), [NuGet.org](https://www.npmjs.com) and [PyPi](https://pypi.org). You can also extend DGMF by adding adapters for new repositories with very little implementation effort. We rely on a [Neo4j](https://neo4j.org) graph database backend to store resulting graphs.

## Prerequisites
Building DGMF locally requires *Java 15* or higher, as well as *Maven*. Alternatively, you can soley rely on *Docker* to build the application and generate dependency graphs.

## Quickstart
DGMF comes with a total of four different repository adapters built-in: *Maven Central*, *NPM*, *NuGet.org* and *PyPi*. To build dependency graphs for one of those repositories, execute the following steps:

1. Make sure you have access to an empty *Neo4j* Database, Version 5.0.0 or higher. You will need `username`, `password` and the `url` for its *Bolt* protocol.
    

2. Configure the application by editing the `system.properties` file:
    * Set the value for `dgm.repo` to either `maven`, `npm`, `nuget` or `pypi` to select your repository
    * Set the value for `dgm.linkage` to either `pp` (Package-to-Package), `ap` (Artifact-to-Package) or `aa` (Artifact-to-Artifact) to configure the dependency resolution level
    
    * Set the values for `dgm.databaseaddress`, `dgm.databaseusername` and `dgm.databasepassword` according to your Neo4j instance

3. Build the application by executing `mvn clean package`
4. Start building a dependency graph by executing `java -jar ./target/dgmf.jar start`
5. **Using Docker:** Instead of steps 3) and 4) execute `docker build --tag dgm-miner:latest .` to build an image, and `docker run --detach dgm-miner:latest` to start building a dependency graph

## Building Dependency Graphs
DGMF outputs dependency graphs to a *Neo4j* graph database instance. Therefore, in order to run DGMF you will need access to an empty Neo4j instance in version 5.0.0 or higher. Specifically, you need the *Bolt Protocol URL* (of form `bolt://<host>:<port>`), as well as username and password for the database. If you do not have access to an existing Neo4j instance you can deploy one yourself:

* The most convenient way to deploy Neo4j is via *Docker*. Just execute `docker pull neo4j` and `docker run --detach -p 7687:7687 -p 7474:7474 neo4j:latest`. Afterwards you can set the password for the default user `neo4j` via a Web-Interface at `localhost:7474`. Note that you need to restart the Docker container to apply the password changes. Details about the docker image can be found [here](https://hub.docker.com/_/neo4j/).
* You can also [install Neo4j directly on your machine](https://neo4j.com/download/) if you want to. Remember to use a version higher than 5.0.0.

To configure DGMF, you may edit all properties specified in the `system.properties` file. Alternatively, you can configure and control the DGMF interactively in the console when the `interactive-shell` flag is set. The following configuration properties are available:

| Property                   | Values                            |      Default Value      | Description                                                                                                                                                         |
|:---------------------------|:----------------------------------|:-----------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `dgm.repo`                 | {`pypi`, `maven`, `npm`, `nuget`} |           npm           | Select Repository to work on                                                                                                                                        |
| `dgm.parallel`             | Positive Integers                 |           10            | Number of parallel streaming pipelines to use. Empirically, we found values between 10 and 30 to be a good fit for most architectures.                              |
| `dgm.linkage`              | {`pp`, `ap`, `aa`}                |           pp            | Dependency resolution level to use for resolving dependency edges. Either Package-to-Package (`pp`), Artifact-to-Package (`ap`) or Artifact-to-Artifact (`aa`).     |
| `dgm.databaseaddress`      | Strings                           | `bolt://localhost:7687` | Neo4j *Bolt Protocol* URL of form `bolt://<host>:<port>`                                                                                                            |
| `dgm.databaseusername`     | Strings                           |         `neo4j`         | Username for connecting to Neo4j                                                                                                                                    |
| `dgm.databasepassword`     | Strings                           |         `neo4j`         | Password for connecting to Neo4j                                                                                                                                    |
| `dgm.limit`                | Positive Integers                 |            0            | If limit != 0, mining is stopped after processing the specified number of packages.                                                                                 |
| `dgm.offset`               | Positive Integers                 |            0            | If offset != 0, the specified number of packages are skipped when building a dependency graph.                                                                      |
| `dgm.interactive-shell`    | {`true`, `false`}                 |         `false`         | If true, DGMF starts an interactive shell session.                                                                                                                  |
| `dgm.import-ids`           | {`true`, `false`}                 |         `false`         | If true, package ids are not generated live, but imported from an id file that was previously exported using DGMF.                                                  |
| `dgm.id-file`              | String                            |  `<dgm.repo>_ids.txt`   | Only applies if `dgm.import-ids` is `true`. Specifies path to file that holds package ids.                                                                          |
| `dgm.npm.commit-qualifier` | String                            |        `master`         | Sets which commit or branch of [Connor White's NPM package list](https://github.com/bconnorwhite/all-package-names) shall be used to generate the NPM package list. |

### Building DGMF locally
You can build the DGMF executable `.jar` file locally on your machine. To do this, you need to execute to following command:
```
mvn clean package
```
Afterwards, the executable file can be found at `./target/dgmf.jar`. You can start building your configured dependency graph by executing:
```
java -jar ./target/dgmf.jar start
```
You can also use the executable file to invoke any other supported command, [as specified below](#other-functionality).

### Building DGMF via Docker
You can also use our preconfigured Docker image to build and start DGMF. Just execute the following commands inside the repository root directory to build an image and start generating your configured dependency graph:
```
docker build --tag dgm-miner:latest .
docker run --detach dgm-miner:latest
```


## Other Functionality
Besides building full dependency graphs, DGMF also provides a number of commands for executing partial and additional tasks. The following commands are available:

|         Command         | Description                                                                                                                                                                                                                      |
|:-----------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|    `start [<repo>]`     | Starts building a new dependency graph as described above. An optionally specified value for `<repo>` will override the corresponding value in `system.properties`                                                               |
|        `export`         | Generates a list of all package ids for the specified repository and exports it to a file. The filename will be `<repo>_ids.txt`.                                                                                                |
|         `parse`         | Resolves all unresolved Artifact-to-Package edges in the specified Neo4j database into Artifact-to-Artifact edges.                                                                                                               |
|        `update`         | Performs an incremental update on the dependency graph contained in the specified Neo4j database. Needs to be executed with the same dependency resolution level as the original graph.                                          |
|        `delete`         | Clears the currently selected Neo4j database by removing all nodes and edges.                                                                                                                                                    |
|         `help`          | Prints usage and further help                                                                                                                                                                                                    |
|         `stop`          | **Only available in interactive shell:** Stops whatever task is currently being executed by DGMF.                                                                                                                                |
|        `status`         | **Only available in interactive shell:** Prints progress for whatever task is currently being executed by DGMF.                                                                                                                  |
|    `logs [<logId>]`     | **Only available in interactive shell:** Prints a summary of all exceptions that have been thrown in the current session. If `<logId>` is specified, a detailed stack trace for the exception with the respective id is printed. |
|        `config`         | **Only available in interactive shell:** Prints the current configuration for DGMF                                                                                                                                               |
| `config <prop> <value>` | **Only available in interactive shell:** Changes the current configuration by setting the value of `<prop>` to `<value>`                                                                                                         |
|      `config save`      | **Only available in interactive shell:** Writes the current session configuration back to the `system.properties` file                                                                                                           |

## Implementing new Repository Adapters
In order to create dependency graphs for repositories not supported out-of-the-box, DGMF can be extended by implementing additional repository adapters. In essence, adapters need to specify three repository-specific aspects:
1. How a set of all package ids is enumerated for the repository
2. How package- and artifactdata is accessed and converted to DGMFs internal data model.
3. How version ranges for dependency specifications are resolved (only if you want to be able to generate Artifact-to-Artifact graphs).

All other functionality, including I/O, stream supervision and error handling are managed by DGMF. This guide walks you through the steps necessary to intergrate a new repository adapter.

### Prerequisites
At first, you need to create a new Java package `Repositories.<RepoName>` at `./src/main/java/Repositories`. All classes that you will create for your adapter in the subsequent steps go into this package.

### Enumerating Package Ids
For enumerating all package ids for the repository, you need to provide an implementation of the `IdGenerator` interface as described below. It defines a single Method `generateIds()` to return a list of all package identifiers for the repository. Based on this list, DGMF will setup a stream processing pipeline and distribute package ids to individual worker threads.

Note that if you want your adapter to support pagination (ie. `limit` and `offset` as described in the [configuration](#building-dependency-graphs)), you must read the *Java Properties* `dgm.offset` and `dgm.limit` and consider them while implementing the algorithm for retrieving package ids.

Check out existing impementations of the `IdGenerator` interface to learn more, for example at `./src/main/java/Repositories/Nuget/NugetIdGenerator.java`.
```java
public interface IdGenerator {

    List<String> generateIds();

}
```
### Accessing Package Data
In order to download and process packages, you need to provide an implementation of the abstract class `Miner`, which has to at least define the two methods `minePackage` and `parsePackage`. DGMF uses instance of your `Miner` implementation inside its worker threads to transform a *Package Id* into an actual `Package` instance, which will subsequently be stored. 

First, a package id (as generated by you `IdGenerator` implementation) is used to call `minePackage(String packageId)`. This method is supposed to download the corresponding data on the package itself, all artifacts and all dependencies. Depending on you repository's interfaces, this may involove multiple HTTP calls. The data is returned as an instance of `JSONObject`, which DGMF will pass down the transformation pipeline. At this point, the structure and semantics of the JSON object can be arbitrary, depending on your repository.

Afterwards, the output of `minePackage` is passed down the stream and used to invoke `parsePackage(JSONObject p)`. This method is meant to transform the JSON data on the current package (inlcuding artifacts and dependencies) into DGMF's internal, repository-independent data model. This model is represented by the `Package` type, which is returned by `parsePackage`.

Have a look into existing implementations like the `NugetMiner` (`./src/main/java/Repositories/Nuget/NugetMiner.java`) to learn more about providing a custom `Miner` implementation. Details about our data model can be found at `./src/main/java/Model/`.
```java
public abstract class Miner {
    
    public abstract JSONObject minePackage(String packageId);
   
    public abstract Package parsePackage(JSONObject p);

    [...]
}

```
### Resolving Version Ranges
This step is only required if you want to be able to use the *Artifact-to-Artifact* resolution level for dependencies. For this, you need to be able to decide  which artifacts of a package belong to a version range of a dependency. Usually, repositories use some form of domain-specific expression to denote version ranges. To resolve them via DGMF, you need to provide an implementation of the generic class `VersionRangeResolver<T>`, where `T` denotes any suitable intermediate representation of version ranges after parsing.

The abstract superclass provides a number of different caches that make sure a version range expression is parsed only once, and tests for version number containment are not repeated redundantly. The former is realized by having the generic type `T` used for representing the parsing outcome of a textual range expression. The method `T buildVersionRangeRepresentation(String versionRangeSpec)` is used to parse a textual range expression into the intermediate representation of type `T`, which will then be cached and used when appropriate.

Most importantly, the method `isVersionInRange` for a given textual representation (`originalVersionRangeSpec`) and parsed intermediate representation (`versionRangeRepresentation`) decides whether a single version number (`String version`) falls into the version range or not. Outcomes of this method are also cached to avoid duplicate checks.

Most repositories have a notion of dependency version ranges being *fixed*, i.e. referencing exactly one target version. These dependencies are not in fact a range, but a single fixed reference. The method `isRangeSpecification` is used to decide whether a given specification is in fact are *Range* or not (i.e. *fixed*). Furthermore, `normalizeFixedVersionReference` is used to normalize such specifications that are not ranges.

Lastly, before a `VersionRangeResolver` processes anything, an implementation of the method `isValidVersionReference` is used to decided whether the given version range specification is valid and can be processed. If not (e.g. due to illegal characters) it is discarded.

You can find more about the inner working of the `VersionRangeResolver<T>` at `./src/main/java/Repositories/VersionRangeResolver.java`, or see some demo implementations for the existing repositories.


```java
public abstract class VersionRangeResolver<T> {

    /**
     * Checks whether the given dependency specification is a range specification or not.
     * @param potentialVersionRangeSpec The dependency specification to check
     * @return True if the specification is a range specification, false otherwise
     */
    protected abstract boolean isRangeSpecification(String potentialVersionRangeSpec);

    /**
     * Normalizes a fixed version reference so that it can be compared to the actual versions of target artifacts. This
     * is necessary e.g. in Maven, where [1.2.3-SNAPSHOT] is a valid fixed version reference, but maps to the target version
     * 1.2.3
     * @param versionRef Fixed version reference to normalize
     * @return The normalized version
     */
    protected abstract String normalizeFixedVersionReference(String versionRef);

    /**
     * Checks whether the given version is contained in the given version range specification. The specification must
     * be a valid range specification, and not a fixed version specification.
     * @param originalVersionRangeSpec Version Range Specification that is NOT a fixed version reference
     * @param versionRangeRepresentation Intermediate, preprocessed representation of the version range
     * @param version The version to check
     * @return True if version is contained in versionRangeSpec, false otherwise
     */
    protected abstract boolean isVersionInRange(String originalVersionRangeSpec, T versionRangeRepresentation, String version);

    /**
     * Processes a version range and builds an intermediate representation. This intermediate representation may carry
     * any information that is necessary to do the actual version containment checks.
     * @param versionRangeSpec The version range specification to process
     * @return Intermediate representation of the version range
     */
    protected abstract T buildVersionRangeRepresentation(String versionRangeSpec);

    /**
     * Checks whether the given versionRef is a valid reference. Returns true if this is the case, false otherwise
     * (e.g. when there are illegal characters contained).
     * @param versionRef Version reference to validate
     * @return true if valid, false otherwise
     */
    protected abstract boolean isValidVersionReference(String versionRef);

```

### Registering the Adapter
Having implemented an `IdGenerator`, `Miner` and optionally `VersionRangeResolver`, all that is left to do is to register these components at DGMF's registry. For this, you have to add your adapter in the `RepositoryController` at `./src/main/java/Repositories/RepositoryController.java`. First, you have to extend the list of repository names available:

```java
/* --> Add new repositories here */
public static final String[] repositoryList = {"npm","pypi","maven", "nuget"};
```

Then, add a reference to your IdGenerator implementation:

```java
    public static IdGenerator getIdGenerator(String repositoryName, boolean allowLoadFromFile){
        if(allowLoadFromFile && idImportRequested()){
            return new FileBasedIdGenerator();
        } else {
            return switch (repositoryName) {
                case "npm" -> new NpmIdGenerator();
                case "maven" -> new MavenIdGenerator();
                case "pypi" -> new PyPiIdGenerator();
                case "nuget" -> new NugetIdGenerator();
                /* --> Add new repositories here */
                default -> null;
            };
        }
    }
```

Similiarly, you als have to register your Miner:

```java
    public static Miner getMiner(String repositoryName){
        return switch (repositoryName) {
            case "npm" -> new NpmMiner();
            case "maven" -> new MavenMiner();
            case "pypi" -> new PyPiMiner();
            case "nuget" -> new NugetMiner();
            /* --> Add new repositories here */
            default -> null;
        };
    }
```

And finally, register the `VersionRangeResolver` if needed:
```java
    public static void registerAllVersionRangeResolver(GlobalVersionRangeResolver versionRangeResolver){
        versionRangeResolver.registerResolver("maven", new MavenVersionRangeResolver());
        versionRangeResolver.registerResolver("npm", new NpmVersionRangeResolver());
        versionRangeResolver.registerResolver("pypi", new PyPiVersionRangeResolver());
        versionRangeResolver.registerResolver("nuget", new NugetVersionRangeResolver());
        /* --> Add new repositories here */
    }
```
That's it, after compiling you can now select your newly registered repository when building dependency graphs via DGMF.

### Handling Exceptions
All invocations of adapter functionality happens in an exceptions-safe way, meaning that any exception in user-supplied code will be caught and logged instead of crashing the streaming pipeline. That said, we strongly suggest to handle exceptions inside the adapters, where there is appropriate context information to deal with the respective cause. If you want to persist exception information during execution (for the current session), you can use the `Application.ExceptionLogger` class. Via the static method `ExceptionLogger.add` you can persist this information for troubleshooting or additional insights into malformed packages.
