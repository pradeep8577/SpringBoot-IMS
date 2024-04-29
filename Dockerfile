# Use an OpenJDK runtime as a base image
FROM openjdk:21

# Set the working directory inside the container
WORKDIR /app

# Copy the packaged JAR file into the container at the defined working directory
COPY ./target/IMS-0.0.1-SNAPSHOT.jar /app

# Expose the port that your application runs on
EXPOSE 8000

# Specify the command to run your application when the container starts
CMD ["java", "-jar", "IMS-0.0.1-SNAPSHOT.jar"]
