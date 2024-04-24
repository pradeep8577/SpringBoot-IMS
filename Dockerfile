FROM openjdk:17
EXPOSE 8081
ADD target/SpringBoot-IMS.jar SpringBoot-IMS.jar
ENTRYPOINT ["java","-jar","/devops-integration.jar"]
