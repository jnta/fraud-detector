# Stage 1: Build the native image and generate index.bin
FROM --platform=linux/amd64 ghcr.io/graalvm/native-image-community:25 AS build

WORKDIR /home/app
COPY . .

# Remove host-specific gradle.properties so Gradle uses the container's GraalVM 25 JDK
RUN rm -f gradle.properties

# Run IndexGenerator to generate index.bin
RUN ./gradlew generateIndex --no-daemon

# Build the native image
RUN ./gradlew nativeCompile --no-daemon

# Stage 2: Final minimal image
FROM --platform=linux/amd64 debian:bookworm-slim
WORKDIR /app

# Copy the native binary and index.bin from the build stage
COPY --from=build /home/app/build/native/nativeCompile/fraud-detector /app/fraud-detector
COPY --from=build /home/app/index.bin /app/index.bin

# Expose the API port
EXPOSE 8080

# Run the application
ENTRYPOINT ["/app/fraud-detector"]
