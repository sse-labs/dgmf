FROM maven:3.8.5-openjdk-17 as build
RUN mkdir -p /usr/miner
COPY ./src/ /usr/miner/src/
COPY ./pom.xml /usr/miner/pom.xml
RUN cd /usr/miner && \
    mvn clean package -DskipTests

FROM openjdk:17-alpine
COPY --from=build /usr/miner/target/dgmf.jar dependencyGraphMiner.jar
COPY system.properties system.properties
WORKDIR ./
CMD ["java", "-jar", "dependencyGraphMiner.jar", "start"]