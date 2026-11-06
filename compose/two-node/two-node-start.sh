#!/bin/sh
# Writes this node's XNAT configuration, then starts XNAT the way the image does.
# FOR TESTING ONLY: the credentials below are fixed and public.
set -eu

: "${NODE_ID:?}"
: "${PRIMARY_NODE:?}"

mkdir -p "$XNAT_HOME/config"

# Both nodes use the shared broker, so either node can take queued work, and the dist-events
# plugin can reach the other node.
cat > "$XNAT_HOME/config/xnat-conf.properties" << EOF
datasource.driver=org.postgresql.Driver
datasource.url=jdbc:postgresql://postgres/xnat
datasource.username=xnat
datasource.password=xnat

hibernate.dialect=org.hibernate.dialect.PostgreSQL9Dialect
hibernate.hbm2ddl.auto=update
hibernate.show_sql=false
hibernate.cache.use_second_level_cache=true
hibernate.cache.use_query_cache=true

spring.activemq.broker-url=tcp://activemq:61616
spring.activemq.user=xnat
spring.activemq.password=xnat
xnat.is_primary_node=$PRIMARY_NODE

spring.http.multipart.max-file-size=1073741824
spring.http.multipart.max-request-size=1073741824
EOF

# Recorded in xdat_user_login.node_id, among other things.
echo "node.id=$NODE_ID" > "$XNAT_HOME/config/node-conf.properties"

exec /usr/local/bin/entrypoint.sh /usr/local/tomcat/bin/catalina.sh run
