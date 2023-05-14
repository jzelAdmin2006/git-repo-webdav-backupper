FROM ubuntu:latest

RUN apt update && apt install -y wget apt-transport-https
RUN mkdir -p /etc/apt/keyrings
RUN wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /etc/apt/keyrings/adoptium.asc
RUN echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list
RUN apt update && apt install -y temurin-17-jdk

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew build
RUN mv ./build/libs/git-repo-webdav-backupper-0.0.1-SNAPSHOT.jar ./app.jar
RUN find . ! -name 'app.jar' -type f -exec rm -f {} +
RUN mkdir /tmpwork

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
