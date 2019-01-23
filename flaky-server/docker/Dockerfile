FROM ubuntu:14.04

# TODO clean up

RUN apt-get -y clean && apt-get -y update && apt-get -y upgrade && apt-get -y install software-properties-common
RUN add-apt-repository ppa:openjdk-r/ppa
RUN apt-get -y update && apt-get -y upgrade && apt-get -y install wget unzip python-minimal openjdk-11-jdk sudo iptables net-tools iproute2

RUN wget -P /tmp https://github.com/tomakehurst/saboteur/releases/download/v0.7/saboteur_0.7_all.deb && dpkg --install /tmp/saboteur_0.7_all.deb

COPY flaky-server.zip /app/app.zip
RUN unzip /app/app.zip -d /app

WORKDIR /app
COPY start.sh /app
RUN chmod +x /app/start.sh

ENTRYPOINT ["/app/start.sh"]

