FROM openjdk:8
MAINTAINER Tahsin Kurc 

RUN apt-get -y update
RUN apt-get -y install python-pip maven
RUN pip install --upgrade pip
RUN pip install pyyaml pymongo

RUN mkdir -p /home/QuIPutils
COPY . /home/QuIPutils

WORKDIR /home/QuIPutils
RUN mvn install
RUN cp run_overlays.sh /bin/.

CMD ["/bin/bash"]
