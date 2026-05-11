FROM bellsoft/liberica-openjre-alpine:26-37 AS builder
WORKDIR /builder
COPY target/*-SNAPSHOT.jar application.jar
RUN java -Djarmode=tools  -jar application.jar extract --layers --destination extracted

FROM bellsoft/liberica-openjre-alpine:26-37
RUN apk update && apk upgrade && apk add --no-cache tini=0.19.0-r3 unzip=6.0-r16 wget=1.25.0-r2
WORKDIR /deployments

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod a+x /docker-entrypoint.sh
ENTRYPOINT ["sh", "/docker-entrypoint.sh"]

RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
CMD [ "/sbin/tini", "--", "java", "-jar", "application.jar" ]
