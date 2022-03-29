FROM navikt/java:17-appdynamics
COPY build/libs/app.jar /app/
COPY init.sh /init-scripts/init.sh

ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom \
               -Dhttps.proxyHost=webproxy-nais.nav.no \
               -Dhttps.proxyPort=8088 \
               -Dhttp.nonProxyHosts=*.adeo.no|*.preprod.local"
