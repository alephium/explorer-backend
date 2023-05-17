assembly:
	sbt app/assembly

docker:
	sbt app/docker

clean:
	sbt clean

format:
	sbt scalafmt test:scalafmt scalafmtSbt 

style:
	sbt scalastyle test:scalastyle

test:
	sbt test

test-all: clean format style test 
	sbt unidoc

publish-local:
	sbt publishLocal

run:
	sbt app/run

update-openapi:
	sbt "tools/runMain org.alephium.tools.OpenApiUpdate"

benchmark-run:
	sbt "benchmark/jmh:run"
	
create-db:
	# Env variables as defined in `application.conf`
	psql \
		-h $(or $(DB_HOST), localhost) \
		-p $(or $(DB_PORT), 5432) \
		-U $(or $(DB_USER), postgres) \
		-c "CREATE DATABASE $(or $(DB_NAME), explorer)"

restore-db:
	#Make sure to define ALEPHIUM_NETWORK to "mainnet" or "testnet"
	curl $(shell curl -L -s "https://s3.eu-central-1.amazonaws.com/archives.alephium.org/archives/${ALEPHIUM_NETWORK}/explorer-db/_latest.txt") -L ${url} | \
	gunzip -c | \
	psql \
		-h $(or $(DB_HOST), localhost) \
		-p $(or $(DB_PORT), 5432) \
		-U $(or $(DB_USER), postgres) \
		-d $(or $(DB_NAME), explorer)	
