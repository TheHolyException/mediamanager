# Use a base image with Java 17
FROM eclipse-temurin:17-jre-jammy

# Set working directory
WORKDIR /app

# Install required system dependencies
RUN apt-get update && apt-get install -y \
    maven \
    && rm -rf /var/lib/apt/lists/*

# Copy Maven files and download dependencies
COPY pom.xml .
COPY src ./src
COPY www ./www

# Build the application
RUN mvn clean package -DskipTests

# Create a non-root user
RUN useradd -m mediamanager
USER mediamanager

# Create necessary directories
RUN mkdir -p /home/mediamanager/config \
    && mkdir -p /home/mediamanager/logs \
    && mkdir -p /home/mediamanager/downloads

# Expose the default port
EXPOSE 8080

# Set environment variables
ENV CONFIG_DIR=/home/mediamanager/config
ENV DOWNLOAD_DIR=/home/mediamanager/downloads
ENV LOG_DIR=/home/mediamanager/logs

# Copy the built JAR file
COPY --chown=mediamanager:mediamanager target/MediaManager-1.0.jar /app/

# Set the entry point
ENTRYPOINT ["java", "-jar", "MediaManager-1.0.jar"]

# Default command (can be overridden)
CMD []
