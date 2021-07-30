ARG ALPINE_VERSION=3.13
FROM alpine:${ALPINE_VERSION}

ARG JAVA_VERSION=11

# Parameters for the JVM
ENV JVM_OPTS=""

# Application information
ENV APP_NAME=pub-sub-delivered
ENV APP_DIR=/usr/src/${APP_NAME}
ENV APP_MAIN_CLASS=com.fuljo.polimi.middleware.pub_sub_delivered.users.UsersService
ENV APP_ARGS=""

# Install dependencies
RUN apk add --no-cache bash coreutils openjdk${JAVA_VERSION} maven

WORKDIR ${APP_DIR}

# Copy the POM and download dependencies, determine name of the JAR and persist it at runtime
COPY pom.xml ./
RUN mvn dependency:copy-dependencies && \
    mvn verify && \
    BUILD_DIR=$(mvn help:evaluate -Dexpression=project.build.directory -q -DforceStdout) && \
    JAR_NAME=$(mvn help:evaluate -Dexpression=project.build.finalName -q -DforceStdout) && \
    echo APP_JAR_LOCATION="$BUILD_DIR/$JAR_NAME.jar" > .env && \
    echo LIB_DIR="$BUILD_DIR/dependency" >> .env


# Copy and build the source code
COPY . ${APP_DIR}
RUN mvn package

CMD ["./docker-cmd.sh"]


