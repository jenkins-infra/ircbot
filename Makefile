IMAGENAME=jenkinsciinfra/ircbot
TAG=$(shell date '+%Y%m%d_%H%M%S')

# Build Info
VERSION_FILE_DIR=src/main/resources
VERSION_FILE=${VERSION_FILE_DIR}/versionInfo.properties
VERSION_BUILD_NUMBER=$(BUILD_NUMBER)
VERSION_BUILD_DATE=$(shell date '+%Y%m%d_%H%M%S')
VERSION_BUILD_ID=$(BUILD_ID)
VERSION_BUILD_URL=$(BUILD_URL)
VERSION_GIT_COMMIT=$(GIT_COMMIT)

target/ircbot-1.0-SNAPSHOT-bin.zip : ${VERSION_FILE}
	mvn install

${VERSION_FILE} : ${VERSION_FILE_DIR}
	echo buildNumber=${VERSION_BUILD_NUMBER} > ${VERSION_FILE}
	echo buildDate=${VERSION_BUILD_DATE} >> ${VERSION_FILE}
	echo buildID=${VERSION_BUILD_ID} >> ${VERSION_FILE}
	echo buildURL=${VERSION_BUILD_URL} >> ${VERSION_FILE}
	echo gitCommit=${VERSION_GIT_COMMIT} >> ${VERSION_FILE}

${VERSION_FILE_DIR} :
	mkdir ${VERSION_FILE_DIR}

image : clean target/ircbot-1.0-SNAPSHOT-bin.zip
	docker build -t ${IMAGENAME} .

run :
	docker run -P --rm -i -t ${IMAGENAME}

tag :
	docker tag ${IMAGENAME} ${IMAGENAME}:${TAG}

push :
	docker push ${IMAGENAME}

clean:
	rm -rf target/*
	rm -f ${VERSION_FILE} 

