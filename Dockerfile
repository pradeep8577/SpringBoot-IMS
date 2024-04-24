FROM openjdk:17
EXPOSE 8081
ADD target/springboot-ims.jar springboot-ims.jar
ENTRYPOINT ["java","-jar","/springboot-ims.jar"]
