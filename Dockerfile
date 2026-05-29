FROM ubuntu:latest

RUN mkdir -p /usr/app

WORKDIR /usr/app
COPY ./app.jar ./

RUN apt update
RUN apt -y upgrade
RUN apt -y install wget gpg
RUN wget -O - https://apt.corretto.aws/corretto.key | gpg --dearmor -o /usr/share/keyrings/corretto-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" | tee /etc/apt/sources.list.d/corretto.list
RUN apt update
RUN apt -y install java-21-amazon-corretto-jdk

EXPOSE 80

# Maybe pass a production profile?
#CMD ["java", "-server", "-XX:+UseZGC", "-XX:+ZGenerational", "-jar", "/usr/app/app.jar"]
CMD ["java", "-server", "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:MaxRAMPercentage=66", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseContainerSupport", "-jar", "/usr/app/app.jar"]
