FROM bellsoft/liberica-openjdk-alpine:21.0.7-9

RUN apk update && apk upgrade && apk add --no-cache \
    tini=0.19.0-r3

WORKDIR /deployments
COPY target/gibil-*-SNAPSHOT.jar gibil.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser

CMD [ "/sbin/tini", "--", "java", "-jar", "gibil.jar" ]