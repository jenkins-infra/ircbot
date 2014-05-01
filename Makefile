IMAGENAME=jenkinsciinfra/ircbot
TAG=$(shell date '+%Y%m%d_%H%M%S')

target/ircbot-1.0-SNAPSHOT-bin.zip :
	mvn install

image : target/ircbot-1.0-SNAPSHOT-bin.zip
	docker build -t ${IMAGENAME} .

run :
	docker run -P --rm -i -t ${IMAGENAME}

tag :
	docker tag ${IMAGENAME} ${IMAGENAME}:${TAG}

push :
	docker push ${IMAGENAME}


